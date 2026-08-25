#include <jni.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "ContentExpandNative", __VA_ARGS__)

namespace {

struct Frame {
    float cx, cy, bw, bh, angDeg;
    float ux, uy, vx, vy;
    int imgW, imgH;
};

static bool frameFromQuad(const float* pts, Frame* f) {
    std::vector<cv::Point2f> p(4);
    for (int i = 0; i < 4; ++i) {
        p[i] = cv::Point2f(pts[i * 2], pts[i * 2 + 1]);
    }
    cv::RotatedRect rr = cv::minAreaRect(p);
    f->cx = static_cast<float>(rr.center.x);
    f->cy = static_cast<float>(rr.center.y);
    f->bw = std::max(2.f, static_cast<float>(rr.size.width));
    f->bh = std::max(2.f, static_cast<float>(rr.size.height));
    f->angDeg = static_cast<float>(rr.angle);
    if (f->bw < f->bh) {
        const float tmp = f->bw;
        f->bw = f->bh;
        f->bh = tmp;
        f->angDeg += 90.f;
    }
    const double rad = f->angDeg * (3.14159265358979323846 / 180.0);
    f->ux = static_cast<float>(std::cos(rad));
    f->uy = static_cast<float>(std::sin(rad));
    f->vx = -f->uy;
    f->vy = f->ux;
    return true;
}

static inline float magAt(const cv::Mat& mag, int x, int y) {
    return mag.ptr<float>(y)[x];
}

static float sampleEnergy(const cv::Mat& mag, float px, float py, int imgW, int imgH) {
    int x = static_cast<int>(std::lround(px));
    int y = static_cast<int>(std::lround(py));
    if (x < 0) x = 0;
    if (y < 0) y = 0;
    if (x >= imgW) x = imgW - 1;
    if (y >= imgH) y = imgH - 1;
    return magAt(mag, x, y);
}

// alongU: strip just outside ±u face; else outside ±v face. Matches Kotlin stripEnergy.
static double stripEnergy(
    const cv::Mat& mag, const Frame& fr,
    float cx, float cy, float bw, float bh,
    float duSign, float dvSign, bool alongU
) {
    const float halfU = bw * 0.5f;
    const float halfV = bh * 0.5f;
    const int n = std::max(4, static_cast<int>(std::lround(alongU ? bh : bw)));
    double sum = 0.0;
    int cnt = 0;
    for (int i = 0; i < n; ++i) {
        const float t = (i + 0.5f) / static_cast<float>(n) - 0.5f;
        float u, v;
        if (alongU) {
            u = duSign * (halfU + 0.5f);
            v = t * bh;
        } else {
            u = t * bw;
            v = dvSign * (halfV + 0.5f);
        }
        const float px = cx + u * fr.ux + v * fr.vx;
        const float py = cy + u * fr.uy + v * fr.vy;
        if (px < 0 || py < 0 || px >= fr.imgW || py >= fr.imgH) continue;
        sum += sampleEnergy(mag, px, py, fr.imgW, fr.imgH);
        ++cnt;
    }
    return cnt > 0 ? sum / cnt : 0.0;
}

static int runCount(const std::vector<float>& vals, float thr, int minRun = 3) {
    int n = 0;
    int run = 0;
    for (float v : vals) {
        if (v >= thr) {
            ++run;
        } else {
            if (run >= minRun) ++n;
            run = 0;
        }
    }
    if (run >= minRun) ++n;
    return n;
}

static void smooth1d(const std::vector<double>& a, double sigma, std::vector<double>* out) {
    const double s = std::max(0.6, sigma);
    const int rad = std::max(1, static_cast<int>(std::lround(s * 3.0)));
    std::vector<double> k(2 * rad + 1);
    double sum = 0.0;
    for (int i = 0; i < static_cast<int>(k.size()); ++i) {
        const double x = (i - rad) / s;
        k[i] = std::exp(-0.5 * x * x);
        sum += k[i];
    }
    for (double& kv : k) kv /= sum;
    out->assign(a.size(), 0.0);
    const int last = static_cast<int>(a.size()) - 1;
    for (int i = 0; i < static_cast<int>(a.size()); ++i) {
        double acc = 0.0;
        for (int j = 0; j < static_cast<int>(k.size()); ++j) {
            int ii = i + j - rad;
            if (ii < 0) ii = 0;
            if (ii > last) ii = last;
            acc += a[ii] * k[j];
        }
        (*out)[i] = acc;
    }
}

static void padCountTip(
    bool pulled, bool stopEnergy, double tipCount, double cThr,
    int existIdx, int countIdx, int seedIdx, bool outwardPositive,
    int clearSteps, int growSteps, int lo, int hi, bool allowGrow,
    int* outIdx, bool* grew
) {
    int idx = countIdx;
    *grew = false;
    if (pulled) {
        const int give = outwardPositive
            ? std::min(clearSteps, std::max(0, existIdx - countIdx))
            : std::min(clearSteps, std::max(0, countIdx - existIdx));
        idx = outwardPositive ? countIdx + give : countIdx - give;
    } else if (allowGrow && stopEnergy && cThr >= 0.45 && tipCount >= cThr) {
        idx = outwardPositive ? existIdx + growSteps : existIdx - growSteps;
        *grew = true;
    }
    if (idx < lo) idx = lo;
    if (idx > hi) idx = hi;
    idx = outwardPositive ? std::max(idx, seedIdx) : std::min(idx, seedIdx);
    *outIdx = idx;
}

}  // namespace

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeExpandOriented(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr,
    jfloatArray seedPts,
    jfloat maxFrac, jfloat energyRatio,
    jboolean freezeHorz, jboolean enableJump,
    jfloat jumpFrac, jfloat retractClearFrac, jfloat vertPadFrac
) {
    auto* gray = reinterpret_cast<cv::Mat*>(grayPtr);
    if (!gray || gray->empty() || gray->type() != CV_8UC1) return nullptr;
    if (!seedPts || env->GetArrayLength(seedPts) < 8) return nullptr;
    jfloat pts[8];
    env->GetFloatArrayRegion(seedPts, 0, 8, pts);

    Frame fr{};
    if (!frameFromQuad(pts, &fr)) return nullptr;
    fr.imgW = gray->cols;
    fr.imgH = gray->rows;

    cv::Mat gx, gy, mag;
    cv::Sobel(*gray, gx, CV_32F, 1, 0, 3);
    cv::Sobel(*gray, gy, CV_32F, 0, 1, 3);
    cv::magnitude(gx, gy, mag);
    gx.release();
    gy.release();

    float cx = fr.cx, cy = fr.cy, bw = fr.bw, bh = fr.bh;

    double baseSum = 0.0;
    int baseN = 0;
    const int nu = std::max(4, static_cast<int>(std::lround(bw * 0.6f)));
    const int nv = std::max(3, static_cast<int>(std::lround(bh * 0.6f)));
    for (int iu = 0; iu < nu; ++iu) {
        for (int iv = 0; iv < nv; ++iv) {
            const float u = ((iu + 0.5f) / nu - 0.5f) * bw * 0.7f;
            const float v = ((iv + 0.5f) / nv - 0.5f) * bh * 0.7f;
            const float px = cx + u * fr.ux + v * fr.vx;
            const float py = cy + u * fr.uy + v * fr.vy;
            if (px < 0 || py < 0 || px >= fr.imgW || py >= fr.imgH) continue;
            baseSum += sampleEnergy(mag, px, py, fr.imgW, fr.imgH);
            ++baseN;
        }
    }
    const double base = baseSum / std::max(baseN, 1);
    const double thr = energyRatio * std::max(base, 1e-3);
    const float seedBh = bh;
    const int cap = std::max(1, static_cast<int>(std::lround(maxFrac * seedBh)));
    int stepsVNeg = 0;
    int stepsVPos = 0;

    auto strip = [&](float du, float dv, bool alongU) {
        return stripEnergy(mag, fr, cx, cy, bw, bh, du, dv, alongU);
    };

    // 1px just outside seed ±v: below thr → do not grow or pad that tip.
    const bool allowVNeg = strip(0.f, -1.f, false) >= thr;
    const bool allowVPos = strip(0.f, +1.f, false) >= thr;

    for (int step = 0; step < cap; ++step) {
        bool grew = false;
        if (!freezeHorz) {
            if (strip(-1.f, 0.f, true) >= thr) {
                cx -= 0.5f * fr.ux;
                cy -= 0.5f * fr.uy;
                bw += 1.f;
                grew = true;
            }
            if (strip(+1.f, 0.f, true) >= thr) {
                cx += 0.5f * fr.ux;
                cy += 0.5f * fr.uy;
                bw += 1.f;
                grew = true;
            }
        }
        if (allowVNeg && strip(0.f, -1.f, false) >= thr) {
            cx -= 0.5f * fr.vx;
            cy -= 0.5f * fr.vy;
            bh += 1.f;
            ++stepsVNeg;
            grew = true;
        }
        if (allowVPos && strip(0.f, +1.f, false) >= thr) {
            cx += 0.5f * fr.vx;
            cy += 0.5f * fr.vy;
            bh += 1.f;
            ++stepsVPos;
            grew = true;
        }
        if (!grew) break;
    }

    int padV = 0;
    if (vertPadFrac > 0.f) {
        padV = std::max(1, static_cast<int>(std::lround(vertPadFrac * seedBh)));
        if (allowVNeg) {
            cx -= 0.5f * fr.vx * padV;
            cy -= 0.5f * fr.vy * padV;
            bh += static_cast<float>(padV);
        }
        if (allowVPos) {
            cx += 0.5f * fr.vx * padV;
            cy += 0.5f * fr.vy * padV;
            bh += static_cast<float>(padV);
        }
    }

    if (enableJump) {
        const float floorBw = bw;
        const float floorCx = cx;
        const float floorCy = cy;
        const float j = std::max(1.f, jumpFrac * bh);
        bw += 2.f * j;
        const bool stillText =
            strip(-1.f, 0.f, true) >= thr || strip(+1.f, 0.f, true) >= thr;
        if (stillText) {
            for (int step = 0; step < cap; ++step) {
                bool grew = false;
                if (strip(-1.f, 0.f, true) >= thr) {
                    cx -= 0.5f * fr.ux;
                    cy -= 0.5f * fr.uy;
                    bw += 1.f;
                    grew = true;
                }
                if (strip(+1.f, 0.f, true) >= thr) {
                    cx += 0.5f * fr.ux;
                    cy += 0.5f * fr.uy;
                    bw += 1.f;
                    grew = true;
                }
                if (!grew) break;
            }
        } else {
            while (bw > floorBw + 0.5f && strip(-1.f, 0.f, true) < thr) {
                cx += 0.5f * fr.ux;
                cy += 0.5f * fr.uy;
                bw -= 1.f;
            }
            while (bw > floorBw + 0.5f && strip(+1.f, 0.f, true) < thr) {
                cx -= 0.5f * fr.ux;
                cy -= 0.5f * fr.uy;
                bw -= 1.f;
            }
            if (bw < floorBw) {
                bw = floorBw;
                cx = floorCx;
                cy = floorCy;
            }
            const float clear = std::max(1.f, retractClearFrac * bh);
            bw += 2.f * clear;
        }
    }

    const int hitVertCap =
        (stepsVNeg >= cap || stepsVPos >= cap) ? 1 : 0;
    const float halfSeed = seedBh * 0.5f;
    const float stopEnergyUp = static_cast<float>(
        stripEnergy(mag, fr, cx, cy, bw, bh, 0.f, -1.f, false));
    // Match Kotlin stopEnergy via seed-centered v, not the grown box edge.
    auto stripAtSeedV = [&](float offsetFromCenterV) {
        const int n = std::max(4, static_cast<int>(std::lround(fr.bw)));
        double sum = 0.0;
        int cnt = 0;
        for (int i = 0; i < n; ++i) {
            const float t = (i + 0.5f) / n - 0.5f;
            const float u = t * fr.bw;
            const float px = fr.cx + u * fr.ux + offsetFromCenterV * fr.vx;
            const float py = fr.cy + u * fr.uy + offsetFromCenterV * fr.vy;
            if (px < 0 || py < 0 || px >= fr.imgW || py >= fr.imgH) continue;
            sum += sampleEnergy(mag, px, py, fr.imgW, fr.imgH);
            ++cnt;
        }
        return cnt > 0 ? sum / cnt : 0.0;
    };
    const float stopUpE = static_cast<float>(
        stripAtSeedV(-halfSeed - stepsVNeg - 1.f));
    const float stopDownE = static_cast<float>(
        stripAtSeedV(halfSeed + stepsVPos + 1.f));
    (void)stopEnergyUp;

    jfloat out[13] = {
        cx, cy, bw, bh, fr.angDeg,
        static_cast<jfloat>(stepsVNeg), static_cast<jfloat>(stepsVPos),
        static_cast<jfloat>(padV), static_cast<jfloat>(hitVertCap),
        stopUpE, stopDownE, static_cast<jfloat>(base), static_cast<jfloat>(thr),
    };
    jfloatArray arr = env->NewFloatArray(13);
    if (!arr) return nullptr;
    env->SetFloatArrayRegion(arr, 0, 13, out);
    return arr;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeCountPullbackOriented(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr,
    jfloatArray seedPts,
    jfloatArray existPts,
    jint extraLook,
    jboolean stopUpEnergy,
    jboolean stopDownEnergy,
    jfloat clearFrac,
    jfloat growFrac,
    jfloat maxHFrac
) {
    auto* gray = reinterpret_cast<cv::Mat*>(grayPtr);
    if (!gray || gray->empty() || gray->type() != CV_8UC1) return nullptr;
    if (!seedPts || !existPts) return nullptr;
    if (env->GetArrayLength(seedPts) < 8 || env->GetArrayLength(existPts) < 8) return nullptr;
    jfloat spts[8], epts[8];
    env->GetFloatArrayRegion(seedPts, 0, 8, spts);
    env->GetFloatArrayRegion(existPts, 0, 8, epts);

    Frame seedFr{};
    if (!frameFromQuad(spts, &seedFr)) return nullptr;
    seedFr.imgW = gray->cols;
    seedFr.imgH = gray->rows;
    const float seedCx = seedFr.cx;
    const float seedCy = seedFr.cy;
    const float seedBw = seedFr.bw;
    const float seedBh = seedFr.bh;
    const float ux = seedFr.ux, uy = seedFr.uy, vx = seedFr.vx, vy = seedFr.vy;

    auto uOf = [&](float px, float py) {
        return (px - seedCx) * ux + (py - seedCy) * uy;
    };
    auto vOf = [&](float px, float py) {
        return (px - seedCx) * vx + (py - seedCy) * vy;
    };
    float eU0 = 1e30f, eU1 = -1e30f, eV0 = 1e30f, eV1 = -1e30f;
    for (int i = 0; i < 4; ++i) {
        const float u = uOf(epts[i * 2], epts[i * 2 + 1]);
        const float v = vOf(epts[i * 2], epts[i * 2 + 1]);
        if (u < eU0) eU0 = u;
        if (u > eU1) eU1 = u;
        if (v < eV0) eV0 = v;
        if (v > eV1) eV1 = v;
    }
    const float existBw = std::max(2.f, eU1 - eU0);
    const float existCu = (eU0 + eU1) * 0.5f;
    const float vSeedNeg = -seedBh * 0.5f;
    const float vSeedPos = seedBh * 0.5f;
    const float vExistNeg = eV0;
    const float vExistPos = eV1;

    cv::Mat gx, gy;
    cv::Sobel(*gray, gx, CV_32F, 1, 0, 3);
    cv::Sobel(*gray, gy, CV_32F, 0, 1, 3);
    auto duAbs = [&](float px, float py) -> float {
        int x = static_cast<int>(std::lround(px));
        int y = static_cast<int>(std::lround(py));
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x >= seedFr.imgW) x = seedFr.imgW - 1;
        if (y >= seedFr.imgH) y = seedFr.imgH - 1;
        const float gxv = gx.ptr<float>(y)[x];
        const float gyv = gy.ptr<float>(y)[x];
        return std::fabs(gxv * ux + gyv * uy);
    };

    const int nU = std::max(4, static_cast<int>(std::lround(seedBw)));
    std::vector<float> seedBuf;
    seedBuf.reserve(static_cast<size_t>(nU * std::max(1, static_cast<int>(std::lround(seedBh)))));
    for (float sv = vSeedNeg; sv <= vSeedPos; sv += 1.f) {
        for (int i = 0; i < nU; ++i) {
            const float t = (i + 0.5f) / nU - 0.5f;
            const float u = t * seedBw;
            seedBuf.push_back(duAbs(seedCx + u * ux + sv * vx, seedCy + u * uy + sv * vy));
        }
    }
    double p90 = 8.0;
    if (seedBuf.size() >= 2) {
        std::sort(seedBuf.begin(), seedBuf.end());
        const int idx = static_cast<int>((seedBuf.size() - 1) * 0.90);
        p90 = seedBuf[std::max(0, std::min(idx, static_cast<int>(seedBuf.size()) - 1))];
    }
    const double gxThr = std::max(8.0, 0.55 * p90);
    const int look = std::max(0, extraLook);
    const int v0 = static_cast<int>(std::floor(std::min(vSeedNeg, vExistNeg) - look));
    const int v1 = static_cast<int>(std::ceil(std::max(vSeedPos, vExistPos) + look));
    const int n = v1 - v0;
    if (n < 2) return nullptr;

    std::vector<double> raw(n);
    std::vector<float> row(nU);
    for (int i = 0; i < n; ++i) {
        const float vv = static_cast<float>(v0 + i);
        for (int k = 0; k < nU; ++k) {
            const float t = (k + 0.5f) / nU - 0.5f;
            const float u = t * seedBw;
            row[k] = duAbs(seedCx + u * ux + vv * vx, seedCy + u * uy + vv * vy);
        }
        raw[i] = static_cast<double>(runCount(row, static_cast<float>(gxThr)));
    }
    gx.release();
    gy.release();

    const int sh = std::max(1, static_cast<int>(std::lround(seedBh)));
    std::vector<double> sm;
    smooth1d(raw, std::max(1.0, 0.04 * sh), &sm);
    int st = static_cast<int>(std::lround(vSeedNeg - v0));
    int sb = static_cast<int>(std::lround(vSeedPos - v0));
    if (st < 0) st = 0;
    if (st > n - 2) st = n - 2;
    if (sb < st + 1) sb = st + 1;
    if (sb > n) sb = n;
    std::vector<double> seedVals(sm.begin() + st, sm.begin() + sb);
    std::sort(seedVals.begin(), seedVals.end());
    const double cSeed = seedVals[seedVals.size() / 2];
    int te = static_cast<int>(std::lround(vExistNeg - v0));
    int be = static_cast<int>(std::lround(vExistPos - v0));
    if (te < 0) te = 0;
    if (te > st) te = st;
    if (be < sb) be = sb;
    if (be > n) be = n;

    bool pulledT = false, pulledB = false;
    int top = te, bot = be;
    if (cSeed >= 1.0) {
        const double cThr = 0.45 * cSeed;
        for (int i = st - 1; i >= te; --i) {
            const double left = (i == 0) ? sm[i] : sm[i - 1];
            const double right = (i == n - 1) ? sm[i] : sm[i + 1];
            if (sm[i] < cThr && sm[i] <= left && sm[i] <= right) {
                top = i;
                pulledT = true;
                break;
            }
        }
        for (int i = sb; i < be; ++i) {
            const double left = (i == 0) ? sm[i] : sm[i - 1];
            const double right = (i == n - 1) ? sm[i] : sm[i + 1];
            if (sm[i] < cThr && sm[i] <= left && sm[i] <= right) {
                bot = i;
                pulledB = true;
                break;
            }
        }
    }
    top = std::min(top, st);
    bot = std::max(bot, sb);
    const double cThr = 0.45 * cSeed;
    const int clearSteps = std::max(1, static_cast<int>(std::lround(clearFrac * sh)));
    const int growSteps = std::max(1, static_cast<int>(std::lround(growFrac * sh)));
    const double existH = static_cast<double>(vExistPos - vExistNeg);
    const bool allowGrow = existH <= maxHFrac * sh;
    const double tipCountTop = (te >= 0 && te < n) ? sm[te] : 0.0;
    const double tipCountBot = (be - 1 >= 0 && be - 1 < n) ? sm[be - 1]
        : ((be >= 0 && be < n) ? sm[be] : 0.0);
    const int topBeforePad = top;
    const int botBeforePad = bot;
    const int idxLo = std::min(0, te - growSteps);
    const int idxHi = std::max(n, be + growSteps);
    int topPad = top, botPad = bot;
    bool grewT = false, grewB = false;
    padCountTip(pulledT, stopUpEnergy, tipCountTop, cThr, te, top, st, false,
                clearSteps, growSteps, idxLo, idxHi, allowGrow, &topPad, &grewT);
    padCountTip(pulledB, stopDownEnergy, tipCountBot, cThr, be, bot, sb, true,
                clearSteps, growSteps, idxLo, idxHi, allowGrow, &botPad, &grewB);
    top = std::min(topPad, st);
    bot = std::max(botPad, sb);
    if (bot < top + 1) bot = std::max(top + 1, sb);
    const double vNegOut = static_cast<double>(v0 + top);
    const double vPosOut = static_cast<double>(v0 + bot);

    const int header = 24;
    const int nOut = header + n;
    std::vector<jfloat> out(nOut, 0.f);
    out[0] = pulledT ? 1.f : 0.f;
    out[1] = pulledB ? 1.f : 0.f;
    out[2] = grewT ? 1.f : 0.f;
    out[3] = grewB ? 1.f : 0.f;
    out[4] = static_cast<jfloat>(cSeed);
    out[5] = static_cast<jfloat>(cThr);
    out[6] = static_cast<jfloat>(gxThr);
    out[7] = vExistNeg;
    out[8] = vExistPos;
    out[9] = static_cast<jfloat>(vNegOut);
    out[10] = static_cast<jfloat>(vPosOut);
    out[11] = static_cast<jfloat>(top - topBeforePad);
    out[12] = static_cast<jfloat>(bot - botBeforePad);
    out[13] = static_cast<jfloat>(v0);
    out[14] = static_cast<jfloat>(n);
    out[15] = seedCx;
    out[16] = seedCy;
    out[17] = existCu;
    out[18] = existBw;
    out[19] = seedFr.angDeg;
    out[20] = seedBh;
    for (int i = 0; i < n; ++i) out[header + i] = static_cast<jfloat>(sm[i]);

    jfloatArray arr = env->NewFloatArray(nOut);
    if (!arr) return nullptr;
    env->SetFloatArrayRegion(arr, 0, nOut, out.data());
    return arr;
}

namespace {

static bool fillChromaMag(const cv::Mat& y, const cv::Mat& uv, cv::Mat* dst) {
    if (y.empty() || y.type() != CV_8UC1) return false;
    const int h = y.rows, w = y.cols;
    dst->create(h, w, CV_8UC1);
    if (uv.empty() || uv.type() != CV_8UC2 || uv.rows <= 0 || uv.cols <= 0) {
        dst->setTo(0);
        return true;
    }
    const int uvH = uv.rows, uvW = uv.cols;
    const bool half = uvW * 2 <= w + 1;
    for (int ly = 0; ly < h; ++ly) {
        int uy = half ? ly / 2 : (ly & ~1);
        if (uy < 0) uy = 0;
        if (uy >= uvH) uy = uvH - 1;
        const cv::Vec2b* uvp = uv.ptr<cv::Vec2b>(uy);
        uint8_t* op = dst->ptr<uint8_t>(ly);
        for (int lx = 0; lx < w; ++lx) {
            int ux = half ? lx / 2 : (lx & ~1);
            if (ux < 0) ux = 0;
            if (ux >= uvW) ux = uvW - 1;
            const int du = static_cast<int>(uvp[ux][0]) - 128;
            const int dv = static_cast<int>(uvp[ux][1]) - 128;
            int m = static_cast<int>(std::lround(std::hypot(static_cast<double>(du),
                                                            static_cast<double>(dv))));
            if (m > 255) m = 255;
            op[lx] = static_cast<uint8_t>(m);
        }
    }
    return true;
}

static double meanRectF(const cv::Mat& e, int l, int t, int r, int b, int W, int H) {
    if (l < 0) l = 0;
    if (t < 0) t = 0;
    if (r > W) r = W;
    if (b > H) b = H;
    if (r <= l || b <= t) return 0.0;
    double s = 0.0;
    int n = 0;
    for (int y = t; y < b; ++y) {
        const float* p = e.ptr<float>(y);
        for (int x = l; x < r; ++x) {
            s += p[x];
            ++n;
        }
    }
    return n > 0 ? s / n : 0.0;
}

static void jumpRetractH(
    const cv::Mat& eng, int* l, int t, int* r, int b,
    int imgW, int imgH, double thr, int capPx, float jumpFrac, float retractClearFrac
) {
    const int floorL = *l, floorR = *r;
    const int hgt = std::max(1, b - t);
    const int jx = std::max(1, static_cast<int>(std::lround(jumpFrac * hgt)));
    *l = std::max(0, *l - jx);
    *r = std::min(imgW, *r + jx);
    const bool inText =
        (*l < floorL && meanRectF(eng, *l, t, std::min(*l + 1, *r), b, imgW, imgH) >= thr) ||
        (*r > floorR && meanRectF(eng, std::max(*r - 1, *l), t, *r, b, imgW, imgH) >= thr);
    if (inText) {
        for (int k = 0; k < capPx; ++k) {
            bool grew = false;
            if (*l > 0 && meanRectF(eng, *l - 1, t, *l, b, imgW, imgH) >= thr) {
                --(*l);
                grew = true;
            }
            if (*r < imgW && meanRectF(eng, *r, t, *r + 1, b, imgW, imgH) >= thr) {
                ++(*r);
                grew = true;
            }
            if (!grew) break;
        }
    } else {
        while (*l < floorL &&
               meanRectF(eng, *l, t, std::min(*l + 1, *r), b, imgW, imgH) < thr) ++(*l);
        while (*r > floorR &&
               meanRectF(eng, std::max(*r - 1, *l), t, *r, b, imgW, imgH) < thr) --(*r);
        const int clear = std::max(1, static_cast<int>(std::lround(
            retractClearFrac * std::max(1, b - t))));
        *l = std::max(0, *l - clear);
        *r = std::min(imgW, *r + clear);
    }
}

static void extrema1d(
    const std::vector<double>& a, double prominence, int minDist, bool maxima,
    std::vector<int>* out
) {
    const double sign = maxima ? 1.0 : -1.0;
    std::vector<int> cand;
    if (a.size() < 3) {
        out->clear();
        return;
    }
    for (int i = 1; i + 1 < static_cast<int>(a.size()); ++i) {
        const double v = sign * a[i];
        if (v < sign * a[i - 1] || v < sign * a[i + 1]) continue;
        double leftMin = v;
        int j = i - 1;
        while (j >= 0 && sign * a[j] <= v + 1e-12) {
            leftMin = std::min(leftMin, sign * a[j]);
            --j;
        }
        double rightMin = v;
        j = i + 1;
        while (j < static_cast<int>(a.size()) && sign * a[j] <= v + 1e-12) {
            rightMin = std::min(rightMin, sign * a[j]);
            ++j;
        }
        const double prom = v - std::max(leftMin, rightMin);
        if (prom + 1e-12 >= prominence) cand.push_back(i);
    }
    std::vector<int> order = cand;
    std::sort(order.begin(), order.end(), [&](int i, int j) {
        return sign * a[i] > sign * a[j];
    });
    std::vector<int> kept;
    for (int i : order) {
        bool ok = true;
        for (int k : kept) {
            if (std::abs(k - i) < minDist) { ok = false; break; }
        }
        if (ok) kept.push_back(i);
    }
    std::sort(kept.begin(), kept.end());
    *out = std::move(kept);
}

static void xycutOnProfile(
    const cv::Mat& eng, int l, int seedT, int r, int seedB,
    int imgW, int imgH, int look, int* top, int* bot
) {
    const int y0 = std::max(0, seedT - look);
    const int y1 = std::min(imgH, seedB + look);
    if (y1 - y0 < 4 || r <= l) {
        *top = seedT;
        *bot = seedB;
        return;
    }
    const int n = y1 - y0;
    std::vector<double> raw(n, 0.0);
    for (int i = 0; i < n; ++i) {
        raw[i] = meanRectF(eng, l, y0 + i, r, y0 + i + 1, imgW, imgH);
    }
    const int sh = std::max(1, seedB - seedT);
    std::vector<double> sm;
    smooth1d(raw, std::max(1.0, 0.04 * sh), &sm);
    int st = seedT - y0;
    int sb = seedB - y0;
    if (st < 0) st = 0;
    if (st > n - 1) st = n - 1;
    if (sb < st + 1) sb = st + 1;
    if (sb > n) sb = n;
    double seedMin = sm[st], seedMax = sm[st];
    for (int i = st; i < sb; ++i) {
        if (sm[i] < seedMin) seedMin = sm[i];
        if (sm[i] > seedMax) seedMax = sm[i];
    }
    const double prom = std::max(1e-8, 0.12 * (seedMax - seedMin + 1e-6));
    const int minDist = std::max(3, sh / 6);
    std::vector<int> peaks, valleys;
    extrema1d(sm, prom, minDist, true, &peaks);
    extrema1d(sm, prom, std::max(2, sh / 10), false, &valleys);
    const double mid = 0.5 * (st + sb);
    int own = st;
    if (!peaks.empty()) {
        own = peaks[0];
        double best = std::abs(peaks[0] - mid);
        for (int p : peaks) {
            const double d = std::abs(p - mid);
            if (d < best) { best = d; own = p; }
        }
    } else {
        double bestV = sm[st];
        for (int i = st; i < sb; ++i) {
            if (sm[i] > bestV) { bestV = sm[i]; own = i; }
        }
    }
    int tp = y0, bt = y1;
    for (int vi = static_cast<int>(valleys.size()) - 1; vi >= 0; --vi) {
        if (valleys[vi] < own) { tp = y0 + valleys[vi]; break; }
    }
    for (int v : valleys) {
        if (v > own) { bt = y0 + v; break; }
    }
    tp = std::min(tp, seedT);
    bt = std::max(bt, seedB);
    *top = std::max(0, tp);
    *bot = std::min(imgH, bt);
}

static double medianVec(std::vector<double> v) {
    if (v.empty()) return 1.0;
    std::sort(v.begin(), v.end());
    const int n = static_cast<int>(v.size());
    if (n % 2 == 1) return v[n / 2];
    return 0.5 * (v[n / 2 - 1] + v[n / 2]);
}

static void chi2Walk(
    const cv::Mat& g8, int sl, int st, int sr, int sb,
    int imgW, int imgH, int capPx, float chi2K,
    int* top, int* bot
) {
    *top = st;
    *bot = sb;
    if (sr <= sl || sb <= st || g8.empty() || g8.type() != CV_8UC1) return;
    const int w = sr - sl;
    const int bins = 16;
    auto fillOh = [&](int y, float* oh) {
        for (int i = 0; i < bins; ++i) oh[i] = 0.f;
        if (y < 0 || y >= imgH) return;
        const uint8_t* p = g8.ptr<uint8_t>(y);
        for (int x = sl; x < sr; ++x) {
            int b = p[x] / 16;
            if (b > 15) b = 15;
            oh[b] += 1.f;
        }
        const float inv = 1.f / std::max(1, w);
        for (int i = 0; i < bins; ++i) oh[i] *= inv;
    };
    float seedHist[16] = {};
    float oh[16];
    for (int y = st; y < sb; ++y) {
        fillOh(y, oh);
        for (int i = 0; i < bins; ++i) seedHist[i] += oh[i];
    }
    const float nSeed = static_cast<float>(std::max(1, sb - st));
    float histSum = 0.f;
    for (int i = 0; i < bins; ++i) {
        seedHist[i] /= nSeed;
        histSum += seedHist[i];
    }
    if (histSum > 1e-6f) {
        const float inv = 1.f / histSum;
        for (int i = 0; i < bins; ++i) seedHist[i] *= inv;
    }
    auto chi2At = [&](int y) {
        fillOh(y, oh);
        double s = 0.0;
        for (int i = 0; i < bins; ++i) {
            const double d = static_cast<double>(oh[i] - seedHist[i]);
            s += d * d / (seedHist[i] + 1e-3);
        }
        return s;
    };
    std::vector<double> seedScores;
    seedScores.reserve(sb - st);
    for (int y = st; y < sb; ++y) seedScores.push_back(chi2At(y));
    const double seedChi2 = std::max(medianVec(seedScores), 1e-6);
    const double thr = chi2K * seedChi2;
    auto walk = [&](int start, int step) {
        int y = start, streak = 0, steps = 0;
        while (true) {
            const int ny = y + step;
            if (ny < 0 || ny >= imgH) return y;
            if (steps >= capPx) return y;
            if (chi2At(ny) > thr) {
                ++streak;
                if (streak >= 2) return y;
            } else {
                streak = 0;
                y = ny;
                ++steps;
            }
        }
    };
    const int tp = walk(st, -1);
    const int last = walk(sb - 1, +1);
    *top = std::min(tp, st);
    *bot = std::max(last + 1, sb);
}

static int runCountRow(const float* row, int n, float thr, int minRun = 3) {
    int cnt = 0, run = 0;
    for (int i = 0; i < n; ++i) {
        if (row[i] >= thr) ++run;
        else {
            if (run >= minRun) ++cnt;
            run = 0;
        }
    }
    if (run >= minRun) ++cnt;
    return cnt;
}

static void countPullY(
    const cv::Mat& gxAbs, int sl, int st, int sr, int sb,
    int el, int et, int er, int eb,
    int imgW, int imgH, bool stopUpEnergy, bool stopDownEnergy,
    int* ct, int* cb, int* pulledT, int* pulledB
) {
    *ct = et;
    *cb = eb;
    *pulledT = 0;
    *pulledB = 0;
    if (sr <= sl || sb <= st) return;
    const int sw = sr - sl;
    std::vector<float> buf;
    buf.reserve(std::max(1, (sb - st) * sw));
    for (int y = st; y < sb; ++y) {
        const float* p = gxAbs.ptr<float>(y);
        for (int x = sl; x < sr; ++x) buf.push_back(p[x]);
    }
    double p90 = 8.0;
    if (buf.size() >= 2) {
        std::vector<float> s = buf;
        std::sort(s.begin(), s.end());
        const int idx = static_cast<int>((s.size() - 1) * 0.90);
        p90 = s[std::max(0, std::min(idx, static_cast<int>(s.size()) - 1))];
    }
    const double gxThr = std::max(8.0, 0.55 * p90);
    const int y0 = std::max(0, std::min(st, et));
    const int y1 = std::min(imgH, std::max(sb, eb));
    const int n = std::max(1, y1 - y0);
    std::vector<double> raw(n, 0.0);
    std::vector<float> row(sw);
    for (int i = 0; i < n; ++i) {
        const int y = y0 + i;
        const float* p = gxAbs.ptr<float>(y);
        for (int x = 0; x < sw; ++x) row[x] = p[sl + x];
        raw[i] = runCountRow(row.data(), sw, static_cast<float>(gxThr));
    }
    const int sh = std::max(1, sb - st);
    std::vector<double> sm;
    smooth1d(raw, std::max(1.0, 0.04 * sh), &sm);
    int sti = st - y0;
    int sbi = sb - y0;
    if (sti < 0) sti = 0;
    if (sti > n - 2) sti = std::max(0, n - 2);
    if (sbi < sti + 1) sbi = sti + 1;
    if (sbi > n) sbi = n;
    std::vector<double> seedVals(sm.begin() + sti, sm.begin() + sbi);
    std::sort(seedVals.begin(), seedVals.end());
    const double cSeed = seedVals[seedVals.size() / 2];
    int te = et - y0;
    int be = eb - y0;
    if (te < 0) te = 0;
    if (te > sti) te = sti;
    if (be < sbi) be = sbi;
    if (be > n) be = n;
    int top = te, bot = be;
    int pT = 0, pB = 0;
    if (cSeed >= 1.0) {
        const double cThr = 0.45 * cSeed;
        for (int i = sti - 1; i >= te; --i) {
            const double left = (i == 0) ? sm[i] : sm[i - 1];
            const double right = (i == n - 1) ? sm[i] : sm[i + 1];
            if (sm[i] < cThr && sm[i] <= left && sm[i] <= right) {
                top = i;
                pT = 1;
                break;
            }
        }
        for (int i = sbi; i < be; ++i) {
            const double left = (i == 0) ? sm[i] : sm[i - 1];
            const double right = (i == n - 1) ? sm[i] : sm[i + 1];
            if (sm[i] < cThr && sm[i] <= left && sm[i] <= right) {
                bot = i;
                pB = 1;
                break;
            }
        }
    }
    top = std::min(top, sti);
    bot = std::max(bot, sbi);
    const double cThr = 0.45 * cSeed;
    const int clearSteps = std::max(1, static_cast<int>(std::lround(0.10 * sh)));
    const int growSteps = std::max(1, static_cast<int>(std::lround(0.08 * sh)));
    const bool allowGrow = (eb - et) <= 2.4f * sh;
    const double tipTop = (te >= 0 && te < n) ? sm[te] : 0.0;
    double tipBot = 0.0;
    if (be - 1 >= 0 && be - 1 < n) tipBot = sm[be - 1];
    else if (be >= 0 && be < n) tipBot = sm[be];
    const int idxLo = std::min(0, te - growSteps);
    const int idxHi = std::max(n, be + growSteps);
    int topPad = top, botPad = bot;
    bool grewT = false, grewB = false;
    padCountTip(pT != 0, stopUpEnergy, tipTop, cThr, te, top, sti, false,
                clearSteps, growSteps, idxLo, idxHi, allowGrow, &topPad, &grewT);
    padCountTip(pB != 0, stopDownEnergy, tipBot, cThr, be, bot, sbi, true,
                clearSteps, growSteps, idxLo, idxHi, allowGrow, &botPad, &grewB);
    top = std::min(topPad, sti);
    bot = std::max(botPad, sbi);
    if (bot < top + 1) bot = std::max(top + 1, sbi);
    *ct = std::max(0, std::min(imgH, y0 + top));
    *cb = std::max(*ct + 1, std::min(imgH, y0 + bot));
    *pulledT = pT;
    *pulledB = pB;
    (void)el;
    (void)er;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeChromaMag(
    JNIEnv* /*env*/, jobject /*thiz*/,
    jlong yPtr, jlong uvPtr, jlong dstPtr
) {
    auto* y = reinterpret_cast<cv::Mat*>(yPtr);
    auto* uv = reinterpret_cast<cv::Mat*>(uvPtr);
    auto* dst = reinterpret_cast<cv::Mat*>(dstPtr);
    if (!y || !dst) return JNI_FALSE;
    if (!uv) {
        cv::Mat empty;
        return fillChromaMag(*y, empty, dst) ? JNI_TRUE : JNI_FALSE;
    }
    return fillChromaMag(*y, *uv, dst) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeAabbGrowMany(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr, jlong uvPtr,
    jintArray seedsArr,
    jboolean chroma,
    jint vertKind,
    jfloat maxFrac, jfloat energyRatio,
    jboolean freezeHorz, jboolean enableJump,
    jfloat jumpFrac, jfloat retractClearFrac, jfloat vertPadFrac, jfloat chi2K
) {
    auto* gray = reinterpret_cast<cv::Mat*>(grayPtr);
    if (!gray || gray->empty() || gray->type() != CV_8UC1) return nullptr;
    if (!seedsArr) return nullptr;
    const int imgW = gray->cols, imgH = gray->rows;
    const jint n4 = env->GetArrayLength(seedsArr);
    if (n4 <= 0 || n4 % 4 != 0) {
        jintArray empty = env->NewIntArray(0);
        return empty;
    }
    const int n = n4 / 4;
    std::vector<jint> seeds(n4);
    env->GetIntArrayRegion(seedsArr, 0, n4, seeds.data());

    cv::Mat gxY, gyY, magY, gxAbs;
    cv::Sobel(*gray, gxY, CV_32F, 1, 0, 3);
    cv::Sobel(*gray, gyY, CV_32F, 0, 1, 3);
    cv::magnitude(gxY, gyY, magY);
    cv::absdiff(gxY, cv::Scalar(0.0), gxAbs);

    cv::Mat cMag, cF, gxC, gyC, magC, vertEng;
    const bool useChroma = chroma == JNI_TRUE;
    if (useChroma) {
        auto* uv = reinterpret_cast<cv::Mat*>(uvPtr);
        cv::Mat uvRef = uv ? *uv : cv::Mat();
        fillChromaMag(*gray, uvRef, &cMag);
        cMag.convertTo(cF, CV_32F);
        cv::Sobel(cF, gxC, CV_32F, 1, 0, 3);
        cv::Sobel(cF, gyC, CV_32F, 0, 1, 3);
        cv::magnitude(gxC, gyC, magC);
        cF.release();
        gxC.release();
        gyC.release();
    }

    // vertKind: 0 MAG, 1 GX, 2 XYCUT, 3 CHI2
    if (vertKind == 1 || vertKind == 2) {
        if (useChroma) {
            cv::Mat absGxY, absGxC;
            cv::absdiff(gxY, cv::Scalar(0.0), absGxY);
            cv::Mat gxC2, gyC2;
            cv::Mat cF2;
            cMag.convertTo(cF2, CV_32F);
            cv::Sobel(cF2, gxC2, CV_32F, 1, 0, 3);
            cv::absdiff(gxC2, cv::Scalar(0.0), absGxC);
            cv::magnitude(absGxY, absGxC, vertEng);
            cF2.release();
            gxC2.release();
            gyC2.release();
            absGxY.release();
            absGxC.release();
        } else {
            gxAbs.copyTo(vertEng);
        }
    } else {
        if (useChroma) {
            cv::magnitude(magY, magC, vertEng);
        } else {
            magY.copyTo(vertEng);
        }
    }
    gxY.release();
    gyY.release();
    magC.release();

    const cv::Mat* chi2Src = useChroma ? &cMag : gray;
    std::vector<jint> out(n * 11, 0);
    for (int i = 0; i < n; ++i) {
        int l = seeds[i * 4 + 0];
        int t = seeds[i * 4 + 1];
        int r = seeds[i * 4 + 2];
        int b = seeds[i * 4 + 3];
        if (l < 0) l = 0;
        if (t < 0) t = 0;
        if (r > imgW) r = imgW;
        if (b > imgH) b = imgH;
        if (r <= l) r = std::min(imgW, l + 1);
        if (b <= t) b = std::min(imgH, t + 1);
        const int seedL = l, seedT = t, seedR = r, seedB = b;
        const int seedH = std::max(1, b - t);
        const int cap = std::max(1, static_cast<int>(std::lround(maxFrac * seedH)));
        const int il = l + 2, it = t + 2, ir = r - 2, ib = b - 2;
        const double base = (ir > il && ib > it)
            ? meanRectF(vertEng, il, it, ir, ib, imgW, imgH)
            : meanRectF(vertEng, l, t, r, b, imgW, imgH);
        const double thr = energyRatio * std::max(base, 1e-3);
        const double jumpBase = (ir > il && ib > it)
            ? meanRectF(magY, il, it, ir, ib, imgW, imgH)
            : meanRectF(magY, l, t, r, b, imgW, imgH);
        const double jumpThr = energyRatio * std::max(jumpBase, 1e-3);
        bool allowUp = t > 0 && meanRectF(vertEng, l, t - 1, r, t, imgW, imgH) >= thr;
        bool allowDown = b < imgH && meanRectF(vertEng, l, b, r, b + 1, imgW, imgH) >= thr;
        int walkT = 0, walkB = 0;
        if (vertKind == 2) {
            int cutT = t, cutB = b;
            xycutOnProfile(vertEng, l, t, r, b, imgW, imgH, cap, &cutT, &cutB);
            t = allowUp ? std::min(cutT, seedT) : seedT;
            b = allowDown ? std::max(cutB, seedB) : seedB;
        } else if (vertKind == 3) {
            int cutT = t, cutB = b;
            chi2Walk(*chi2Src, l, t, r, b, imgW, imgH, cap, chi2K, &cutT, &cutB);
            allowUp = t > 0 && cutT < seedT;
            allowDown = b < imgH && cutB > seedB;
            t = allowUp ? std::min(cutT, seedT) : seedT;
            b = allowDown ? std::max(cutB, seedB) : seedB;
        } else {
            for (int k = 0; k < cap; ++k) {
                bool grew = false;
                if (allowUp && t > 0 &&
                    meanRectF(vertEng, l, t - 1, r, t, imgW, imgH) >= thr) {
                    --t;
                    grew = true;
                }
                if (allowDown && b < imgH &&
                    meanRectF(vertEng, l, b, r, b + 1, imgW, imgH) >= thr) {
                    ++b;
                    grew = true;
                }
                if (!freezeHorz) {
                    if (l > 0 && meanRectF(vertEng, l - 1, t, l, b, imgW, imgH) >= thr) {
                        --l;
                        grew = true;
                    }
                    if (r < imgW && meanRectF(vertEng, r, t, r + 1, b, imgW, imgH) >= thr) {
                        ++r;
                        grew = true;
                    }
                }
                if (!grew) break;
            }
        }
        walkT = seedT - t;
        walkB = b - seedB;
        if (vertPadFrac > 0.f) {
            const int extra = std::max(1, static_cast<int>(std::lround(vertPadFrac * seedH)));
            if (allowUp) t = std::max(0, t - extra);
            if (allowDown) b = std::min(imgH, b + extra);
        }
        const int hit = (walkT >= cap || walkB >= cap) ? 1 : 0;
        const bool stopUpE = (vertKind != 2 && vertKind != 3 && walkT < cap);
        const bool stopDownE = (vertKind != 2 && vertKind != 3 && walkB < cap);
        if (enableJump) {
            jumpRetractH(magY, &l, t, &r, b, imgW, imgH, jumpThr, cap,
                         jumpFrac, retractClearFrac);
        }
        if (l < 0) l = 0;
        if (t < 0) t = 0;
        if (r > imgW) r = imgW;
        if (b > imgH) b = imgH;
        if (r <= l) r = std::min(imgW, l + 1);
        if (b <= t) b = std::min(imgH, t + 1);
        int ct = t, cb = b, pT = 0, pB = 0;
        countPullY(gxAbs, seedL, seedT, seedR, seedB, l, t, r, b,
                   imgW, imgH, stopUpE, stopDownE, &ct, &cb, &pT, &pB);
        const int o = i * 11;
        out[o + 0] = l;
        out[o + 1] = t;
        out[o + 2] = r;
        out[o + 3] = b;
        out[o + 4] = l;
        out[o + 5] = ct;
        out[o + 6] = r;
        out[o + 7] = cb;
        out[o + 8] = hit;
        out[o + 9] = pT;
        out[o + 10] = pB;
    }
    magY.release();
    gxAbs.release();
    vertEng.release();
    cMag.release();
    jintArray arr = env->NewIntArray(static_cast<jint>(out.size()));
    if (!arr) return nullptr;
    env->SetIntArrayRegion(arr, 0, static_cast<jint>(out.size()), out.data());
    return arr;
}

namespace {

static int peakCapped(const std::vector<int>& hist, int minK, int maxK) {
    int bestK = minK, bestV = 0;
    const int hi = std::min(maxK, static_cast<int>(hist.size()) - 1);
    for (int k = minK; k <= hi; ++k) {
        if (hist[k] > bestV) {
            bestV = hist[k];
            bestK = k;
        }
    }
    return bestV > 0 ? bestK : minK;
}

static int maxInkRunRow(const cv::Mat& bin, int y, int x0, int x1) {
    if (y < 0 || y >= bin.rows) return 0;
    const uint8_t* p = bin.ptr<uint8_t>(y);
    int best = 0, run = 0;
    const int r = std::min(x1, bin.cols);
    for (int x = std::max(0, x0); x < r; ++x) {
        if (p[x] != 0) {
            ++run;
            if (run > best) best = run;
        } else {
            run = 0;
        }
    }
    return best;
}

struct HorizSW {
    int peak = 4;
    int maxRun = 0;
    int nNonSpan = 0;
    std::vector<int> hist;
};

static HorizSW horizPeakSW(const cv::Mat& bin, int seedH, int seedW) {
    HorizSW out;
    out.hist.assign(std::max(seedW + 1, 36), 0);
    for (int y = 0; y < bin.rows; ++y) {
        const uint8_t* p = bin.ptr<uint8_t>(y);
        int run = 0;
        for (int x = 0; x <= bin.cols; ++x) {
            const bool on = x < bin.cols && p[x] != 0;
            if (on) ++run;
            else if (run > 0) {
                if (run != seedW) {
                    if (run < static_cast<int>(out.hist.size())) out.hist[run]++;
                    ++out.nNonSpan;
                    if (run > out.maxRun) out.maxRun = run;
                }
                run = 0;
            }
        }
    }
    const int maxV = std::max(35, static_cast<int>(seedH * 0.50f));
    out.peak = peakCapped(out.hist, 4, maxV);
    return out;
}

static int vertPeakSW(const cv::Mat& bin, int seedH) {
    const int h = bin.rows;
    std::vector<int> hist(std::max(h + 1, 21), 0);
    for (int x = 0; x < bin.cols; ++x) {
        int run = 0;
        for (int y = 0; y <= h; ++y) {
            const bool on = y < h && bin.ptr<uint8_t>(y)[x] != 0;
            if (on) ++run;
            else if (run > 0) {
                if (run != h && run < static_cast<int>(hist.size())) hist[run]++;
                run = 0;
            }
        }
    }
    const int maxH = std::max(20, static_cast<int>(seedH * 0.40f));
    return peakCapped(hist, 4, maxH);
}

static void dropWide(cv::Mat* bin, int glareW) {
    if (glareW <= 0 || bin->empty()) return;
    cv::Mat labels, stats, centroids;
    const int nLab = cv::connectedComponentsWithStats(*bin, labels, stats, centroids, 8);
    if (nLab <= 1) return;
    std::vector<char> drop(nLab, 0);
    int dropped = 0;
    for (int i = 1; i < nLab; ++i) {
        const int w = stats.at<int>(i, cv::CC_STAT_WIDTH);
        if (w > glareW) {
            drop[i] = 1;
            ++dropped;
        }
    }
    if (!dropped) return;
    for (int y = 0; y < bin->rows; ++y) {
        const int* lp = labels.ptr<int>(y);
        uint8_t* bp = bin->ptr<uint8_t>(y);
        for (int x = 0; x < bin->cols; ++x) {
            const int id = lp[x];
            if (id >= 0 && id < nLab && drop[id]) bp[x] = 0;
        }
    }
}

static void seg7One(
    const cv::Mat& src, int sl, int st, int sr, int sb,
    int imgW, int imgH,
    int* ol, int* ot, int* oright, int* ob, int* sPxOut, int* vSWOut, int* hSWOut, int* usedFb,
    bool srcIsBin = false
) {
    *ol = sl; *ot = st; *oright = sr; *ob = sb;
    const int seedH = std::max(1, sb - st);
    const int seedW = std::max(1, sr - sl);
    const int fallback = std::max(2, static_cast<int>(std::lround(0.08f * seedH)));
    *sPxOut = fallback;
    *vSWOut = 4;
    *hSWOut = 4;
    *usedFb = 1;
    if (sr <= sl || sb <= st || src.empty() || src.type() != CV_8UC1) return;
    if (seedH < 4 || seedW < 4) return;
    cv::Mat roi = src(cv::Range(st, sb), cv::Range(sl, sr));
    cv::Mat bin;
    double thr = 0.0;
    bool darkInk = true;
    bool invertedBin = false;
    if (srcIsBin) {
        roi.copyTo(bin);
    } else {
        thr = cv::threshold(roi, bin, 0, 255, cv::THRESH_BINARY_INV | cv::THRESH_OTSU);
    }
    const int nPix = seedW * seedH;
    int nz = cv::countNonZero(bin);
    float inkFrac = nPix > 0 ? nz / static_cast<float>(nPix) : 0.f;
    if (inkFrac >= 0.45f) {
        cv::bitwise_not(bin, bin);
        nz = cv::countNonZero(bin);
        inkFrac = nPix > 0 ? nz / static_cast<float>(nPix) : 0.f;
        darkInk = false;
        invertedBin = srcIsBin;
    }
    HorizSW hh0 = horizPeakSW(bin, seedH, seedW);
    const int v0 = hh0.peak;
    const int glareW = 3 * std::max(v0, 4);
    dropWide(&bin, glareW);
    HorizSW hh = horizPeakSW(bin, seedH, seedW);
    const int vSW = hh.peak;
    const int hSW = vertPeakSW(bin, seedH);
    const int lo = std::max(1, static_cast<int>(std::lround(0.7f * vSW)));
    const int hi = std::max(lo, static_cast<int>(std::lround(1.3f * vSW)));
    int band = 0;
    const int hiClamp = std::min(hi, static_cast<int>(hh.hist.size()) - 1);
    for (int k = lo; k <= hiClamp; ++k) band += hh.hist[k];
    const float strokeShare = hh.nNonSpan > 0
        ? band / static_cast<float>(hh.nNonSpan) : 0.f;
    const float maxRunOverW = hh.maxRun / static_cast<float>(seedW);
    const bool needFb = vSW <= 4 || inkFrac >= 0.45f ||
        strokeShare < 0.30f || maxRunOverW >= 0.50f;
    const int sPx = needFb ? fallback : vSW;
    *sPxOut = sPx;
    *vSWOut = vSW;
    *hSWOut = hSW;
    *usedFb = needFb ? 1 : 0;
    const int capPx = std::max(1, static_cast<int>(std::lround(2.5f * seedH)));
    const int gapStop = std::max(1, static_cast<int>(std::lround(0.5f * sPx)));
    const int vLook = capPx + 2;
    const int nt = std::max(0, st - vLook);
    const int nb = std::min(imgH, sb + vLook);
    if (sr <= sl || nb <= nt) return;
    cv::Mat look = src(cv::Range(nt, nb), cv::Range(sl, sr));
    cv::Mat lookBin;
    if (srcIsBin) {
        look.copyTo(lookBin);
        if (invertedBin) cv::bitwise_not(lookBin, lookBin);
    } else {
        const int ttype = darkInk ? cv::THRESH_BINARY_INV : cv::THRESH_BINARY;
        cv::threshold(look, lookBin, thr, 255, ttype);
    }
    dropWide(&lookBin, glareW);
    const int minRun = std::max(1, static_cast<int>(std::lround(0.5f * sPx)));
    auto hasBar = [&](int y) {
        return maxInkRunRow(lookBin, y, 0, lookBin.cols) >= minRun;
    };
    auto peek = [&](int startY, int dir) {
        int y = startY, i = 0;
        while (i < gapStop) {
            if (hasBar(y)) return true;
            y += dir;
            ++i;
        }
        return false;
    };
    const int localT = st - nt;
    const int localB = sb - nt;
    const bool allowUp = peek(localT - 1, -1);
    const bool allowDown = peek(localB, +1);
    int t = localT, b = localB;
    if (allowUp) {
        int gap = 0, y = localT - 1;
        while (y >= 0 && localT - y <= capPx) {
            if (hasBar(y)) { t = y; gap = 0; }
            else {
                ++gap;
                if (gap >= gapStop) break;
            }
            --y;
        }
    }
    if (allowDown) {
        int gap = 0, y = localB;
        while (y < lookBin.rows && y - localB < capPx) {
            if (hasBar(y)) { b = y + 1; gap = 0; }
            else {
                ++gap;
                if (gap >= gapStop) break;
            }
            ++y;
        }
    }
    if (b <= t) b = std::min(t + 1, lookBin.rows);
    *ol = sl;
    *ot = nt + t;
    *oright = sr;
    *ob = nt + b;
    if (*ot < 0) *ot = 0;
    if (*ob > imgH) *ob = imgH;
    if (*ob <= *ot) *ob = std::min(imgH, *ot + 1);
}

static constexpr float kTintDotThr = 0.5f;
static constexpr float kTintChromaEps = 8.0f;

static void uvAt(const cv::Mat& uv, int imgW, int x, int y, int* u, int* v) {
    if (uv.empty() || uv.type() != CV_8UC2 || uv.rows <= 0 || uv.cols <= 0) {
        *u = 128;
        *v = 128;
        return;
    }
    const int uvH = uv.rows, uvW = uv.cols;
    const bool half = uvW * 2 <= imgW + 1;
    int uy = half ? y / 2 : (y & ~1);
    int ux = half ? x / 2 : (x & ~1);
    if (uy < 0) uy = 0;
    if (uy >= uvH) uy = uvH - 1;
    if (ux < 0) ux = 0;
    if (ux >= uvW) ux = uvW - 1;
    const cv::Vec2b p = uv.ptr<cv::Vec2b>(uy)[ux];
    *u = p[0];
    *v = p[1];
}

/** Seed-ROI Y Otsu ink bin + dropWide; returns s_px (fallback 0.08×seedH). */
static int seedInkBinY(
    const cv::Mat& y, int sl, int st, int sr, int sb, cv::Mat* binOut
) {
    const int seedH = std::max(1, sb - st);
    const int seedW = std::max(1, sr - sl);
    const int fallback = std::max(2, static_cast<int>(std::lround(0.08f * seedH)));
    if (sr <= sl || sb <= st || y.empty()) return fallback;
    cv::Mat roi = y(cv::Range(st, sb), cv::Range(sl, sr));
    cv::Mat bin;
    cv::threshold(roi, bin, 0, 255, cv::THRESH_BINARY_INV | cv::THRESH_OTSU);
    const int nPix = seedW * seedH;
    int nz = cv::countNonZero(bin);
    float inkFrac = nPix > 0 ? nz / static_cast<float>(nPix) : 0.f;
    if (inkFrac >= 0.45f) {
        cv::bitwise_not(bin, bin);
        nz = cv::countNonZero(bin);
        inkFrac = nPix > 0 ? nz / static_cast<float>(nPix) : 0.f;
    }
    HorizSW hh0 = horizPeakSW(bin, seedH, seedW);
    const int glareW = 3 * std::max(hh0.peak, 4);
    dropWide(&bin, glareW);
    HorizSW hh = horizPeakSW(bin, seedH, seedW);
    const int sPx = (hh.peak <= 4 || inkFrac >= 0.45f) ? fallback : hh.peak;
    *binOut = bin;
    return std::max(1, sPx);
}

/**
 * Per-seed shadow-invariant tint mask: 255 = ink, 0 = blackout.
 * Samples stroke chromaticity inside seed ink runs; background at ±s_px
 * outside those edges; classifies look-strip pixels by u_p·u_ink (or Y
 * polarity when chroma is near zero).
 */
static bool fillChromaTintMask(
    const cv::Mat& y, const cv::Mat& uv,
    int sl, int st, int sr, int sb,
    cv::Mat* dst
) {
    if (y.empty() || y.type() != CV_8UC1 || !dst) return false;
    const int h = y.rows, w = y.cols;
    dst->create(h, w, CV_8UC1);
    dst->setTo(0);
    if (sl < 0) sl = 0;
    if (st < 0) st = 0;
    if (sr > w) sr = w;
    if (sb > h) sb = h;
    if (sr <= sl || sb <= st) return false;
    cv::Mat seedBin;
    const int sPx = seedInkBinY(y, sl, st, sr, sb, &seedBin);
    if (seedBin.empty()) return false;

    double su = 0.0, sv = 0.0, yInkSum = 0.0, chromaInkSum = 0.0;
    int nInk = 0;
    for (int yy = 0; yy < seedBin.rows; ++yy) {
        const uint8_t* bp = seedBin.ptr<uint8_t>(yy);
        const uint8_t* yp = y.ptr<uint8_t>(st + yy);
        for (int xx = 0; xx < seedBin.cols; ++xx) {
            if (!bp[xx]) continue;
            int u, v;
            uvAt(uv, w, sl + xx, st + yy, &u, &v);
            const double du = static_cast<double>(u) - 128.0;
            const double dv = static_cast<double>(v) - 128.0;
            const double n = std::hypot(du, dv);
            if (n >= 1.0) {
                su += du / n;
                sv += dv / n;
            }
            chromaInkSum += n;
            yInkSum += yp[sl + xx];
            ++nInk;
        }
    }
    if (nInk <= 0) return false;
    double uInkX = su / nInk;
    double uInkY = sv / nInk;
    const double nrm = std::hypot(uInkX, uInkY);
    if (nrm > 1e-6) {
        uInkX /= nrm;
        uInkY /= nrm;
    }
    const double yInk = yInkSum / nInk;
    const double meanChromaInk = chromaInkSum / nInk;
    const bool inkHasChroma = meanChromaInk >= kTintChromaEps && nrm > 1e-6;

    const int d = std::max(1, sPx);
    double yBgSum = 0.0;
    int nBg = 0;
    auto tryBg = [&](int gx, int gy) {
        if (gx < 0 || gy < 0 || gx >= w || gy >= h) return;
        const int lx = gx - sl, ly = gy - st;
        if (lx >= 0 && ly >= 0 && lx < seedBin.cols && ly < seedBin.rows &&
            seedBin.ptr<uint8_t>(ly)[lx]) {
            return;
        }
        yBgSum += y.ptr<uint8_t>(gy)[gx];
        ++nBg;
    };
    for (int yy = 0; yy < seedBin.rows; ++yy) {
        const uint8_t* bp = seedBin.ptr<uint8_t>(yy);
        for (int xx = 0; xx < seedBin.cols; ++xx) {
            if (!bp[xx]) continue;
            const int gx = sl + xx, gy = st + yy;
            tryBg(gx - d, gy);
            tryBg(gx + d, gy);
            tryBg(gx, gy - d);
            tryBg(gx, gy + d);
        }
    }
    const double yBg = nBg > 0 ? yBgSum / nBg : (yInk < 128.0 ? 200.0 : 40.0);

    const int seedH = std::max(1, sb - st);
    const int capPx = std::max(1, static_cast<int>(std::lround(2.5f * seedH)));
    const int vLook = capPx + 2;
    const int nt = std::max(0, st - vLook);
    const int nb = std::min(h, sb + vLook);
    for (int gy = nt; gy < nb; ++gy) {
        const uint8_t* yp = y.ptr<uint8_t>(gy);
        uint8_t* op = dst->ptr<uint8_t>(gy);
        for (int gx = sl; gx < sr; ++gx) {
            const double Y = yp[gx];
            int u, v;
            uvAt(uv, w, gx, gy, &u, &v);
            const double du = static_cast<double>(u) - 128.0;
            const double dv = static_cast<double>(v) - 128.0;
            const double c = std::hypot(du, dv);
            const double dInk = yInk - yBg;
            const double dPix = Y - yBg;
            const bool polOk = dInk * dPix >= 0.0;
            bool isInk;
            if (!inkHasChroma || c < kTintChromaEps) {
                isInk = polOk && std::abs(Y - yInk) <= std::abs(Y - yBg);
            } else {
                const double dot = (du / c) * uInkX + (dv / c) * uInkY;
                isInk = dot >= kTintDotThr && polOk;
            }
            op[gx] = isInk ? 255 : 0;
        }
    }
    return true;
}

static double medianU8Rect(const cv::Mat& m, int l, int t, int r, int b) {
    if (r <= l || b <= t || m.empty()) return 0.0;
    std::vector<int> vals;
    vals.reserve((r - l) * (b - t));
    for (int y = t; y < b; ++y) {
        const uint8_t* p = m.ptr<uint8_t>(y);
        for (int x = l; x < r; ++x) vals.push_back(p[x]);
    }
    if (vals.empty()) return 0.0;
    std::sort(vals.begin(), vals.end());
    const int n = static_cast<int>(vals.size());
    if (n % 2 == 1) return vals[n / 2];
    return 0.5 * (vals[n / 2 - 1] + vals[n / 2]);
}

}  // namespace

extern "C" JNIEXPORT jintArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeSeg7Many(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr, jlong uvPtr, jintArray seedsArr, jint chromaMode
) {
    auto* gray = reinterpret_cast<cv::Mat*>(grayPtr);
    if (!gray || gray->empty() || gray->type() != CV_8UC1 || !seedsArr) return nullptr;
    const int imgW = gray->cols, imgH = gray->rows;
    const jint n4 = env->GetArrayLength(seedsArr);
    if (n4 <= 0 || n4 % 4 != 0) return env->NewIntArray(0);
    const int n = n4 / 4;
    std::vector<jint> seeds(n4);
    env->GetIntArrayRegion(seedsArr, 0, n4, seeds.data());
    // chromaMode: 0 gray, 1 chromaMag, 2 chromaTint2
    cv::Mat cMag;
    const bool useChromaMag = chromaMode == 1;
    const bool useTint2 = chromaMode == 2;
    auto* uv = reinterpret_cast<cv::Mat*>(uvPtr);
    if (useChromaMag) {
        fillChromaMag(*gray, uv ? *uv : cv::Mat(), &cMag);
    }
    std::vector<jint> out(n * 8, 0);
    for (int i = 0; i < n; ++i) {
        int l = seeds[i * 4], t = seeds[i * 4 + 1], r = seeds[i * 4 + 2], b = seeds[i * 4 + 3];
        if (l < 0) l = 0;
        if (t < 0) t = 0;
        if (r > imgW) r = imgW;
        if (b > imgH) b = imgH;
        int ol, ot, orr, ob, sPx, vSW, hSW, fb;
        if (useTint2) {
            cv::Mat tint;
            const bool ok = uv && fillChromaTintMask(
                *gray, *uv, l, t, r, b, &tint);
            if (ok) {
                seg7One(tint, l, t, r, b, imgW, imgH,
                    &ol, &ot, &orr, &ob, &sPx, &vSW, &hSW, &fb, true);
            } else {
                seg7One(*gray, l, t, r, b, imgW, imgH,
                    &ol, &ot, &orr, &ob, &sPx, &vSW, &hSW, &fb);
            }
        } else {
            const cv::Mat* src = gray;
            if (useChromaMag && !cMag.empty() && medianU8Rect(cMag, l, t, r, b) >= 8.0) {
                src = &cMag;
            }
            seg7One(*src, l, t, r, b, imgW, imgH, &ol, &ot, &orr, &ob, &sPx, &vSW, &hSW, &fb);
        }
        const int o = i * 8;
        out[o] = ol; out[o + 1] = ot; out[o + 2] = orr; out[o + 3] = ob;
        out[o + 4] = sPx; out[o + 5] = vSW; out[o + 6] = hSW; out[o + 7] = fb;
    }
    jintArray arr = env->NewIntArray(static_cast<jint>(out.size()));
    if (!arr) return nullptr;
    env->SetIntArrayRegion(arr, 0, static_cast<jint>(out.size()), out.data());
    return arr;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeJumpMany(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr, jintArray boxesArr,
    jfloat maxFrac, jfloat energyRatio, jfloat jumpFrac, jfloat retractClearFrac
) {
    auto* gray = reinterpret_cast<cv::Mat*>(grayPtr);
    if (!gray || gray->empty() || gray->type() != CV_8UC1 || !boxesArr) return nullptr;
    const int imgW = gray->cols, imgH = gray->rows;
    const jint n4 = env->GetArrayLength(boxesArr);
    if (n4 <= 0 || n4 % 4 != 0) return env->NewIntArray(0);
    const int n = n4 / 4;
    std::vector<jint> boxes(n4);
    env->GetIntArrayRegion(boxesArr, 0, n4, boxes.data());
    cv::Mat gx, gy, mag;
    cv::Sobel(*gray, gx, CV_32F, 1, 0, 3);
    cv::Sobel(*gray, gy, CV_32F, 0, 1, 3);
    cv::magnitude(gx, gy, mag);
    gx.release();
    gy.release();
    std::vector<jint> out(n * 4, 0);
    for (int i = 0; i < n; ++i) {
        int l = boxes[i * 4], t = boxes[i * 4 + 1], r = boxes[i * 4 + 2], b = boxes[i * 4 + 3];
        if (l < 0) l = 0;
        if (t < 0) t = 0;
        if (r > imgW) r = imgW;
        if (b > imgH) b = imgH;
        if (r <= l) r = std::min(imgW, l + 1);
        if (b <= t) b = std::min(imgH, t + 1);
        const int il = l + 2, it = t + 2, ir = r - 2, ib = b - 2;
        const double base = (ir > il && ib > it)
            ? meanRectF(mag, il, it, ir, ib, imgW, imgH)
            : meanRectF(mag, l, t, r, b, imgW, imgH);
        const double thr = energyRatio * std::max(base, 1e-3);
        const int cap = std::max(1, static_cast<int>(std::lround(maxFrac * std::max(1, b - t))));
        jumpRetractH(mag, &l, t, &r, b, imgW, imgH, thr, cap, jumpFrac, retractClearFrac);
        if (l < 0) l = 0;
        if (t < 0) t = 0;
        if (r > imgW) r = imgW;
        if (b > imgH) b = imgH;
        if (r <= l) r = std::min(imgW, l + 1);
        if (b <= t) b = std::min(imgH, t + 1);
        out[i * 4] = l;
        out[i * 4 + 1] = t;
        out[i * 4 + 2] = r;
        out[i * 4 + 3] = b;
    }
    mag.release();
    jintArray arr = env->NewIntArray(static_cast<jint>(out.size()));
    if (!arr) return nullptr;
    env->SetIntArrayRegion(arr, 0, static_cast<jint>(out.size()), out.data());
    return arr;
}
