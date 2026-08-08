#!/usr/bin/env python3
"""Run TimeSformer head-only CelebDF adaptation with a continuous 4fps sampler."""
from __future__ import annotations

import argparse
import csv
import json
import random
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from statistics import mean, median

import cv2
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import torch
import torch.nn as nn
from sklearn.metrics import accuracy_score, confusion_matrix, f1_score, precision_score, recall_score, roc_auc_score
from torch.utils.data import DataLoader, Dataset

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

from video_clip_transformer_common import build_clip_score_breakdown, normalize_face_crops
from video_timesformer_infer import CLIP_SIZE, DEFAULT_PRETRAINED, MODEL_ID, TimeSformerDetectorLite, clip_to_tensor
from video_videomae_finetune import collect_exclude_paths, resolve
from video_xception_infer import build_item, classification_row_from_logits, crop_face, write_per_file_json


@dataclass(frozen=True)
class SamplerConfig:
    clip_frames: int = 8
    sampling_fps: float = 4.0
    clip_duration_sec: float = 2.0
    max_clips: int = 4
    max_gap_ms: float = 500.0
    min_valid_face_ratio: float = 0.75
    clip_size: int = CLIP_SIZE


def safe_float(value: float | int | None, default: float = 0.0) -> float:
    if value is None:
        return default
    return float(value)


def video_meta(video_path: Path) -> dict:
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        return {"fps": 0.0, "total_frames": 0, "duration": 0.0}
    fps = safe_float(cap.get(cv2.CAP_PROP_FPS), 0.0)
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    cap.release()
    duration = float(total / fps) if fps > 0 else 0.0
    return {"fps": fps, "total_frames": total, "duration": duration}


def candidate_start_indices(total_frames: int, frame_step: int, cfg: SamplerConfig) -> list[int]:
    needed_span = (cfg.clip_frames - 1) * frame_step
    if total_frames <= needed_span:
        return []
    max_start = total_frames - 1 - needed_span
    if cfg.max_clips <= 1:
        return [0]
    starts = [int(round(i * max_start / max(1, cfg.max_clips - 1))) for i in range(cfg.max_clips)]
    deduped: list[int] = []
    for start in starts:
        start = max(0, min(max_start, start))
        if start not in deduped:
            deduped.append(start)
    return deduped


def read_frame_at(cap: cv2.VideoCapture, frame_index: int) -> np.ndarray | None:
    cap.set(cv2.CAP_PROP_POS_FRAMES, frame_index)
    ok, frame = cap.read()
    if not ok or frame is None:
        return None
    return frame


def fill_missing_crops(crops: list[np.ndarray | None]) -> list[np.ndarray]:
    valid_positions = [idx for idx, crop in enumerate(crops) if crop is not None]
    if not valid_positions:
        return []
    filled: list[np.ndarray] = []
    for idx, crop in enumerate(crops):
        if crop is not None:
            filled.append(crop)
            continue
        nearest = min(valid_positions, key=lambda pos: abs(pos - idx))
        filled.append(crops[nearest])
    return [crop for crop in filled if crop is not None]


def continuous_clip_windows(
    video_path: Path,
    face_cascade: cv2.CascadeClassifier,
    cfg: SamplerConfig,
) -> tuple[list[dict], dict]:
    meta = video_meta(video_path)
    fps = meta["fps"]
    total_frames = meta["total_frames"]
    if fps <= 0 or total_frames <= 0:
        return [], {**meta, "reason": "VIDEO_OPEN_FAILED", "frame_step": 0}

    frame_step = max(1, int(round(fps / cfg.sampling_fps)))
    starts = candidate_start_indices(total_frames, frame_step, cfg)
    if not starts:
        return [], {**meta, "reason": "SHORT_VIDEO", "frame_step": frame_step}

    cap = cv2.VideoCapture(str(video_path))
    windows: list[dict] = []
    for clip_id, start in enumerate(starts):
        frame_indices = [start + i * frame_step for i in range(cfg.clip_frames)]
        timestamps_ms = [idx * 1000.0 / fps for idx in frame_indices]
        timestamp_gaps_ms = [timestamps_ms[i + 1] - timestamps_ms[i] for i in range(len(timestamps_ms) - 1)]
        max_gap_ms = max(timestamp_gaps_ms) if timestamp_gaps_ms else 0.0
        crops: list[np.ndarray | None] = []
        detected = 0
        for frame_index in frame_indices:
            frame = read_frame_at(cap, frame_index)
            crop = crop_face(frame, face_cascade, size=cfg.clip_size) if frame is not None else None
            if crop is not None:
                detected += 1
            crops.append(crop)

        valid_face_ratio = detected / max(1, cfg.clip_frames)
        warning: list[str] = []
        if max_gap_ms > cfg.max_gap_ms:
            warning.append("NON_CONTIGUOUS_CLIP")
        if valid_face_ratio < cfg.min_valid_face_ratio:
            warning.append("LOW_FACE_RATIO")
        filled_crops = fill_missing_crops(crops)
        if len(filled_crops) < cfg.clip_frames:
            warning.append("SHORT_CLIP")

        if not warning or warning == []:
            windows.append(
                {
                    "clip_id": clip_id,
                    "crops": filled_crops[: cfg.clip_frames],
                    "frame_indices": frame_indices,
                    "timestamps_ms": timestamps_ms,
                    "timestamp_gaps_ms": timestamp_gaps_ms,
                    "max_gap_ms": max_gap_ms,
                    "mean_gap_ms": float(mean(timestamp_gaps_ms)) if timestamp_gaps_ms else 0.0,
                    "valid_face_ratio": valid_face_ratio,
                    "face_detected_count": detected,
                    "warning": "",
                }
            )
    cap.release()

    reason = "OK" if windows else "INSUFFICIENT_CONTINUOUS_CLIPS"
    return windows[: cfg.max_clips], {**meta, "reason": reason, "frame_step": frame_step}


def list_train_videos(
    fake_dir: Path,
    real_dir: Path,
    excluded: set[str],
    max_per_class: int,
    seed: int,
    face_cascade: cv2.CascadeClassifier,
    cfg: SamplerConfig,
) -> tuple[list[tuple[Path, int]], list[dict]]:
    rng = random.Random(seed)
    samples: list[tuple[Path, int]] = []
    rejected: list[dict] = []
    for label, directory in ((1, fake_dir), (0, real_dir)):
        candidates = [p.resolve() for p in sorted(directory.glob("*.mp4")) if str(p.resolve()) not in excluded]
        rng.shuffle(candidates)
        picked = 0
        label_name = "fake" if label == 1 else "real"
        print(f"scanning {label_name}: {len(candidates)} mp4 in {directory}", flush=True)
        for path in candidates:
            if picked >= max_per_class:
                break
            windows, meta = continuous_clip_windows(path, face_cascade, cfg)
            if windows:
                samples.append((path, label))
                picked += 1
                if picked % 10 == 0:
                    print(f"  {label_name}: picked {picked}/{max_per_class}", flush=True)
            else:
                rejected.append(
                    {
                        "stage": "train",
                        "video_id": path.name,
                        "video_path": str(path),
                        "label": label_name,
                        "status": meta.get("reason"),
                        "fps": round(meta.get("fps", 0.0), 6),
                        "duration": round(meta.get("duration", 0.0), 6),
                        "total_frames": meta.get("total_frames", 0),
                    }
                )
        print(f"  {label_name}: selected {picked} videos", flush=True)
        if picked == 0:
            raise SystemExit(f"No usable {label_name} training videos in {directory}")
    rng.shuffle(samples)
    return samples, rejected


class ContinuousClipDataset(Dataset):
    def __init__(self, samples: list[tuple[Path, int]], face_cascade: cv2.CascadeClassifier, cfg: SamplerConfig, seed: int):
        self.samples = samples
        self.face_cascade = face_cascade
        self.cfg = cfg
        self.rng = random.Random(seed)

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, idx: int):
        path, label = self.samples[idx]
        windows, meta = continuous_clip_windows(path, self.face_cascade, self.cfg)
        if not windows:
            raise RuntimeError(f"{meta.get('reason')}:{path.name}")
        window = self.rng.choice(windows)
        arr = normalize_face_crops(window["crops"])
        tensor = torch.from_numpy(arr).permute(0, 3, 1, 2)
        return tensor, torch.tensor(label, dtype=torch.long)


def collate_clips(batch):
    tensors, labels = zip(*batch)
    return torch.stack(tensors, dim=0), torch.stack(labels, dim=0)


def train_one_epoch(model, loader, criterion, optimizer, device) -> tuple[float, float]:
    model.train()
    total_loss = 0.0
    correct = 0
    total = 0
    mean_arr = np.array([0.485, 0.456, 0.406], dtype=np.float32)
    std_arr = np.array([0.229, 0.224, 0.225], dtype=np.float32)
    for clips, labels in loader:
        labels = labels.to(device)
        logits_list = []
        for i in range(clips.size(0)):
            sample = clips[i]
            crop_list = []
            for t in range(sample.size(0)):
                frame = sample[t].permute(1, 2, 0).numpy()
                frame = np.clip(frame * std_arr + mean_arr, 0.0, 1.0)
                crop_list.append((frame * 255.0).astype(np.uint8))
            logits_list.append(model.forward_logits(clip_to_tensor(crop_list, device)))
        logits = torch.cat(logits_list, dim=0)
        loss = criterion(logits, labels)
        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        optimizer.step()
        total_loss += float(loss.item()) * labels.size(0)
        preds = logits.argmax(dim=1)
        correct += int((preds == labels).sum().item())
        total += labels.size(0)
    return total_loss / max(1, total), correct / max(1, total)


@torch.no_grad()
def infer_one_video(model, video_path: Path, label: str, face_cascade, device, weights: Path, run_id: str, cfg: SamplerConfig) -> tuple[dict, list[dict], dict | None]:
    windows, meta = continuous_clip_windows(video_path, face_cascade, cfg)
    audit_rows: list[dict] = []
    for window in windows:
        audit_rows.append(
            {
                "video_id": video_path.name,
                "video_path": str(video_path),
                "dataset": "celebdf",
                "profile": "celebdf",
                "label": label,
                "clip_id": window["clip_id"],
                "frame_indices": json.dumps(window["frame_indices"]),
                "timestamps_ms": json.dumps([round(x, 4) for x in window["timestamps_ms"]]),
                "timestamp_gaps_ms": json.dumps([round(x, 4) for x in window["timestamp_gaps_ms"]]),
                "max_gap_ms": round(window["max_gap_ms"], 6),
                "mean_gap_ms": round(window["mean_gap_ms"], 6),
                "valid_face_ratio": round(window["valid_face_ratio"], 6),
                "face_detected_count": window["face_detected_count"],
                "warning": window["warning"],
            }
        )

    if not windows:
        item = build_item(
            video_path,
            {
                "file": video_path.name,
                "status": "no_face" if meta.get("reason") != "SHORT_VIDEO" else "short_clip",
                "fake_score": None,
                "pred_label": None,
                "frames_used": 0,
                "score_breakdown": {
                    "method": "timesformer_continuous_sampler_head_only_outputs",
                    "threshold": 0.5,
                    "clip_frames": cfg.clip_frames,
                    "clip_size": cfg.clip_size,
                    "max_clips": cfg.max_clips,
                    "clips_used": 0,
                    "per_clip": [],
                    "reason": meta.get("reason"),
                },
            },
            run_id=run_id,
            weights=weights,
            device=device,
            ground_truth_label=label,
            model_id=MODEL_ID,
        )
        failure = {
            "video_id": video_path.name,
            "video_path": str(video_path),
            "label": label,
            "status": item["status"],
            "reason": meta.get("reason"),
            "fps": round(meta.get("fps", 0.0), 6),
            "duration": round(meta.get("duration", 0.0), 6),
            "total_frames": meta.get("total_frames", 0),
            "num_clips": 0,
        }
        return item, audit_rows, failure

    per_clip = []
    for clip_index, window in enumerate(windows):
        clip = clip_to_tensor(window["crops"], device)
        feature_tensor = model.forward_features(clip)
        logits = model.head(feature_tensor).detach().cpu().numpy().reshape(-1)
        features = feature_tensor.detach().cpu().numpy().reshape(-1)
        row = classification_row_from_logits(float(logits[0]), float(logits[1]), threshold=0.5)
        row.update(
            {
                "clip_index": clip_index,
                "frame_indices": window["frame_indices"],
                "clip_start_frame": window["frame_indices"][0],
                "clip_end_frame": window["frame_indices"][-1],
                "timestamps_ms": [round(x, 4) for x in window["timestamps_ms"]],
                "timestamp_gaps_ms": [round(x, 4) for x in window["timestamp_gaps_ms"]],
                "max_gap_ms": round(window["max_gap_ms"], 6),
                "mean_gap_ms": round(window["mean_gap_ms"], 6),
                "valid_face_ratio": round(window["valid_face_ratio"], 6),
                "face_detected_count": window["face_detected_count"],
                "warning": window["warning"],
                "representation": {
                    "dim": int(features.shape[0]),
                    "l2_norm": round(float(np.linalg.norm(features)), 4),
                    "mean": round(float(np.mean(features)), 4),
                    "std": round(float(np.std(features)), 4),
                    "min": round(float(np.min(features)), 4),
                    "max": round(float(np.max(features)), 4),
                },
            }
        )
        per_clip.append(row)

    breakdown = build_clip_score_breakdown(
        per_clip,
        method="timesformer_continuous_sampler_head_only_outputs",
        threshold=0.5,
        frames_sampled=len(windows) * cfg.clip_frames,
        frames_without_face=sum(cfg.clip_frames - w["face_detected_count"] for w in windows),
        clip_frames=cfg.clip_frames,
        clip_size=cfg.clip_size,
        max_clips=cfg.max_clips,
    )
    item = build_item(
        video_path,
        {
            "file": video_path.name,
            "status": "ok",
            "fake_score": breakdown["aggregate_fake_score"],
            "pred_label": breakdown["aggregate"]["pred_label"],
            "frames_used": breakdown["frames_with_face"],
            "score_breakdown": breakdown,
        },
        run_id=run_id,
        weights=weights,
        device=device,
        ground_truth_label=label,
        model_id=MODEL_ID,
    )
    return item, audit_rows, None


def score_metrics(items: list[dict]) -> dict:
    scored = [item for item in items if item.get("status") == "ok" and item.get("fake_score") is not None]
    y = [1 if item["ground_truth_label"] == "fake" else 0 for item in scored]
    scores = [float(item["fake_score"]) for item in scored]
    preds = [1 if score >= 0.5 else 0 for score in scores]
    tn, fp, fn, tp = confusion_matrix(y, preds, labels=[0, 1]).ravel()
    fake_scores = [float(item["fake_score"]) for item in scored if item["ground_truth_label"] == "fake"]
    real_scores = [float(item["fake_score"]) for item in scored if item["ground_truth_label"] == "real"]
    return {
        "threshold": 0.5,
        "n_total": len(items),
        "n_scored": len(scored),
        "no_face_or_insufficient": len(items) - len(scored),
        "auc": round(float(roc_auc_score(y, scores)), 6) if len(set(y)) == 2 else None,
        "accuracy": round(float(accuracy_score(y, preds)), 6),
        "precision": round(float(precision_score(y, preds, zero_division=0)), 6),
        "recall": round(float(recall_score(y, preds, zero_division=0)), 6),
        "f1": round(float(f1_score(y, preds, zero_division=0)), 6),
        "confusion_matrix": {"tn": int(tn), "fp": int(fp), "fn": int(fn), "tp": int(tp)},
        "fake_mean": round(float(mean(fake_scores)), 6),
        "real_mean": round(float(mean(real_scores)), 6),
        "fake_median": round(float(median(fake_scores)), 6),
        "real_median": round(float(median(real_scores)), 6),
    }


def write_csv(path: Path, rows: list[dict], fields: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field, "") for field in fields})


def plot_distribution(items: list[dict], metrics: dict, out_path: Path) -> None:
    scored = [item for item in items if item.get("status") == "ok" and item.get("fake_score") is not None]
    fake = [float(item["fake_score"]) for item in scored if item["ground_truth_label"] == "fake"]
    real = [float(item["fake_score"]) for item in scored if item["ground_truth_label"] == "real"]
    fig, axes = plt.subplots(1, 3, figsize=(16, 5))
    bins = np.linspace(0, 1, 21)
    axes[0].hist(fake, bins=bins, alpha=0.65, label=f"fake mean={metrics['fake_mean']:.3f}", color="#ef7777")
    axes[0].hist(real, bins=bins, alpha=0.65, label=f"real mean={metrics['real_mean']:.3f}", color="#7098ee")
    axes[0].axvline(0.5, color="black", linestyle=":", label="threshold=0.5")
    axes[0].set_title("Histogram")
    axes[0].set_xlabel("fake_score")
    axes[0].set_ylabel("video count")
    axes[0].legend()
    axes[0].grid(alpha=0.25)
    axes[1].violinplot([fake, real], showmeans=True, showmedians=True)
    axes[1].set_xticks([1, 2], ["fake", "real"])
    axes[1].axhline(0.5, color="black", linestyle=":")
    axes[1].set_title("Violin")
    axes[1].set_ylabel("fake_score")
    axes[1].grid(alpha=0.25)
    axes[2].boxplot([fake, real], showmeans=True)
    axes[2].set_xticks([1, 2])
    axes[2].set_xticklabels(["fake", "real"])
    axes[2].axhline(0.5, color="black", linestyle=":")
    axes[2].set_title("Boxplot")
    axes[2].grid(alpha=0.25)
    fig.suptitle(
        f"TimeSformer Continuous Sampler CelebDF Head-only (20260624)\n"
        f"AUC={metrics['auc']}, acc@0.5={metrics['accuracy']}, F1={metrics['f1']}",
        fontsize=14,
        fontweight="bold",
    )
    fig.tight_layout()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(out_path, dpi=160)
    plt.close(fig)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--train-fake-dir", default="data/train/video/celeb-df-v2/fake")
    parser.add_argument("--train-real-dir", default="data/train/video/celeb-df-v2/real")
    parser.add_argument("--eval-fake-dir", default="data/benchmark/video-benchmark-datasets/celebdf/fake")
    parser.add_argument("--eval-real-dir", default="data/benchmark/video-benchmark-datasets/celebdf/real")
    parser.add_argument("--exclude-dirs", nargs="*", default=["data/test/video/celeb-df-v2/fake", "data/test/video/celeb-df-v2/real", "data/benchmark/video-benchmark-datasets/celebdf/fake", "data/benchmark/video-benchmark-datasets/celebdf/real"])
    parser.add_argument("--init-weights", default="models/test/video/timesformer/v1.0.0/timesformer_finetuned.pth")
    parser.add_argument("--output", default="models/test/video/timesformer/v1.0.0/timesformer_continuous_sampler_celebdf_head_only_20260624.pth")
    parser.add_argument("--run-id", default="timesformer_continuous_sampler_celebdf_head_only_20260624")
    parser.add_argument("--max-per-class", type=int, default=100)
    parser.add_argument("--epochs", type=int, default=3)
    parser.add_argument("--batch-size", type=int, default=1)
    parser.add_argument("--lr", type=float, default=2e-5)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--pretrained-id", default=DEFAULT_PRETRAINED)
    parser.add_argument("--clip-frames", type=int, default=8)
    parser.add_argument("--sampling-fps", type=float, default=4.0)
    parser.add_argument("--clip-duration-sec", type=float, default=2.0)
    parser.add_argument("--max-clips", type=int, default=4)
    parser.add_argument("--max-gap-ms", type=float, default=500.0)
    parser.add_argument("--min-valid-face-ratio", type=float, default=0.75)
    args = parser.parse_args()

    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)

    root = Path(args.root).resolve()
    cfg = SamplerConfig(
        clip_frames=args.clip_frames,
        sampling_fps=args.sampling_fps,
        clip_duration_sec=args.clip_duration_sec,
        max_clips=args.max_clips,
        max_gap_ms=args.max_gap_ms,
        min_valid_face_ratio=args.min_valid_face_ratio,
        clip_size=CLIP_SIZE,
    )
    output = resolve(root, args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    init_weights = resolve(root, args.init_weights)
    infer_dir = root / "results/infer" / args.run_id
    eval_dir = root / "results/eval" / args.run_id
    analysis_dir = root / "results/analysis"
    figures_dir = root / "results/figures"
    json_dir = infer_dir / "json"
    for directory in (infer_dir, eval_dir, analysis_dir, figures_dir, json_dir):
        directory.mkdir(parents=True, exist_ok=True)

    face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")
    excluded = collect_exclude_paths(root, args.exclude_dirs)
    samples, rejected_train = list_train_videos(
        resolve(root, args.train_fake_dir),
        resolve(root, args.train_real_dir),
        excluded,
        args.max_per_class,
        args.seed,
        face_cascade,
        cfg,
    )

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = TimeSformerDetectorLite(pretrained_id=args.pretrained_id).to(device)
    if init_weights.is_file():
        model.load_state_dict(torch.load(init_weights, map_location=device, weights_only=False), strict=True)
        print(f"init weights: {init_weights}", flush=True)

    for param in model.backbone.parameters():
        param.requires_grad = False
    trainable = [param for param in model.parameters() if param.requires_grad]
    optimizer = torch.optim.AdamW(trainable, lr=args.lr)
    criterion = nn.CrossEntropyLoss()
    dataset = ContinuousClipDataset(samples, face_cascade, cfg, seed=args.seed)
    loader = DataLoader(dataset, batch_size=args.batch_size, shuffle=True, num_workers=0, collate_fn=collate_clips)

    print(f"run_id: {args.run_id}", flush=True)
    print(f"samples: {len(samples)} (fake={sum(1 for _, y in samples if y == 1)}, real={sum(1 for _, y in samples if y == 0)})", flush=True)
    print(f"sampler: continuous, clip_frames={cfg.clip_frames}, sampling_fps={cfg.sampling_fps}, max_clips={cfg.max_clips}", flush=True)
    print(f"head-only: backbone freeze=True, lr={args.lr}, epochs={args.epochs}, batch={args.batch_size}", flush=True)

    history: list[dict] = []
    for epoch in range(1, args.epochs + 1):
        loss, acc = train_one_epoch(model, loader, criterion, optimizer, device)
        row = {"epoch": epoch, "loss": round(loss, 4), "train_acc": round(acc, 4)}
        history.append(row)
        print(f"epoch {epoch}/{args.epochs} loss={row['loss']} train_acc={row['train_acc']}", flush=True)

    torch.save(model.state_dict(), output)
    meta = {
        "created_at": datetime.now(timezone.utc).isoformat(),
        "run_id": args.run_id,
        "model": "timesformer",
        "pretrained_id": args.pretrained_id,
        "init_weights": str(init_weights),
        "output": str(output),
        "sampler": "continuous",
        "clip_frames": cfg.clip_frames,
        "sampling_fps": cfg.sampling_fps,
        "clip_duration_sec": cfg.clip_duration_sec,
        "max_clips": cfg.max_clips,
        "max_gap_ms": cfg.max_gap_ms,
        "min_valid_face_ratio": cfg.min_valid_face_ratio,
        "max_per_class": args.max_per_class,
        "epochs": args.epochs,
        "batch_size": args.batch_size,
        "lr": args.lr,
        "backbone_freeze": True,
        "partial_unfreeze": False,
        "num_samples": len(samples),
        "history": history,
        "rejected_train_count": len(rejected_train),
    }
    output.with_suffix(".meta.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")

    model.eval()
    items: list[dict] = []
    audit_rows: list[dict] = []
    failure_rows: list[dict] = rejected_train
    for label, directory in (("fake", resolve(root, args.eval_fake_dir)), ("real", resolve(root, args.eval_real_dir))):
        for video_path in sorted(directory.glob("*.mp4")):
            item, clip_rows, failure = infer_one_video(model, video_path.resolve(), label, face_cascade, device, output, args.run_id, cfg)
            items.append(item)
            audit_rows.extend(clip_rows)
            if failure:
                failure_rows.append({**failure, "stage": "eval"})
            write_per_file_json(json_dir, item)
            print(f"{video_path.name}: {item['status']} pred={item.get('pred_label')} fake_score={item.get('fake_score')}", flush=True)

    metrics = score_metrics(items)
    payload = {
        "run_id": args.run_id,
        "model": MODEL_ID,
        "sampler": "continuous",
        "training": "head_only",
        "threshold": 0.5,
        "clip_frames": cfg.clip_frames,
        "sampling_fps": cfg.sampling_fps,
        "clip_duration_sec": cfg.clip_duration_sec,
        "max_clips": cfg.max_clips,
        "max_gap_ms": cfg.max_gap_ms,
        "min_valid_face_ratio": cfg.min_valid_face_ratio,
        "weights": str(output),
        "init_weights": str(init_weights),
        "device": str(device),
        "items": items,
    }
    (infer_dir / "predictions.json").write_text(json.dumps(payload, indent=2), encoding="utf-8")
    (eval_dir / "metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")

    prefix = "timesformer_continuous_sampler_celebdf_head_only_20260624"
    pred_rows = []
    for item in items:
        pred_rows.append(
            {
                "video_id": item.get("file"),
                "video_path": item.get("input_path", ""),
                "dataset": "celebdf",
                "profile": "celebdf",
                "label": item.get("ground_truth_label"),
                "status": item.get("status"),
                "score": item.get("fake_score"),
                "pred_label": item.get("pred_label"),
                "correct": item.get("correct"),
                "num_clips": item.get("score_breakdown", {}).get("clips_used"),
            }
        )
    write_csv(
        analysis_dir / f"{prefix}_predictions.csv",
        pred_rows,
        ["video_id", "video_path", "dataset", "profile", "label", "status", "score", "pred_label", "correct", "num_clips"],
    )
    write_csv(
        analysis_dir / f"{prefix}_clip_audit.csv",
        audit_rows,
        [
            "video_id",
            "video_path",
            "dataset",
            "profile",
            "label",
            "clip_id",
            "frame_indices",
            "timestamps_ms",
            "timestamp_gaps_ms",
            "max_gap_ms",
            "mean_gap_ms",
            "valid_face_ratio",
            "face_detected_count",
            "warning",
        ],
    )
    write_csv(
        analysis_dir / f"{prefix}_no_face_insufficient_clip_cases.csv",
        failure_rows,
        ["stage", "video_id", "video_path", "label", "status", "reason", "fps", "duration", "total_frames", "num_clips"],
    )
    metric_row = {
        "sampler": "continuous_sampler_head_only",
        "threshold": 0.5,
        **{k: v for k, v in metrics.items() if k != "confusion_matrix"},
        "confusion_matrix": json.dumps(metrics["confusion_matrix"], ensure_ascii=False),
    }
    write_csv(
        analysis_dir / f"{prefix}_metrics.csv",
        [metric_row],
        [
            "sampler",
            "threshold",
            "n_total",
            "n_scored",
            "no_face_or_insufficient",
            "auc",
            "accuracy",
            "precision",
            "recall",
            "f1",
            "confusion_matrix",
            "fake_mean",
            "real_mean",
            "fake_median",
            "real_median",
        ],
    )
    (analysis_dir / f"{prefix}_metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    plot_distribution(items, metrics, figures_dir / f"{prefix}_score_distribution.png")

    print("saved weights:", output, flush=True)
    print("saved metrics:", eval_dir / "metrics.json", flush=True)
    print("saved analysis prefix:", analysis_dir / prefix, flush=True)
    print(json.dumps(metrics, indent=2), flush=True)


if __name__ == "__main__":
    main()
