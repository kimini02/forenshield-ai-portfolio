#!/usr/bin/env python3
"""Eval a TimeSformer checkpoint with the continuous sampler at a chosen max_clips."""
from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

import cv2
import torch

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

import timesformer_continuous_sampler_head_only_experiment as exp
from video_timesformer_infer import DEFAULT_PRETRAINED, MODEL_ID, TimeSformerDetectorLite
from video_videomae_finetune import resolve
from video_xception_infer import write_per_file_json


def write_csv(path: Path, rows: list[dict], fields: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field, "") for field in fields})


def enrich_metrics(metrics: dict, failures: list[dict]) -> dict:
    eval_failures = [row for row in failures if row.get("stage") == "eval"]
    no_face_count = sum(1 for row in eval_failures if row.get("status") == "no_face")
    insufficient_clip_count = sum(
        1 for row in eval_failures if row.get("reason") == "INSUFFICIENT_CONTINUOUS_CLIPS"
    )
    return {
        **metrics,
        "no_face_count": no_face_count,
        "insufficient_clip_count": insufficient_clip_count,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--weights", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--eval-fake-dir", default="data/benchmark/video-benchmark-datasets/celebdf/fake")
    parser.add_argument("--eval-real-dir", default="data/benchmark/video-benchmark-datasets/celebdf/real")
    parser.add_argument("--pretrained-id", default=DEFAULT_PRETRAINED)
    parser.add_argument("--clip-frames", type=int, default=8)
    parser.add_argument("--sampling-fps", type=float, default=4.0)
    parser.add_argument("--clip-duration-sec", type=float, default=2.0)
    parser.add_argument("--max-clips", type=int, required=True)
    parser.add_argument("--max-gap-ms", type=float, default=500.0)
    parser.add_argument("--min-valid-face-ratio", type=float, default=0.75)
    args = parser.parse_args()

    root = Path(args.root).resolve()
    weights = resolve(root, args.weights)
    cfg = exp.SamplerConfig(
        clip_frames=args.clip_frames,
        sampling_fps=args.sampling_fps,
        clip_duration_sec=args.clip_duration_sec,
        max_clips=args.max_clips,
        max_gap_ms=args.max_gap_ms,
        min_valid_face_ratio=args.min_valid_face_ratio,
    )
    infer_dir = root / "results/infer" / args.run_id
    eval_dir = root / "results/eval" / args.run_id
    analysis_dir = root / "results/analysis"
    figures_dir = root / "results/figures"
    json_dir = infer_dir / "json"
    for directory in (infer_dir, eval_dir, analysis_dir, figures_dir, json_dir):
        directory.mkdir(parents=True, exist_ok=True)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = TimeSformerDetectorLite(pretrained_id=args.pretrained_id).to(device)
    model.load_state_dict(torch.load(weights, map_location=device, weights_only=False), strict=True)
    model.eval()
    face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")

    items: list[dict] = []
    audit_rows: list[dict] = []
    failure_rows: list[dict] = []
    for label, directory in (("fake", resolve(root, args.eval_fake_dir)), ("real", resolve(root, args.eval_real_dir))):
        for video_path in sorted(directory.glob("*.mp4")):
            item, clip_rows, failure = exp.infer_one_video(
                model,
                video_path.resolve(),
                label,
                face_cascade,
                device,
                weights,
                args.run_id,
                cfg,
            )
            items.append(item)
            audit_rows.extend(clip_rows)
            if failure:
                failure_rows.append({**failure, "stage": "eval"})
            write_per_file_json(json_dir, item)
            print(f"{video_path.name}: {item['status']} pred={item.get('pred_label')} fake_score={item.get('fake_score')}", flush=True)

    metrics = enrich_metrics(exp.score_metrics(items), failure_rows)
    payload = {
        "run_id": args.run_id,
        "model": MODEL_ID,
        "sampler": "continuous",
        "training": "eval_only",
        "threshold": 0.5,
        "clip_frames": cfg.clip_frames,
        "sampling_fps": cfg.sampling_fps,
        "clip_duration_sec": cfg.clip_duration_sec,
        "max_clips": cfg.max_clips,
        "max_gap_ms": cfg.max_gap_ms,
        "min_valid_face_ratio": cfg.min_valid_face_ratio,
        "weights": str(weights),
        "device": str(device),
        "items": items,
    }
    (infer_dir / "predictions.json").write_text(json.dumps(payload, indent=2), encoding="utf-8")
    (eval_dir / "metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")

    prefix = f"timesformer_continuous_sampler_celebdf_head_only_maxclips{args.max_clips}_eval_20260624"
    pred_rows = [
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
        for item in items
    ]
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
        "experiment_name": f"continuous_sampler_head_only_maxclips{args.max_clips}",
        "eval_count": metrics["n_scored"],
        "CelebDF AUC": metrics["auc"],
        "accuracy": metrics["accuracy"],
        "precision": metrics["precision"],
        "fake_recall": metrics["recall"],
        "F1": metrics["f1"],
        "TN": metrics["confusion_matrix"]["tn"],
        "FP": metrics["confusion_matrix"]["fp"],
        "FN": metrics["confusion_matrix"]["fn"],
        "TP": metrics["confusion_matrix"]["tp"],
        "fake_mean_score": metrics["fake_mean"],
        "real_mean_score": metrics["real_mean"],
        "fake_median_score": metrics["fake_median"],
        "real_median_score": metrics["real_median"],
        "no_face_count": metrics["no_face_count"],
        "insufficient_clip_count": metrics["insufficient_clip_count"],
    }
    write_csv(
        analysis_dir / f"{prefix}_summary.csv",
        [metric_row],
        list(metric_row.keys()),
    )
    (analysis_dir / f"{prefix}_metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    exp.plot_distribution(items, metrics, figures_dir / f"{prefix}_score_distribution.png")

    print("saved metrics:", eval_dir / "metrics.json")
    print("saved analysis prefix:", analysis_dir / prefix)
    print(json.dumps(metrics, indent=2), flush=True)


if __name__ == "__main__":
    main()
