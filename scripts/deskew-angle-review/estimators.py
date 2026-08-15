#!/usr/bin/env python3
"""Angle estimators from det heatmap (P(text) in [0,1]).

Product assumption: *most* text is horizontal; *some* components may be far from
horizontal. No per-image special cases. No aspect-ratio filters.

Production may combine several methods into **one** reported angle (e.g. median vote).
"""
from __future__ import annotations

import math
import time
from dataclasses import dataclass

import cv2
import numpy as np


@dataclass
class AngleResult:
    method: str
    angle_raw: float
    time_ms: float
    notes: str = ""


def _norm_pm45(ang: float) -> float:
    a = float(ang)
    while a <= -45.0:
        a += 90.0
    while a > 45.0:
        a -= 90.0
    return a


def calculate_angle_minarearect(rect) -> float:
    """Match native calculateAngle."""
    box = cv2.boxPoints(rect)
    min_abs = 180.0
    res = 0.0
    for i in range(4):
        p1, p2 = box[i], box[(i + 1) % 4]
        dx, dy = float(p2[0] - p1[0]), float(p2[1] - p1[1])
        ang = math.degrees(math.atan2(dy, dx))
        norm = _norm_pm45(ang)
        if abs(norm) < min_abs:
            min_abs = abs(norm)
            res = norm
    return res


def _components(heat: np.ndarray, thr: float, min_area: int = 10):
    mask = (heat > thr).astype(np.uint8)
    nlab, labels, stats, _ = cv2.connectedComponentsWithStats(mask, connectivity=8)
    blobs = []
    for lab in range(1, nlab):
        area = int(stats[lab, cv2.CC_STAT_AREA])
        if area < min_area:
            continue
        left = int(stats[lab, cv2.CC_STAT_LEFT])
        top = int(stats[lab, cv2.CC_STAT_TOP])
        width = int(stats[lab, cv2.CC_STAT_WIDTH])
        height = int(stats[lab, cv2.CC_STAT_HEIGHT])
        ys, xs = np.where(labels[top : top + height, left : left + width] == lab)
        if len(xs) == 0:
            continue
        pts = np.column_stack([xs + left, ys + top]).astype(np.float32)
        conf = float(heat[ys + top, xs + left].mean())
        blobs.append((pts, conf, area, width, height))
    return blobs


def method_cpp_style(heat: np.ndarray, thr: float = 0.20) -> AngleResult:
    """App C++ heatmapToAngle-style: CC + minAreaRect + weighted 0.5° buckets; raw = mean in winning bucket."""
    t0 = time.perf_counter()
    name = f"cpp_style_thr{thr:g}"
    blobs = _components(heat, thr)
    if not blobs:
        return AngleResult(name, 0.0, (time.perf_counter() - t0) * 1000, "no_blobs")
    buckets: dict[int, float] = {}
    raw_angles: list[float] = []
    for pts, conf, area, w, h in blobs:
        rect = cv2.minAreaRect(pts)
        ang = calculate_angle_minarearect(rect)
        raw_angles.append(ang)
        b = int(round(ang * 2.0))
        peri = float(cv2.arcLength(pts.reshape(-1, 1, 2), True))
        buckets[b] = buckets.get(b, 0.0) + peri * conf
    best = max(buckets.items(), key=lambda kv: kv[1])[0]
    win = [a for a in raw_angles if int(round(a * 2.0)) == best]
    raw = float(np.mean(win)) if win else best * 0.5
    return AngleResult(
        name,
        raw,
        (time.perf_counter() - t0) * 1000,
        f"bucket={best * 0.5:.1f} n_blobs={len(blobs)}",
    )


def method_median_minarearect(heat: np.ndarray, thr: float = 0.20) -> AngleResult:
    """Median of per-blob minAreaRect angles — no aspect-ratio filtering."""
    t0 = time.perf_counter()
    name = f"median_rect_thr{thr:g}"
    blobs = _components(heat, thr)
    if not blobs:
        return AngleResult(name, 0.0, (time.perf_counter() - t0) * 1000, "no_blobs")
    angs = [calculate_angle_minarearect(cv2.minAreaRect(pts)) for pts, *_ in blobs]
    raw = float(np.median(angs))
    return AngleResult(name, raw, (time.perf_counter() - t0) * 1000, f"n={len(angs)}")


def method_hough_on_heat(heat: np.ndarray, thr: float = 0.20) -> AngleResult:
    """Hough lines on thresholded heat; median angle of lines near horizontal preference via |ang|<30 else all."""
    t0 = time.perf_counter()
    name = f"hough_thr{thr:g}"
    mask = (heat > thr).astype(np.uint8) * 255
    edges = cv2.Canny(mask, 50, 150)
    lines = cv2.HoughLines(
        edges, 1, np.pi / 180.0, threshold=max(40, int(min(heat.shape) * 0.05))
    )
    if lines is None:
        return AngleResult(name, 0.0, (time.perf_counter() - t0) * 1000, "no_lines")
    angs = []
    for rho_theta in lines[:200]:
        _rho, theta = rho_theta[0]
        deg = math.degrees(float(theta)) - 90.0
        angs.append(_norm_pm45(deg))
    near = [a for a in angs if abs(a) < 30]
    raw = float(np.median(near if near else angs))
    return AngleResult(name, raw, (time.perf_counter() - t0) * 1000, f"n_lines={len(angs)}")


def combine_median(results: list[AngleResult], name: str = "vote_median") -> AngleResult:
    """Cross-check: one output = median of member angles. time_ms = sum of parts + combine."""
    t0 = time.perf_counter()
    angs = [r.angle_raw for r in results if r is not None and math.isfinite(r.angle_raw)]
    raw = float(np.median(angs)) if angs else 0.0
    paid = sum(r.time_ms for r in results)
    return AngleResult(name, raw, paid + (time.perf_counter() - t0) * 1000, f"from={len(angs)}")


def run_all_estimators(heat: np.ndarray) -> list[AngleResult]:
    """
    Phase-2 set (GT review + production-candidate ensemble):
      - hough @ 0.20
      - cpp_style @ 1/255 and @ 0.20
      - median_rect @ 0.20 (no aspect filter)
      - vote_median of {hough, cpp_1/255, median_rect}  → single production-style result
    Dropped: soft_pca, proj, vote_trim, aspect filters.
    """
    hough = method_hough_on_heat(heat, 0.20)
    cpp_lsb = method_cpp_style(heat, 1.0 / 255.0)
    cpp_020 = method_cpp_style(heat, 0.20)
    med = method_median_minarearect(heat, 0.20)
    vote = combine_median([hough, cpp_lsb, med], "vote_median_hough_cpp_med")
    return [hough, cpp_lsb, cpp_020, med, vote]


def bin_half_deg(ang: float) -> float:
    return round(float(ang) * 2.0) / 2.0
