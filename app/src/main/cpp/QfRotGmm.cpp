#include <jni.h>
#include <android/log.h>

#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/imgproc/imgproc_c.h>

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <limits>
#include <string>
#include <vector>

#include "paddle/paddle_api.h"

#include "CropPlan.h"
#include "VeJni.h"

#include <mutex>
#include <stdexcept>

extern "C" bool veEncodeYuvMatJpeg(const cv::Mat& y, const cv::Mat& uv, std::vector<uint8_t>* out);

namespace {

constexpr int kRecH = 48;
constexpr float kVert = 0.34f;
constexpr float kLeftFrac = 0.54f;
constexpr float kRightFrac = 0.55f;
constexpr int kAllowedDecimal[] = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 93};
constexpr int kDetScales[] = {224, 608};


struct BoxUv {
    float cx, cy, ux, uy, vx, vy;
    float u0, u1, v0, v1;
    bool ok;
};

struct RawBox {
    int scale;
    float corners[8];
    float score;
};

struct RecRow {
    std::string asis;
    std::string asisProbs;
    std::string digits;
    std::string digitsProbs;
};

struct EvenRect {
    int l, t, r, b;
    bool ok;
};




float spanAtLeast(float a, float b) {
    const float s = b - a;
    return s < 0.f ? 0.f : s;
}


BoxUv boxFromQuad(const float* p) {
    BoxUv b{};
    b.ok = false;
    double bestAng = 1e9;
    double bestLen = 0.0;
    float ux = 1.f;
    float uy = 0.f;
    for (int i = 0; i < 4; ++i) {
        const int j = (i + 1) % 4;
        const double dx = static_cast<double>(p[j * 2] - p[i * 2]);
        const double dy = static_cast<double>(p[j * 2 + 1] - p[i * 2 + 1]);
        const double len = std::hypot(dx, dy);
        if (len < 1e-3) continue;
        double ang = std::fabs(std::atan2(dy, dx));
        if (ang > 3.141592653589793 / 2.0) ang = 3.141592653589793 - ang;
        const bool closer = ang < bestAng - 1e-6;
        const bool tieLonger = std::fabs(ang - bestAng) <= 1e-6 && len > bestLen;
        if (closer || tieLonger) {
            bestAng = ang;
            bestLen = len;
            ux = static_cast<float>(dx / len);
            uy = static_cast<float>(dy / len);
        }
    }
    if (bestLen < 2.0) return b;
    float vx = -uy;
    float vy = ux;
    if (vy < 0.f) {
        vx = -vx;
        vy = -vy;
    }
    float cx = 0.f;
    float cy = 0.f;
    for (int i = 0; i < 4; ++i) {
        cx += p[i * 2];
        cy += p[i * 2 + 1];
    }
    cx /= 4.f;
    cy /= 4.f;
    float u0 = std::numeric_limits<float>::infinity();
    float u1 = -std::numeric_limits<float>::infinity();
    float v0 = std::numeric_limits<float>::infinity();
    float v1 = -std::numeric_limits<float>::infinity();
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
    if (u1 - u0 < 2.f || v1 - v0 < 2.f) return b;
    if (ux < 0.f) {
        ux = -ux;
        uy = -uy;
        const float t = u0;
        u0 = -u1;
        u1 = -t;
    }
    b.cx = cx;
    b.cy = cy;
    b.ux = ux;
    b.uy = uy;
    b.vx = vx;
    b.vy = vy;
    b.u0 = u0;
    b.u1 = u1;
    b.v0 = v0;
    b.v1 = v1;
    b.ok = true;
    return b;
}

float longAngleDeg(const BoxUv& b) {
    float a = static_cast<float>(std::atan2(static_cast<double>(b.uy), static_cast<double>(b.ux)) * (180.0 / 3.141592653589793));
    while (a > 90.f) a -= 180.f;
    while (a < -90.f) a += 180.f;
    return a;
}

float angleDelta(float a, float b) {
    float d = std::fabs(a - b);
    d = std::fmod(d, 180.f);
    if (d > 90.f) d = 180.f - d;
    return d;
}

void cornerImage(const BoxUv& b, int i, float* xy) {
    const float u = (i == 0 || i == 3) ? b.u0 : b.u1;
    const float v = (i == 0 || i == 1) ? b.v0 : b.v1;
    xy[0] = b.cx + u * b.ux + v * b.vx;
    xy[1] = b.cy + u * b.uy + v * b.vy;
}

void proj(const BoxUv& b, float px, float py, float* u, float* v) {
    const float dx = px - b.cx;
    const float dy = py - b.cy;
    *u = dx * b.ux + dy * b.uy;
    *v = dx * b.vx + dy * b.vy;
}

void includePoint(BoxUv* b, float px, float py) {
    float u, v;
    proj(*b, px, py, &u, &v);
    if (u < b->u0) b->u0 = u;
    if (u > b->u1) b->u1 = u;
    if (v < b->v0) b->v0 = v;
    if (v > b->v1) b->v1 = v;
}

void includeBox(BoxUv* a, const BoxUv& other) {
    for (int i = 0; i < 4; ++i) {
        float xy[2];
        cornerImage(other, i, xy);
        includePoint(a, xy[0], xy[1]);
    }
}

float maxPoke(const BoxUv& a, const BoxUv& other) {
    float m = 0.f;
    for (int i = 0; i < 4; ++i) {
        float xy[2];
        cornerImage(other, i, xy);
        float u, v;
        proj(a, xy[0], xy[1], &u, &v);
        if (u > a.u1) m = std::max(m, u - a.u1);
        if (u < a.u0) m = std::max(m, a.u0 - u);
        if (v > a.v1) m = std::max(m, v - a.v1);
        if (v < a.v0) m = std::max(m, a.v0 - v);
    }
    return m;
}

void localAabb(const BoxUv& a, const BoxUv& other, float* loc) {
    loc[0] = std::numeric_limits<float>::infinity();
    loc[1] = -std::numeric_limits<float>::infinity();
    loc[2] = std::numeric_limits<float>::infinity();
    loc[3] = -std::numeric_limits<float>::infinity();
    for (int i = 0; i < 4; ++i) {
        float xy[2];
        cornerImage(other, i, xy);
        float u, v;
        proj(a, xy[0], xy[1], &u, &v);
        if (u < loc[0]) loc[0] = u;
        if (u > loc[1]) loc[1] = u;
        if (v < loc[2]) loc[2] = v;
        if (v > loc[3]) loc[3] = v;
    }
}

int cornersInside(const BoxUv& a, const BoxUv& other) {
    int n = 0;
    constexpr float eps = 0.5f;
    for (int i = 0; i < 4; ++i) {
        float xy[2];
        cornerImage(other, i, xy);
        float u, v;
        proj(a, xy[0], xy[1], &u, &v);
        if (u >= a.u0 - eps && u <= a.u1 + eps && v >= a.v0 - eps && v <= a.v1 + eps) ++n;
    }
    return n;
}

float boxArea(const BoxUv& b) {
    return spanAtLeast(b.u0, b.u1) * spanAtLeast(b.v0, b.v1);
}

bool shouldGrow(const BoxUv& a, const BoxUv& b, float pokePx) {
    const float poke = maxPoke(a, b);
    if (poke <= 0.5f) return true;
    float loc[4];
    localAabb(a, b, loc);
    const float iu0 = std::max(a.u0, loc[0]);
    const float iu1 = std::min(a.u1, loc[1]);
    const float iv0 = std::max(a.v0, loc[2]);
    const float iv1 = std::min(a.v1, loc[3]);
    const float inter = spanAtLeast(iu0, iu1) * spanAtLeast(iv0, iv1);
    if (inter <= 0.f) return false;
    const float aArea = std::max(1.f, boxArea(a));
    const float bArea = std::max(1.f, spanAtLeast(loc[0], loc[1]) * spanAtLeast(loc[2], loc[3]));
    const float unionArea = aArea + bArea - inter;
    const float iou = unionArea > 0.f ? inter / unionArea : 0.f;
    const float coverMin = inter / std::min(aArea, bArea);
    const float locV = spanAtLeast(loc[2], loc[3]);
    const float vRatio = std::min(spanAtLeast(a.v0, a.v1), locV) /
        std::max(spanAtLeast(a.v0, a.v1), std::max(1.f, locV));
    const int nIn = cornersInside(a, b);
    if (poke <= pokePx && nIn >= 2) return true;
    if (poke <= pokePx && coverMin >= 0.50f) return true;
    if (angleDelta(longAngleDeg(a), longAngleDeg(b)) <= 15.f &&
        (iou >= 0.50f || (coverMin >= 0.80f && vRatio >= 0.70f))) {
        return true;
    }
    return false;
}

void mergeSeeds(std::vector<BoxUv>* boxes, float pokePx) {
    bool changed = true;
    int guard = 0;
    while (changed && guard++ < 16) {
        changed = false;
        std::sort(boxes->begin(), boxes->end(), [](const BoxUv& a, const BoxUv& b) {
            return boxArea(a) > boxArea(b);
        });
        std::vector<char> used(boxes->size(), 0);
        std::vector<BoxUv> out;
        out.reserve(boxes->size());
        for (size_t i = 0; i < boxes->size(); ++i) {
            if (used[i]) continue;
            BoxUv cur = (*boxes)[i];
            for (size_t j = i + 1; j < boxes->size(); ++j) {
                if (used[j]) continue;
                if (!shouldGrow(cur, (*boxes)[j], pokePx)) continue;
                includeBox(&cur, (*boxes)[j]);
                used[j] = 1;
                changed = true;
            }
            out.push_back(cur);
        }
        *boxes = std::move(out);
    }
}

void keepTopN(std::vector<BoxUv>* boxes, int maxN) {
    if (maxN < 1 || static_cast<int>(boxes->size()) <= maxN) return;
    std::sort(boxes->begin(), boxes->end(), [](const BoxUv& a, const BoxUv& b) {
        return boxArea(a) > boxArea(b);
    });
    boxes->resize(static_cast<size_t>(maxN));
}

void orderTlTrBrBl(const float* p, float* out) {
    struct C { int i; float x, y; };
    C c[4];
    for (int i = 0; i < 4; ++i) c[i] = {i, p[i * 2], p[i * 2 + 1]};
    int i0 = 0;
    int i1 = 1;
    if (c[1].x < c[0].x) {
        i0 = 1;
        i1 = 0;
    }
    for (int i = 2; i < 4; ++i) {
        if (c[i].x < c[i0].x) {
            i1 = i0;
            i0 = i;
        } else if (c[i].x < c[i1].x) {
            i1 = i;
        }
    }
    const int bl = c[i0].y >= c[i1].y ? i0 : i1;
    const int n0 = (bl + 3) % 4;
    const int n1 = (bl + 1) % 4;
    const int br = c[n0].x >= c[n1].x ? n0 : n1;
    const int tl = (br == n0) ? n1 : n0;
    int tr = 0;
    for (int i = 0; i < 4; ++i) {
        if (i != bl && i != br && i != tl) tr = i;
    }
    const float raw[8] = {c[tl].x, c[tl].y, c[tr].x, c[tr].y, c[br].x, c[br].y, c[bl].x, c[bl].y};
    std::memcpy(out, raw, sizeof(raw));
}

void boxToQuad(const BoxUv& b, float* out) {
    float a[2], r[2], d[2], e[2];
    auto put = [&](float u, float v, float* xy) {
        xy[0] = b.cx + u * b.ux + v * b.vx;
        xy[1] = b.cy + u * b.uy + v * b.vy;
    };
    put(b.u0, b.v0, a);
    put(b.u1, b.v0, r);
    put(b.u1, b.v1, d);
    put(b.u0, b.v1, e);
    const float raw[8] = {a[0], a[1], r[0], r[1], d[0], d[1], e[0], e[1]};
    orderTlTrBrBl(raw, out);
}

BoxUv calculatedUv(const BoxUv& box, float v, float leftFrac, float rightFrac) {
    float bh = spanAtLeast(box.v0, box.v1);
    if (bh < 2.f) bh = 2.f;
    const float newH = bh * (1.f + 2.f * v);
    const float vMid = 0.5f * (box.v0 + box.v1);
    const float half = 0.5f * newH;
    BoxUv out = box;
    out.u0 = box.u0 - leftFrac * bh;
    out.u1 = box.u1 + rightFrac * bh;
    out.v0 = vMid - half;
    out.v1 = vMid + half;
    out.ok = true;
    return out;
}

void aabbOfQuad(const float* p, int* l, int* t, int* r, int* b) {
    float minX = p[0];
    float maxX = p[0];
    float minY = p[1];
    float maxY = p[1];
    for (int i = 1; i < 4; ++i) {
        minX = std::min(minX, p[i * 2]);
        maxX = std::max(maxX, p[i * 2]);
        minY = std::min(minY, p[i * 2 + 1]);
        maxY = std::max(maxY, p[i * 2 + 1]);
    }
    const int il = static_cast<int>(minX);
    const int it = static_cast<int>(minY);
    int ir = static_cast<int>(maxX);
    int ib = static_cast<int>(maxY);
    if (ir < il + 1) ir = il + 1;
    if (ib < it + 1) ib = it + 1;
    *l = il;
    *t = it;
    *r = ir;
    *b = ib;
}

void expandQuadFrame(const float* pts, float angleDeg, float minusU, float plusU, float minusV, float plusV, float* out) {
    if (minusU == 0.f && plusU == 0.f && minusV == 0.f && plusV == 0.f) {
        std::memcpy(out, pts, sizeof(float) * 8);
        return;
    }
    const double ang = static_cast<double>(angleDeg) * (3.141592653589793 / 180.0);
    const float ux = static_cast<float>(std::cos(ang));
    const float uy = static_cast<float>(std::sin(ang));
    float vx = -uy;
    float vy = ux;
    if (vy < 0.f) {
        vx = -vx;
        vy = -vy;
    }
    float us[4];
    float vs[4];
    for (int i = 0; i < 4; ++i) {
        us[i] = pts[i * 2] * ux + pts[i * 2 + 1] * uy;
        vs[i] = pts[i * 2] * vx + pts[i * 2 + 1] * vy;
    }
    float uMin = us[0], uMax = us[0], vMin = vs[0], vMax = vs[0];
    for (int i = 1; i < 4; ++i) {
        if (us[i] < uMin) uMin = us[i];
        if (us[i] > uMax) uMax = us[i];
        if (vs[i] < vMin) vMin = vs[i];
        if (vs[i] > vMax) vMax = vs[i];
    }
    const float uMid = (uMin + uMax) * 0.5f;
    const float vMid = (vMin + vMax) * 0.5f;
    for (int i = 0; i < 4; ++i) {
        const float du = us[i] <= uMid ? -minusU : plusU;
        const float dv = vs[i] <= vMid ? -minusV : plusV;
        out[i * 2] = pts[i * 2] + du * ux + dv * vx;
        out[i * 2 + 1] = pts[i * 2 + 1] + du * uy + dv * vy;
    }
}

bool warpToStrip(const cv::Mat& gray, const float* q, cv::Mat& dest) {
    if (gray.empty() || dest.empty()) return false;
    const float x1 = std::max(0.f, static_cast<float>(dest.cols - 1));
    const float y1 = std::max(0.f, static_cast<float>(dest.rows - 1));
    CvMat srcMat = cvMat(gray);
    CvMat dstMat = cvMat(dest);
    double mdata[9];
    CvMat map = cvMat(3, 3, CV_64FC1, mdata);
    const CvPoint2D32f src[4] = {{q[0], q[1]}, {q[2], q[3]}, {q[4], q[5]}, {q[6], q[7]}};
    const CvPoint2D32f dst[4] = {{0.f, 0.f}, {x1, 0.f}, {x1, y1}, {0.f, y1}};
    cvGetPerspectiveTransform(src, dst, &map);
    cvWarpPerspective(&srcMat, &dstMat, &map, CV_INTER_CUBIC + CV_WARP_FILL_OUTLIERS, cvScalarAll(0));
    return !dest.empty() && dest.cols >= 1 && dest.rows >= 1;
}

void scaleArea(const cv::Mat& src, cv::Mat& dest, float s) {
    const int dw = dest.cols;
    const int dh = dest.rows;
    const int sw = src.cols;
    const int sh = src.rows;
    if (dw < 1 || dh < 1 || sw < 1 || sh < 1 || !(s > 0.f)) return;
    for (int y = 0; y < dh; ++y) {
        const float fy0 = static_cast<float>(y) / s;
        const float fy1 = static_cast<float>(y + 1) / s;
        int sy0 = static_cast<int>(std::floor(static_cast<double>(fy0)));
        int sy1e = static_cast<int>(std::ceil(static_cast<double>(fy1)));
        if (sy0 < 0) sy0 = 0;
        if (sy0 > sh - 1) sy0 = sh - 1;
        if (sy1e < 1) sy1e = 1;
        if (sy1e > sh) sy1e = sh;
        uint8_t* drow = dest.ptr<uint8_t>(y);
        for (int x = 0; x < dw; ++x) {
            const float fx0 = static_cast<float>(x) / s;
            const float fx1 = static_cast<float>(x + 1) / s;
            int sx0 = static_cast<int>(std::floor(static_cast<double>(fx0)));
            int sx1e = static_cast<int>(std::ceil(static_cast<double>(fx1)));
            if (sx0 < 0) sx0 = 0;
            if (sx0 > sw - 1) sx0 = sw - 1;
            if (sx1e < 1) sx1e = 1;
            if (sx1e > sw) sx1e = sw;
            double acc = 0.0;
            double wsum = 0.0;
            for (int sy = sy0; sy < sy1e; ++sy) {
                const float yA = std::max(fy0, static_cast<float>(sy));
                const float yB = std::min(fy1, static_cast<float>(sy + 1));
                const double wy = static_cast<double>(yB - yA);
                if (wy <= 0.0) continue;
                const uint8_t* row = src.ptr<uint8_t>(sy);
                for (int sx = sx0; sx < sx1e; ++sx) {
                    const float xA = std::max(fx0, static_cast<float>(sx));
                    const float xB = std::min(fx1, static_cast<float>(sx + 1));
                    const double wx = static_cast<double>(xB - xA);
                    if (wx <= 0.0) continue;
                    const double wt = wx * wy;
                    acc += static_cast<double>(row[sx]) * wt;
                    wsum += wt;
                }
            }
            int iv = 0;
            if (wsum > 0.0) {
                iv = static_cast<int>(acc / wsum + 0.5);
                if (iv < 0) iv = 0;
                if (iv > 255) iv = 255;
            }
            drow[x] = static_cast<uint8_t>(iv);
        }
    }
}

void appendProb(std::string* probs, const std::string& ch, float v) {
    if (!probs->empty()) probs->push_back(',');
    char buf[32];
    std::snprintf(buf, sizeof(buf), ":%.2f", v);
    probs->append(ch);
    probs->append(buf);
}

std::string decodeFull(const float* data, int seqLen, int dictSize, const std::vector<std::string>& dict, std::string* probs) {
    // dict = dictionaryV3, which already carries the appended use_space_char ' ' (classes = dict + 1).
    static std::atomic<bool> warnedClassCount{false};
    if (dictSize != static_cast<int>(dict.size()) + 1 && !warnedClassCount.exchange(true)) {
        __android_log_print(ANDROID_LOG_WARN, "QfRotGmm", "rec class count %d != dict.size()+1 (%d); space class may be wrong",
                            dictSize, static_cast<int>(dict.size()) + 1);
    }
    std::string result;
    int lastIdx = -1;
    for (int i = 0; i < seqLen; ++i) {
        int maxIdx = 0;
        float maxVal = -1.f;
        const float* row = data + static_cast<size_t>(i) * static_cast<size_t>(dictSize);
        for (int j = 0; j < dictSize; ++j) {
            const float v = row[j];
            if (v > maxVal) {
                maxVal = v;
                maxIdx = j;
            }
        }
        if (maxIdx > 0 && maxIdx != lastIdx && maxIdx <= static_cast<int>(dict.size())) {
            const std::string& ch = dict[maxIdx - 1];
            result.append(ch);
            appendProb(probs, ch, maxVal);
        }
        lastIdx = maxIdx;
    }
    // Full text only: drop leading/trailing spaces from the model's space class.
    const size_t b = result.find_first_not_of(' ');
    if (b == std::string::npos) return std::string();
    return result.substr(b, result.find_last_not_of(' ') - b + 1);
}

std::string decodeDecimal(const float* data, int seqLen, int dictSize, const std::vector<std::string>& dict, std::string* probs) {
    std::string result;
    int lastIdx = -1;
    float lastConf = 1.f;
    for (int i = 0; i < seqLen; ++i) {
        const float* row = data + static_cast<size_t>(i) * static_cast<size_t>(dictSize);
        int maxIdx = 0;
        float maxVal = row[0];
        for (int allowed : kAllowedDecimal) {
            if (allowed >= dictSize) continue;
            const float v = row[allowed];
            if (v > maxVal) {
                maxVal = v;
                maxIdx = allowed;
            }
        }
        if (maxIdx > 0 && maxIdx != lastIdx && maxIdx <= static_cast<int>(dict.size())) {
            const std::string& ch = dict[maxIdx - 1];
            const float ratioThr = 0.30f * lastConf;
            const bool isSafe = result.size() < 5;
            const bool pass = isSafe || maxVal >= ratioThr;
            __android_log_print(
                ANDROID_LOG_INFO, "PaddleOCR",
                "Decoded: '%s' Len: %d Conf: %.3f Thr: %.3f Safe: %s Pass: %s",
                ch.c_str(), static_cast<int>(result.size()), maxVal, ratioThr,
                isSafe ? "true" : "false", pass ? "true" : "false");
            if (pass) {
                result.append(ch);
                lastConf = maxVal;
                appendProb(probs, ch, maxVal);
            } else {
                __android_log_print(
                    ANDROID_LOG_WARN, "PaddleOCR",
                    "Pruning: Confidence drop too high for '%s' (Ratio: %.3f < %.3f)",
                    ch.c_str(), maxVal, ratioThr);
                break;
            }
        }
        lastIdx = maxIdx;
    }
    return result;
}

bool checkExc(JNIEnv* env) {
    return env->ExceptionCheck();
}

std::shared_ptr<paddle::lite_api::PaddlePredictor> loadRecognizer(JNIEnv* env) {
    return veLoadSharedRecognizerV3(env);
}

std::vector<std::string> loadDictionary(JNIEnv* env) {
    std::vector<std::string> dict;
    jclass eng = env->FindClass("com/davidlang/vehicleexpensesautomated/ui/util/NativePaddleEngine");
    if (!eng || checkExc(env)) return dict;
    jfieldID fid = env->GetStaticFieldID(eng, "dictionaryV3", "Ljava/util/List;");
    if (checkExc(env)) return dict;
    if (!fid) {
        env->DeleteLocalRef(eng);
        return dict;
    }
    jobject list = env->GetStaticObjectField(eng, fid);
    env->DeleteLocalRef(eng);
    if (checkExc(env) || !list) return dict;
    jclass listCls = env->GetObjectClass(list);
    jmethodID sizeM = env->GetMethodID(listCls, "size", "()I");
    jmethodID getM = env->GetMethodID(listCls, "get", "(I)Ljava/lang/Object;");
    env->DeleteLocalRef(listCls);
    if (checkExc(env)) return dict;
    if (!sizeM || !getM) {
        env->DeleteLocalRef(list);
        return dict;
    }
    const jint n = env->CallIntMethod(list, sizeM);
    if (checkExc(env)) return dict;
    dict.reserve(n > 0 ? static_cast<size_t>(n) : 0);
    for (jint i = 0; i < n; ++i) {
        jobject item = env->CallObjectMethod(list, getM, i);
        if (checkExc(env)) return dict;
        if (!item) {
            dict.emplace_back();
            continue;
        }
        const char* utf = env->GetStringUTFChars(static_cast<jstring>(item), nullptr);
        dict.emplace_back(utf ? utf : "");
        if (utf) env->ReleaseStringUTFChars(static_cast<jstring>(item), utf);
        env->DeleteLocalRef(item);
    }
    env->DeleteLocalRef(list);
    return dict;
}

void bothDecodes(paddle::lite_api::PaddlePredictor& pred, const cv::Mat& mat, const std::vector<std::string>& dict, RecRow* row) {
    if (mat.cols < 1 || mat.rows != kRecH || mat.type() != CV_8UC1) {
        throw std::runtime_error("bothDecodes: bad crop");
    }
    try {
        std::unique_ptr<paddle::lite_api::Tensor> input = pred.GetInput(0);
        if (!input) throw std::runtime_error("bothDecodes: no input tensor");
        input->Resize({1, 1, static_cast<int64_t>(mat.rows), static_cast<int64_t>(mat.cols)});
        int8_t* dst = input->mutable_data<int8_t>();
        if (!dst) throw std::runtime_error("bothDecodes: no input data");
        for (int y = 0; y < mat.rows; ++y) {
            std::memcpy(
                dst + static_cast<size_t>(y) * static_cast<size_t>(mat.cols),
                mat.ptr<uint8_t>(y),
                static_cast<size_t>(mat.cols));
        }
        pred.Run();
        std::unique_ptr<const paddle::lite_api::Tensor> output = pred.GetOutput(0);
        if (!output) throw std::runtime_error("bothDecodes: no output tensor");
        const float* data = output->data<float>();
        const paddle::lite_api::shape_t shape = output->shape();
        if (!data || shape.size() < 3 || shape[1] < 1 || shape[2] < 1) {
            throw std::runtime_error("bothDecodes: bad output");
        }
        const int seqLen = static_cast<int>(shape[1]);
        const int dictSize = static_cast<int>(shape[2]);
        row->asis = decodeFull(data, seqLen, dictSize, dict, &row->asisProbs);
        row->digits = decodeDecimal(data, seqLen, dictSize, dict, &row->digitsProbs);
    } catch (const std::runtime_error&) {
        throw;
    } catch (const std::exception& ex) {
        throw std::runtime_error(std::string("bothDecodes: ") + ex.what());
    }
}

void recognizeOne(
    const cv::Mat& gray,
    const float* quad,
    int scratchW,
    int scratchH,
    paddle::lite_api::PaddlePredictor* pred,
    const std::vector<std::string>& dict,
    RecRow* row
) {
    (void)scratchW;
    (void)scratchH;
    const BoxUv box = boxFromQuad(quad);
    if (!box.ok) throw std::runtime_error("recognizeOne: bad quad");
    const float bw = std::max(1.f, spanAtLeast(box.u0, box.u1));
    const float bh = std::max(1.f, spanAtLeast(box.v0, box.v1));
    if (bw < 2.f || bh < 2.f) throw std::runtime_error("recognizeOne: crop too small");
    const Plan40 plan = planContent40(bw, bh, kRecH);
    const int srcW = plan.srcW;
    const int srcH = plan.srcH;
    float expanded[8];
    const float plusU = static_cast<float>(srcW) - plan.marginSrc - bw;
    const float plusV = static_cast<float>(srcH) - plan.marginSrc - bh;
    expandQuadFrame(quad, longAngleDeg(box), plan.marginSrc, plusU, plan.marginSrc, plusV, expanded);
    cv::Mat strip(srcH, srcW, CV_8UC1);
    cv::Mat dest(plan.destH, plan.destW, CV_8UC1, cv::Scalar(0));
    bool warped = false;
    try {
        warped = warpToStrip(gray, expanded, strip);
    } catch (const cv::Exception& ex) {
        __android_log_print(ANDROID_LOG_ERROR, "QfRotGmm", "warp failed: %s", ex.what());
        throw std::runtime_error(std::string("recognizeOne: warp failed: ") + ex.what());
    }
    if (!warped) throw std::runtime_error("recognizeOne: warp failed");
    scaleArea(strip, dest, plan.s);
    if (!pred) throw std::runtime_error("recognizeOne: no predictor");
    bothDecodes(*pred, dest, dict, row);
}

struct QfJniIds {
    jclass bufCls = nullptr;
    jmethodID getP = nullptr;
    jmethodID getS = nullptr;
    jmethodID getWidth = nullptr;
    jmethodID getHeight = nullptr;
    jclass sliceCls = nullptr;
    jmethodID getMat = nullptr;
    jmethodID getUv = nullptr;
    jclass matCls = nullptr;
    jclass integerCls = nullptr;
    jmethodID intValue = nullptr;
    jmethodID valueOf = nullptr;
    jclass utilsCls = nullptr;
    jclass engCls = nullptr;
    jmethodID prepare = nullptr;
    jclass compCls = nullptr;
    jmethodID deskew = nullptr;
    jclass pairCls = nullptr;
    jmethodID getFirst = nullptr;
    jmethodID getSecond = nullptr;
    jmethodID ctor = nullptr;
    jmethodID detect = nullptr;
    jclass resCls = nullptr;
    jmethodID getBoxes = nullptr;
    jclass listCls = nullptr;
    jmethodID sizeM = nullptr;
    jmethodID getM = nullptr;
    jclass boxCls = nullptr;
    jmethodID getPts = nullptr;
    jmethodID getConf = nullptr;
};

QfJniIds qfJni;

jclass holdClass(JNIEnv* env, jclass* slot, jclass local) {
    if (checkExc(env)) return nullptr;
    if (*slot) {
        if (local) env->DeleteLocalRef(local);
        return *slot;
    }
    if (!local) return nullptr;
    jobject global = env->NewGlobalRef(local);
    if (!global || checkExc(env)) return nullptr;
    env->DeleteLocalRef(local);
    *slot = static_cast<jclass>(global);
    return *slot;
}

jclass findHeld(JNIEnv* env, jclass* slot, const char* name) {
    if (*slot) return *slot;
    if (checkExc(env)) return nullptr;
    return holdClass(env, slot, env->FindClass(name));
}

jclass classOfHeld(JNIEnv* env, jclass* slot, jobject obj) {
    if (*slot) return *slot;
    if (!obj || checkExc(env)) return nullptr;
    return holdClass(env, slot, env->GetObjectClass(obj));
}

jmethodID holdMethod(JNIEnv* env, jmethodID* slot, jclass cls, const char* name, const char* sig) {
    if (*slot) return *slot;
    if (!cls || checkExc(env)) return nullptr;
    jmethodID mid = env->GetMethodID(cls, name, sig);
    if (!mid || checkExc(env)) return nullptr;
    *slot = mid;
    return mid;
}

jmethodID holdStaticMethod(JNIEnv* env, jmethodID* slot, jclass cls, const char* name, const char* sig) {
    if (*slot) return *slot;
    if (!cls || checkExc(env)) return nullptr;
    jmethodID mid = env->GetStaticMethodID(cls, name, sig);
    if (!mid || checkExc(env)) return nullptr;
    *slot = mid;
    return mid;
}

bool sliceYuv(JNIEnv* env, jobject buffer, const char* getterName, jmethodID* getterSlot, cv::Mat* y, cv::Mat* uv, int* w, int* h) {
    jclass bufCls = classOfHeld(env, &qfJni.bufCls, buffer);
    jmethodID getSlice = holdMethod(
        env, getterSlot, bufCls, getterName,
        "()Lcom/davidlang/vehicleexpensesautomated/ui/util/BufferSet$Slice;");
    jmethodID getW = holdMethod(env, &qfJni.getWidth, bufCls, "getWidth", "()I");
    jmethodID getH = holdMethod(env, &qfJni.getHeight, bufCls, "getHeight", "()I");
    if (checkExc(env)) return false;
    if (!getSlice || !getW || !getH) return false;
    *w = env->CallIntMethod(buffer, getW);
    *h = env->CallIntMethod(buffer, getH);
    jobject slice = env->CallObjectMethod(buffer, getSlice);
    if (checkExc(env)) return false;
    if (!slice) return false;
    jclass sliceCls = classOfHeld(env, &qfJni.sliceCls, slice);
    jmethodID getMat = holdMethod(env, &qfJni.getMat, sliceCls, "getMat", "()Lorg/opencv/core/Mat;");
    jmethodID getUv = holdMethod(env, &qfJni.getUv, sliceCls, "getUvMat", "()Lorg/opencv/core/Mat;");
    if (checkExc(env)) return false;
    if (!getMat || !getUv) {
        env->DeleteLocalRef(slice);
        return false;
    }
    jobject yObj = env->CallObjectMethod(slice, getMat);
    jobject uvObj = env->CallObjectMethod(slice, getUv);
    env->DeleteLocalRef(slice);
    if (checkExc(env)) return false;
    if (!yObj || !uvObj) return false;
    jclass matCls = classOfHeld(env, &qfJni.matCls, yObj);
    if (checkExc(env)) return false;
    if (!matCls) return false;
    jfieldID nativeObj = env->GetFieldID(matCls, "nativeObj", "J");
    if (checkExc(env)) return false;
    if (!nativeObj) return false;
    const jlong yPtr = env->GetLongField(yObj, nativeObj);
    const jlong uvPtr = env->GetLongField(uvObj, nativeObj);
    env->DeleteLocalRef(yObj);
    env->DeleteLocalRef(uvObj);
    if (yPtr == 0 || uvPtr == 0) return false;
    *y = *reinterpret_cast<cv::Mat*>(yPtr);
    *uv = *reinterpret_cast<cv::Mat*>(uvPtr);
    return !y->empty();
}

bool primaryYuv(JNIEnv* env, jobject buffer, cv::Mat* y, cv::Mat* uv, int* w, int* h) {
    return sliceYuv(env, buffer, "getP", &qfJni.getP, y, uv, w, h);
}

bool scratchYuv(JNIEnv* env, jobject buffer, cv::Mat* y, cv::Mat* uv, int* w, int* h) {
    return sliceYuv(env, buffer, "getS", &qfJni.getS, y, uv, w, h);
}

std::vector<uint8_t> encodeYuv(const cv::Mat& y, const cv::Mat& uv) {
    std::vector<uint8_t> out;
    if (y.empty() || uv.empty()) return out;
    if (!veEncodeYuvMatJpeg(y, uv, &out)) out.clear();
    return out;
}

void drawSeed(cv::Mat& y, cv::Mat& uv, const float* q) {
    const cv::Scalar yCol(76);
    const cv::Scalar uvCol(255, 84);
    for (int i = 0; i < 4; ++i) {
        const int j = (i + 1) % 4;
        const cv::Point a(static_cast<int>(std::lround(q[i * 2])), static_cast<int>(std::lround(q[i * 2 + 1])));
        const cv::Point b(static_cast<int>(std::lround(q[j * 2])), static_cast<int>(std::lround(q[j * 2 + 1])));
        cv::line(y, a, b, yCol, 2, cv::LINE_8);
        if (!uv.empty()) {
            cv::line(uv, cv::Point(a.x / 2, a.y / 2), cv::Point(b.x / 2, b.y / 2), uvCol, 1, cv::LINE_8);
        }
    }
}

int intValue(JNIEnv* env, jobject integerObj) {
    if (!integerObj) return 0;
    jclass cls = classOfHeld(env, &qfJni.integerCls, integerObj);
    jmethodID m = holdMethod(env, &qfJni.intValue, cls, "intValue", "()I");
    if (checkExc(env)) return 0;
    if (!m) return 0;
    return env->CallIntMethod(integerObj, m);
}

void collectScaleBoxes(JNIEnv* env, jobject buffer, jobject context, int scale, std::vector<BoxUv>* boxes, std::vector<RawBox>* raw) {
    jclass utilsCls = findHeld(env, &qfJni.utilsCls, "com/davidlang/vehicleexpensesautomated/ui/util/PumpCostVolUtils");
    if (!utilsCls || checkExc(env)) return;
    jclass engCls = findHeld(env, &qfJni.engCls, "com/davidlang/vehicleexpensesautomated/ui/util/NativePaddleEngine");
    if (!engCls || checkExc(env)) return;
    jfieldID utilsInst = env->GetStaticFieldID(utilsCls, "INSTANCE", "Lcom/davidlang/vehicleexpensesautomated/ui/util/PumpCostVolUtils;");
    jfieldID companionFid = env->GetStaticFieldID(
        engCls, "Companion", "Lcom/davidlang/vehicleexpensesautomated/ui/util/NativePaddleEngine$Companion;");
    if (!utilsInst || !companionFid || checkExc(env)) return;
    jobject utils = env->GetStaticObjectField(utilsCls, utilsInst);
    jobject companion = env->GetStaticObjectField(engCls, companionFid);
    if (!utils || !companion || checkExc(env)) return;
    jmethodID prepare = holdMethod(
        env, &qfJni.prepare, utilsCls, "prepareScale",
        "(Lcom/davidlang/vehicleexpensesautomated/ui/util/BufferSet;I)Lkotlin/Pair;");
    jclass compCls = classOfHeld(env, &qfJni.compCls, companion);
    jmethodID deskew = holdMethod(
        env, &qfJni.deskew, compCls, "deskewSetFor",
        "(I)Lcom/davidlang/vehicleexpensesautomated/ui/util/BufferSet;");
    if (!prepare || !deskew || checkExc(env)) return;
    jobject pair = env->CallObjectMethod(utils, prepare, buffer, scale);
    if (checkExc(env) || !pair) return;
    jclass pairCls = classOfHeld(env, &qfJni.pairCls, pair);
    jmethodID getFirst = holdMethod(env, &qfJni.getFirst, pairCls, "getFirst", "()Ljava/lang/Object;");
    jmethodID getSecond = holdMethod(env, &qfJni.getSecond, pairCls, "getSecond", "()Ljava/lang/Object;");
    if (!getFirst || !getSecond || checkExc(env)) return;
    jobject first = env->CallObjectMethod(pair, getFirst);
    jobject second = env->CallObjectMethod(pair, getSecond);
    const int contentW = intValue(env, first);
    if (checkExc(env)) return;
    const int contentH = intValue(env, second);
    if (checkExc(env)) return;
    env->DeleteLocalRef(pair);
    if (first) env->DeleteLocalRef(first);
    if (second) env->DeleteLocalRef(second);
    if (contentW < 1 || contentH < 1) return;
    jobject dest = env->CallObjectMethod(companion, deskew, scale);
    if (checkExc(env) || !dest) return;
    jclass destCls = classOfHeld(env, &qfJni.bufCls, dest);
    jmethodID getW = holdMethod(env, &qfJni.getWidth, destCls, "getWidth", "()I");
    jmethodID getH = holdMethod(env, &qfJni.getHeight, destCls, "getHeight", "()I");
    if (!getW || checkExc(env)) return;
    const int S = env->CallIntMethod(dest, getW);
    jclass bufCls = classOfHeld(env, &qfJni.bufCls, buffer);
    jmethodID bufW = holdMethod(env, &qfJni.getWidth, bufCls, "getWidth", "()I");
    jmethodID bufH = holdMethod(env, &qfJni.getHeight, bufCls, "getHeight", "()I");
    if (!bufW || !bufH || checkExc(env)) return;
    const int fullW = env->CallIntMethod(buffer, bufW);
    const int fullH = env->CallIntMethod(buffer, bufH);
    const int contentMax = std::max(contentW, contentH);
    const float heatToPhoto = static_cast<float>(std::max(fullW, fullH)) / static_cast<float>(std::max(1, contentMax));
    jmethodID ctor = holdMethod(env, &qfJni.ctor, engCls, "<init>", "(Landroid/content/Context;Ljava/lang/String;)V");
    if (!ctor || checkExc(env)) return;
    jstring variant = env->NewStringUTF("Numeric");
    jobject engine = env->NewObject(engCls, ctor, context, variant);
    env->DeleteLocalRef(variant);
    if (checkExc(env) || !engine) return;
    jmethodID detect = holdMethod(
        env, &qfJni.detect, engCls, "detect",
        "(Ljava/lang/Object;Ljava/lang/Integer;Ljava/lang/Integer;ZILjava/io/File;FILjava/util/Map;Ljava/util/Map;ILorg/opencv/core/Mat;FII)Lcom/davidlang/vehicleexpensesautomated/ui/util/NativePaddleEngine$DetectionResult;");
    if (!detect || checkExc(env)) return;
    jclass integerCls = findHeld(env, &qfJni.integerCls, "java/lang/Integer");
    jmethodID valueOf = holdStaticMethod(env, &qfJni.valueOf, integerCls, "valueOf", "(I)Ljava/lang/Integer;");
    if (!integerCls || !valueOf || checkExc(env)) return;
    jobject iw = env->CallStaticObjectMethod(integerCls, valueOf, S);
    jobject ih = env->CallStaticObjectMethod(integerCls, valueOf, S);
    jobject result = env->CallObjectMethod(
        engine, detect, dest, iw, ih, JNI_FALSE, 0, nullptr, 0.f, 0, nullptr, nullptr, 0, nullptr,
        heatToPhoto, fullW, fullH);
    if (iw) env->DeleteLocalRef(iw);
    if (ih) env->DeleteLocalRef(ih);
    env->DeleteLocalRef(engine);
    env->DeleteLocalRef(dest);
    if (checkExc(env) || !result) return;
    jclass resCls = classOfHeld(env, &qfJni.resCls, result);
    jmethodID getBoxes = holdMethod(env, &qfJni.getBoxes, resCls, "getNativeBoxes", "()Ljava/util/List;");
    if (!getBoxes || checkExc(env)) return;
    jobject list = env->CallObjectMethod(result, getBoxes);
    env->DeleteLocalRef(result);
    if (checkExc(env) || !list) return;
    jclass listCls = classOfHeld(env, &qfJni.listCls, list);
    jmethodID sizeM = holdMethod(env, &qfJni.sizeM, listCls, "size", "()I");
    jmethodID getM = holdMethod(env, &qfJni.getM, listCls, "get", "(I)Ljava/lang/Object;");
    if (!sizeM || !getM || checkExc(env)) return;
    const jint n = env->CallIntMethod(list, sizeM);
    if (checkExc(env)) return;
    (void)getH;
    for (jint i = 0; i < n; ++i) {
        jobject box = env->CallObjectMethod(list, getM, i);
        if (checkExc(env)) return;
        if (!box) continue;
        jmethodID getPts = qfJni.getPts;
        jmethodID getConf = qfJni.getConf;
        if (!getPts || !getConf) {
            jclass local = env->GetObjectClass(box);
            if (!local || checkExc(env)) return;
            if (!getPts) {
                getPts = env->GetMethodID(local, "getPoints", "()[F");
                if (checkExc(env)) return;
                if (!getPts) {
                    env->DeleteLocalRef(local);
                    env->DeleteLocalRef(box);
                    continue;
                }
                qfJni.getPts = getPts;
            }
            if (!getConf) {
                getConf = env->GetMethodID(local, "getConfidence", "()F");
                if (checkExc(env)) return;
                if (!getConf) {
                    env->DeleteLocalRef(local);
                    env->DeleteLocalRef(box);
                    continue;
                }
                qfJni.getConf = getConf;
            }
            if (!holdClass(env, &qfJni.boxCls, local) || checkExc(env)) return;
        }
        const jfloat score = env->CallFloatMethod(box, getConf);
        if (checkExc(env)) return;
        jobject ptsObj = env->CallObjectMethod(box, getPts);
        env->DeleteLocalRef(box);
        if (checkExc(env)) return;
        if (!ptsObj) continue;
        jfloatArray arr = static_cast<jfloatArray>(ptsObj);
        if (env->GetArrayLength(arr) < 8) {
            env->DeleteLocalRef(arr);
            continue;
        }
        float p[8];
        env->GetFloatArrayRegion(arr, 0, 8, p);
        env->DeleteLocalRef(arr);
        RawBox rb{};
        rb.scale = scale;
        std::memcpy(rb.corners, p, sizeof(rb.corners));
        rb.score = score;
        raw->push_back(rb);
        const BoxUv b = boxFromQuad(p);
        if (b.ok) boxes->push_back(b);
    }
    env->DeleteLocalRef(list);
}

jbyteArray toByteArray(JNIEnv* env, const std::vector<uint8_t>& bytes) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(bytes.size()));
    if (!arr) return nullptr;
    if (!bytes.empty()) {
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(bytes.size()), reinterpret_cast<const jbyte*>(bytes.data()));
    }
    return arr;
}

jstring toJString(JNIEnv* env, const std::string& s) {
    if (env->ExceptionCheck()) return nullptr;
    jstring js = env->NewStringUTF(s.c_str());
    if (!js || env->ExceptionCheck()) {
        veThrowIllegalState(env, "toJString: NewStringUTF failed");
        return nullptr;
    }
    return js;
}

jobject newDetectOut(
    JNIEnv* env,
    const std::vector<uint8_t>& original,
    const std::vector<uint8_t>& discovery,
    const std::vector<float>& pts,
    const std::vector<jint>& aabb,
    const std::vector<jint>& rawScale,
    const std::vector<float>& rawCorners,
    const std::vector<float>& rawScore
) {
    jclass cls = env->FindClass("com/davidlang/vehicleexpensesautomated/ui/util/QfRotGmmDetectOut");
    if (!cls || checkExc(env)) return nullptr;
    jmethodID ctor = env->GetMethodID(cls, "<init>", "([B[B[F[I[I[F[F)V");
    if (checkExc(env)) return nullptr;
    if (!ctor) {
        env->DeleteLocalRef(cls);
        return nullptr;
    }
    jbyteArray orig = toByteArray(env, original);
    jbyteArray disc = toByteArray(env, discovery);
    jfloatArray p = env->NewFloatArray(static_cast<jsize>(pts.size()));
    jintArray a = env->NewIntArray(static_cast<jsize>(aabb.size()));
    jintArray scales = env->NewIntArray(static_cast<jsize>(rawScale.size()));
    jfloatArray corners = env->NewFloatArray(static_cast<jsize>(rawCorners.size()));
    jfloatArray scores = env->NewFloatArray(static_cast<jsize>(rawScore.size()));
    if (!pts.empty()) env->SetFloatArrayRegion(p, 0, static_cast<jsize>(pts.size()), pts.data());
    if (!aabb.empty()) env->SetIntArrayRegion(a, 0, static_cast<jsize>(aabb.size()), aabb.data());
    if (!rawScale.empty()) env->SetIntArrayRegion(scales, 0, static_cast<jsize>(rawScale.size()), rawScale.data());
    if (!rawCorners.empty()) env->SetFloatArrayRegion(corners, 0, static_cast<jsize>(rawCorners.size()), rawCorners.data());
    if (!rawScore.empty()) env->SetFloatArrayRegion(scores, 0, static_cast<jsize>(rawScore.size()), rawScore.data());
    jobject out = env->NewObject(cls, ctor, orig, disc, p, a, scales, corners, scores);
    env->DeleteLocalRef(cls);
    env->DeleteLocalRef(orig);
    env->DeleteLocalRef(disc);
    env->DeleteLocalRef(p);
    env->DeleteLocalRef(a);
    env->DeleteLocalRef(scales);
    env->DeleteLocalRef(corners);
    env->DeleteLocalRef(scores);
    return out;
}

jobject newRecOut(JNIEnv* env, const std::vector<RecRow>& rows) {
    const jsize n = static_cast<jsize>(rows.size());
    jclass stringCls = env->FindClass("java/lang/String");
    jobjectArray asis = env->NewObjectArray(n, stringCls, nullptr);
    jobjectArray asisProbs = env->NewObjectArray(n, stringCls, nullptr);
    jobjectArray digits = env->NewObjectArray(n, stringCls, nullptr);
    jobjectArray digitsProbs = env->NewObjectArray(n, stringCls, nullptr);
    env->DeleteLocalRef(stringCls);
    for (jsize i = 0; i < n; ++i) {
        jstring a = toJString(env, rows[i].asis);
        jstring ap = toJString(env, rows[i].asisProbs);
        jstring d = toJString(env, rows[i].digits);
        jstring dp = toJString(env, rows[i].digitsProbs);
        env->SetObjectArrayElement(asis, i, a);
        env->SetObjectArrayElement(asisProbs, i, ap);
        env->SetObjectArrayElement(digits, i, d);
        env->SetObjectArrayElement(digitsProbs, i, dp);
        env->DeleteLocalRef(a);
        env->DeleteLocalRef(ap);
        env->DeleteLocalRef(d);
        env->DeleteLocalRef(dp);
    }
    jclass cls = env->FindClass("com/davidlang/vehicleexpensesautomated/ui/util/QfRotGmmRecOut");
    if (!cls || checkExc(env)) return nullptr;
    jmethodID ctor = env->GetMethodID(
        cls, "<init>", "([Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;)V");
    if (checkExc(env)) return nullptr;
    if (!ctor) {
        env->DeleteLocalRef(cls);
        return nullptr;
    }
    jobject out = env->NewObject(cls, ctor, asis, asisProbs, digits, digitsProbs);
    env->DeleteLocalRef(cls);
    env->DeleteLocalRef(asis);
    env->DeleteLocalRef(asisProbs);
    env->DeleteLocalRef(digits);
    env->DeleteLocalRef(digitsProbs);
    return out;
}

EvenRect evenRect(int l, int t, int r, int b, int imgW, int imgH) {
    EvenRect er{};
    er.ok = false;
    if (r <= l || b <= t || imgW < 2 || imgH < 2) return er;
    int l2 = l & ~1;
    int t2 = t & ~1;
    if (l2 < 0) l2 = 0;
    if (t2 < 0) t2 = 0;
    int r2 = (r + 1) & ~1;
    int b2 = (b + 1) & ~1;
    const int maxR = imgW & ~1;
    const int maxB = imgH & ~1;
    if (r2 > maxR) r2 = maxR;
    if (b2 > maxB) b2 = maxB;
    if (r2 - l2 < 2 || b2 - t2 < 2) return er;
    er.l = l2;
    er.t = t2;
    er.r = r2;
    er.b = b2;
    er.ok = true;
    return er;
}

void copyPlane(const cv::Mat& src, cv::Mat& dst, int sx, int sy, int dx, int dy, int w, int h) {
    if (src.empty() || dst.empty() || src.type() != dst.type() || w < 1 || h < 1) return;
    const int elem = static_cast<int>(src.elemSize());
    for (int row = 0; row < h; ++row) {
        const int srow = sy + row;
        const int drow = dy + row;
        if (srow < 0 || drow < 0 || srow >= src.rows || drow >= dst.rows) continue;
        const int copyW = std::min(w, std::min(src.cols - sx, dst.cols - dx));
        if (copyW < 1 || sx < 0 || dx < 0) continue;
        std::memcpy(dst.ptr(drow) + dx * elem, src.ptr(srow) + sx * elem, static_cast<size_t>(copyW) * elem);
    }
}

}  // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_OcrHarness_qfRotGmmDetect(
    JNIEnv* env, jobject, jobject buffer, jint maxRedBoxes, jobject context
) {
    VePredictorGuard guard;
    try {
    __android_log_print(ANDROID_LOG_INFO, "QfRotGmm", "qfRotGmmDetect");
    std::vector<float> pts;
    std::vector<jint> aabb;
    std::vector<uint8_t> original;
    std::vector<uint8_t> discovery;
    std::vector<jint> rawScale;
    std::vector<float> rawCorners;
    std::vector<float> rawScore;
    if (!buffer || !context) return newDetectOut(env, original, discovery, pts, aabb, rawScale, rawCorners, rawScore);
    cv::Mat y, uv;
    int imgW = 0, imgH = 0;
    if (!primaryYuv(env, buffer, &y, &uv, &imgW, &imgH)) {
        if (checkExc(env)) return nullptr;
        return newDetectOut(env, original, discovery, pts, aabb, rawScale, rawCorners, rawScore);
    }
    std::vector<BoxUv> seeds;
    std::vector<RawBox> raw;
    for (int scale : kDetScales) {
        if (env->PushLocalFrame(64) < 0) return nullptr;
        collectScaleBoxes(env, buffer, context, scale, &seeds, &raw);
        if (checkExc(env)) return nullptr;
        env->PopLocalFrame(nullptr);
    }
    rawScale.reserve(raw.size());
    rawCorners.reserve(raw.size() * 8);
    rawScore.reserve(raw.size());
    for (const RawBox& rb : raw) {
        rawScale.push_back(rb.scale);
        for (int k = 0; k < 8; ++k) rawCorners.push_back(rb.corners[k]);
        rawScore.push_back(rb.score);
    }
    mergeSeeds(&seeds, 40.f);
    keepTopN(&seeds, maxRedBoxes);
    cv::Mat yInk, uvInk;
    int scratchW = 0, scratchH = 0;
    const bool haveScratch = scratchYuv(env, buffer, &yInk, &uvInk, &scratchW, &scratchH);
    if (checkExc(env)) return nullptr;
    const bool scratchSame =
        haveScratch &&
        y.rows == yInk.rows && y.cols == yInk.cols && y.type() == yInk.type() &&
        uv.rows == uvInk.rows && uv.cols == uvInk.cols && uv.type() == uvInk.type();
    if (haveScratch && !scratchSame) {
        throw std::runtime_error("qfRotGmmDetect: scratch size mismatch");
    }
    if (scratchSame) {
        copyPlane(y, yInk, 0, 0, 0, 0, y.cols, y.rows);
        copyPlane(uv, uvInk, 0, 0, 0, 0, uv.cols, uv.rows);
    }
    pts.reserve(seeds.size() * 8);
    aabb.reserve(seeds.size() * 4);
    for (const BoxUv& seed : seeds) {
        float seedQ[8];
        float blueQ[8];
        boxToQuad(seed, seedQ);
        const BoxUv blue = calculatedUv(seed, kVert, kLeftFrac, kRightFrac);
        boxToQuad(blue, blueQ);
        if (scratchSame) drawSeed(yInk, uvInk, seedQ);
        for (int k = 0; k < 8; ++k) pts.push_back(blueQ[k]);
        int l, t, r, b;
        aabbOfQuad(blueQ, &l, &t, &r, &b);
        aabb.push_back(l);
        aabb.push_back(t);
        aabb.push_back(r);
        aabb.push_back(b);
    }
    if (scratchSame) discovery = encodeYuv(yInk, uvInk);
    return newDetectOut(env, original, discovery, pts, aabb, rawScale, rawCorners, rawScore);
    } catch (const std::exception& ex) {
        veThrowIllegalState(env, ex.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_OcrHarness_qfRotGmmRecognize(
    JNIEnv* env, jobject, jobject buffer, jfloatArray bluePts
) {
    VePredictorGuard guard;
    try {
    __android_log_print(ANDROID_LOG_INFO, "QfRotGmm", "qfRotGmmRecognize");
    const jsize len = bluePts ? env->GetArrayLength(bluePts) : 0;
    const int n = len >= 8 ? static_cast<int>(len / 8) : 0;
    std::vector<RecRow> rows(static_cast<size_t>(n));
    if (n == 0 || !buffer) return newRecOut(env, rows);
    cv::Mat y, uv;
    int imgW = 0, imgH = 0;
    if (!primaryYuv(env, buffer, &y, &uv, &imgW, &imgH)) {
        if (checkExc(env)) return nullptr;
        throw std::runtime_error("qfRotGmmRecognize: primary yuv missing");
    }
    std::vector<float> pts(static_cast<size_t>(n) * 8);
    env->GetFloatArrayRegion(bluePts, 0, n * 8, pts.data());
    if (checkExc(env)) return nullptr;
    std::shared_ptr<paddle::lite_api::PaddlePredictor> pred = loadRecognizer(env);
    if (checkExc(env)) return nullptr;
    const std::vector<std::string> dict = loadDictionary(env);
    if (checkExc(env)) return nullptr;
    for (int i = 0; i < n; ++i) {
        recognizeOne(y, pts.data() + static_cast<size_t>(i) * 8, imgW, imgH, pred.get(), dict, &rows[i]);
    }
    return newRecOut(env, rows);
    } catch (const std::exception& ex) {
        veThrowIllegalState(env, ex.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_OcrHarness_qfRotGmmStackJpeg(
    JNIEnv* env, jobject, jobject buffer,
    jint costL, jint costT, jint costR, jint costB,
    jint volL, jint volT, jint volR, jint volB
) {
    __android_log_print(ANDROID_LOG_INFO, "QfRotGmm", "qfRotGmmStackJpeg");
    cv::Mat y, uv;
    int imgW = 0, imgH = 0;
    if (!buffer) return env->NewByteArray(0);
    if (!primaryYuv(env, buffer, &y, &uv, &imgW, &imgH)) {
        if (checkExc(env)) return nullptr;
        return env->NewByteArray(0);
    }
    const EvenRect cost = evenRect(costL, costT, costR, costB, imgW, imgH);
    const EvenRect vol = evenRect(volL, volT, volR, volB, imgW, imgH);
    if (!cost.ok && !vol.ok) return env->NewByteArray(0);
    const int costW = cost.ok ? cost.r - cost.l : 0;
    const int costH = cost.ok ? cost.b - cost.t : 0;
    const int volW = vol.ok ? vol.r - vol.l : 0;
    const int volH = vol.ok ? vol.b - vol.t : 0;
    int outW = std::max(costW, volW);
    int outH = costH + volH;
    if (outW < 2) outW = 2;
    if (outH < 2) outH = 2;
    if (outW & 1) ++outW;
    if (outH & 1) ++outH;
    static std::mutex stackMu;
    static cv::Mat yOut;
    static cv::Mat uvOut;
    std::lock_guard<std::mutex> stackLock(stackMu);
    yOut.create(outH, outW, CV_8UC1);
    uvOut.create(outH / 2, outW / 2, CV_8UC2);
    yOut.setTo(0);
    uvOut.setTo(cv::Scalar(128, 128));
    int yCursor = 0;
    auto place = [&](const EvenRect& er) {
        if (!er.ok) return;
        const int w = er.r - er.l;
        const int h = er.b - er.t;
        copyPlane(y, yOut, er.l, er.t, 0, yCursor, w, h);
        if (!uv.empty()) {
            copyPlane(uv, uvOut, er.l / 2, er.t / 2, 0, yCursor / 2, w / 2, h / 2);
        }
        yCursor += h;
    };
    place(cost);
    place(vol);
    const std::vector<uint8_t> jpeg = encodeYuv(yOut, uvOut);
    return toByteArray(env, jpeg);
}
