from __future__ import annotations

import argparse
import csv
import math
import re
from pathlib import Path

import numpy as np


SUBJECT_PATTERN = re.compile(r"Subject_(\d+)")
MOVEMENT_PATTERN = re.compile(r"_(?:Fall|ADL)_(.+)_\d+_\d{4}-")


def read_phone_acceleration(path: Path) -> tuple[np.ndarray, np.ndarray]:
    phone_sensor_id: int | None = None
    timestamps: list[int] = []
    vectors: list[tuple[float, float, float]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            columns = [column.strip() for column in line.split(";")]
            if line.startswith("%") and len(columns) >= 3 and columns[2].upper() == "RIGHTPOCKET":
                phone_sensor_id = int(columns[1])
                continue
            if not line or not line[0].isdigit() or len(columns) < 7:
                continue
            if int(columns[5]) != 0 or int(columns[6]) != phone_sensor_id:
                continue
            timestamps.append(int(columns[0]))
            vectors.append((float(columns[2]), float(columns[3]), float(columns[4])))
    return np.asarray(timestamps, dtype=np.int64), np.asarray(vectors, dtype=np.float64)


def low_pass(timestamps_ms: np.ndarray, vectors: np.ndarray, cutoff_hz: float = 5.0) -> np.ndarray:
    if len(vectors) < 2:
        return vectors.copy()
    positive_steps = np.diff(timestamps_ms)
    positive_steps = positive_steps[positive_steps > 0]
    fallback_step_ms = float(np.median(positive_steps)) if len(positive_steps) else 20.0
    rc_seconds = 1.0 / (2.0 * math.pi * cutoff_hz)
    filtered = np.empty_like(vectors)
    filtered[0] = vectors[0]
    for index in range(1, len(vectors)):
        step_ms = float(timestamps_ms[index] - timestamps_ms[index - 1])
        if step_ms <= 0.0:
            step_ms = fallback_step_ms
        step_seconds = min(max(step_ms / 1000.0, 0.001), 0.1)
        alpha = step_seconds / (rc_seconds + step_seconds)
        filtered[index] = filtered[index - 1] + alpha * (vectors[index] - filtered[index - 1])
    return filtered


def angle_degrees(first: np.ndarray, second: np.ndarray) -> float:
    denominator = float(np.linalg.norm(first) * np.linalg.norm(second))
    if denominator <= 1e-9:
        return 0.0
    cosine = float(np.dot(first, second) / denominator)
    return math.degrees(math.acos(max(-1.0, min(1.0, cosine))))


def extract_features(timestamps_ms: np.ndarray, vectors: np.ndarray) -> tuple[float, float, float]:
    filtered = low_pass(timestamps_ms, vectors)
    sv = np.abs(filtered).sum(axis=1)
    impact_index = int(np.argmax(sv))
    impact_time = int(timestamps_ms[impact_index])

    lower = int(np.searchsorted(timestamps_ms, impact_time - 1000, side="left"))
    upper = int(np.searchsorted(timestamps_ms, impact_time + 1000, side="right"))
    local_vectors = filtered[lower:upper]
    if len(local_vectors) >= 2:
        first = local_vectors[:-1]
        second = local_vectors[1:]
        denominators = np.linalg.norm(first, axis=1) * np.linalg.norm(second, axis=1)
        valid = denominators > 1e-9
        cosines = np.ones_like(denominators)
        cosines[valid] = np.sum(first[valid] * second[valid], axis=1) / denominators[valid]
        angle_variation = float(np.degrees(np.arccos(np.clip(cosines, -1.0, 1.0))).max())
    else:
        angle_variation = 0.0

    before_start = int(np.searchsorted(timestamps_ms, impact_time - 2000, side="left"))
    before_end = int(np.searchsorted(timestamps_ms, impact_time - 1000, side="right"))
    after_start = int(np.searchsorted(timestamps_ms, impact_time + 1000, side="left"))
    after_end = int(np.searchsorted(timestamps_ms, impact_time + 2000, side="right"))
    before = filtered[before_start:before_end]
    after = filtered[after_start:after_end]
    change_angle = angle_degrees(before.mean(axis=0), after.mean(axis=0)) if len(before) and len(after) else 0.0
    return float(sv[impact_index]), angle_variation, change_angle


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("dataset", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    rows: list[dict[str, object]] = []
    for path in sorted(args.dataset.glob("*.csv")):
        timestamps, vectors = read_phone_acceleration(path)
        sv_peak, angle_variation, change_angle = extract_features(timestamps, vectors)
        subject_match = SUBJECT_PATTERN.search(path.name)
        movement_match = MOVEMENT_PATTERN.search(path.name)
        rows.append({
            "file": path.name,
            "subject": int(subject_match.group(1)) if subject_match else -1,
            "label": "FALL" if "_Fall_" in path.name else "ADL",
            "movement": movement_match.group(1) if movement_match else "Unknown",
            "sv_peak": f"{sv_peak:.6f}",
            "angle_variation": f"{angle_variation:.6f}",
            "change_angle": f"{change_angle:.6f}",
        })

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    print(f"traces={len(rows)} output={args.output.resolve()}")


if __name__ == "__main__":
    main()
