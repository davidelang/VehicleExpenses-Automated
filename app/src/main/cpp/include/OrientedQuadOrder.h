#pragma once
/**
 * Shared oriented-quad order for heatmap seeds and content expand.
 *
 * Guarantees after oriFromQuad + oriToQuad (or canonicalizeVerticesTlTrBrBl):
 * - Corners packed TL, TR, BR, BL in image space (y down).
 * - u (TL→TR) is the edge closest to horizontal (|atan2| folded to [0,90°]);
 *   tie → longer edge.
 * - +u points right (ux ≥ 0); +v points down (vy ≥ 0).
 *
 * Same rules as Kotlin OrientedBox.fromQuad / quadPtsTlTrBrBl.
 */
#include <cmath>
#include <cstdlib>

struct OriBox {
    float cx, cy, ux, uy, vx, vy, u0, u1, v0, v1;
};

inline bool oriFromQuad(const float* p, OriBox* b) {
    // u = edge closest to horizontal (|atan2| folded to [0, 90°]); tie → longer.
    // v = +90° from u, flipped so vy ≥ 0 (v1 = lower flatter side).
    // Then if ux < 0: flip u and swap-negate u extents so ux ≥ 0 (no L-R mirror).
    double bestAng = 1e9;
    double bestLen = 0.0;
    float ux = 1.f, uy = 0.f;
    for (int i = 0; i < 4; ++i) {
        const int j = (i + 1) & 3;
        const double dx = static_cast<double>(p[j * 2] - p[i * 2]);
        const double dy = static_cast<double>(p[j * 2 + 1] - p[i * 2 + 1]);
        const double len = std::hypot(dx, dy);
        if (len < 1e-3) continue;
        double ang = std::fabs(std::atan2(dy, dx));
        if (ang > 1.5707963267948966) ang = 3.141592653589793 - ang;
        const bool closer = ang < bestAng - 1e-6;
        const bool tieLonger = std::fabs(ang - bestAng) <= 1e-6 && len > bestLen;
        if (closer || tieLonger) {
            bestAng = ang;
            bestLen = len;
            ux = static_cast<float>(dx / len);
            uy = static_cast<float>(dy / len);
        }
    }
    if (bestLen < 2.0) return false;
    float vx = -uy, vy = ux;
    if (vy < 0.f) { vx = -vx; vy = -vy; }
    float cx = 0.f, cy = 0.f;
    for (int i = 0; i < 4; ++i) {
        cx += p[i * 2];
        cy += p[i * 2 + 1];
    }
    cx *= 0.25f;
    cy *= 0.25f;
    float u0 = 1e30f, u1 = -1e30f, v0 = 1e30f, v1 = -1e30f;
    for (int i = 0; i < 4; ++i) {
        const float dx = p[i * 2] - cx;
        const float dy = p[i * 2 + 1] - cy;
        const float u = dx * ux + dy * uy;
        const float v = dx * vx + dy * vy;
        if (u < u0) u0 = u;
        if (u > u1) u1 = u;
        if (v < v0) v0 = v;
        if (v > v1) v1 = v;
    }
    if (u1 - u0 < 2.f || v1 - v0 < 2.f) return false;
    if (ux < 0.f) {
        ux = -ux;
        uy = -uy;
        const float t = u0;
        u0 = -u1;
        u1 = -t;
    }
    b->cx = cx; b->cy = cy; b->ux = ux; b->uy = uy;
    b->vx = vx; b->vy = vy;
    b->u0 = u0; b->u1 = u1; b->v0 = v0; b->v1 = v1;
    return true;
}

/** Image y-down: TL,TR,BR,BL. BL = two smallest-x then max y; BR = cyclic neighbor with larger x. */
inline void canonicalizeQuadTlTrBrBl(float* p) {
    struct C { int i; float x, y; };
    C c[4];
    for (int i = 0; i < 4; ++i) {
        c[i].i = i;
        c[i].x = p[i * 2];
        c[i].y = p[i * 2 + 1];
    }
    int a = 0, b = 1;
    if (c[1].x < c[0].x) {
        a = 1;
        b = 0;
    }
    for (int i = 2; i < 4; ++i) {
        if (c[i].x < c[a].x) {
            b = a;
            a = i;
        } else if (c[i].x < c[b].x) {
            b = i;
        }
    }
    const C bl = (c[a].y >= c[b].y) ? c[a] : c[b];
    const C n0 = c[(bl.i + 3) % 4];
    const C n1 = c[(bl.i + 1) % 4];
    const C br = (n0.x >= n1.x) ? n0 : n1;
    const C tl = (br.i == n0.i) ? n1 : n0;
    C tr = c[0];
    for (int i = 0; i < 4; ++i) {
        if (i != bl.i && i != br.i && i != tl.i) {
            tr = c[i];
            break;
        }
    }
    const float o[8] = {tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y};
    for (int i = 0; i < 8; ++i) p[i] = o[i];
}

inline void oriToQuad(const OriBox& b, float* out) {
    auto c = [&](float u, float v, int i) {
        out[i] = b.cx + u * b.ux + v * b.vx;
        out[i + 1] = b.cy + u * b.uy + v * b.vy;
    };
    c(b.u0, b.v0, 0);
    c(b.u1, b.v0, 2);
    c(b.u1, b.v1, 4);
    c(b.u0, b.v1, 6);
    canonicalizeQuadTlTrBrBl(out);
}

/**
 * Rebuild 4 image corners so TL→TR is closest-to-horizontal (+u right, +v down).
 * Falls back to corner reorder alone if the quad is too small for oriFromQuad.
 * In-place on OpenCV Point2f[4] (x,y pairs).
 */
template <typename Point2fT>
inline void canonicalizeVerticesTlTrBrBl(Point2fT vertices[4]) {
    float p[8];
    for (int i = 0; i < 4; ++i) {
        p[i * 2] = vertices[i].x;
        p[i * 2 + 1] = vertices[i].y;
    }
    OriBox b;
    if (oriFromQuad(p, &b)) {
        oriToQuad(b, p);
    } else {
        canonicalizeQuadTlTrBrBl(p);
    }
    for (int i = 0; i < 4; ++i) {
        vertices[i].x = p[i * 2];
        vertices[i].y = p[i * 2 + 1];
    }
}
