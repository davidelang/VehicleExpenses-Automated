#!/usr/bin/env python3
"""Expanded angle estimators for GT experiments (research only)."""
from __future__ import annotations

import math
import time

import cv2
import numpy as np

from estimators import (
    AngleResult,
    _components,
    _norm_pm45,
    calculate_angle_minarearect,
    combine_median,
    method_cpp_style,
    method_hough_on_heat,
    method_median_minarearect,
)


def method_cpp_baseline_bucket(heat: np.ndarray, thr: float = 0.20) -> AngleResult:
    """Exact app NativeImageUtils heatmapToAngle: returns winning 0.5° *bucket* (not mean)."""
    t0 = time.perf_counter()
    name = f"BASELINE_app_cpp_bucket_thr{thr:g}"
    blobs = _components(heat, thr)
    if not blobs:
        return AngleResult(name, 0.0, (time.perf_counter() - t0) * 1000, "no_blobs")
    buckets: dict[int, float] = {}
    for pts, conf, area, w, h in blobs:
        rect = cv2.minAreaRect(pts)
        ang = calculate_angle_minarearect(rect)
        b = int(round(ang * 2.0))
        peri = float(cv2.arcLength(pts.reshape(-1, 1, 2), True))
        buckets[b] = buckets.get(b, 0.0) + peri * conf
    best = max(buckets.items(), key=lambda kv: kv[1])[0]
    raw = best * 0.5
    return AngleResult(name, raw, (time.perf_counter() - t0) * 1000, f"bucket={raw:.1f} n={len(blobs)}")


def method_soft_pca(heat: np.ndarray, thr: float = 0.05) -> AngleResult:
    t0 = time.perf_counter()
    name = f"soft_pca_thr{thr:g}"
    ys, xs = np.where(heat > thr)
    if len(xs) < 20:
        return AngleResult(name, 0.0, (time.perf_counter() - t0) * 1000, "too_few")
    wts = heat[ys, xs].astype(np.float64)
    wts = wts / (wts.sum() + 1e-12)
    pts = np.column_stack([xs.astype(np.float64), ys.astype(np.float64)])
    mu = (pts * wts[:, None]).sum(axis=0)
    c = pts - mu
    cov = (c * wts[:, None]).T @ c
    evals, evecs = np.linalg.eigh(cov)
    v = evecs[:, int(np.argmax(evals))]
    ang = _norm_pm45(math.degrees(math.atan2(float(v[1]), float(v[0]))))
    return AngleResult(name, ang, (time.perf_counter() - t0) * 1000, f"n={len(xs)}")


def method_proj_profile(heat: np.ndarray, thr: float = 0.20) -> AngleResult:
    """Search angle maximizing projection variance of thresholded mass."""
    t0 = time.perf_counter()
    name = f"proj_thr{thr:g}"
    mask = (heat > thr).astype(np.float32)
    if mask.sum() < 20:
        return AngleResult(name, 0.0, (time.perf_counter() - t0) * 1000, "empty")
    h, w = mask.shape
    ys, xs = np.where(mask > 0)
    pts = np.column_stack([xs, ys]).astype(np.float32)
    best_a, best_s = 0.0, -1.0
    for a in np.linspace(-45, 45, 181):  # 0.5° steps
        rad = math.radians(float(a))
        c, s = math.cos(rad), math.sin(rad)
        # project onto normal to text direction → want sharp 1D profile = high variance of hist
        proj = pts[:, 0] * (-s) + pts[:, 1] * c
        hist, _ = np.histogram(proj, bins=64)
        score = float(hist.var())
        if score > best_s:
            best_s, best_a = score, float(a)
    return AngleResult(name, best_a, (time.perf_counter() - t0) * 1000, f"score={best_s:.1f}")


def method_hough_prob(heat: np.ndarray, thr: float = 0.20) -> AngleResult:
    t0 = time.perf_counter()
    name = f"houghP_thr{thr:g}"
    mask = (heat > thr).astype(np.uint8) * 255
    edges = cv2.Canny(mask, 50, 150)
    min_len = max(20, int(min(heat.shape) * 0.08))
    lines = cv2.HoughLinesP(
        edges, 1, np.pi / 180.0, threshold=max(30, int(min(heat.shape) * 0.04)),
        minLineLength=min_len, maxLineGap=10,
    )
    if lines is None:
        return AngleResult(name, 0.0, (time.perf_counter() - t0) * 1000, "no_lines")
    angs = []
    for x1, y1, x2, y2 in lines[:, 0]:
        ang = _norm_pm45(math.degrees(math.atan2(float(y2 - y1), float(x2 - x1))))
        angs.append(ang)
    near = [a for a in angs if abs(a) < 30]
    raw = float(np.median(near if near else angs))
    return AngleResult(name, raw, (time.perf_counter() - t0) * 1000, f"n={len(angs)}")


def method_area_weighted_median(heat: np.ndarray, thr: float = 0.20) -> AngleResult:
    """Area-weighted median of blob minAreaRect angles."""
    t0 = time.perf_counter()
    name = f"wmedian_area_thr{thr:g}"
    blobs = _components(heat, thr)
    if not blobs:
        return AngleResult(name, 0.0, (time.perf_counter() - t0) * 1000, "no_blobs")
    angs, weights = [], []
    for pts, conf, area, w, h in blobs:
        angs.append(calculate_angle_minarearect(cv2.minAreaRect(pts)))
        weights.append(float(area))
    order = np.argsort(angs)
    angs_s = np.array(angs)[order]
    w_s = np.array(weights)[order]
    cdf = np.cumsum(w_s) / w_s.sum()
    raw = float(angs_s[np.searchsorted(cdf, 0.5)])
    return AngleResult(name, raw, (time.perf_counter() - t0) * 1000, f"n={len(angs)}")


def method_conf_weighted_mean(heat: np.ndarray, thr: float = 0.20) -> AngleResult:
    t0 = time.perf_counter()
    name = f"wmean_conf_thr{thr:g}"
    blobs = _components(heat, thr)
    if not blobs:
        return AngleResult(name, 0.0, (time.perf_counter() - t0) * 1000, "no_blobs")
    angs, wts = [], []
    for pts, conf, area, w, h in blobs:
        angs.append(calculate_angle_minarearect(cv2.minAreaRect(pts)))
        peri = float(cv2.arcLength(pts.reshape(-1, 1, 2), True))
        wts.append(peri * conf)
    raw = float(np.average(angs, weights=wts))
    return AngleResult(name, raw, (time.perf_counter() - t0) * 1000, f"n={len(angs)}")


def combine_mean(results: list[AngleResult], name: str) -> AngleResult:
    t0 = time.perf_counter()
    angs = [r.angle_raw for r in results if r is not None and math.isfinite(r.angle_raw)]
    raw = float(np.mean(angs)) if angs else 0.0
    paid = sum(r.time_ms for r in results)
    return AngleResult(name, raw, paid + (time.perf_counter() - t0) * 1000, f"from={len(angs)}")


def combine_trim_mean(results: list[AngleResult], name: str) -> AngleResult:
    """Drop min/max then mean (needs ≥3)."""
    t0 = time.perf_counter()
    angs = sorted(r.angle_raw for r in results if r is not None and math.isfinite(r.angle_raw))
    if len(angs) >= 3:
        angs = angs[1:-1]
    raw = float(np.mean(angs)) if angs else 0.0
    paid = sum(r.time_ms for r in results)
    return AngleResult(name, raw, paid + (time.perf_counter() - t0) * 1000, f"from={len(angs)}")


def run_experiment_suite(heat: np.ndarray) -> list[AngleResult]:
    """Broad algorithm set for offline GT scoring (single heat)."""
    out: list[AngleResult] = []

    # --- baseline (production today) ---
    base = method_cpp_baseline_bucket(heat, 0.20)
    out.append(base)

    # --- cpp variants ---
    for thr in (1.0 / 255.0, 0.05, 0.10, 0.15, 0.20, 0.30, 0.40):
        out.append(method_cpp_style(heat, thr))  # mean-in-bucket (slightly different from app)

    # --- median rect ---
    for thr in (0.05, 0.10, 0.20, 0.30):
        out.append(method_median_minarearect(heat, thr))

    # --- weighted variants ---
    for thr in (0.10, 0.20):
        out.append(method_area_weighted_median(heat, thr))
        out.append(method_conf_weighted_mean(heat, thr))

    # --- hough ---
    for thr in (0.05, 0.10, 0.20, 0.30):
        out.append(method_hough_on_heat(heat, thr))
    out.append(method_hough_prob(heat, 0.20))

    # --- soft pca / proj (known weak, for contrast) ---
    out.append(method_soft_pca(heat, 0.05))
    out.append(method_soft_pca(heat, 0.20))
    out.append(method_proj_profile(heat, 0.20))

    # cache by name for ensembles
    by = {r.method: r for r in out}

    def g(*names):
        return [by[n] for n in names if n in by]

    # --- ensembles (production-style one result) ---
    hough = by.get("hough_thr0.2")
    cpp_lsb = by.get("cpp_style_thr0.00392157")
    cpp020 = by.get("cpp_style_thr0.2")
    med = by.get("median_rect_thr0.2")
    base_r = by.get("BASELINE_app_cpp_bucket_thr0.2")

    if hough and cpp_lsb and med:
        out.append(combine_median([hough, cpp_lsb, med], "vote_median_hough_cppLsb_med"))
    if hough and cpp020 and med:
        out.append(combine_median([hough, cpp020, med], "vote_median_hough_cpp020_med"))
    if hough and cpp020 and base_r:
        out.append(combine_median([hough, cpp020, base_r], "vote_median_hough_cpp020_base"))
    if hough and med:
        out.append(combine_median([hough, med], "vote_median_hough_med"))
    if hough and cpp020:
        out.append(combine_median([hough, cpp020], "vote_median_hough_cpp020"))
    if cpp_lsb and med:
        out.append(combine_median([cpp_lsb, med], "vote_median_cppLsb_med"))
    if hough and cpp_lsb and cpp020 and med:
        out.append(combine_median([hough, cpp_lsb, cpp020, med], "vote_median_4way"))
        out.append(combine_trim_mean([hough, cpp_lsb, cpp020, med], "vote_trim_4way"))
        out.append(combine_mean([hough, cpp_lsb, cpp020, med], "vote_mean_4way"))
    if hough and cpp020 and med:
        out.append(combine_trim_mean([hough, cpp020, med], "vote_trim_hough_cpp020_med"))

    # hough alone is strong — also "ensemble" of thr variants
    hs = [by[k] for k in ("hough_thr0.05", "hough_thr0.1", "hough_thr0.2", "hough_thr0.3") if k in by]
    if len(hs) >= 3:
        out.append(combine_median(hs, "vote_median_hough_thr_stack"))

    cps = [by[k] for k in (
        "cpp_style_thr0.00392157", "cpp_style_thr0.05", "cpp_style_thr0.1",
        "cpp_style_thr0.15", "cpp_style_thr0.2", "cpp_style_thr0.3",
    ) if k in by]
    if len(cps) >= 3:
        out.append(combine_median(cps, "vote_median_cpp_thr_stack"))

    return out
