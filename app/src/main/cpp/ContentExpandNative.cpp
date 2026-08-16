#include <jni.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <algorithm>
#include <cmath>
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
        if (strip(0.f, -1.f, false) >= thr) {
            cx -= 0.5f * fr.vx;
            cy -= 0.5f * fr.vy;
            bh += 1.f;
            ++stepsVNeg;
            grew = true;
        }
        if (strip(0.f, +1.f, false) >= thr) {
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
        bh += 2.f * padV;
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
        (stepsVNeg + padV >= cap || stepsVPos + padV >= cap) ? 1 : 0;
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
