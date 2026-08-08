#!/usr/bin/env python3
"""Run one optical-flow model (raft or gmflow) on fake+real benchmark dirs."""
from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

import torch

sys.path.insert(0, str(Path(__file__).resolve().parent))
from optical_flow_backends import BACKENDS
from optical_flow_common import (
    DEFAULT_ANOMALY_THRESHOLD,
    GMFLOW_DISPLAY,
    GMFLOW_EXPLANATION,
    GMFLOW_LIMITATIONS,
    GMFLOW_ROLE,
    attach_gmflow_auxiliary_fields,
    build_flow_quality_metadata,
    enrich_reports_with_scores,
    is_gmflow_model,
    read_video_metadata,
    aggregate_pair_stats,
    sample_frame_pairs,
)


def _mean(values: list[float]) -> float | None:
    return sum(values) / len(values) if values else None


def _std(values: list[float]) -> float | None:
    avg = _mean(values)
    if avg is None:
        return None
    return (sum((value - avg) ** 2 for value in values) / len(values)) ** 0.5


def _simple_temporal_jitter(pair_stats: list[dict]) -> float | None:
    values = [float(row["magnitude_mean"]) for row in pair_stats if row.get("magnitude_mean") is not None]
    avg = _mean(values)
    std = _std(values)
    if avg is None or std is None:
        return None
    return std / (avg + 1e-6)


def _simple_spatial_inconsistency(aggregate: dict) -> float | None:
    flow_mag_mean = aggregate.get("magnitude_mean_mean")
    flow_mag_std = aggregate.get("magnitude_std_mean")
    if flow_mag_mean is None or flow_mag_std is None:
        return None
    return float(flow_mag_std) / (float(flow_mag_mean) + 1e-6)


def infer_video(
    video_path: Path,
    backend,
    *,
    max_pairs: int,
    max_side: int,
    run_id: str,
    model_name: str,
    ground_truth_label: str | None,
    device: torch.device,
) -> dict:
    video_metadata = read_video_metadata(video_path)
    pairs = sample_frame_pairs(video_path, max_pairs=max_pairs, max_side=max_side)
    if not pairs:
        item = {
            "run_id": run_id,
            "model": model_name,
            "file": video_path.name,
            "source_path": str(video_path.resolve()),
            "ground_truth_label": ground_truth_label,
            "status": "no_frames",
            "frame_pairs": 0,
            "max_side": max_side,
            "pair_stats": [],
            "aggregate": {},
            "video_metadata": video_metadata,
            "quality_metadata": build_flow_quality_metadata(video_metadata=video_metadata, num_flow_pairs=0),
            "errors": [],
            "analyzed_at": datetime.now(timezone.utc).isoformat(),
            "device": str(device),
        }
        return attach_gmflow_auxiliary_fields(item, raw_score=None) if is_gmflow_model(model_name) else item

    pair_stats: list[dict] = []
    errors: list[str] = []
    for img1, img2, idx1, idx2 in pairs:
        try:
            stats = backend.infer_pair(img1, img2)
            stats["frame_index_a"] = idx1
            stats["frame_index_b"] = idx2
            pair_stats.append(stats)
        except Exception as exc:  # noqa: BLE001
            errors.append(f"{idx1}->{idx2}: {exc}")

    status = "ok" if pair_stats else "error"
    aggregate = aggregate_pair_stats(pair_stats)
    quality_metadata = build_flow_quality_metadata(
        video_metadata=video_metadata,
        num_flow_pairs=len(pair_stats),
        flow_mag_mean=aggregate.get("magnitude_mean_mean"),
        flow_mag_std=aggregate.get("magnitude_std_mean"),
        temporal_jitter=_simple_temporal_jitter(pair_stats),
        spatial_inconsistency=_simple_spatial_inconsistency(aggregate),
        angle_dispersion=aggregate.get("angle_std_mean"),
    )
    item = {
        "run_id": run_id,
        "model": model_name,
        "file": video_path.name,
        "source_path": str(video_path.resolve()),
        "ground_truth_label": ground_truth_label,
        "status": status,
        "frame_pairs": len(pairs),
        "max_side": max_side,
        "pair_stats": pair_stats,
        "aggregate": aggregate,
        "video_metadata": video_metadata,
        "quality_metadata": quality_metadata,
        "qualityMetadata": quality_metadata,
        "errors": errors,
        "analyzed_at": datetime.now(timezone.utc).isoformat(),
        "device": str(device),
    }
    return attach_gmflow_auxiliary_fields(item, raw_score=None) if is_gmflow_model(model_name) else item


def run_directory(
    input_dir: Path,
    backend,
    *,
    max_pairs: int,
    max_side: int,
    ground_truth_label: str | None,
    run_id: str,
    model_name: str,
    device: torch.device,
    json_dir: Path,
) -> list[dict]:
    videos = sorted(input_dir.glob("*.mp4"))
    if not videos:
        raise SystemExit(f"No mp4 files in {input_dir}")

    items: list[dict] = []
    for i, video_path in enumerate(videos, start=1):
        item = infer_video(
            video_path,
            backend,
            max_pairs=max_pairs,
            max_side=max_side,
            run_id=run_id,
            model_name=model_name,
            ground_truth_label=ground_truth_label,
            device=device,
        )
        items.append(item)
        json_dir.mkdir(parents=True, exist_ok=True)
        (json_dir / f"{video_path.stem}.json").write_text(json.dumps(item, indent=2), encoding="utf-8")
        print(
            f"[{model_name}] {i}/{len(videos)} {video_path.name}: status={item['status']} "
            f"pairs_ok={len(item.get('pair_stats', []))}",
            flush=True,
        )
    return items


def summarize_metrics(items: list[dict], model_name: str) -> dict:
    ok_items = [x for x in items if x.get("status") == "ok"]
    fake_ok = [x for x in ok_items if x.get("ground_truth_label") == "fake"]
    real_ok = [x for x in ok_items if x.get("ground_truth_label") == "real"]

    def avg_mag(rows: list[dict]) -> float | None:
        vals = [r.get("aggregate", {}).get("magnitude_mean_mean") for r in rows]
        vals = [v for v in vals if v is not None]
        if not vals:
            return None
        return round(sum(vals) / len(vals), 6)

    return {
        "model": model_name,
        "total": len(items),
        "ok": len(ok_items),
        "error": sum(1 for x in items if x.get("status") == "error"),
        "no_frames": sum(1 for x in items if x.get("status") == "no_frames"),
        "fake": {"count": 50, "ok": len(fake_ok), "magnitude_mean_mean_avg": avg_mag(fake_ok)},
        "real": {"count": 50, "ok": len(real_ok), "magnitude_mean_mean_avg": avg_mag(real_ok)},
    }


def build_infer_summary(
    *,
    run_id: str,
    model_name: str,
    items: list[dict],
    fake_dir: Path,
    real_dir: Path,
    max_pairs: int,
    max_side: int,
    device: torch.device,
    weights: str,
) -> dict:
    ok_items = [x for x in items if x.get("status") == "ok"]
    summary_items = []
    for item in ok_items:
        agg = item.get("aggregate") or {}
        row = {
            "file": item["file"],
            "ground_truth_label": item.get("ground_truth_label"),
            "status": item.get("status"),
            "frame_pairs": item.get("frame_pairs"),
            "magnitude_mean": agg.get("magnitude_mean_mean"),
            "magnitude_std": agg.get("magnitude_std_mean"),
            "magnitude_p95": agg.get("magnitude_p95_mean"),
            "angle_std": agg.get("angle_std_mean"),
            "face_valid_ratio": item.get("quality_metadata", {}).get("face_valid_ratio"),
            "num_flow_pairs": item.get("quality_metadata", {}).get("num_flow_pairs"),
            "bbox_jitter": item.get("quality_metadata", {}).get("bbox_jitter"),
            "flow_mag_mean": item.get("quality_metadata", {}).get("flow_mag_mean"),
            "flow_mag_std": item.get("quality_metadata", {}).get("flow_mag_std"),
            "temporal_jitter": item.get("quality_metadata", {}).get("temporal_jitter"),
            "spatial_inconsistency": item.get("quality_metadata", {}).get("spatial_inconsistency"),
            "angle_dispersion": item.get("quality_metadata", {}).get("angle_dispersion"),
            "duration": item.get("quality_metadata", {}).get("duration"),
            "fps": item.get("quality_metadata", {}).get("fps"),
            "qualityMetadata": item.get("qualityMetadata") or item.get("quality_metadata"),
        }
        if is_gmflow_model(model_name):
            evidence = item.get("gmflowAuxiliaryEvidence") or {}
            row.update(
                {
                    "modelName": "GMFlow",
                    "role": GMFLOW_ROLE,
                    "processingStatus": evidence.get("processingStatus"),
                    "gmflowStatus": evidence.get("gmflowStatus"),
                    "qualityStatus": evidence.get("qualityStatus"),
                    "contributesToFinalScore": False,
                    "gmflowRawScore": evidence.get("gmflowRawScore"),
                    "motionInstabilityScore": evidence.get("motionInstabilityScore"),
                    "motionInstabilityLevel": evidence.get("motionInstabilityLevel"),
                    "scoreMeaning": evidence.get("scoreMeaning"),
                    "display": evidence.get("display"),
                    "explanation": evidence.get("explanation"),
                    "limitations": evidence.get("limitations"),
                    "notes": evidence.get("notes"),
                    "qualityMetadataMissing": evidence.get("qualityMetadataMissing"),
                    "qualityGateReasons": evidence.get("qualityGateReasons"),
                    "motionTimeline": evidence.get("motionTimeline") or [],
                    "suspiciousMotionSegments": evidence.get("suspiciousMotionSegments") or [],
                    "motionHeuristicLabel": item.get("motionHeuristicLabel"),
                    "predictionUsage": item.get("predictionUsage"),
                    "gmflowAuxiliaryEvidence": evidence,
                }
            )
        summary_items.append(row)

    payload = {
        "schema_version": "1.0",
        "task": "optical_flow_benchmark",
        "run_id": run_id,
        "model": model_name,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "max_pairs": max_pairs,
        "max_side": max_side,
        "fake_dir": str(fake_dir),
        "real_dir": str(real_dir),
        "weights": weights,
        "device": str(device),
        "count": len(items),
        "ok_count": len(ok_items),
        "error_count": sum(1 for x in items if x.get("status") == "error"),
        "metrics": summarize_metrics(items, model_name),
        "items": summary_items,
    }
    if is_gmflow_model(model_name):
        payload.update(
            {
                "modelName": "GMFlow",
                "role": GMFLOW_ROLE,
                "gmflowStatus": "AUXILIARY_ONLY",
                "contributesToFinalScore": False,
                "explanation": GMFLOW_EXPLANATION,
                "display": dict(GMFLOW_DISPLAY),
                "limitations": list(GMFLOW_LIMITATIONS),
            }
        )
    return payload


def main() -> None:
    parser = argparse.ArgumentParser(description="Optical-flow infer for one model (raft|gmflow)")
    parser.add_argument("--root", default=".")
    parser.add_argument("--model", required=True, choices=sorted(BACKENDS.keys()))
    parser.add_argument("--fake-dir", default="data/test/video/celeb-df-v2/fake")
    parser.add_argument("--real-dir", default="data/test/video/celeb-df-v2/real")
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--max-pairs", type=int, default=8)
    parser.add_argument("--max-side", type=int, default=512, help="resize longest edge before flow infer")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    fake_dir = Path(args.fake_dir)
    real_dir = Path(args.real_dir)
    if not fake_dir.is_absolute():
        fake_dir = (root / fake_dir).resolve()
    if not real_dir.is_absolute():
        real_dir = (root / real_dir).resolve()

    model_name = args.model
    run_id = args.run_id
    infer_root = root / "results/infer" / run_id / model_name
    eval_dir = root / "results/eval" / run_id
    json_dir = infer_root / "json"
    datasets_dir = root / "results/infer" / run_id / "datasets"
    infer_root.mkdir(parents=True, exist_ok=True)
    eval_dir.mkdir(parents=True, exist_ok=True)
    datasets_dir.mkdir(parents=True, exist_ok=True)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    backend_cls = BACKENDS[model_name]
    backend = backend_cls(root, device)

    print("run_id:", run_id)
    print("model:", model_name)
    print("device:", device)
    print("max_pairs:", args.max_pairs)
    print("max_side:", args.max_side)
    print("fake:", fake_dir)
    print("real:", real_dir)
    print()

    backend.load()
    print()

    fake_items = run_directory(
        fake_dir,
        backend,
        max_pairs=args.max_pairs,
        max_side=args.max_side,
        ground_truth_label="fake",
        run_id=run_id,
        model_name=model_name,
        device=device,
        json_dir=json_dir,
    )
    print()
    real_items = run_directory(
        real_dir,
        backend,
        max_pairs=args.max_pairs,
        max_side=args.max_side,
        ground_truth_label="real",
        run_id=run_id,
        model_name=model_name,
        device=device,
        json_dir=json_dir,
    )

    all_items = fake_items + real_items
    if is_gmflow_model(model_name):
        enrich_reports_with_scores(all_items, threshold=DEFAULT_ANOMALY_THRESHOLD)
        for item in all_items:
            json_dir.mkdir(parents=True, exist_ok=True)
            (json_dir / f"{Path(item['source_path']).stem}.json").write_text(
                json.dumps(item, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )

    weights_path = str(getattr(backend, "weights", ""))
    infer_summary = build_infer_summary(
        run_id=run_id,
        model_name=model_name,
        items=all_items,
        fake_dir=fake_dir,
        real_dir=real_dir,
        max_pairs=args.max_pairs,
        max_side=args.max_side,
        device=device,
        weights=weights_path,
    )
    summary_path = datasets_dir / f"infer_summary_{model_name}.json"
    summary_path.write_text(json.dumps(infer_summary, indent=2), encoding="utf-8")

    metrics = summarize_metrics(all_items, model_name)
    metrics["run_id"] = run_id
    metrics["max_pairs"] = args.max_pairs
    metrics["max_side"] = args.max_side
    (eval_dir / f"metrics_{model_name}.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")

    payload = {
        "run_id": run_id,
        "model": model_name,
        "task": "optical_flow_benchmark",
        "max_pairs": args.max_pairs,
        "max_side": args.max_side,
        "fake_dir": str(fake_dir),
        "real_dir": str(real_dir),
        "device": str(device),
        "weights": weights_path,
        "items": all_items,
    }
    (infer_root / "predictions.json").write_text(json.dumps(payload, indent=2), encoding="utf-8")

    print()
    print("done:", len(list(json_dir.glob("*.json"))), "json in", json_dir)
    print("infer_summary:", summary_path)
    print("metrics:", eval_dir / f"metrics_{model_name}.json")


if __name__ == "__main__":
    main()
