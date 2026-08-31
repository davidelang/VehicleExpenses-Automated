#include <jni.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/imgcodecs.hpp>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <exception>
#include <vector>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "ContentExpandNative", __VA_ARGS__)

static constexpr int kRunHistBins = 32;
static constexpr int kSeg7TeleN = 21 + kRunHistBins * 2;

enum : int {
    kFlagUnchanged = 0,
    kFlagNormalExpand = 1,
    kFlagNormalRetract = 2,
    kFlagBlocked10pct = 3,
    kFlagBlockedGap = 4
};

static constexpr float kVertRetractCapFrac = 0.50f;

struct Seg7Tele {
    float method = 0.f;
    float yInk = 0.f;
    float yBg = 0.f;
    float dInk = 0.f;
    float meanChroma = 0.f;
    float uInkX = 0.f;
    float uInkY = 0.f;
    float inkBgDot = 0.f;
    float otsuThr = 0.f;
    float sPx = 0.f;
    float dTop = 0.f;
    float dBot = 0.f;
    float dLeft = 0.f;
    float dRight = 0.f;
    float fTop = 0.f;
    float fBot = 0.f;
    float fLeft = 0.f;
    float fRight = 0.f;
    float gapJumpTop = 0.f;
    float gapJumpBot = 0.f;
    float landTop = 0.f;
    float landBot = 0.f;
    int histH[kRunHistBins]{};
    int histV[kRunHistBins]{};
};

static int runLengthBin(int run) {
    if (run < 1) run = 1;
    int hi = 2;
    for (int b = 0; b < kRunHistBins - 1; ++b) {
        if (run <= hi) return b;
        const int next = hi * 2;
        if (next <= hi) return kRunHistBins - 1;
        hi = next;
    }
    return kRunHistBins - 1;
}

static void addRunHist(int run, int* hist) {
    if (run <= 0 || !hist) return;
    hist[runLengthBin(run)] += 1;
}

static void fillRunHists(const cv::Mat& bin, int* histH, int* histV) {
    for (int i = 0; i < kRunHistBins; ++i) {
        histH[i] = 0;
        histV[i] = 0;
    }
    if (bin.empty() || bin.type() != CV_8UC1) return;
    const int h = bin.rows, w = bin.cols;
    for (int y = 0; y < h; ++y) {
        const uint8_t* p = bin.ptr<uint8_t>(y);
        int run = 0;
        for (int x = 0; x <= w; ++x) {
            const bool on = x < w && p[x] != 0;
            if (on) ++run;
            else if (run > 0) {
                addRunHist(run, histH);
                run = 0;
            }
        }
    }
    for (int x = 0; x < w; ++x) {
        int run = 0;
        for (int y = 0; y <= h; ++y) {
            const bool on = y < h && bin.ptr<uint8_t>(y)[x] != 0;
            if (on) ++run;
            else if (run > 0) {
                addRunHist(run, histV);
                run = 0;
            }
        }
    }
}

static void packSeg7Tele(const Seg7Tele& t, float* dst) {
    dst[0] = t.method;
    dst[1] = t.yInk;
    dst[2] = t.yBg;
    dst[3] = t.dInk;
    dst[4] = t.meanChroma;
    dst[5] = t.uInkX;
    dst[6] = t.uInkY;
    dst[7] = t.otsuThr;
    dst[8] = t.sPx;
    dst[9] = t.dTop;
    dst[10] = t.dBot;
    dst[11] = t.dLeft;
    dst[12] = t.dRight;
    dst[13] = t.fTop;
    dst[14] = t.fBot;
    dst[15] = t.fLeft;
    dst[16] = t.fRight;
    dst[17] = t.gapJumpTop;
    dst[18] = t.gapJumpBot;
    dst[19] = t.landTop;
    dst[20] = t.landBot;
    for (int i = 0; i < kRunHistBins; ++i) {
        dst[21 + i] = static_cast<float>(t.histH[i]);
        dst[21 + kRunHistBins + i] = static_cast<float>(t.histV[i]);
    }
}

static void storeTeleArr(JNIEnv* env, jfloatArray teleArr, int i, const Seg7Tele& t) {
    if (!teleArr) return;
    const jint n = env->GetArrayLength(teleArr);
    const int off = i * kSeg7TeleN;
    if (off < 0 || off + kSeg7TeleN > n) return;
    float buf[kSeg7TeleN];
    packSeg7Tele(t, buf);
    env->SetFloatArrayRegion(teleArr, off, kSeg7TeleN, buf);
}

static void fillYInkBg(
    const cv::Mat& y, const cv::Mat& seedBin, int sl, int st, int sPx,
    float* yInkOut, float* yBgOut
) {
    const int h = y.rows, w = y.cols;
    float yInkSum = 0.f;
    int nInk = 0;
    for (int yy = 0; yy < seedBin.rows; ++yy) {
        const uint8_t* bp = seedBin.ptr<uint8_t>(yy);
        const uint8_t* yp = y.ptr<uint8_t>(st + yy);
        for (int xx = 0; xx < seedBin.cols; ++xx) {
            if (!bp[xx]) continue;
            yInkSum += yp[sl + xx];
            ++nInk;
        }
    }
    const float yInk = nInk > 0 ? yInkSum / static_cast<float>(nInk) : 0.f;
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
    *yInkOut = yInk;
    *yBgOut = nBg > 0 ? static_cast<float>(yBgSum / nBg)
        : (yInk < 128.f ? 200.f : 40.f);
}

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

struct InkSweepPack {
    float thr = 0.f;
    float sPx = 0.f;
    float energyRatio = 0.f;
    int minRun = 0;
    int vOrigin = 0;
    int hOrigin = 0;
    int v0 = 0;
    int v1 = 0;
    int h0 = 0;
    int h1 = 0;
    std::vector<int> vScores;
    std::vector<int> hScores;
    std::vector<uint8_t> threshJpeg;
};

struct PoisonCcPack {
    int x = 0, y = 0, w = 0, h = 0, noPeak = 1, thr = 0, nInk = 0;
};
struct PoisonStats {
    int bandTop = 0, bandBot = 0, bandH = 0;
    std::vector<PoisonCcPack> ccs;
};

static void writePoisonArr(JNIEnv* env, jintArray arr, const std::vector<PoisonStats>& packs) {
    if (!env || !arr) return;
    const jint cap = env->GetArrayLength(arr);
    if (cap < 1) return;
    std::vector<jint> buf;
    buf.push_back(static_cast<jint>(packs.size()));
    for (const auto& s : packs) {
        buf.push_back(s.bandTop);
        buf.push_back(s.bandBot);
        buf.push_back(s.bandH);
        buf.push_back(static_cast<jint>(s.ccs.size()));
        for (const auto& c : s.ccs) {
            buf.push_back(c.x);
            buf.push_back(c.y);
            buf.push_back(c.w);
            buf.push_back(c.h);
            buf.push_back(c.noPeak);
            buf.push_back(c.thr);
            buf.push_back(c.nInk);
        }
    }
    const jint n = std::min(cap, static_cast<jint>(buf.size()));
    env->SetIntArrayRegion(arr, 0, n, buf.data());
}

static void writeSweepArr(JNIEnv* env, jintArray arr, const std::vector<InkSweepPack>& packs) {
    if (!env || !arr) return;
    const jint cap = env->GetArrayLength(arr);
    if (cap < 1) return;
    std::vector<jint> buf;
    buf.push_back(static_cast<jint>(packs.size()));
    for (const auto& p : packs) {
        const int nV = static_cast<int>(p.vScores.size());
        const int nH = static_cast<int>(p.hScores.size());
        buf.push_back(static_cast<jint>(std::lround(p.thr * 1000.f)));
        buf.push_back(static_cast<jint>(std::lround(p.sPx)));
        buf.push_back(p.minRun);
        buf.push_back(static_cast<jint>(std::lround(p.energyRatio * 1000.f)));
        buf.push_back(p.vOrigin);
        buf.push_back(p.hOrigin);
        buf.push_back(p.v0);
        buf.push_back(p.v1);
        buf.push_back(nV);
        buf.push_back(p.h0);
        buf.push_back(p.h1);
        buf.push_back(nH);
        buf.insert(buf.end(), p.vScores.begin(), p.vScores.end());
        buf.insert(buf.end(), p.hScores.begin(), p.hScores.end());
        const int nJ = static_cast<int>(p.threshJpeg.size());
        buf.push_back(nJ);
        for (uint8_t b : p.threshJpeg) buf.push_back(b);
    }
    const jint n = std::min(cap, static_cast<jint>(buf.size()));
    env->SetIntArrayRegion(arr, 0, n, buf.data());
}

static void packSeedBinJpeg(const cv::Mat& bin, InkSweepPack* out) {
    if (!out || bin.empty() || bin.type() != CV_8UC1) return;
    try {
        const int w = bin.cols;
        const int h = bin.rows;
        if (w < 1 || h < 1) return;
        cv::Mat small;
        const int longSide = std::max(w, h);
        if (longSide > 400) {
            const double sc = 400.0 / static_cast<double>(longSide);
            const int64_t nw64 = std::llround(static_cast<double>(w) * sc);
            const int64_t nh64 = std::llround(static_cast<double>(h) * sc);
            if (nw64 < 1 || nh64 < 1 || nw64 > 65000 || nh64 > 65000) return;
            int nw = static_cast<int>(nw64);
            int nh = static_cast<int>(nh64);
            nw = std::max(2, (nw + 1) / 2 * 2);
            nh = std::max(2, (nh + 1) / 2 * 2);
            cv::resize(bin, small, cv::Size(nw, nh), 0, 0, cv::INTER_NEAREST);
        } else {
            small = bin;
        }
        cv::Mat bgr;
        cv::cvtColor(small, bgr, cv::COLOR_GRAY2BGR);
        std::vector<int> params = {cv::IMWRITE_JPEG_QUALITY, 70};
        std::vector<uint8_t> jpg;
        if (cv::imencode(".jpg", bgr, jpg, params) && !jpg.empty() && jpg.size() <= 16000) {
            out->threshJpeg = std::move(jpg);
        }
    } catch (const cv::Exception&) {
    } catch (const std::exception&) {
    }
}

static void packSeedLookRows(const cv::Mat& lookBin, int y0, int y1, InkSweepPack* out) {
    if (!out || lookBin.empty() || lookBin.type() != CV_8UC1) return;
    y0 = std::max(0, y0);
    y1 = std::min(lookBin.rows, y1);
    if (y1 <= y0 || lookBin.cols < 1) return;
    packSeedBinJpeg(lookBin(cv::Range(y0, y1), cv::Range(0, lookBin.cols)), out);
}

static void packSeedEnergyRect(
    const cv::Mat& mag, int l, int t, int r, int b, float thr, InkSweepPack* out
) {
    if (!out || mag.empty() || mag.type() != CV_32F) return;
    const int imgH = mag.rows;
    const int imgW = mag.cols;
    if (l < 0) l = 0;
    if (t < 0) t = 0;
    if (r > imgW) r = imgW;
    if (b > imgH) b = imgH;
    if (r <= l || b <= t) return;
    cv::Mat bin(b - t, r - l, CV_8UC1);
    for (int y = t; y < b; ++y) {
        const float* ep = mag.ptr<float>(y);
        uint8_t* op = bin.ptr<uint8_t>(y - t);
        for (int x = l; x < r; ++x) {
            op[x - l] = ep[x] >= thr ? 255 : 0;
        }
    }
    packSeedBinJpeg(bin, out);
}

static void fillOrientedEnergySweep(
    const cv::Mat& mag, const Frame& fr,
    float seedCx, float seedCy, float seedBw, float seedBh,
    int walkedH, float energyRatio, double thr, float jumpFrac,
    InkSweepPack* out
);

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeExpandOriented(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr,
    jfloatArray seedPts,
    jfloat maxFrac, jfloat energyRatio,
    jboolean freezeHorz, jboolean enableJump,
    jfloat jumpFrac, jfloat retractClearFrac, jfloat vertPadFrac,
    jint boundStrategy, jint tightInsetPx,
    jintArray sweepArr
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
    if (boundStrategy == 1) {
        const float ins = static_cast<float>(std::max(1, tightInsetPx));
        if (bw > 2.f * ins + 2.f) bw -= 2.f * ins;
        if (bh > 2.f * ins + 2.f) bh -= 2.f * ins;
    }
    const float seedCx = cx, seedCy = cy, seedBw = bw, seedBh0 = bh;
    if (bw < 4.f || bh < 4.f) {
        jfloat out[13] = {
            cx, cy, bw, bh, fr.angDeg,
            0.f, 0.f, 0.f, 0.f,
            0.f, 0.f, 0.f, 0.f,
        };
        jfloatArray arr = env->NewFloatArray(13);
        if (!arr) return nullptr;
        env->SetFloatArrayRegion(arr, 0, 13, out);
        return arr;
    }

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
    bool allowVNeg = strip(0.f, -1.f, false) >= thr;
    bool allowVPos = strip(0.f, +1.f, false) >= thr;
    auto onEdgeV = [&](float dvSign) {
        return stripEnergy(mag, fr, cx, cy, bw, std::max(2.f, bh - 1.f),
            0.f, dvSign, false);
    };

    if (boundStrategy == 2) {
        if (onEdgeV(-1.f) >= thr) {
            while (stepsVNeg < cap && strip(0.f, -1.f, false) >= thr) {
                cx -= 0.5f * fr.vx;
                cy -= 0.5f * fr.vy;
                bh += 1.f;
                ++stepsVNeg;
            }
            allowVNeg = stepsVNeg > 0;
        } else {
            const int maxRetractPx = std::max(1, static_cast<int>(std::lround(kVertRetractCapFrac * seedBh)));
            int nRetr = 0;
            while (bh > 2.f && nRetr < maxRetractPx && onEdgeV(-1.f) < thr) {
                cx += 0.5f * fr.vx;
                cy += 0.5f * fr.vy;
                bh -= 1.f;
                ++nRetr;
            }
            allowVNeg = false;
        }
        if (onEdgeV(+1.f) >= thr) {
            while (stepsVPos < cap && strip(0.f, +1.f, false) >= thr) {
                cx += 0.5f * fr.vx;
                cy += 0.5f * fr.vy;
                bh += 1.f;
                ++stepsVPos;
            }
            allowVPos = stepsVPos > 0;
        } else {
            const int maxRetractPx = std::max(1, static_cast<int>(std::lround(kVertRetractCapFrac * seedBh)));
            int nRetr = 0;
            while (bh > 2.f && nRetr < maxRetractPx && onEdgeV(+1.f) < thr) {
                cx -= 0.5f * fr.vx;
                cy -= 0.5f * fr.vy;
                bh -= 1.f;
                ++nRetr;
            }
            allowVPos = false;
        }
    } else {
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
    try {
        InkSweepPack pack;
        fillOrientedEnergySweep(
            mag, fr, seedCx, seedCy, seedBw, seedBh0,
            std::max(1, static_cast<int>(std::lround(bh))),
            energyRatio, thr, jumpFrac, &pack);
        std::vector<InkSweepPack> packs;
        packs.push_back(std::move(pack));
        writeSweepArr(env, sweepArr, packs);
    } catch (const cv::Exception&) {
    }
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

static bool scratchFits(const cv::Mat* s, int w, int h) {
    return s && !s->empty() && s->type() == CV_8UC1 && s->rows >= h && s->cols >= w;
}

static bool fillChromaMag(const cv::Mat& y, const cv::Mat& uv, cv::Mat* dst) {
    if (y.empty() || y.type() != CV_8UC1 || !dst) return false;
    const int h = y.rows, w = y.cols;
    const bool reuse = scratchFits(dst, w, h);
    if (!reuse) dst->create(h, w, CV_8UC1);
    if (uv.empty() || uv.type() != CV_8UC2 || uv.rows <= 0 || uv.cols <= 0) {
        if (reuse) (*dst)(cv::Rect(0, 0, w, h)).setTo(0);
        else dst->setTo(0);
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
    if (r <= l || b <= t || e.empty()) return 0.0;
    double s = 0.0;
    int n = 0;
    if (e.type() == CV_8UC1) {
        for (int y = t; y < b; ++y) {
            const uint8_t* p = e.ptr<uint8_t>(y);
            for (int x = l; x < r; ++x) {
                s += p[x];
                ++n;
            }
        }
    } else if (e.type() == CV_32F) {
        for (int y = t; y < b; ++y) {
            const float* p = e.ptr<float>(y);
            for (int x = l; x < r; ++x) {
                s += p[x];
                ++n;
            }
        }
    }
    return n > 0 ? s / n : 0.0;
}

static constexpr int kJumpMax = 4;

static int maxInkRunCol(const cv::Mat& bin, int x, int y0, int y1);

static void jumpRetractH(
    const cv::Mat& eng, int* l, int t, int* r, int b,
    int imgW, int imgH, double thr, int capPx, float jumpFrac, float retractClearFrac,
    int seedH = 0,
    const cv::Mat* lookBin = nullptr, int seedT = 0, int seedB = 0, int minRun = 0
) {
    (void)capPx;
    (void)retractClearFrac;
    const int hgt = std::max(1, b - t);
    const int jx = std::max(1, static_cast<int>(std::lround(jumpFrac * hgt)));
    const int coreH = seedH > 0 ? seedH : hgt;
    int coreT = (t + b) / 2 - coreH / 2;
    int coreB = coreT + coreH;
    if (coreT < t) coreT = t;
    if (coreB > b) coreB = b;
    if (coreB <= coreT + 1) {
        coreT = t;
        coreB = b;
    }
    int inkT = seedT;
    int inkB = seedB;
    if (inkB <= inkT + 1) {
        inkT = t;
        inkB = b;
    }
    const bool useInk = lookBin && !lookBin->empty() && lookBin->type() == CV_8UC1 && minRun > 0;
    auto colHas = [&](int x) -> bool {
        if (x < 0 || x >= imgW) return false;
        if (useInk) return maxInkRunCol(*lookBin, x, inkT, inkB) >= minRun;
        return meanRectF(eng, x, coreT, x + 1, coreB, imgW, imgH) >= thr;
    };
    int jumpsL = 0;
    while (*l > 0 && jumpsL < kJumpMax) {
        const int nextL = std::max(0, *l - jx);
        if (nextL >= *l) break;
        if (colHas(nextL)) {
            *l = nextL;
            ++jumpsL;
            continue;
        }
        for (int cur = nextL + 1; cur < *l; ++cur) {
            if (colHas(cur)) {
                *l = cur;
                break;
            }
        }
        break;
    }
    int jumpsR = 0;
    while (*r < imgW && jumpsR < kJumpMax) {
        const int nextR = std::min(imgW, *r + jx);
        if (nextR <= *r) break;
        if (colHas(nextR - 1)) {
            *r = nextR;
            ++jumpsR;
            continue;
        }
        int newR = *r;
        for (int cur = nextR - 2; cur >= *r; --cur) {
            if (colHas(cur)) {
                newR = cur + 1;
                break;
            }
        }
        *r = newR;
        break;
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

static void fillOrientedEnergySweep(
    const cv::Mat& mag, const Frame& fr,
    float seedCx, float seedCy, float seedBw, float seedBh,
    int walkedH, float energyRatio, double thr, float jumpFrac,
    InkSweepPack* out
) {
    if (!out || mag.empty()) return;
    const int capPx = std::max(1, static_cast<int>(std::lround(2.5f * std::max(1.f, seedBh))));
    const int xPad = std::max(1, static_cast<int>(std::lround(
        jumpFrac * static_cast<float>(std::max(1, walkedH)) * (kJumpMax + 1))));
    const int v0s = static_cast<int>(std::lround(-0.5f * seedBh));
    const int v1s = static_cast<int>(std::lround(0.5f * seedBh));
    const int u0s = static_cast<int>(std::lround(-0.5f * seedBw));
    const int u1s = static_cast<int>(std::lround(0.5f * seedBw));
    const int vStart = v0s - capPx;
    const int vEnd = v1s + capPx;
    const int uStart = u0s - xPad;
    const int uEnd = u1s + xPad;
    if (vEnd <= vStart) return;
    out->thr = static_cast<float>(thr);
    out->energyRatio = energyRatio;
    out->sPx = static_cast<float>(std::max(1, static_cast<int>(seedBh) / 12));
    out->minRun = 0;
    out->vOrigin = vStart;
    out->hOrigin = uStart;
    out->v0 = v0s - vStart;
    out->v1 = v1s - vStart;
    out->h0 = u0s - uStart;
    out->h1 = u1s - uStart;
    const int nu = std::max(4, static_cast<int>(std::lround(seedBw)));
    const int nv = std::max(4, static_cast<int>(std::lround(seedBh)));
    out->vScores.reserve(static_cast<size_t>(vEnd - vStart));
    for (int v = vStart; v < vEnd; ++v) {
        double s = 0.0;
        int c = 0;
        for (int i = 0; i < nu; ++i) {
            const float uu = ((i + 0.5f) / nu - 0.5f) * seedBw;
            const float px = seedCx + uu * fr.ux + static_cast<float>(v) * fr.vx;
            const float py = seedCy + uu * fr.uy + static_cast<float>(v) * fr.vy;
            if (px < 0 || py < 0 || px >= fr.imgW || py >= fr.imgH) continue;
            s += sampleEnergy(mag, px, py, fr.imgW, fr.imgH);
            ++c;
        }
        out->vScores.push_back(static_cast<int>(std::lround(c > 0 ? s / c : 0.0)));
    }
    if (uEnd <= uStart) return;
    out->hScores.reserve(static_cast<size_t>(uEnd - uStart));
    for (int u = uStart; u < uEnd; ++u) {
        double s = 0.0;
        int c = 0;
        for (int i = 0; i < nv; ++i) {
            const float vv = ((i + 0.5f) / nv - 0.5f) * seedBh;
            const float px = seedCx + static_cast<float>(u) * fr.ux + vv * fr.vx;
            const float py = seedCy + static_cast<float>(u) * fr.uy + vv * fr.vy;
            if (px < 0 || py < 0 || px >= fr.imgW || py >= fr.imgH) continue;
            s += sampleEnergy(mag, px, py, fr.imgW, fr.imgH);
            ++c;
        }
        out->hScores.push_back(static_cast<int>(std::lround(c > 0 ? s / c : 0.0)));
    }
    const int wu = std::max(1, static_cast<int>(std::lround(seedBw)));
    const int hv = std::max(1, static_cast<int>(std::lround(seedBh)));
    cv::Mat seedBin(hv, wu, CV_8UC1);
    const float thrF = static_cast<float>(thr);
    for (int y = 0; y < hv; ++y) {
        uint8_t* row = seedBin.ptr<uint8_t>(y);
        const float vv = ((y + 0.5f) / hv - 0.5f) * seedBh;
        for (int x = 0; x < wu; ++x) {
            const float uu = ((x + 0.5f) / wu - 0.5f) * seedBw;
            const float px = seedCx + uu * fr.ux + vv * fr.vx;
            const float py = seedCy + uu * fr.uy + vv * fr.vy;
            if (px < 0 || py < 0 || px >= fr.imgW || py >= fr.imgH) {
                row[x] = 0;
                continue;
            }
            row[x] = sampleEnergy(mag, px, py, fr.imgW, fr.imgH) >= thrF ? 255 : 0;
        }
    }
    packSeedBinJpeg(seedBin, out);
}

static void fillAabbEnergySweep(
    const cv::Mat& vertEng, const cv::Mat& magY,
    int sl, int st, int sr, int sb,
    int walkedH, int imgW, int imgH,
    float energyRatio, double thr, float jumpFrac,
    InkSweepPack* out
) {
    if (!out) return;
    if (sl < 0) sl = 0;
    if (st < 0) st = 0;
    if (sr > imgW) sr = imgW;
    if (sb > imgH) sb = imgH;
    if (sr <= sl || sb <= st) return;
    const int seedH = std::max(1, sb - st);
    const int capPx = std::max(1, static_cast<int>(std::lround(2.5f * seedH)));
    const int hgt = std::max(1, walkedH);
    const int xPad = std::max(1, static_cast<int>(std::lround(jumpFrac * hgt * (kJumpMax + 1))));
    const int y0 = std::max(0, st - capPx);
    const int y1 = std::min(imgH, sb + capPx);
    const int x0 = std::max(0, sl - xPad);
    const int x1 = std::min(imgW, sr + xPad);
    if (y1 <= y0 || x1 <= x0) return;
    out->thr = static_cast<float>(thr);
    out->energyRatio = energyRatio;
    out->sPx = static_cast<float>(std::max(1, seedH / 12));
    out->minRun = 0;
    out->vOrigin = y0;
    out->hOrigin = x0;
    out->v0 = st - y0;
    out->v1 = sb - y0;
    out->h0 = sl - x0;
    out->h1 = sr - x0;
    out->vScores.reserve(static_cast<size_t>(y1 - y0));
    for (int y = y0; y < y1; ++y) {
        out->vScores.push_back(static_cast<int>(
            std::lround(meanRectF(vertEng, sl, y, sr, y + 1, imgW, imgH))));
    }
    const cv::Mat& hEng = magY.empty() ? vertEng : magY;
    out->hScores.reserve(static_cast<size_t>(x1 - x0));
    for (int x = x0; x < x1; ++x) {
        out->hScores.push_back(static_cast<int>(
            std::lround(meanRectF(hEng, x, st, x + 1, sb, imgW, imgH))));
    }
    packSeedEnergyRect(vertEng, sl, st, sr, sb, static_cast<float>(thr), out);
}

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
    jfloat jumpFrac, jfloat retractClearFrac, jfloat vertPadFrac, jfloat chi2K,
    jint boundStrategy, jint tightInsetPx,
    jfloatArray teleArr, jintArray sweepArr
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
    std::vector<InkSweepPack> sweeps;
    sweeps.resize(static_cast<size_t>(n));
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
        if (boundStrategy == 1) {
            const int ins = std::max(1, tightInsetPx);
            if (r - l > 2 * ins + 2) { l += ins; r -= ins; }
            if (b - t > 2 * ins + 2) { t += ins; b -= ins; }
        }
        const int seedL = l, seedT = t, seedR = r, seedB = b;
        const int seedH = std::max(1, b - t);
        const int seedW = std::max(1, r - l);
        if (seedH < 4 || seedW < 4) {
            const int o = i * 11;
            out[o + 0] = seedL;
            out[o + 1] = seedT;
            out[o + 2] = seedR;
            out[o + 3] = seedB;
            out[o + 4] = seedL;
            out[o + 5] = seedT;
            out[o + 6] = seedR;
            out[o + 7] = seedB;
            out[o + 8] = 0;
            out[o + 9] = 0;
            out[o + 10] = 0;
            continue;
        }
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
        int fTop = kFlagUnchanged, fBot = kFlagUnchanged;
        const int maxRetractPx = std::max(1, static_cast<int>(std::lround(kVertRetractCapFrac * seedH)));
        if (boundStrategy == 2) {
            auto edgeInk = [&](int sl, int st, int sr, int sb) {
                return meanRectF(vertEng, sl, st, sr, sb, imgW, imgH) >= thr;
            };
            if (edgeInk(l, t, r, t + 1)) {
                while (t > 0 && seedT - (t - 1) <= cap && edgeInk(l, t - 1, r, t)) --t;
                fTop = t < seedT ? kFlagNormalExpand : kFlagUnchanged;
            } else {
                while (t < b - 1 && (t - seedT) < maxRetractPx && !edgeInk(l, t, r, t + 1)) ++t;
                if (t > seedT && edgeInk(l, t, r, t + 1)) fTop = kFlagNormalRetract;
                else if (t - seedT >= maxRetractPx) fTop = kFlagBlocked10pct;
                else fTop = kFlagNormalRetract;
            }
            if (edgeInk(l, b - 1, r, b)) {
                while (b < imgH && b - seedB < cap && edgeInk(l, b, r, b + 1)) ++b;
                fBot = b > seedB ? kFlagNormalExpand : kFlagUnchanged;
            } else {
                while (b > t + 1 && (seedB - b) < maxRetractPx && !edgeInk(l, b - 1, r, b)) --b;
                if (b < seedB && edgeInk(l, b - 1, r, b)) fBot = kFlagNormalRetract;
                else if (seedB - b >= maxRetractPx) fBot = kFlagBlocked10pct;
                else fBot = kFlagNormalRetract;
            }
            allowUp = t < seedT;
            allowDown = b > seedB;
        } else if (vertKind == 2) {
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
            const cv::Mat& jumpEng = useChroma ? vertEng : magY;
            const double jThr = useChroma ? thr : jumpThr;
            jumpRetractH(jumpEng, &l, t, &r, b, imgW, imgH, jThr, cap,
                         jumpFrac, retractClearFrac, seedH);
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
        if (boundStrategy != 2) {
            fTop = walkT > 0 ? kFlagNormalExpand : kFlagBlockedGap;
            fBot = walkB > 0 ? kFlagNormalExpand : kFlagBlockedGap;
        }
        Seg7Tele tele{};
        tele.method = 1.f;
        tele.yInk = static_cast<float>(
            meanRectF(*gray, seedL, seedT, seedR, seedB, imgW, imgH));
        const double yBgT = meanRectF(*gray, seedL, std::max(0, seedT - 1), seedR, seedT, imgW, imgH);
        const double yBgB = meanRectF(*gray, seedL, seedB, seedR, std::min(imgH, seedB + 1), imgW, imgH);
        tele.yBg = static_cast<float>(0.5 * (yBgT + yBgB));
        tele.dInk = tele.yInk - tele.yBg;
        tele.sPx = static_cast<float>(std::max(1, seedH / 12));
        tele.dTop = static_cast<float>(t - seedT);
        tele.dBot = static_cast<float>(b - seedB);
        tele.dLeft = static_cast<float>(l - seedL);
        tele.dRight = static_cast<float>(r - seedR);
        tele.fTop = static_cast<float>(fTop);
        tele.fBot = static_cast<float>(fBot);
        tele.fLeft = static_cast<float>(kFlagUnchanged);
        tele.fRight = static_cast<float>(kFlagUnchanged);
        const int ht = std::max(0, t - 2), hb = std::min(imgH, b + 2);
        const int hl = std::max(0, l), hr = std::min(imgW, r);
        if (hb > ht && hr > hl && !vertEng.empty() && vertEng.type() == CV_32F) {
            cv::Mat eBin(hb - ht, hr - hl, CV_8UC1);
            for (int yy = ht; yy < hb; ++yy) {
                uint8_t* op = eBin.ptr<uint8_t>(yy - ht);
                const float* ep = vertEng.ptr<float>(yy);
                for (int xx = hl; xx < hr; ++xx) {
                    op[xx - hl] = ep[xx] >= static_cast<float>(thr) ? 255 : 0;
                }
            }
            fillRunHists(eBin, tele.histH, tele.histV);
        }
        storeTeleArr(env, teleArr, i, tele);
        fillAabbEnergySweep(
            vertEng, magY, seedL, seedT, seedR, seedB,
            std::max(1, b - t), imgW, imgH, energyRatio, thr, jumpFrac,
            &sweeps[static_cast<size_t>(i)]);
    }
    writeSweepArr(env, sweepArr, sweeps);
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

static int maxInkRunCol(const cv::Mat& bin, int x, int y0, int y1) {
    if (x < 0 || x >= bin.cols) return 0;
    int best = 0, run = 0;
    const int yEnd = std::min(y1, bin.rows);
    for (int y = std::max(0, y0); y < yEnd; ++y) {
        if (bin.ptr<uint8_t>(y)[x] != 0) {
            ++run;
            if (run > best) best = run;
        } else {
            run = 0;
        }
    }
    return best;
}

/** Per-seed gray/color minRun: never raise 0.5×sPx; if seed has ink, min(halfS, 0.4×max in-seed row run). */
static int usedMinRun(int sPx, int maxInSeedRun) {
    const int halfS = std::max(1, static_cast<int>(std::lround(0.5f * static_cast<float>(sPx))));
    if (maxInSeedRun > 0) {
        const int dyn = std::max(1, static_cast<int>(std::lround(0.4f * static_cast<float>(maxInSeedRun))));
        return std::min(halfS, dyn);
    }
    return halfS;
}

static int maxInSeedRunRows(const cv::Mat& bin, int y0, int y1, int x0, int x1) {
    int best = 0;
    if (bin.empty() || bin.type() != CV_8UC1) return 0;
    const int ya = std::max(0, y0);
    const int yb = std::min(bin.rows, y1);
    const int xa = x0;
    const int xb = x1 < 0 ? bin.cols : x1;
    for (int y = ya; y < yb; ++y) {
        const int r = maxInkRunRow(bin, y, xa, xb);
        if (r > best) best = r;
    }
    return best;
}

static void dropWide(cv::Mat* bin, int glareW);
static void fillSaltPepper(cv::Mat* bin);
static bool rowHasStrokeBar(const cv::Mat& bin, int y, int minRun, int glareW);
static int fillPoisonLookRaster(
    const cv::Mat& seedY, const cv::Mat& lookY, int ySeed0, int xSeed0,
    bool srcIsBin, int glareMult, int fallback, cv::Mat* lookBin,
    cv::Mat* overlayY = nullptr, cv::Mat* overlayUv = nullptr,
    int ovX = 0, int ovY = 0, PoisonStats* statsOut = nullptr);

static void fillAabbLookSweep(
    const cv::Mat& src, bool srcIsBin, double otsu, bool darkInk, int glareW,
    const cv::Mat& lookBin, int nt,
    int sl, int st, int sr, int sb,
    int walkedT, int walkedB,
    int imgW, int imgH, int minRun, float sPx,
    InkSweepPack* out
) {
    if (!out || src.empty()) return;
    if (sl < 0) sl = 0;
    if (st < 0) st = 0;
    if (sr > imgW) sr = imgW;
    if (sb > imgH) sb = imgH;
    if (sr <= sl || sb <= st) return;
    const int seedH = std::max(1, sb - st);
    const int capPx = std::max(1, static_cast<int>(std::lround(2.5f * seedH)));
    const int walkedH = std::max(1, walkedB - walkedT);
    const int xPad = std::max(1, static_cast<int>(std::lround(0.40f * walkedH * (kJumpMax + 1))));
    const int y0 = std::max(0, st - capPx);
    const int y1 = std::min(imgH, sb + capPx);
    const int x0 = std::max(0, sl - xPad);
    const int x1 = std::min(imgW, sr + xPad);
    if (y1 <= y0) return;
    out->thr = static_cast<float>(minRun);
    out->sPx = sPx;
    out->energyRatio = 0.f;
    out->minRun = minRun;
    out->vOrigin = y0;
    out->hOrigin = x0;
    out->v0 = st - y0;
    out->v1 = sb - y0;
    out->h0 = sl - x0;
    out->h1 = sr - x0;
    out->vScores.reserve(static_cast<size_t>(y1 - y0));
    for (int y = y0; y < y1; ++y) {
        const int ly = y - nt;
        int sc = 0;
        if (!lookBin.empty() && ly >= 0 && ly < lookBin.rows) {
            sc = maxInkRunRow(lookBin, ly, 0, lookBin.cols);
        }
        out->vScores.push_back(sc);
    }
    packSeedLookRows(lookBin, st - nt, sb - nt, out);
    if (x1 <= x0) return;
    cv::Mat wide;
    if (st >= 0 && sb <= imgH && sb > st && x1 > x0) {
        cv::Mat strip = src(cv::Range(st, sb), cv::Range(x0, x1));
        if (srcIsBin) {
            strip.copyTo(wide);
        } else {
            const int ttype = darkInk ? cv::THRESH_BINARY_INV : cv::THRESH_BINARY;
            cv::threshold(strip, wide, otsu, 255, ttype);
        }
        if (!wide.empty() && glareW > 0) dropWide(&wide, glareW);
    }
    out->hScores.reserve(static_cast<size_t>(x1 - x0));
    for (int x = x0; x < x1; ++x) {
        int sc = 0;
        if (!wide.empty()) {
            const int lx = x - x0;
            sc = maxInkRunCol(wide, lx, 0, wide.rows);
        }
        out->hScores.push_back(sc);
    }
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

/** One H pass then one V pass. Fill interior 0-runs with gap∈(0,4] and 2×gap≤leading ink. No iterate. */
static void fillSaltPepperLine(uint8_t* p, int n, int stride) {
    int i = 0;
    while (i < n) {
        if (p[i * stride] == 0) {
            ++i;
            continue;
        }
        const int leadStart = i;
        while (i < n && p[i * stride] != 0) ++i;
        const int lead = i - leadStart;
        if (i >= n) break;
        const int gapStart = i;
        while (i < n && p[i * stride] == 0) ++i;
        const int gap = i - gapStart;
        if (i >= n) break;
        if (gap > 0 && gap <= 4 && 2 * gap <= lead) {
            for (int k = gapStart; k < gapStart + gap; ++k) p[k * stride] = 255;
        }
    }
}

static void fillSaltPepper(cv::Mat* bin) {
    if (!bin || bin->empty() || bin->type() != CV_8UC1) return;
    const int h = bin->rows, w = bin->cols;
    if (h < 1 || w < 1) return;
    for (int y = 0; y < h; ++y) {
        fillSaltPepperLine(bin->ptr<uint8_t>(y), w, 1);
    }
    const int step = static_cast<int>(bin->step[0]);
    uint8_t* base = bin->ptr<uint8_t>(0);
    for (int x = 0; x < w; ++x) {
        fillSaltPepperLine(base + x, h, step);
    }
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
    bool srcIsBin = false,
    float gapFrac = 0.5f,
    float minSeedHsToFreeze = 0.f,
    int glareMult = 11,
    int boundStrategy = 0,
    int tightInsetPx = 16,
    Seg7Tele* tele = nullptr,
    bool keepColorStats = false,
    InkSweepPack* sweepOut = nullptr,
    cv::Mat* inkDump = nullptr,
    cv::Mat* overlayY = nullptr,
    cv::Mat* overlayUv = nullptr,
    PoisonStats* poisonStats = nullptr
) {
    if (boundStrategy == 1) {
        const int ins = std::max(1, tightInsetPx);
        if (sr - sl > 2 * ins + 2) { sl += ins; sr -= ins; }
        if (sb - st > 2 * ins + 2) { st += ins; sb -= ins; }
    }
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
    const int gm = glareMult > 0 ? glareMult : 11;
    const int capPx = std::max(1, static_cast<int>(std::lround(2.5f * seedH)));
    const int vLook = capPx + 2;
    const int nt = std::max(0, st - vLook);
    const int nb = std::min(imgH, sb + vLook);
    if (sr <= sl || nb <= nt) return;
    cv::Mat look = src(cv::Range(nt, nb), cv::Range(sl, sr));
    cv::Mat seedY;
    src(cv::Range(st, sb), cv::Range(sl, sr)).copyTo(seedY);
    cv::Mat lookBinLocal;
    cv::Mat lookBin;
    const bool bsRoi = inkDump && inkDump->type() == CV_8UC1 &&
        inkDump->rows >= imgH && inkDump->cols >= imgW &&
        nb > nt && sr > sl && nt >= 0 && sl >= 0 && nb <= inkDump->rows && sr <= inkDump->cols;
    if (bsRoi) {
        lookBin = (*inkDump)(cv::Range(nt, nb), cv::Range(sl, sr));
    } else {
        lookBinLocal.create(nb - nt, sr - sl, CV_8UC1);
        lookBin = lookBinLocal;
    }
    const int localT = st - nt;
    const int localB = sb - nt;
    PoisonStats stLocal;
    const int sPx = fillPoisonLookRaster(
        seedY, look, localT, 0, srcIsBin, gm, fallback, &lookBin,
        overlayY, overlayUv, sl, nt, poisonStats ? &stLocal : nullptr);
    if (poisonStats) *poisonStats = stLocal;
    const int glareW = gm * std::max(sPx, 4);
    const int vSW = sPx;
    const int hSW = vertPeakSW(lookBin, seedH);
    *sPxOut = sPx;
    *vSWOut = vSW;
    *hSWOut = hSW;
    *usedFb = (sPx == fallback) ? 1 : 0;
    const float gf = gapFrac > 0.f ? gapFrac : 0.5f;
    const int gapStop = std::max(1, static_cast<int>(std::lround(gf * sPx)));
    const int minRun = usedMinRun(sPx, maxInSeedRunRows(lookBin, localT, localB, 0, lookBin.cols));
    auto hasBar = [&](int y) {
        return rowHasStrokeBar(lookBin, y, minRun, glareW);
    };
    int t = localT, b = localB;
    const int maxRetractPx = std::max(1, static_cast<int>(std::lround(kVertRetractCapFrac * seedH)));
    int fTop = kFlagUnchanged, fBot = kFlagUnchanged;
    int gapJumpTop = 0, gapJumpBot = 0;
    int landTop = -1, landBot = -1;
    auto expandTopOneShot = [&]() {
        bool usedGap = false;
        while (t > 0 && localT - (t - 1) <= capPx && hasBar(t - 1)) --t;
        if (t < localT) fTop = kFlagNormalExpand;
        if (!usedGap && t > 0 && localT - (t - 1) <= capPx && !hasBar(t - 1)) {
            int y = t - 1;
            int n = 0;
            while (n < gapStop && y >= 0 && localT - y <= capPx) {
                if (hasBar(y)) {
                    t = y;
                    usedGap = true;
                    gapJumpTop = 1;
                    landTop = y;
                    fTop = kFlagNormalExpand;
                    while (t > 0 && localT - (t - 1) <= capPx && hasBar(t - 1)) --t;
                    break;
                }
                --y;
                ++n;
            }
        }
        if (fTop == kFlagUnchanged) fTop = kFlagBlockedGap;
    };
    auto expandBotOneShot = [&]() {
        bool usedGap = false;
        while (b < lookBin.rows && b - localB < capPx && hasBar(b)) ++b;
        if (b > localB) fBot = kFlagNormalExpand;
        if (!usedGap && b < lookBin.rows && b - localB < capPx && !hasBar(b)) {
            int y = b;
            int n = 0;
            while (n < gapStop && y < lookBin.rows && y - localB < capPx) {
                if (hasBar(y)) {
                    b = y + 1;
                    usedGap = true;
                    gapJumpBot = 1;
                    landBot = y;
                    fBot = kFlagNormalExpand;
                    while (b < lookBin.rows && b - localB < capPx && hasBar(b)) ++b;
                    break;
                }
                ++y;
                ++n;
            }
        }
        if (fBot == kFlagUnchanged) fBot = kFlagBlockedGap;
    };
    if (boundStrategy == 2) {
        if (hasBar(localT)) {
            expandTopOneShot();
        } else {
            while (t < b - 1 && (t - localT) < maxRetractPx && !hasBar(t)) ++t;
            if (t > localT && hasBar(t)) fTop = kFlagNormalRetract;
            else if (t - localT >= maxRetractPx) fTop = kFlagBlocked10pct;
            else fTop = kFlagNormalRetract;
        }
        if (localB > 0 && hasBar(localB - 1)) {
            expandBotOneShot();
        } else {
            while (b > t + 1 && (localB - b) < maxRetractPx && !hasBar(b - 1)) --b;
            if (b < localB && localB > 0 && hasBar(b - 1)) fBot = kFlagNormalRetract;
            else if (localB - b >= maxRetractPx) fBot = kFlagBlocked10pct;
            else fBot = kFlagNormalRetract;
        }
    } else {
        expandTopOneShot();
        expandBotOneShot();
    }
    if (b <= t) b = std::min(t + 1, lookBin.rows);
    *ol = sl;
    *ot = nt + t;
    *oright = sr;
    *ob = nt + b;
    if (*ot < 0) *ot = 0;
    if (*ob > imgH) *ob = imgH;
    if (*ob <= *ot) *ob = std::min(imgH, *ot + 1);
    if (tele) {
        if (!keepColorStats) {
            float yi = 0.f, yb = 0.f;
            const int y0s = std::max(0, localT);
            const int y1s = std::min(lookBin.rows, localB);
            if (y1s > y0s) {
                cv::Mat seedInk = lookBin(cv::Range(y0s, y1s), cv::Range(0, lookBin.cols));
                fillYInkBg(src, seedInk, sl, st, sPx, &yi, &yb);
            }
            tele->yInk = yi;
            tele->yBg = yb;
            tele->dInk = yi - yb;
            tele->otsuThr = 0.f;
        }
        tele->sPx = static_cast<float>(sPx);
        tele->dTop = static_cast<float>(*ot - st);
        tele->dBot = static_cast<float>(*ob - sb);
        tele->dLeft = 0.f;
        tele->dRight = 0.f;
        tele->fTop = static_cast<float>(fTop);
        tele->fBot = static_cast<float>(fBot);
        tele->fLeft = static_cast<float>(kFlagUnchanged);
        tele->fRight = static_cast<float>(kFlagUnchanged);
        tele->gapJumpTop = gapJumpTop ? 1.f : 0.f;
        tele->gapJumpBot = gapJumpBot ? 1.f : 0.f;
        tele->landTop = landTop >= 0 ? static_cast<float>(nt + landTop) : 0.f;
        tele->landBot = landBot >= 0 ? static_cast<float>(nt + landBot) : 0.f;
        fillRunHists(lookBin, tele->histH, tele->histV);
    }
    if (sweepOut && !lookBin.empty()) {
        fillAabbLookSweep(
            src, true, 0.0, true, 0, lookBin, nt,
            sl, st, sr, sb, *ot, *ob, imgW, imgH, minRun,
            static_cast<float>(sPx), sweepOut);
    }
}

static constexpr float kTintDotThr = 0.5f;
static constexpr float kTintChromaEps = 8.0f;

static bool skipTintWalk(bool adaptive, const Seg7Tele& tele) {
    return adaptive && (tele.meanChroma < 12.f || tele.inkBgDot >= kTintDotThr);
}

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

static bool strokeNeedFb(const HorizSW& hh, int vSW, int seedW, float inkFrac) {
    const int lo = std::max(1, static_cast<int>(std::lround(0.7f * static_cast<float>(vSW))));
    const int hi = std::max(lo, static_cast<int>(std::lround(1.3f * static_cast<float>(vSW))));
    int band = 0;
    const int hiClamp = std::min(hi, static_cast<int>(hh.hist.size()) - 1);
    for (int k = lo; k <= hiClamp; ++k) band += hh.hist[k];
    const float strokeShare = hh.nNonSpan > 0
        ? band / static_cast<float>(hh.nNonSpan) : 0.f;
    const float maxRunOverW = hh.maxRun / static_cast<float>(std::max(1, seedW));
    return vSW <= 4 || inkFrac >= 0.45f || strokeShare < 0.30f || maxRunOverW >= 0.50f;
}

static bool strokesAgree(int a, int b) {
    if (a <= 4 || b <= 4) return false;
    const float fa = static_cast<float>(a), fb = static_cast<float>(b);
    return fa >= 0.7f * fb && fa <= 1.3f * fb && fb >= 0.7f * fa && fb <= 1.3f * fa;
}

static void fillRunLenHV(const cv::Mat& bin, cv::Mat* hRun, cv::Mat* vRun) {
    const int h = bin.rows, w = bin.cols;
    hRun->create(h, w, CV_32S);
    vRun->create(h, w, CV_32S);
    hRun->setTo(0);
    vRun->setTo(0);
    for (int y = 0; y < h; ++y) {
        const uint8_t* p = bin.ptr<uint8_t>(y);
        int* hr = hRun->ptr<int>(y);
        int x = 0;
        while (x < w) {
            if (!p[x]) { ++x; continue; }
            const int x0 = x;
            while (x < w && p[x]) ++x;
            const int len = x - x0;
            for (int k = x0; k < x; ++k) hr[k] = len;
        }
    }
    for (int x = 0; x < w; ++x) {
        int y = 0;
        while (y < h) {
            if (!bin.ptr<uint8_t>(y)[x]) { ++y; continue; }
            const int y0 = y;
            while (y < h && bin.ptr<uint8_t>(y)[x]) ++y;
            const int len = y - y0;
            for (int k = y0; k < y; ++k) vRun->ptr<int>(k)[x] = len;
        }
    }
}

static void fillPoisonMask(
    const cv::Mat& bin, int v0, bool needFb, int seedW, int glareMult, cv::Mat* poison
) {
    const int h = bin.rows, w = bin.cols;
    poison->create(h, w, CV_8UC1);
    poison->setTo(0);
    cv::Mat hRun, vRun;
    fillRunLenHV(bin, &hRun, &vRun);
    const int vRef = std::max(v0, 4);
    const int fat = 3 * vRef;
    const int longH = (glareMult > 0 ? glareMult : 11) * vRef;
    const int thinW = std::max(1, static_cast<int>(std::lround(0.25f * static_cast<float>(seedW))));
    const bool weak = v0 <= 4 || needFb;
    for (int y = 0; y < h; ++y) {
        const uint8_t* bp = bin.ptr<uint8_t>(y);
        const int* hr = hRun.ptr<int>(y);
        const int* vr = vRun.ptr<int>(y);
        uint8_t* op = poison->ptr<uint8_t>(y);
        for (int x = 0; x < w; ++x) {
            if (!bp[x]) continue;
            const int mn = std::min(hr[x], vr[x]);
            if (mn > fat || hr[x] > longH || (weak && hr[x] > thinW)) op[x] = 255;
        }
    }
}

static bool otsuKeep(
    const cv::Mat& y, const cv::Mat& keep, double* thr, bool* dark, float* inkFrac
) {
    std::vector<uint8_t> vals;
    vals.reserve(static_cast<size_t>(y.rows * y.cols));
    for (int yy = 0; yy < y.rows; ++yy) {
        const uint8_t* yp = y.ptr<uint8_t>(yy);
        const uint8_t* kp = keep.ptr<uint8_t>(yy);
        for (int xx = 0; xx < y.cols; ++xx) {
            if (kp[xx]) vals.push_back(yp[xx]);
        }
    }
    if (vals.size() < 2) return false;
    cv::Mat col(1, static_cast<int>(vals.size()), CV_8UC1);
    uint8_t* cp = col.ptr<uint8_t>(0);
    for (size_t i = 0; i < vals.size(); ++i) cp[i] = vals[i];
    cv::Mat b;
    *thr = cv::threshold(col, b, 0, 255, cv::THRESH_BINARY_INV | cv::THRESH_OTSU);
    int nz = cv::countNonZero(b);
    *inkFrac = nz / static_cast<float>(vals.size());
    *dark = true;
    if (*inkFrac >= 0.45f) {
        *dark = false;
        cv::bitwise_not(b, b);
        nz = cv::countNonZero(b);
        *inkFrac = nz / static_cast<float>(vals.size());
    }
    return true;
}

static void applyThrKeep(
    const cv::Mat& y, const cv::Mat& keep, double thr, bool dark, cv::Mat* out
) {
    out->create(y.rows, y.cols, CV_8UC1);
    out->setTo(0);
    for (int yy = 0; yy < y.rows; ++yy) {
        const uint8_t* yp = y.ptr<uint8_t>(yy);
        const uint8_t* kp = keep.ptr<uint8_t>(yy);
        uint8_t* op = out->ptr<uint8_t>(yy);
        for (int xx = 0; xx < y.cols; ++xx) {
            if (!kp[xx]) continue;
            const bool ink = dark ? (static_cast<double>(yp[xx]) <= thr)
                                  : (static_cast<double>(yp[xx]) > thr);
            if (ink) op[xx] = 255;
        }
    }
}

static float p50YRows(const cv::Mat& y, int y0, int y1) {
    if (y.empty() || y1 <= y0) return 0.f;
    y0 = std::max(0, y0);
    y1 = std::min(y.rows, y1);
    std::vector<uint8_t> vals;
    vals.reserve(static_cast<size_t>(std::max(0, y1 - y0) * y.cols));
    for (int yy = y0; yy < y1; ++yy) {
        const uint8_t* p = y.ptr<uint8_t>(yy);
        for (int xx = 0; xx < y.cols; ++xx) vals.push_back(p[xx]);
    }
    if (vals.empty()) return 0.f;
    std::sort(vals.begin(), vals.end());
    return static_cast<float>(vals[vals.size() / 2]);
}

static float dInkFromBin(const cv::Mat& y, const cv::Mat& bin) {
    double sI = 0.0, sB = 0.0;
    int nI = 0, nB = 0;
    for (int yy = 0; yy < y.rows; ++yy) {
        const uint8_t* yp = y.ptr<uint8_t>(yy);
        const uint8_t* bp = bin.ptr<uint8_t>(yy);
        for (int xx = 0; xx < y.cols; ++xx) {
            if (bp[xx]) { sI += yp[xx]; ++nI; }
            else { sB += yp[xx]; ++nB; }
        }
    }
    if (nI <= 0 || nB <= 0) return 0.f;
    return static_cast<float>(sI / nI - sB / nB);
}

static void orBrightBands(const cv::Mat& y, const cv::Mat& firstBin, int sPx, cv::Mat* poison,
    bool* topOut = nullptr, bool* botOut = nullptr, int* hOut = nullptr) {
    if (topOut) *topOut = false;
    if (botOut) *botOut = false;
    if (hOut) *hOut = 0;
    if (!poison || y.empty() || firstBin.empty()) return;
    const int h = y.rows, w = y.cols;
    const int bandH = std::max(sPx, static_cast<int>(std::lround(0.12f * static_cast<float>(h))));
    if (hOut) *hOut = bandH;
    if (bandH < 1 || h < bandH * 3) return;
    const float lim = std::max(16.f, 0.5f * std::fabs(dInkFromBin(y, firstBin)));
    const float medI = p50YRows(y, bandH, h - bandH);
    const float medT = p50YRows(y, 0, bandH);
    const float medB = p50YRows(y, h - bandH, h);
    auto paint = [&](int y0, int y1) {
        for (int yy = y0; yy < y1; ++yy) {
            uint8_t* op = poison->ptr<uint8_t>(yy);
            for (int xx = 0; xx < w; ++xx) op[xx] = 255;
        }
    };
    if (std::fabs(medT - medI) >= lim) {
        paint(0, bandH);
        if (topOut) *topOut = true;
    }
    if (std::fabs(medB - medI) >= lim) {
        paint(h - bandH, h);
        if (botOut) *botOut = true;
    }
}

static void orBin(cv::Mat* dst, const cv::Mat& src) {
    if (!dst || dst->empty() || src.empty() || dst->size() != src.size()) return;
    cv::bitwise_or(*dst, src, *dst);
}

/** Seed-ROI Y Otsu + poison map; chroma samples clean + agreeing poison ink. */
static int seedInkBinY(
    const cv::Mat& y, int sl, int st, int sr, int sb, cv::Mat* binOut,
    int glareMult = 11,
    double* otsuOut = nullptr,
    bool* invertedOut = nullptr
) {
    if (y.empty()) {
        const int fallback = 2;
        return fallback;
    }
    const int w = y.cols, h = y.rows;
    if (sl < 0) sl = 0;
    if (st < 0) st = 0;
    if (sr > w) sr = w;
    if (sb > h) sb = h;
    const int seedH = std::max(1, sb - st);
    const int seedW = std::max(1, sr - sl);
    const int fallback = std::max(2, static_cast<int>(std::lround(0.08f * seedH)));
    if (sr <= sl || sb <= st) return fallback;
    cv::Mat roi = y(cv::Range(st, sb), cv::Range(sl, sr));
    cv::Mat combined;
    const int sPx = fillPoisonLookRaster(
        roi, roi, 0, 0, false, glareMult, fallback, &combined);
    if (otsuOut) *otsuOut = 0.0;
    if (invertedOut) *invertedOut = false;
    *binOut = combined;
    return std::max(1, sPx);
}

struct PoisonReg {
    int x0 = 0, x1 = 0, y0 = 0, y1 = 0;
    double thr = 0.0;
    bool dark = true;
    bool noPeak = true;
    int nInk = 0;
};

static void dropWideRuns(cv::Mat* bin, int glareW) {
    if (!bin || bin->empty() || glareW <= 0) return;
    const int h = bin->rows, w = bin->cols;
    for (int y = 0; y < h; ++y) {
        uint8_t* p = bin->ptr<uint8_t>(y);
        int x = 0;
        while (x < w) {
            if (!p[x]) { ++x; continue; }
            const int x0 = x;
            while (x < w && p[x]) ++x;
            if (x - x0 > glareW) {
                for (int k = x0; k < x; ++k) p[k] = 0;
            }
        }
    }
}

static bool rowHasStrokeBar(const cv::Mat& bin, int y, int minRun, int glareW) {
    if (y < 0 || y >= bin.rows) return false;
    const uint8_t* p = bin.ptr<uint8_t>(y);
    int run = 0;
    for (int x = 0; x <= bin.cols; ++x) {
        const bool on = x < bin.cols && p[x] != 0;
        if (on) ++run;
        else if (run > 0) {
            if (run >= minRun && run <= glareW) return true;
            run = 0;
        }
    }
    return false;
}

static void yuvPut(
    cv::Mat* yPlane, cv::Mat* uvPlane, int x, int y,
    uint8_t Y, uint8_t U, uint8_t V
) {
    if (yPlane && y >= 0 && y < yPlane->rows && x >= 0 && x < yPlane->cols) {
        yPlane->ptr<uint8_t>(y)[x] = Y;
    }
    if (uvPlane && uvPlane->type() == CV_8UC2 && uvPlane->rows > 0 && uvPlane->cols > 0) {
        int uy = y / 2;
        int ux = x / 2;
        if (uy < 0) uy = 0;
        if (ux < 0) ux = 0;
        if (uy >= uvPlane->rows) uy = uvPlane->rows - 1;
        if (ux >= uvPlane->cols) ux = uvPlane->cols - 1;
        uvPlane->ptr<cv::Vec2b>(uy)[ux] = cv::Vec2b(U, V);
    }
}

/** One look raster: clean vs per-poison rule; runs ignore region edges. Returns sPx. */
static int fillPoisonLookRaster(
    const cv::Mat& seedY, const cv::Mat& lookY, int ySeed0, int xSeed0,
    bool srcIsBin, int glareMult, int fallback, cv::Mat* lookBin,
    cv::Mat* overlayY, cv::Mat* overlayUv, int ovX, int ovY, PoisonStats* statsOut
) {
    const int seedH = seedY.rows, seedW = seedY.cols;
    if (!lookBin) return std::max(1, fallback);
    if (lookBin->empty() || lookBin->rows != lookY.rows || lookBin->cols != lookY.cols ||
        lookBin->type() != CV_8UC1) {
        lookBin->create(lookY.rows, lookY.cols, CV_8UC1);
    }
    lookBin->setTo(0);
    if (seedH < 1 || seedW < 1 || lookY.empty()) return std::max(1, fallback);
    cv::Mat bin;
    double otsu = 0.0;
    bool inverted = false;
    float inkFrac = 0.f;
    if (srcIsBin) {
        seedY.copyTo(bin);
        inkFrac = (seedH * seedW) > 0
            ? cv::countNonZero(bin) / static_cast<float>(seedH * seedW) : 0.f;
        if (inkFrac >= 0.45f) {
            cv::bitwise_not(bin, bin);
            inkFrac = cv::countNonZero(bin) / static_cast<float>(std::max(1, seedH * seedW));
            inverted = true;
        }
    } else {
        otsu = cv::threshold(seedY, bin, 0, 255, cv::THRESH_BINARY_INV | cv::THRESH_OTSU);
        inkFrac = (seedH * seedW) > 0
            ? cv::countNonZero(bin) / static_cast<float>(seedH * seedW) : 0.f;
        if (inkFrac >= 0.45f) {
            cv::bitwise_not(bin, bin);
            inkFrac = cv::countNonZero(bin) / static_cast<float>(std::max(1, seedH * seedW));
            inverted = true;
        }
    }
    fillSaltPepper(&bin);
    HorizSW hh0 = horizPeakSW(bin, seedH, seedW);
    const int v0 = hh0.peak;
    const bool needFb0 = strokeNeedFb(hh0, v0, seedW, inkFrac);
    const int sPx0 = (v0 > 4 && !needFb0) ? v0 : fallback;
    cv::Mat poison;
    fillPoisonMask(bin, v0, needFb0, seedW, glareMult, &poison);
    bool bandTop = false, bandBot = false;
    int bandH = 0;
    if (!srcIsBin) orBrightBands(seedY, bin, sPx0, &poison, &bandTop, &bandBot, &bandH);
    cv::Mat keepClean(seedH, seedW, CV_8UC1);
    for (int yy = 0; yy < seedH; ++yy) {
        const uint8_t* pp = poison.ptr<uint8_t>(yy);
        uint8_t* kp = keepClean.ptr<uint8_t>(yy);
        for (int xx = 0; xx < seedW; ++xx) kp[xx] = pp[xx] ? 0 : 255;
    }
    double cleanThr = otsu;
    bool cleanDark = !inverted;
    float cleanInkFrac = 0.f;
    cv::Mat sample;
    bool haveClean = false;
    if (srcIsBin) {
        bin.copyTo(sample);
        for (int yy = 0; yy < seedH; ++yy) {
            const uint8_t* pp = poison.ptr<uint8_t>(yy);
            uint8_t* sp = sample.ptr<uint8_t>(yy);
            for (int xx = 0; xx < seedW; ++xx) if (pp[xx]) sp[xx] = 0;
        }
        haveClean = cv::countNonZero(sample) > 0;
        cleanDark = !inverted;
        cleanThr = 127.0;
    } else {
        haveClean = otsuKeep(seedY, keepClean, &cleanThr, &cleanDark, &cleanInkFrac);
        if (haveClean) {
            applyThrKeep(seedY, keepClean, cleanThr, cleanDark, &sample);
            fillSaltPepper(&sample);
        } else {
            sample = cv::Mat::zeros(seedH, seedW, CV_8UC1);
        }
    }
    HorizSW hhC = horizPeakSW(sample, seedH, seedW);
    const int v0Clean = hhC.peak;
    const bool needFbClean = !haveClean || strokeNeedFb(hhC, v0Clean, seedW, srcIsBin
        ? (cv::countNonZero(sample) / static_cast<float>(std::max(1, seedH * seedW)))
        : cleanInkFrac);
    const int sPx = (v0Clean > 4 && !needFbClean) ? v0Clean : fallback;
    cv::Mat combined = cv::Mat::zeros(seedH, seedW, CV_8UC1);
    if (v0Clean > 4) orBin(&combined, sample);
    cv::Mat labels, stats, centroids;
    const int nLab = cv::connectedComponentsWithStats(poison, labels, stats, centroids, 8);
    std::vector<PoisonReg> regs(static_cast<size_t>(std::max(0, nLab)));
    for (int i = 1; i < nLab; ++i) {
        PoisonReg r;
        r.x0 = stats.at<int>(i, cv::CC_STAT_LEFT);
        r.y0 = stats.at<int>(i, cv::CC_STAT_TOP);
        r.x1 = r.x0 + stats.at<int>(i, cv::CC_STAT_WIDTH);
        r.y1 = r.y0 + stats.at<int>(i, cv::CC_STAT_HEIGHT);
        cv::Mat keepR = cv::Mat::zeros(seedH, seedW, CV_8UC1);
        int nR = 0;
        for (int yy = 0; yy < seedH; ++yy) {
            const int* lp = labels.ptr<int>(yy);
            uint8_t* kp = keepR.ptr<uint8_t>(yy);
            for (int xx = 0; xx < seedW; ++xx) {
                if (lp[xx] == i) { kp[xx] = 255; ++nR; }
            }
        }
        if (nR < 2) { r.noPeak = true; regs[static_cast<size_t>(i)] = r; continue; }
        cv::Mat rBin;
        if (srcIsBin) {
            rBin = cv::Mat::zeros(seedH, seedW, CV_8UC1);
            for (int yy = 0; yy < seedH; ++yy) {
                const uint8_t* bp = bin.ptr<uint8_t>(yy);
                const uint8_t* kp = keepR.ptr<uint8_t>(yy);
                uint8_t* rp = rBin.ptr<uint8_t>(yy);
                for (int xx = 0; xx < seedW; ++xx) if (kp[xx] && bp[xx]) rp[xx] = 255;
            }
            fillSaltPepper(&rBin);
            HorizSW hhR = horizPeakSW(rBin, seedH, seedW);
            r.noPeak = hhR.peak <= 4;
            r.thr = 127.0;
            r.dark = !inverted;
        } else {
            double rThr = 0.0;
            bool rDark = true;
            float rFrac = 0.f;
            if (!otsuKeep(seedY, keepR, &rThr, &rDark, &rFrac)) {
                r.noPeak = true;
                regs[static_cast<size_t>(i)] = r;
                continue;
            }
            applyThrKeep(seedY, keepR, rThr, rDark, &rBin);
            fillSaltPepper(&rBin);
            HorizSW hhR = horizPeakSW(rBin, seedH, seedW);
            r.noPeak = hhR.peak <= 4;
            r.thr = rThr;
            r.dark = rDark;
        }
        r.nInk = cv::countNonZero(rBin);
        if (!r.noPeak) orBin(&combined, rBin);
        regs[static_cast<size_t>(i)] = r;
    }
    const int lh = lookY.rows, lw = lookY.cols;
    for (int y = 0; y < lh; ++y) {
        const uint8_t* yp = lookY.ptr<uint8_t>(y);
        uint8_t* op = lookBin->ptr<uint8_t>(y);
        const int sy = y - ySeed0;
        for (int x = 0; x < lw; ++x) {
            const int sx = x - xSeed0;
            if (sy >= 0 && sy < seedH && sx >= 0 && sx < seedW) {
                op[x] = combined.ptr<uint8_t>(sy)[sx];
                continue;
            }
            if (v0Clean <= 4) { op[x] = 0; continue; }
            if (srcIsBin) {
                uint8_t v = yp[x];
                if (inverted) v = static_cast<uint8_t>(255 - v);
                op[x] = v;
                continue;
            }
            const bool ink = cleanDark ? (static_cast<double>(yp[x]) <= cleanThr)
                                       : (static_cast<double>(yp[x]) > cleanThr);
            op[x] = ink ? 255 : 0;
        }
    }
    fillSaltPepper(lookBin);
    const int glareW = (glareMult > 0 ? glareMult : 11) * std::max(sPx, 4);
    const bool paintOv = overlayY && overlayY->type() == CV_8UC1 &&
        overlayY->rows > 0 && overlayY->cols > 0;
    if (paintOv) {
        const int y0 = std::max(0, ovY);
        const int y1 = std::min(overlayY->rows, ovY + lh);
        const int x0 = std::max(0, ovX);
        const int x1 = std::min(overlayY->cols, ovX + lw);
        if (y1 > y0 && x1 > x0) {
            (*overlayY)(cv::Range(y0, y1), cv::Range(x0, x1)).setTo(0);
        }
        if (overlayUv && overlayUv->type() == CV_8UC2 &&
            overlayUv->rows > 0 && overlayUv->cols > 0) {
            const int uy0 = std::max(0, ovY / 2);
            const int ux0 = std::max(0, ovX / 2);
            const int uy1 = std::min(overlayUv->rows, (ovY + lh + 1) / 2);
            const int ux1 = std::min(overlayUv->cols, (ovX + lw + 1) / 2);
            if (uy1 > uy0 && ux1 > ux0) {
                (*overlayUv)(cv::Range(uy0, uy1), cv::Range(ux0, ux1)).setTo(cv::Scalar(128, 128));
            }
        }
        for (int y = 0; y < lh; ++y) {
            const uint8_t* before = lookBin->ptr<uint8_t>(y);
            const int sy = y - ySeed0;
            for (int x = 0; x < lw; ++x) {
                const int sx = x - xSeed0;
                bool pois = false;
                if (sy >= 0 && sy < seedH && sx >= 0 && sx < seedW) {
                    pois = poison.ptr<uint8_t>(sy)[sx] != 0;
                }
                const bool inkBefore = before[x] != 0;
                uint8_t Y = 0, U = 128, V = 128;
                if (pois && !inkBefore) { Y = 19; U = 117; V = 160; }
                if (inkBefore) {
                    if (pois) { Y = 150; U = 44; V = 21; }
                    else { Y = 255; U = 128; V = 128; }
                }
                yuvPut(overlayY, overlayUv, ovX + x, ovY + y, Y, U, V);
            }
        }
    }
    dropWideRuns(lookBin, glareW);
    if (paintOv) {
        for (int y = 0; y < lh; ++y) {
            const uint8_t* after = lookBin->ptr<uint8_t>(y);
            const int sy = y - ySeed0;
            for (int x = 0; x < lw; ++x) {
                if (after[x] != 0) continue;
                const int iy = ovY + y;
                const int ix = ovX + x;
                if (iy < 0 || iy >= overlayY->rows || ix < 0 || ix >= overlayY->cols) continue;
                const uint8_t Y0 = overlayY->ptr<uint8_t>(iy)[ix];
                if (Y0 < 140) continue;
                const int sx = x - xSeed0;
                bool pois = false;
                if (sy >= 0 && sy < seedH && sx >= 0 && sx < seedW) {
                    pois = poison.ptr<uint8_t>(sy)[sx] != 0;
                }
                if (pois) yuvPut(overlayY, overlayUv, ix, iy, 9, 168, 121);
                else yuvPut(overlayY, overlayUv, ix, iy, 48, 128, 128);
            }
        }
    }
    if (statsOut) {
        statsOut->bandTop = bandTop ? 1 : 0;
        statsOut->bandBot = bandBot ? 1 : 0;
        statsOut->bandH = bandH;
        statsOut->ccs.clear();
        for (int i = 1; i < nLab; ++i) {
            const PoisonReg& r = regs[static_cast<size_t>(i)];
            PoisonCcPack c;
            c.x = r.x0;
            c.y = r.y0;
            c.w = r.x1 - r.x0;
            c.h = r.y1 - r.y0;
            c.noPeak = r.noPeak ? 1 : 0;
            c.thr = static_cast<int>(std::lround(r.thr));
            c.nInk = r.nInk;
            statsOut->ccs.push_back(c);
        }
    }
    return std::max(1, sPx);
}

/** Gray Otsu look-strip on seed T/B, x padded for jump. Full-image U8 dst. */
static bool fillGrayJumpLook(
    const cv::Mat& y, int sl, int st, int sr, int sb, int xPad, cv::Mat* dst
) {
    if (y.empty() || y.type() != CV_8UC1 || !dst) return false;
    const int h = y.rows, w = y.cols;
    if (sl < 0) sl = 0;
    if (st < 0) st = 0;
    if (sr > w) sr = w;
    if (sb > h) sb = h;
    if (sr <= sl || sb <= st) return false;
    const bool reuse = scratchFits(dst, w, h);
    if (!reuse) {
        dst->create(h, w, CV_8UC1);
    }
    dst->setTo(0);
    const int xl = std::max(0, sl - std::max(0, xPad));
    const int xr = std::min(w, sr + std::max(0, xPad));
    if (xr <= xl) return false;
    cv::Mat seedY, lookY, stripBin;
    y(cv::Range(st, sb), cv::Range(sl, sr)).copyTo(seedY);
    y(cv::Range(st, sb), cv::Range(xl, xr)).copyTo(lookY);
    const int fallback = std::max(2, static_cast<int>(std::lround(0.08f * (sb - st))));
    fillPoisonLookRaster(seedY, lookY, 0, sl - xl, false, 11, fallback, &stripBin);
    if (stripBin.empty()) return false;
    stripBin.copyTo((*dst)(cv::Rect(xl, st, xr - xl, sb - st)));
    return true;
}

/**
 * Per-seed shadow-invariant tint mask: 255 = ink, 0 = blackout.
 * Samples stroke chromaticity inside seed ink runs; background at ±s_px
 * outside those edges; classifies look-strip pixels by u_p·u_ink (or Y
 * polarity when chroma is near zero). Chromatic pixels: color2 dot≥0.50.
 * Local c²<eps² → Y contrast. Caller skips tint walk when meanChromaInk<12
 * or (chromaMode 4 and uInk·uBg ≥ kTintDotThr). Else panel-hue veto
 * (cos(u_pix, uBg) ≥ kTintDotThr is not ink); else Y-near or ink-hue.
 */
static bool fillChromaTintMask(
    const cv::Mat& y, const cv::Mat& uv,
    int sl, int st, int sr, int sb,
    cv::Mat* dst,
    int glareMult = 11,
    int xPad = 0,
    bool adaptive = false,
    Seg7Tele* tele = nullptr
) {
    if (y.empty() || y.type() != CV_8UC1 || !dst) return false;
    const int h = y.rows, w = y.cols;
    const bool reuse = scratchFits(dst, w, h);
    if (!reuse) {
        dst->create(h, w, CV_8UC1);
        dst->setTo(0);
    }
    if (sl < 0) sl = 0;
    if (st < 0) st = 0;
    if (sr > w) sr = w;
    if (sb > h) sb = h;
    if (sr <= sl || sb <= st) return false;
    cv::Mat seedBin;
    double otsuY = 0.0;
    const int sPx = seedInkBinY(y, sl, st, sr, sb, &seedBin, glareMult, &otsuY);
    if (seedBin.empty()) return false;

    float su = 0.f, sv = 0.f, yInkSum = 0.f, chromaInkSum = 0.f;
    int nInk = 0;
    const bool uvOk = !uv.empty() && uv.type() == CV_8UC2 && uv.rows > 0 && uv.cols > 0;
    const int uvH = uvOk ? uv.rows : 0, uvW = uvOk ? uv.cols : 0;
    const bool uvHalf = uvOk && uvW * 2 <= w + 1;
    for (int yy = 0; yy < seedBin.rows; ++yy) {
        const uint8_t* bp = seedBin.ptr<uint8_t>(yy);
        const uint8_t* yp = y.ptr<uint8_t>(st + yy);
        const cv::Vec2b* uvp = nullptr;
        if (uvOk) {
            int uy = uvHalf ? (st + yy) / 2 : ((st + yy) & ~1);
            if (uy < 0) uy = 0;
            if (uy >= uvH) uy = uvH - 1;
            uvp = uv.ptr<cv::Vec2b>(uy);
        }
        for (int xx = 0; xx < seedBin.cols; ++xx) {
            if (!bp[xx]) continue;
            int u = 128, v = 128;
            if (uvp) {
                int ux = uvHalf ? (sl + xx) / 2 : ((sl + xx) & ~1);
                if (ux < 0) ux = 0;
                if (ux >= uvW) ux = uvW - 1;
                u = uvp[ux][0];
                v = uvp[ux][1];
            }
            const float du = static_cast<float>(u) - 128.f;
            const float dv = static_cast<float>(v) - 128.f;
            const float n2 = du * du + dv * dv;
            if (n2 >= 1.f) {
                const float inv = 1.f / std::sqrt(n2);
                su += du * inv;
                sv += dv * inv;
                chromaInkSum += std::sqrt(n2);
            }
            yInkSum += yp[sl + xx];
            ++nInk;
        }
    }
    if (nInk <= 0) return false;
    float uInkX = su / static_cast<float>(nInk);
    float uInkY = sv / static_cast<float>(nInk);
    const float nrm2 = uInkX * uInkX + uInkY * uInkY;
    if (nrm2 > 1e-12f) {
        const float inv = 1.f / std::sqrt(nrm2);
        uInkX *= inv;
        uInkY *= inv;
    }
    const float yInk = yInkSum / static_cast<float>(nInk);
    const float meanChromaInk = chromaInkSum / static_cast<float>(nInk);
    const bool inkHasChroma = meanChromaInk >= kTintChromaEps && nrm2 > 1e-12f;

    const int d = std::max(1, sPx);
    double yBgSum = 0.0;
    float suBg = 0.f, svBg = 0.f, chromaBgSum = 0.f;
    int nBg = 0;
    auto tryBg = [&](int gx, int gy) {
        if (gx < 0 || gy < 0 || gx >= w || gy >= h) return;
        const int lx = gx - sl, ly = gy - st;
        if (lx >= 0 && ly >= 0 && lx < seedBin.cols && ly < seedBin.rows &&
            seedBin.ptr<uint8_t>(ly)[lx]) {
            return;
        }
        yBgSum += y.ptr<uint8_t>(gy)[gx];
        if (uvOk) {
            int u = 128, v = 128;
            uvAt(uv, w, gx, gy, &u, &v);
            const float du = static_cast<float>(u) - 128.f;
            const float dv = static_cast<float>(v) - 128.f;
            const float n2 = du * du + dv * dv;
            if (n2 >= 1.f) {
                const float inv = 1.f / std::sqrt(n2);
                suBg += du * inv;
                svBg += dv * inv;
                chromaBgSum += std::sqrt(n2);
            }
        }
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
    const float yBg = nBg > 0 ? static_cast<float>(yBgSum / nBg)
        : (yInk < 128.f ? 200.f : 40.f);
    float uBgX = 0.f, uBgY = 0.f;
    float meanChromaBg = 0.f;
    float nrmBg2 = 0.f;
    if (nBg > 0) {
        uBgX = suBg / static_cast<float>(nBg);
        uBgY = svBg / static_cast<float>(nBg);
        nrmBg2 = uBgX * uBgX + uBgY * uBgY;
        if (nrmBg2 > 1e-12f) {
            const float inv = 1.f / std::sqrt(nrmBg2);
            uBgX *= inv;
            uBgY *= inv;
        }
        meanChromaBg = chromaBgSum / static_cast<float>(nBg);
    }
    const bool bgHasChroma = meanChromaBg >= kTintChromaEps && nrmBg2 > 1e-12f;
    const float inkBgDot = (inkHasChroma && bgHasChroma)
        ? (uInkX * uBgX + uInkY * uBgY) : 0.f;
    const bool panelVeto = adaptive && meanChromaInk >= 12.f && bgHasChroma;
    const float dInk = yInk - yBg;
    const float eps2 = kTintChromaEps * kTintChromaEps;
    const float dotThr2 = kTintDotThr * kTintDotThr;

    const int seedH = std::max(1, sb - st);
    const int capPx = std::max(1, static_cast<int>(std::lround(2.5f * seedH)));
    const int vLook = capPx + 2;
    const int nt = std::max(0, st - vLook);
    const int nb = std::min(h, sb + vLook);
    const int xl = std::max(0, sl - std::max(0, xPad));
    const int xr = std::min(w, sr + std::max(0, xPad));
    if (reuse && xr > xl && nb > nt) {
        (*dst)(cv::Rect(xl, nt, xr - xl, nb - nt)).setTo(0);
    }
    for (int gy = nt; gy < nb; ++gy) {
        const uint8_t* yp = y.ptr<uint8_t>(gy);
        uint8_t* op = dst->ptr<uint8_t>(gy);
        const cv::Vec2b* uvp = nullptr;
        if (uvOk) {
            int uy = uvHalf ? gy / 2 : (gy & ~1);
            if (uy < 0) uy = 0;
            if (uy >= uvH) uy = uvH - 1;
            uvp = uv.ptr<cv::Vec2b>(uy);
        }
        for (int gx = xl; gx < xr; ++gx) {
            const float Y = static_cast<float>(yp[gx]);
            int u = 128, v = 128;
            if (uvp) {
                int ux = uvHalf ? gx / 2 : (gx & ~1);
                if (ux < 0) ux = 0;
                if (ux >= uvW) ux = uvW - 1;
                u = uvp[ux][0];
                v = uvp[ux][1];
            }
            const float du = static_cast<float>(u) - 128.f;
            const float dv = static_cast<float>(v) - 128.f;
            const float c2 = du * du + dv * dv;
            const float dPix = Y - yBg;
            const bool polOk = dInk * dPix >= 0.f;
            const bool nearY = std::fabs(Y - yInk) <= std::fabs(Y - yBg);
            const float leftInk = du * uInkX + dv * uInkY;
            const bool inkHue = c2 >= eps2 && leftInk > 0.f &&
                leftInk * leftInk >= dotThr2 * c2;
            bool isInk;
            if (panelVeto) {
                const float leftBg = du * uBgX + dv * uBgY;
                const bool bgHue = c2 >= eps2 && leftBg > 0.f &&
                    leftBg * leftBg >= dotThr2 * c2;
                isInk = polOk && !bgHue && (nearY || inkHue);
            } else if (!inkHasChroma || c2 < eps2) {
                isInk = polOk && nearY;
            } else {
                isInk = polOk && inkHue;
            }
            op[gx] = isInk ? 255 : 0;
        }
    }
    if (tele) {
        tele->yInk = yInk;
        tele->yBg = yBg;
        tele->dInk = dInk;
        tele->meanChroma = meanChromaInk;
        tele->uInkX = uInkX;
        tele->uInkY = uInkY;
        tele->inkBgDot = inkBgDot;
        tele->sPx = static_cast<float>(sPx);
        tele->otsuThr = static_cast<float>(otsuY);
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
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jintArray seedsArr, jint chromaMode,
    jfloat gapFrac, jfloat minSeedHsToFreeze,
    jint boundStrategy, jint tightInsetPx,
    jfloatArray teleArr, jintArray sweepArr, jlong dumpPtr,
    jlong overlayYPtr, jlong overlayUvPtr, jintArray poisonArr
) {
    auto* gray = reinterpret_cast<cv::Mat*>(grayPtr);
    if (!gray || gray->empty() || gray->type() != CV_8UC1 || !seedsArr) return nullptr;
    const int imgW = gray->cols, imgH = gray->rows;
    const jint n4 = env->GetArrayLength(seedsArr);
    if (n4 <= 0 || n4 % 4 != 0) return env->NewIntArray(0);
    const int n = n4 / 4;
    std::vector<jint> seeds(n4);
    env->GetIntArrayRegion(seedsArr, 0, n4, seeds.data());
    // chromaMode: 0 gray, 1 chromaMag, 2/3 tint, 4 color_adaptive hybrid
    cv::Mat localMag;
    cv::Mat* cMag = nullptr;
    const bool useChromaMag = chromaMode == 1;
    const bool useTint = chromaMode == 2 || chromaMode == 3 || chromaMode == 4;
    const bool adaptive = chromaMode == 4;
    const int glareMult = 11;
    auto* uv = reinterpret_cast<cv::Mat*>(uvPtr);
    auto* scratch = reinterpret_cast<cv::Mat*>(scratchPtr);
    auto* inkDump = reinterpret_cast<cv::Mat*>(dumpPtr);
    auto* overlayY = reinterpret_cast<cv::Mat*>(overlayYPtr);
    auto* overlayUv = reinterpret_cast<cv::Mat*>(overlayUvPtr);
    if (inkDump && scratchFits(inkDump, imgW, imgH)) inkDump->setTo(0);
    std::vector<PoisonStats> poisonPacks;
    poisonPacks.resize(static_cast<size_t>(n));
    if (useChromaMag) {
        if (scratchFits(scratch, imgW, imgH)) {
            fillChromaMag(*gray, uv ? *uv : cv::Mat(), scratch);
            cMag = scratch;
        } else {
            fillChromaMag(*gray, uv ? *uv : cv::Mat(), &localMag);
            cMag = &localMag;
        }
    }
    std::vector<jint> out(n * 8, 0);
    std::vector<InkSweepPack> sweeps;
    sweeps.resize(static_cast<size_t>(n));
    for (int i = 0; i < n; ++i) {
        int l = seeds[i * 4], t = seeds[i * 4 + 1], r = seeds[i * 4 + 2], b = seeds[i * 4 + 3];
        if (l < 0) l = 0;
        if (t < 0) t = 0;
        if (r > imgW) r = imgW;
        if (b > imgH) b = imgH;
        int ol, ot, orr, ob, sPx, vSW, hSW, fb;
        Seg7Tele tele{};
        tele.method = adaptive ? 4.f : (useChromaMag ? 1.f : 0.f);
        if (useTint) {
            cv::Mat localTint;
            cv::Mat* tintDst = scratchFits(scratch, imgW, imgH) ? scratch : &localTint;
            const bool ok = uv && fillChromaTintMask(
                *gray, *uv, l, t, r, b, tintDst, glareMult, 0, adaptive, &tele);
            if (ok && tintDst && !tintDst->empty() &&
                !skipTintWalk(adaptive, tele)) {
                seg7One(*tintDst, l, t, r, b, imgW, imgH,
                    &ol, &ot, &orr, &ob, &sPx, &vSW, &hSW, &fb, true,
                    gapFrac, minSeedHsToFreeze, glareMult,
                    boundStrategy, tightInsetPx, &tele, true,
                    &sweeps[static_cast<size_t>(i)], inkDump, overlayY, overlayUv,
                    &poisonPacks[static_cast<size_t>(i)]);
            } else {
                if (ok && skipTintWalk(adaptive, tele)) tele.method = 0.f;
                seg7One(*gray, l, t, r, b, imgW, imgH,
                    &ol, &ot, &orr, &ob, &sPx, &vSW, &hSW, &fb, false,
                    gapFrac, minSeedHsToFreeze, 11,
                    boundStrategy, tightInsetPx, &tele, ok && adaptive,
                    &sweeps[static_cast<size_t>(i)], inkDump, overlayY, overlayUv,
                    &poisonPacks[static_cast<size_t>(i)]);
            }
        } else {
            const cv::Mat* src = gray;
            if (useChromaMag && cMag && !cMag->empty() &&
                medianU8Rect(*cMag, l, t, r, b) >= 8.0) {
                src = cMag;
            }
            seg7One(*src, l, t, r, b, imgW, imgH, &ol, &ot, &orr, &ob, &sPx, &vSW, &hSW, &fb,
                false, gapFrac, minSeedHsToFreeze, 11,
                boundStrategy, tightInsetPx, &tele, false,
                &sweeps[static_cast<size_t>(i)], inkDump, overlayY, overlayUv,
                &poisonPacks[static_cast<size_t>(i)]);
        }
        const int o = i * 8;
        out[o] = ol; out[o + 1] = ot; out[o + 2] = orr; out[o + 3] = ob;
        out[o + 4] = sPx; out[o + 5] = vSW; out[o + 6] = hSW; out[o + 7] = fb;
        storeTeleArr(env, teleArr, i, tele);
    }
    writeSweepArr(env, sweepArr, sweeps);
    writePoisonArr(env, poisonArr, poisonPacks);
    jintArray arr = env->NewIntArray(static_cast<jint>(out.size()));
    if (!arr) return nullptr;
    env->SetIntArrayRegion(arr, 0, static_cast<jint>(out.size()), out.data());
    return arr;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeJumpMany(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jintArray boxesArr, jint chromaMode,
    jfloat maxFrac, jfloat energyRatio, jfloat jumpFrac, jfloat retractClearFrac,
    jintArray seedHArr, jintArray seedRectArr, jintArray sPxArr
) {
    try {
    auto* gray = reinterpret_cast<cv::Mat*>(grayPtr);
    if (!gray || gray->empty() || gray->type() != CV_8UC1 || !boxesArr) return nullptr;
    const int imgW = gray->cols, imgH = gray->rows;
    const jint n4 = env->GetArrayLength(boxesArr);
    if (n4 <= 0 || n4 % 4 != 0) return env->NewIntArray(0);
    const int n = n4 / 4;
    std::vector<jint> boxes(n4);
    env->GetIntArrayRegion(boxesArr, 0, n4, boxes.data());
    auto* uv = reinterpret_cast<cv::Mat*>(uvPtr);
    auto* scratch = reinterpret_cast<cv::Mat*>(scratchPtr);
    cv::Mat gx, gy, magY;
    cv::Sobel(*gray, gx, CV_32F, 1, 0, 3);
    cv::Sobel(*gray, gy, CV_32F, 0, 1, 3);
    cv::magnitude(gx, gy, magY);
    gx.release();
    gy.release();
    cv::Mat localMag;
    cv::Mat* cMag = nullptr;
    const bool useChromaMag = chromaMode == 1;
    const bool useTint = chromaMode == 2 || chromaMode == 3 || chromaMode == 4;
    const bool adaptive = chromaMode == 4;
    if (useChromaMag) {
        if (scratchFits(scratch, imgW, imgH)) {
            fillChromaMag(*gray, uv ? *uv : cv::Mat(), scratch);
            cMag = scratch;
        } else {
            fillChromaMag(*gray, uv ? *uv : cv::Mat(), &localMag);
            cMag = &localMag;
        }
    }
    std::vector<jint> out(n * 4, 0);
    for (int i = 0; i < n; ++i) {
        int l = boxes[i * 4], t = boxes[i * 4 + 1], r = boxes[i * 4 + 2], b = boxes[i * 4 + 3];
        if (l < 0) l = 0;
        if (t < 0) t = 0;
        if (r > imgW) r = imgW;
        if (b > imgH) b = imgH;
        if (r <= l) r = std::min(imgW, l + 1);
        if (b <= t) b = std::min(imgH, t + 1);
        const int hgt = std::max(1, b - t);
        const int jx = std::max(1, static_cast<int>(std::lround(jumpFrac * hgt)));
        int ssl = l, sst = t, ssr = r, ssb = b;
        int sPx = 0;
        if (seedRectArr && env->GetArrayLength(seedRectArr) >= (i + 1) * 4) {
            jint sr4[4] = {};
            env->GetIntArrayRegion(seedRectArr, i * 4, 4, sr4);
            ssl = sr4[0]; sst = sr4[1]; ssr = sr4[2]; ssb = sr4[3];
        }
        if (ssl < 0) ssl = 0;
        if (sst < 0) sst = 0;
        if (ssr > imgW) ssr = imgW;
        if (ssb > imgH) ssb = imgH;
        if (sPxArr && env->GetArrayLength(sPxArr) >= (i + 1)) {
            jint sp = 0;
            env->GetIntArrayRegion(sPxArr, i, 1, &sp);
            sPx = sp;
        }
        const bool inkTest = sPx > 0 && ssb > sst + 1;
        const int xPad = jx * (kJumpMax + 1);
        const cv::Mat* eng = &magY;
        cv::Mat localTint;
        const cv::Mat* lookPtr = nullptr;
        int minRun = 0;
        if (inkTest) {
            cv::Mat* tintDst = scratchFits(scratch, imgW, imgH) ? scratch : &localTint;
            if (useTint && uv) {
                Seg7Tele ttele{};
                const bool tintOk = fillChromaTintMask(
                    *gray, *uv, ssl, sst, ssr, ssb, tintDst, 11, xPad,
                    adaptive, &ttele) && tintDst && !tintDst->empty();
                if (tintOk && !skipTintWalk(adaptive, ttele)) {
                    lookPtr = tintDst;
                } else if (tintOk && skipTintWalk(adaptive, ttele) &&
                    fillGrayJumpLook(*gray, ssl, sst, ssr, ssb, xPad, tintDst) &&
                    tintDst && !tintDst->empty()) {
                    lookPtr = tintDst;
                }
            } else if (fillGrayJumpLook(*gray, ssl, sst, ssr, ssb, xPad, tintDst) &&
                       tintDst && !tintDst->empty()) {
                lookPtr = tintDst;
            }
            const int maxIn = lookPtr
                ? maxInSeedRunRows(*lookPtr, sst, ssb, ssl, ssr) : 0;
            minRun = usedMinRun(sPx, maxIn);
        } else if (useTint && uv) {
            cv::Mat* tintDst = scratchFits(scratch, imgW, imgH) ? scratch : &localTint;
            Seg7Tele ttele{};
            if (fillChromaTintMask(*gray, *uv, l, t, r, b, tintDst, 11, xPad,
                    adaptive, &ttele) &&
                tintDst && !tintDst->empty() &&
                !skipTintWalk(adaptive, ttele)) {
                eng = tintDst;
            }
        } else if (useChromaMag && cMag && !cMag->empty() &&
                   medianU8Rect(*cMag, l, t, r, b) >= 8.0) {
            eng = cMag;
        }
        const int il = l + 2, it = t + 2, ir = r - 2, ib = b - 2;
        const double base = (ir > il && ib > it)
            ? meanRectF(*eng, il, it, ir, ib, imgW, imgH)
            : meanRectF(*eng, l, t, r, b, imgW, imgH);
        const double thr = energyRatio * std::max(base, 1e-3);
        const int cap = std::max(1, static_cast<int>(std::lround(maxFrac * hgt)));
        int seedH = hgt;
        if (seedHArr && env->GetArrayLength(seedHArr) >= (i + 1)) {
            jint sh = 0;
            env->GetIntArrayRegion(seedHArr, i, 1, &sh);
            if (sh > 0) seedH = sh;
        }
        jumpRetractH(*eng, &l, t, &r, b, imgW, imgH, thr, cap, jumpFrac, retractClearFrac,
            seedH, lookPtr, sst, ssb, minRun);
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
    magY.release();
    jintArray arr = env->NewIntArray(static_cast<jint>(out.size()));
    if (!arr) return nullptr;
    env->SetIntArrayRegion(arr, 0, static_cast<jint>(out.size()), out.data());
    return arr;
    } catch (const cv::Exception&) {
        return nullptr;
    }
}

namespace {

struct OriBox {
    float cx, cy, ux, uy, vx, vy, u0, u1, v0, v1;
};

static bool oriFromQuad(const float* p, OriBox* b) {
    // u = edge closest to horizontal (|atan2| folded to [0, 90°]); tie → longer.
    // v = +90° from u, flipped so vy ≥ 0 (v1 = lower flatter side).
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
    b->cx = cx; b->cy = cy; b->ux = ux; b->uy = uy;
    b->vx = vx; b->vy = vy;
    b->u0 = u0; b->u1 = u1; b->v0 = v0; b->v1 = v1;
    return true;
}

static void oriExtentsInFrame(const OriBox& frame, const float* p,
    float* u0, float* u1, float* v0, float* v1) {
    float a0 = 1e30f, a1 = -1e30f, b0 = 1e30f, b1 = -1e30f;
    for (int i = 0; i < 4; ++i) {
        const float dx = p[i * 2] - frame.cx;
        const float dy = p[i * 2 + 1] - frame.cy;
        const float u = dx * frame.ux + dy * frame.uy;
        const float v = dx * frame.vx + dy * frame.vy;
        if (u < a0) a0 = u;
        if (u > a1) a1 = u;
        if (v < b0) b0 = v;
        if (v > b1) b1 = v;
    }
    *u0 = a0; *u1 = a1; *v0 = b0; *v1 = b1;
}

static void oriToQuad(const OriBox& b, float* out) {
    auto c = [&](float u, float v, int i) {
        out[i] = b.cx + u * b.ux + v * b.vx;
        out[i + 1] = b.cy + u * b.uy + v * b.vy;
    };
    c(b.u0, b.v0, 0);
    c(b.u1, b.v0, 2);
    c(b.u1, b.v1, 4);
    c(b.u0, b.v1, 6);
}

static inline bool inImgF(float px, float py, int w, int h) {
    return px >= 0.f && py >= 0.f && px < static_cast<float>(w) &&
        py < static_cast<float>(h);
}

static int sampleU8Trunc(const cv::Mat& m, float px, float py, int w, int h) {
    if (!inImgF(px, py, w, h) || m.empty() || m.type() != CV_8UC1) return -1;
    int x = static_cast<int>(px);
    int y = static_cast<int>(py);
    if (x < 0) x = 0;
    if (y < 0) y = 0;
    if (x >= w) x = w - 1;
    if (y >= h) y = h - 1;
    return m.ptr<uint8_t>(y)[x];
}

static float sampleF32Trunc(const cv::Mat& m, float px, float py, int w, int h) {
    if (!inImgF(px, py, w, h) || m.empty()) return -1.f;
    int x = static_cast<int>(px);
    int y = static_cast<int>(py);
    if (x < 0) x = 0;
    if (y < 0) y = 0;
    if (x >= w) x = w - 1;
    if (y >= h) y = h - 1;
    if (m.type() == CV_8UC1) return static_cast<float>(m.ptr<uint8_t>(y)[x]);
    if (m.type() != CV_32F) return -1.f;
    return m.ptr<float>(y)[x];
}

static double medianInteriorU8(const cv::Mat& m, const OriBox& b, int w, int h) {
    const int wu = std::max(4, static_cast<int>(std::lround(b.u1 - b.u0)));
    const int hv = std::max(4, static_cast<int>(std::lround(b.v1 - b.v0)));
    std::vector<int> vals;
    vals.reserve(static_cast<size_t>(wu * hv));
    for (int y = 0; y < hv; ++y) {
        const float v = b.v0 + (y + 0.5f) / hv * (b.v1 - b.v0);
        for (int x = 0; x < wu; ++x) {
            const float u = b.u0 + (x + 0.5f) / wu * (b.u1 - b.u0);
            const float px = b.cx + u * b.ux + v * b.vx;
            const float py = b.cy + u * b.uy + v * b.vy;
            const int g = sampleU8Trunc(m, px, py, w, h);
            if (g >= 0) vals.push_back(g);
        }
    }
    if (vals.empty()) return 0.0;
    std::sort(vals.begin(), vals.end());
    const int n = static_cast<int>(vals.size());
    if (n % 2 == 1) return vals[n / 2];
    return 0.5 * (vals[n / 2 - 1] + vals[n / 2]);
}

static void fillOrientedLookSweep(
    const cv::Mat& src, bool srcIsBin, double otsu, bool dark, bool invertedBin,
    int glareW, const cv::Mat& lookBin, float lookV0,
    const OriBox& seed, float walkedV0, float walkedV1,
    int imgW, int imgH, int minRun, float sPx,
    InkSweepPack* out
) {
    if (!out) return;
    const float seedBh = std::max(1.f, seed.v1 - seed.v0);
    const int capPx = std::max(1, static_cast<int>(std::lround(2.5f * seedBh)));
    const int walkedH = std::max(1, static_cast<int>(std::lround(walkedV1 - walkedV0)));
    const int xPad = std::max(1, static_cast<int>(std::lround(0.40f * walkedH * (kJumpMax + 1))));
    const int vStart = static_cast<int>(std::lround(seed.v0)) - capPx;
    const int vEnd = static_cast<int>(std::lround(seed.v1)) + capPx;
    const int uStart = static_cast<int>(std::lround(seed.u0)) - xPad;
    const int uEnd = static_cast<int>(std::lround(seed.u1)) + xPad;
    if (vEnd <= vStart) return;
    out->thr = static_cast<float>(minRun);
    out->sPx = sPx;
    out->energyRatio = 0.f;
    out->minRun = minRun;
    out->vOrigin = vStart;
    out->hOrigin = uStart;
    out->v0 = static_cast<int>(std::lround(seed.v0)) - vStart;
    out->v1 = static_cast<int>(std::lround(seed.v1)) - vStart;
    out->h0 = static_cast<int>(std::lround(seed.u0)) - uStart;
    out->h1 = static_cast<int>(std::lround(seed.u1)) - uStart;
    out->vScores.reserve(static_cast<size_t>(vEnd - vStart));
    for (int v = vStart; v < vEnd; ++v) {
        const int y = static_cast<int>(std::lround(static_cast<float>(v) - lookV0));
        int sc = 0;
        if (!lookBin.empty() && y >= 0 && y < lookBin.rows) {
            sc = maxInkRunRow(lookBin, y, 0, lookBin.cols);
        }
        out->vScores.push_back(sc);
    }
    packSeedLookRows(
        lookBin,
        static_cast<int>(std::lround(seed.v0 - lookV0)),
        static_cast<int>(std::lround(seed.v1 - lookV0)),
        out);
    if (uEnd <= uStart) return;
    const int wu = std::max(1, uEnd - uStart);
    const int hv = std::max(1, static_cast<int>(std::lround(seed.v1 - seed.v0)));
    cv::Mat wide(hv, wu, CV_8UC1);
    for (int y = 0; y < hv; ++y) {
        const float v = seed.v0 + (y + 0.5f) / hv * (seed.v1 - seed.v0);
        uint8_t* row = wide.ptr<uint8_t>(y);
        for (int x = 0; x < wu; ++x) {
            const float u = static_cast<float>(uStart) + x + 0.5f;
            const float px = seed.cx + u * seed.ux + v * seed.vx;
            const float py = seed.cy + u * seed.uy + v * seed.vy;
            const int g = sampleU8Trunc(src, px, py, imgW, imgH);
            row[x] = static_cast<uint8_t>(g >= 0 ? g : 0);
        }
    }
    cv::Mat wideBin;
    if (srcIsBin) {
        wide.copyTo(wideBin);
        if (invertedBin) cv::bitwise_not(wideBin, wideBin);
    } else {
        const int ttype = dark ? cv::THRESH_BINARY_INV : cv::THRESH_BINARY;
        cv::threshold(wide, wideBin, otsu, 255, ttype);
    }
    if (!wideBin.empty() && glareW > 0) dropWide(&wideBin, glareW);
    out->hScores.reserve(static_cast<size_t>(wu));
    for (int x = 0; x < wu; ++x) {
        int sc = 0;
        if (!wideBin.empty()) sc = maxInkRunCol(wideBin, x, 0, wideBin.rows);
        out->hScores.push_back(sc);
    }
}

static void seg7OrientedOne(
    const cv::Mat& src, OriBox seed, int imgW, int imgH,
    float* outPts8, float* sPxOut,
    int boundStrategy = 0, int tightInsetPx = 16,
    bool srcIsBin = false,
    Seg7Tele* tele = nullptr,
    bool keepColorStats = false,
    InkSweepPack* sweepOut = nullptr,
    cv::Mat* inkDump = nullptr,
    cv::Mat* overlayY = nullptr,
    cv::Mat* overlayUv = nullptr,
    PoisonStats* poisonStats = nullptr
) {
    if (boundStrategy == 1) {
        const float ins = static_cast<float>(std::max(1, tightInsetPx));
        if (seed.u1 - seed.u0 > 2.f * ins + 2.f) {
            seed.u0 += ins; seed.u1 -= ins;
        }
        if (seed.v1 - seed.v0 > 2.f * ins + 2.f) {
            seed.v0 += ins; seed.v1 -= ins;
        }
    }
    oriToQuad(seed, outPts8);
    const float seedBh = std::max(1.f, seed.v1 - seed.v0);
    const float seedBw = std::max(1.f, seed.u1 - seed.u0);
    const int fallback = std::max(2, static_cast<int>(std::lround(0.08f * seedBh)));
    *sPxOut = static_cast<float>(fallback);
    if (src.empty() || src.type() != CV_8UC1) return;
    if (seedBh < 4.f || seedBw < 4.f) return;
    const int wu = std::max(1, static_cast<int>(std::lround(seed.u1 - seed.u0)));
    const int hv = std::max(1, static_cast<int>(std::lround(seed.v1 - seed.v0)));
    cv::Mat seedMat(hv, wu, CV_8UC1);
    for (int y = 0; y < hv; ++y) {
        const float v = seed.v0 + (y + 0.5f) / hv * (seed.v1 - seed.v0);
        uint8_t* row = seedMat.ptr<uint8_t>(y);
        for (int x = 0; x < wu; ++x) {
            const float u = seed.u0 + (x + 0.5f) / wu * (seed.u1 - seed.u0);
            const float px = seed.cx + u * seed.ux + v * seed.vx;
            const float py = seed.cy + u * seed.uy + v * seed.vy;
            const int g = sampleU8Trunc(src, px, py, imgW, imgH);
            row[x] = static_cast<uint8_t>(g >= 0 ? g : 0);
        }
    }
    const float cap = 2.5f * seedBh;
    const int vLook = std::max(1, static_cast<int>(std::lround(cap)) + 2);
    const float lookV0 = seed.v0 - static_cast<float>(vLook);
    const float lookV1 = seed.v1 + static_cast<float>(vLook);
    const int lookH = std::max(1, static_cast<int>(std::lround(lookV1 - lookV0)));
    cv::Mat look(lookH, wu, CV_8UC1);
    for (int y = 0; y < lookH; ++y) {
        const float v = lookV0 + (y + 0.5f);
        uint8_t* row = look.ptr<uint8_t>(y);
        for (int x = 0; x < wu; ++x) {
            const float u = seed.u0 + (x + 0.5f) / wu * (seed.u1 - seed.u0);
            const float px = seed.cx + u * seed.ux + v * seed.vx;
            const float py = seed.cy + u * seed.uy + v * seed.vy;
            const int g = sampleU8Trunc(src, px, py, imgW, imgH);
            row[x] = static_cast<uint8_t>(g >= 0 ? g : 0);
        }
    }
    cv::Mat seedY;
    seedMat.copyTo(seedY);
    cv::Mat lookBin;
    const int ySeed0 = static_cast<int>(std::lround(seed.v0 - lookV0));
    const int ySeed1 = static_cast<int>(std::lround(seed.v1 - lookV0));
    float ptsDump[8];
    oriToQuad(seed, ptsDump);
    float minx = ptsDump[0];
    for (int k = 1; k < 4; ++k) minx = std::min(minx, ptsDump[k * 2]);
    const int x0 = std::max(0, static_cast<int>(std::floor(minx)));
    const int y0 = std::max(0, static_cast<int>(std::floor(lookV0)));
    PoisonStats stLocal;
    const int sPx = fillPoisonLookRaster(
        seedY, look, ySeed0, 0, srcIsBin, 11, fallback, &lookBin,
        overlayY, overlayUv, x0, y0, poisonStats ? &stLocal : nullptr);
    if (poisonStats) *poisonStats = stLocal;
    if (inkDump && !lookBin.empty() && inkDump->type() == CV_8UC1 &&
        inkDump->rows >= imgH && inkDump->cols >= imgW) {
        const int x1 = std::min(imgW, x0 + lookBin.cols);
        const int y1 = std::min(imgH, y0 + lookBin.rows);
        if (x1 > x0 && y1 > y0) {
            cv::Mat srcR = lookBin(cv::Rect(0, 0, x1 - x0, y1 - y0));
            cv::Mat dstR = (*inkDump)(cv::Rect(x0, y0, x1 - x0, y1 - y0));
            srcR.copyTo(dstR);
        }
    }
    const int glareW = 11 * std::max(sPx, 4);
    *sPxOut = static_cast<float>(std::max(1, sPx));
    const int gapStop = std::max(1, static_cast<int>(std::lround(0.5f * sPx)));
    const int minRun = usedMinRun(sPx, maxInSeedRunRows(lookBin, ySeed0, ySeed1, 0, lookBin.cols));
    auto hasBar = [&](float v) {
        const int y = static_cast<int>(std::lround(v - lookV0));
        if (y < 0 || y >= lookBin.rows) return false;
        return rowHasStrokeBar(lookBin, y, minRun, glareW);
    };
    float v0 = seed.v0, v1 = seed.v1;
    const float maxRetractPx = static_cast<float>(
        std::max(1, static_cast<int>(std::lround(kVertRetractCapFrac * seedBh))));
    int fTop = kFlagUnchanged, fBot = kFlagUnchanged;
    int gapJumpTop = 0, gapJumpBot = 0;
    float landTop = 0.f, landBot = 0.f;
    auto expandNegOneShot = [&]() {
        bool usedGap = false;
        while (seed.v0 - (v0 - 1.f) <= cap && hasBar(v0 - 1.f)) v0 -= 1.f;
        if (v0 < seed.v0) fTop = kFlagNormalExpand;
        if (!usedGap && seed.v0 - (v0 - 1.f) <= cap && !hasBar(v0 - 1.f)) {
            float v = v0 - 1.f;
            int n = 0;
            while (n < gapStop && seed.v0 - v <= cap) {
                if (hasBar(v)) {
                    v0 = v;
                    usedGap = true;
                    gapJumpTop = 1;
                    landTop = v;
                    fTop = kFlagNormalExpand;
                    while (seed.v0 - (v0 - 1.f) <= cap && hasBar(v0 - 1.f)) v0 -= 1.f;
                    break;
                }
                v -= 1.f;
                ++n;
            }
        }
        if (fTop == kFlagUnchanged) fTop = kFlagBlockedGap;
    };
    auto expandPosOneShot = [&]() {
        bool usedGap = false;
        while (v1 - seed.v1 < cap && hasBar(v1)) v1 += 1.f;
        if (v1 > seed.v1) fBot = kFlagNormalExpand;
        if (!usedGap && v1 - seed.v1 < cap && !hasBar(v1)) {
            float v = v1;
            int n = 0;
            while (n < gapStop && v - seed.v1 <= cap) {
                if (hasBar(v)) {
                    v1 = v + 1.f;
                    usedGap = true;
                    gapJumpBot = 1;
                    landBot = v;
                    fBot = kFlagNormalExpand;
                    while (v1 - seed.v1 < cap && hasBar(v1)) v1 += 1.f;
                    break;
                }
                v += 1.f;
                ++n;
            }
        }
        if (fBot == kFlagUnchanged) fBot = kFlagBlockedGap;
    };
    if (boundStrategy == 2) {
        if (hasBar(v0)) {
            expandNegOneShot();
        } else {
            while (v0 < v1 - 1.f && (v0 - seed.v0) < maxRetractPx && !hasBar(v0)) v0 += 1.f;
            if (v0 > seed.v0 && hasBar(v0)) fTop = kFlagNormalRetract;
            else if (v0 - seed.v0 >= maxRetractPx) fTop = kFlagBlocked10pct;
            else fTop = kFlagNormalRetract;
        }
        if (hasBar(v1 - 1.f) || hasBar(v1)) {
            expandPosOneShot();
        } else {
            while (v1 > v0 + 1.f && (seed.v1 - v1) < maxRetractPx && !hasBar(v1 - 1.f)) v1 -= 1.f;
            if (v1 < seed.v1 && hasBar(v1 - 1.f)) fBot = kFlagNormalRetract;
            else if (seed.v1 - v1 >= maxRetractPx) fBot = kFlagBlocked10pct;
            else fBot = kFlagNormalRetract;
        }
    } else {
        expandNegOneShot();
        expandPosOneShot();
    }
    if (v1 < v0 + 2.f) v1 = v0 + 2.f;
    if (tele) {
        if (!keepColorStats) {
            float yi = 0.f, yb = 0.f;
            int ni = 0, nbg = 0;
            for (int y = 0; y < lookBin.rows; ++y) {
                const uint8_t* bp = lookBin.ptr<uint8_t>(y);
                const uint8_t* lp = look.ptr<uint8_t>(y);
                for (int x = 0; x < lookBin.cols; ++x) {
                    if (bp[x]) { yi += lp[x]; ++ni; }
                    else { yb += lp[x]; ++nbg; }
                }
            }
            tele->yInk = ni > 0 ? yi / static_cast<float>(ni) : 0.f;
            tele->yBg = nbg > 0 ? yb / static_cast<float>(nbg) : 0.f;
            tele->dInk = tele->yInk - tele->yBg;
        }
        tele->otsuThr = 0.f;
        tele->sPx = *sPxOut;
        tele->dTop = v0 - seed.v0;
        tele->dBot = v1 - seed.v1;
        tele->dLeft = 0.f;
        tele->dRight = 0.f;
        tele->fTop = static_cast<float>(fTop);
        tele->fBot = static_cast<float>(fBot);
        tele->fLeft = static_cast<float>(kFlagUnchanged);
        tele->fRight = static_cast<float>(kFlagUnchanged);
        tele->gapJumpTop = gapJumpTop ? 1.f : 0.f;
        tele->gapJumpBot = gapJumpBot ? 1.f : 0.f;
        tele->landTop = gapJumpTop ? landTop : 0.f;
        tele->landBot = gapJumpBot ? landBot : 0.f;
        fillRunHists(lookBin, tele->histH, tele->histV);
    }
    if (sweepOut) {
        try {
            OriBox seedSweep = seed;
            fillOrientedLookSweep(
                src, true, 0.0, true, false, 0, lookBin, lookV0,
                seedSweep, v0, v1, imgW, imgH, minRun, *sPxOut, sweepOut);
        } catch (const cv::Exception&) {
        }
    }
    seed.v0 = v0;
    seed.v1 = v1;
    oriToQuad(seed, outPts8);
}

static double meanUFace(
    const cv::Mat& mag, const OriBox& box, float u, int imgW, int imgH,
    float v0, float v1
) {
    const float vLo = v0;
    const float vHi = v1 > v0 + 1.f ? v1 : v0 + 1.f;
    const int n = std::max(4, static_cast<int>(std::lround(vHi - vLo)));
    double s = 0.0;
    int c = 0;
    for (int i = 0; i < n; ++i) {
        const float v = vLo + (i + 0.5f) / n * (vHi - vLo);
        const float px = box.cx + u * box.ux + v * box.vx;
        const float py = box.cy + u * box.uy + v * box.vy;
        const float g = sampleF32Trunc(mag, px, py, imgW, imgH);
        if (g < 0.f) continue;
        s += g;
        ++c;
    }
    return c > 0 ? s / c : 0.0;
}

static double meanUFace(
    const cv::Mat& mag, const OriBox& box, float u, int imgW, int imgH
) {
    return meanUFace(mag, box, u, imgW, imgH, box.v0, box.v1);
}

static void jumpOrientedOne(
    const cv::Mat& mag, OriBox* box, int imgW, int imgH,
    float maxFrac, float energyRatio, float jumpFrac, float retractClearFrac,
    float seedBh = 0.f,
    const cv::Mat* lookBin = nullptr, float seedV0 = 0.f, float seedV1 = 0.f, int minRun = 0
) {
    (void)maxFrac;
    (void)retractClearFrac;
    const float vSpan = std::max(1.f, box->v1 - box->v0);
    const float coreH = seedBh > 0.f ? seedBh : vSpan;
    const float vMid = 0.5f * (box->v0 + box->v1);
    float cv0 = std::max(box->v0, vMid - 0.5f * coreH);
    float cv1 = std::min(box->v1, vMid + 0.5f * coreH);
    if (cv1 < cv0 + 1.f) {
        cv0 = box->v0;
        cv1 = box->v1;
    }
    float sv0 = seedV0;
    float sv1 = seedV1;
    if (sv1 < sv0 + 1.f) {
        sv0 = box->v0;
        sv1 = box->v1;
    }
    const bool useInk = lookBin && !lookBin->empty() && lookBin->type() == CV_8UC1 && minRun > 0;
    auto faceHas = [&](float u) -> bool {
        const int n = std::max(4, static_cast<int>(std::lround(sv1 - sv0)));
        int best = 0, run = 0;
        for (int i = 0; i < n; ++i) {
            const float v = sv0 + (i + 0.5f) / n * (sv1 - sv0);
            const float px = box->cx + u * box->ux + v * box->vx;
            const float py = box->cy + u * box->uy + v * box->vy;
            const int g = sampleU8Trunc(*lookBin, px, py, imgW, imgH);
            if (g > 0) {
                ++run;
                if (run > best) best = run;
            } else {
                run = 0;
            }
        }
        return best >= minRun;
    };
    auto face = [&](float u) {
        return meanUFace(mag, *box, u, imgW, imgH, cv0, cv1);
    };
    const float du = 2.f;
    const float uA = box->u0 + du;
    const float uB = box->u1 - du;
    double base;
    if (uB > uA) {
        double s = 0.0;
        int c = 0;
        const int nu = std::max(4, static_cast<int>(std::lround(uB - uA)));
        for (int i = 0; i < nu; ++i) {
            s += face(uA + (i + 0.5f) / nu * (uB - uA));
            ++c;
        }
        base = c > 0 ? s / c : face((box->u0 + box->u1) * 0.5f);
    } else {
        base = face((box->u0 + box->u1) * 0.5f);
    }
    const double thr = energyRatio * std::max(base, 1e-3);
    auto hit = [&](float u) -> bool {
        return useInk ? faceHas(u) : face(u) >= thr;
    };
    const float jx = static_cast<float>(
        std::max(1, static_cast<int>(std::lround(jumpFrac * vSpan))));
    float u0 = box->u0;
    float u1 = box->u1;
    int jumps0 = 0;
    while (jumps0 < kJumpMax) {
        const float next0 = u0 - jx;
        if (hit(next0)) {
            u0 = next0;
            ++jumps0;
            continue;
        }
        for (float cur = next0 + 1.f; cur < u0; cur += 1.f) {
            if (hit(cur)) {
                u0 = cur;
                break;
            }
        }
        break;
    }
    int jumps1 = 0;
    while (jumps1 < kJumpMax) {
        const float next1 = u1 + jx;
        if (hit(next1)) {
            u1 = next1;
            ++jumps1;
            continue;
        }
        float new1 = u1;
        for (float cur = next1 - 1.f; cur > u1; cur -= 1.f) {
            if (hit(cur)) {
                new1 = cur;
                break;
            }
        }
        u1 = new1;
        break;
    }
    if (u1 < u0 + 2.f) u1 = u0 + 2.f;
    box->u0 = u0;
    box->u1 = u1;
}

}  // namespace

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeSeg7OrientedMany(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jfloatArray seedsArr, jint chromaMode,
    jint boundStrategy, jint tightInsetPx,
    jfloatArray teleArr, jintArray sweepArr, jlong dumpPtr,
    jlong overlayYPtr, jlong overlayUvPtr, jintArray poisonArr
) {
    auto* gray = reinterpret_cast<cv::Mat*>(grayPtr);
    if (!gray || gray->empty() || gray->type() != CV_8UC1 || !seedsArr) return nullptr;
    const int imgW = gray->cols, imgH = gray->rows;
    const jint n8 = env->GetArrayLength(seedsArr);
    if (n8 <= 0 || n8 % 8 != 0) return env->NewFloatArray(0);
    const int n = n8 / 8;
    std::vector<jfloat> seeds(n8);
    env->GetFloatArrayRegion(seedsArr, 0, n8, seeds.data());
    cv::Mat localMag;
    cv::Mat* cMag = nullptr;
    const bool useChroma = chromaMode == 1;
    const bool useTint = chromaMode == 2 || chromaMode == 3 || chromaMode == 4;
    const bool adaptive = chromaMode == 4;
    auto* uv = reinterpret_cast<cv::Mat*>(uvPtr);
    auto* scratch = reinterpret_cast<cv::Mat*>(scratchPtr);
    auto* inkDump = reinterpret_cast<cv::Mat*>(dumpPtr);
    auto* overlayY = reinterpret_cast<cv::Mat*>(overlayYPtr);
    auto* overlayUv = reinterpret_cast<cv::Mat*>(overlayUvPtr);
    if (inkDump && scratchFits(inkDump, imgW, imgH)) inkDump->setTo(0);
    std::vector<PoisonStats> poisonPacks;
    poisonPacks.resize(static_cast<size_t>(n));
    if (useChroma) {
        if (scratchFits(scratch, imgW, imgH)) {
            fillChromaMag(*gray, uv ? *uv : cv::Mat(), scratch);
            cMag = scratch;
        } else {
            fillChromaMag(*gray, uv ? *uv : cv::Mat(), &localMag);
            cMag = &localMag;
        }
    }
    std::vector<jfloat> out(n * 9, 0.f);
    std::vector<InkSweepPack> sweeps;
    sweeps.resize(static_cast<size_t>(n));
    for (int i = 0; i < n; ++i) {
        OriBox box{};
        const float* in = seeds.data() + i * 8;
        float* op = out.data() + i * 9;
        if (!oriFromQuad(in, &box)) {
            for (int k = 0; k < 8; ++k) op[k] = in[k];
            op[8] = 2.f;
            continue;
        }
        Seg7Tele tele{};
        tele.method = adaptive ? 4.f : (useChroma ? 1.f : 0.f);
        const cv::Mat* src = gray;
        bool srcIsBin = false;
        bool keepColor = false;
        cv::Mat localTint;
        if (useTint && uv) {
            float pts[8];
            oriToQuad(box, pts);
            float minx = pts[0], maxx = pts[0], miny = pts[1], maxy = pts[1];
            for (int k = 1; k < 4; ++k) {
                minx = std::min(minx, pts[k * 2]);
                maxx = std::max(maxx, pts[k * 2]);
                miny = std::min(miny, pts[k * 2 + 1]);
                maxy = std::max(maxy, pts[k * 2 + 1]);
            }
            const int sl = std::max(0, static_cast<int>(std::floor(minx)));
            const int st = std::max(0, static_cast<int>(std::floor(miny)));
            const int sr = std::min(imgW, static_cast<int>(std::ceil(maxx)));
            const int sb = std::min(imgH, static_cast<int>(std::ceil(maxy)));
            cv::Mat* tintDst = scratchFits(scratch, imgW, imgH) ? scratch : &localTint;
            if (fillChromaTintMask(*gray, *uv, sl, st, sr, sb, tintDst, 11, 0,
                    adaptive, &tele) && tintDst && !tintDst->empty()) {
                keepColor = true;
                if (skipTintWalk(adaptive, tele)) {
                    tele.method = 0.f;
                    src = gray;
                    srcIsBin = false;
                } else {
                    src = tintDst;
                    srcIsBin = true;
                }
            }
        } else if (useChroma && cMag && !cMag->empty() &&
            medianInteriorU8(*cMag, box, imgW, imgH) >= 8.0) {
            src = cMag;
        }
        float sPx = 2.f;
        seg7OrientedOne(*src, box, imgW, imgH, op, &sPx,
            boundStrategy, tightInsetPx, srcIsBin, &tele, keepColor,
            &sweeps[static_cast<size_t>(i)], inkDump, overlayY, overlayUv,
            &poisonPacks[static_cast<size_t>(i)]);
        op[8] = sPx;
        storeTeleArr(env, teleArr, i, tele);
    }
    writeSweepArr(env, sweepArr, sweeps);
    writePoisonArr(env, poisonArr, poisonPacks);
    jfloatArray arr = env->NewFloatArray(static_cast<jint>(out.size()));
    if (!arr) return nullptr;
    env->SetFloatArrayRegion(arr, 0, static_cast<jint>(out.size()), out.data());
    return arr;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeJumpOrientedMany(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jfloatArray quadsArr, jint chromaMode,
    jfloat maxFrac, jfloat energyRatio, jfloat jumpFrac, jfloat retractClearFrac,
    jfloatArray seedBhArr, jfloatArray seedQuadArr, jfloatArray sPxArr
) {
    try {
    auto* gray = reinterpret_cast<cv::Mat*>(grayPtr);
    if (!gray || gray->empty() || gray->type() != CV_8UC1 || !quadsArr) return nullptr;
    const int imgW = gray->cols, imgH = gray->rows;
    const jint n8 = env->GetArrayLength(quadsArr);
    if (n8 <= 0 || n8 % 8 != 0) return env->NewFloatArray(0);
    const int n = n8 / 8;
    std::vector<jfloat> quads(n8);
    env->GetFloatArrayRegion(quadsArr, 0, n8, quads.data());
    auto* uv = reinterpret_cast<cv::Mat*>(uvPtr);
    auto* scratch = reinterpret_cast<cv::Mat*>(scratchPtr);
    cv::Mat gx, gy, magY;
    cv::Sobel(*gray, gx, CV_32F, 1, 0, 3);
    cv::Sobel(*gray, gy, CV_32F, 0, 1, 3);
    cv::magnitude(gx, gy, magY);
    gx.release();
    gy.release();
    cv::Mat localMag;
    cv::Mat* cMag = nullptr;
    const bool useChromaMag = chromaMode == 1;
    const bool useTint = chromaMode == 2 || chromaMode == 3 || chromaMode == 4;
    const bool adaptive = chromaMode == 4;
    if (useChromaMag) {
        if (scratchFits(scratch, imgW, imgH)) {
            fillChromaMag(*gray, uv ? *uv : cv::Mat(), scratch);
            cMag = scratch;
        } else {
            fillChromaMag(*gray, uv ? *uv : cv::Mat(), &localMag);
            cMag = &localMag;
        }
    }
    std::vector<jfloat> out(n8, 0.f);
    for (int i = 0; i < n; ++i) {
        OriBox box{};
        const float* in = quads.data() + i * 8;
        float* op = out.data() + i * 8;
        OriBox seedBox{};
        bool haveSeed = false;
        if (seedQuadArr && env->GetArrayLength(seedQuadArr) >= (i + 1) * 8) {
            jfloat sq[8] = {};
            env->GetFloatArrayRegion(seedQuadArr, i * 8, 8, sq);
            haveSeed = oriFromQuad(sq, &seedBox);
        }
        if (haveSeed) {
            box = seedBox;
            oriExtentsInFrame(seedBox, in, &box.u0, &box.u1, &box.v0, &box.v1);
            if (box.u1 - box.u0 < 2.f || box.v1 - box.v0 < 2.f) {
                for (int k = 0; k < 8; ++k) op[k] = in[k];
                continue;
            }
        } else if (!oriFromQuad(in, &box)) {
            for (int k = 0; k < 8; ++k) op[k] = in[k];
            continue;
        }
        const cv::Mat* eng = &magY;
        cv::Mat localTint;
        const cv::Mat* lookPtr = nullptr;
        float seedV0 = 0.f, seedV1 = 0.f;
        int minRun = 0;
        float sPx = 0.f;
        if (sPxArr && env->GetArrayLength(sPxArr) >= (i + 1)) {
            jfloat sp = 0.f;
            env->GetFloatArrayRegion(sPxArr, i, 1, &sp);
            sPx = sp;
        }
        const float vSpan = std::max(1.f, box.v1 - box.v0);
        const int jx = std::max(1, static_cast<int>(std::lround(jumpFrac * vSpan)));
        const bool inkTest = sPx > 0.f && haveSeed;
        auto aabbOf = [&](const OriBox& ob, int* sl, int* st, int* sr, int* sb) {
            float pts[8];
            oriToQuad(ob, pts);
            float minx = pts[0], maxx = pts[0], miny = pts[1], maxy = pts[1];
            for (int k = 1; k < 4; ++k) {
                minx = std::min(minx, pts[k * 2]);
                maxx = std::max(maxx, pts[k * 2]);
                miny = std::min(miny, pts[k * 2 + 1]);
                maxy = std::max(maxy, pts[k * 2 + 1]);
            }
            *sl = std::max(0, static_cast<int>(std::floor(minx)));
            *st = std::max(0, static_cast<int>(std::floor(miny)));
            *sr = std::min(imgW, static_cast<int>(std::ceil(maxx)));
            *sb = std::min(imgH, static_cast<int>(std::ceil(maxy)));
        };
        if (inkTest) {
            seedV0 = seedBox.v0;
            seedV1 = seedBox.v1;
            int sl, st, sr, sb;
            aabbOf(seedBox, &sl, &st, &sr, &sb);
            cv::Mat* tintDst = scratchFits(scratch, imgW, imgH) ? scratch : &localTint;
            if (useTint && uv) {
                Seg7Tele ttele{};
                const bool tintOk = fillChromaTintMask(
                    *gray, *uv, sl, st, sr, sb, tintDst, 11,
                    jx * (kJumpMax + 1), adaptive, &ttele) &&
                    tintDst && !tintDst->empty();
                if (tintOk && !skipTintWalk(adaptive, ttele)) {
                    lookPtr = tintDst;
                } else if (tintOk && skipTintWalk(adaptive, ttele) &&
                    fillGrayJumpLook(*gray, sl, st, sr, sb, jx * (kJumpMax + 1), tintDst) &&
                    tintDst && !tintDst->empty()) {
                    lookPtr = tintDst;
                }
            } else if (fillGrayJumpLook(*gray, sl, st, sr, sb, jx * (kJumpMax + 1), tintDst) &&
                       tintDst && !tintDst->empty()) {
                lookPtr = tintDst;
            }
            const int maxIn = lookPtr
                ? maxInSeedRunRows(*lookPtr, st, sb, sl, sr) : 0;
            minRun = usedMinRun(std::max(1, static_cast<int>(std::lround(sPx))), maxIn);
        } else if (useTint && uv) {
            int sl, st, sr, sb;
            aabbOf(box, &sl, &st, &sr, &sb);
            cv::Mat* tintDst = scratchFits(scratch, imgW, imgH) ? scratch : &localTint;
            Seg7Tele ttele{};
            if (fillChromaTintMask(*gray, *uv, sl, st, sr, sb, tintDst, 11,
                    jx * (kJumpMax + 1), adaptive, &ttele) &&
                tintDst && !tintDst->empty() &&
                !skipTintWalk(adaptive, ttele)) {
                eng = tintDst;
            }
        } else if (useChromaMag && cMag && !cMag->empty() &&
                   medianInteriorU8(*cMag, box, imgW, imgH) >= 8.0) {
            eng = cMag;
        }
        float seedBh = 0.f;
        if (seedBhArr && env->GetArrayLength(seedBhArr) >= (i + 1)) {
            jfloat sh = 0.f;
            env->GetFloatArrayRegion(seedBhArr, i, 1, &sh);
            seedBh = sh;
        }
        jumpOrientedOne(*eng, &box, imgW, imgH,
            maxFrac, energyRatio, jumpFrac, retractClearFrac, seedBh,
            lookPtr, seedV0, seedV1, minRun);
        oriToQuad(box, op);
    }
    magY.release();
    jfloatArray arr = env->NewFloatArray(static_cast<jint>(out.size()));
    if (!arr) return nullptr;
    env->SetFloatArrayRegion(arr, 0, static_cast<jint>(out.size()), out.data());
    return arr;
    } catch (const cv::Exception&) {
        return nullptr;
    }
}
