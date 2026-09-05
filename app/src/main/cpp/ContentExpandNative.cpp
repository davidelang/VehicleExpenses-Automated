#include <jni.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/imgcodecs.hpp>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <exception>
#include <mutex>
#include <unistd.h>
#include <vector>
#include <cstdlib>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "ContentExpandNative", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "ContentExpandNative", __VA_ARGS__)

static constexpr size_t kVeAllocCap = 64ull * 1024ull * 1024ull;

static size_t veMatBytes(int rows, int cols, int type) {
    if (rows < 0 || cols < 0) return kVeAllocCap + 1;
    return static_cast<size_t>(rows) * static_cast<size_t>(cols) * CV_ELEM_SIZE(type);
}

static bool veAllocLog(const char* tag, size_t bytes, int rows, int cols, int type) {
    LOGI("veAllocLog tag=%s bytes=%zu rows=%d cols=%d type=%d", tag, bytes, rows, cols, type);
    if (bytes > kVeAllocCap) {
        LOGE("veAllocLog FAIL tag=%s bytes=%zu >64MiB rows=%d cols=%d type=%d",
             tag, bytes, rows, cols, type);
        return false;
    }
    return true;
}

static char g_rssPath[512];
static long g_lastRssKb = -1;
static std::mutex g_rssMu;

static long veRssKb() {
    FILE* f = fopen("/proc/self/statm", "r");
    if (!f) return -1;
    unsigned long size = 0, rss = 0;
    const int n = fscanf(f, "%lu %lu", &size, &rss);
    fclose(f);
    if (n != 2) return -1;
    long page = sysconf(_SC_PAGESIZE);
    if (page < 1) page = 4096;
    return static_cast<long>(rss * (page / 1024));
}

static void veRssLog(const char* tag, const char* extra = nullptr) {
    const long kb = veRssKb();
    long delta = 0;
    bool jump = false;
    {
        std::lock_guard<std::mutex> lock(g_rssMu);
        if (g_lastRssKb >= 0 && kb >= 0) delta = kb - g_lastRssKb;
        jump = delta >= 128 * 1024;
        if (kb >= 0) g_lastRssKb = kb;
        if (g_rssPath[0]) {
            FILE* out = fopen(g_rssPath, "a");
            if (out) {
                fprintf(out, "tag=%s rssKb=%ld deltaKb=%ld%s%s\n",
                        tag ? tag : "?", kb, delta,
                        extra && extra[0] ? " " : "",
                        extra && extra[0] ? extra : "");
                fclose(out);
            }
        }
    }
    if (jump) {
        LOGE("veRss JUMP tag=%s rssKb=%ld deltaKb=%ld %s",
             tag ? tag : "?", kb, delta, extra ? extra : "");
    } else {
        LOGI("veRss tag=%s rssKb=%ld deltaKb=%ld %s",
             tag ? tag : "?", kb, delta, extra ? extra : "");
    }
}

extern "C" void* __real_malloc(size_t);
extern "C" void* __real_calloc(size_t, size_t);
extern "C" void* __real_realloc(void*, size_t);

static constexpr size_t kVeWrapFail = 512ull * 1024ull * 1024ull;

extern "C" void* __wrap_malloc(size_t n) {
    if (n >= kVeWrapFail) {
        LOGE("veMalloc FAIL bytes=%zu >=512MiB", n);
        return nullptr;
    }
    if (n >= 1024ull * 1024ull) LOGI("veMalloc bytes=%zu", n);
    return __real_malloc(n);
}

extern "C" void* __wrap_calloc(size_t nmemb, size_t sz) {
    size_t n = 0;
    if (sz != 0 && nmemb > kVeWrapFail / sz) {
        LOGE("veMalloc FAIL calloc nmemb=%zu sz=%zu >=512MiB", nmemb, sz);
        return nullptr;
    }
    n = nmemb * sz;
    if (n >= kVeWrapFail) {
        LOGE("veMalloc FAIL bytes=%zu >=512MiB", n);
        return nullptr;
    }
    if (n >= 1024ull * 1024ull) LOGI("veMalloc bytes=%zu", n);
    return __real_calloc(nmemb, sz);
}

extern "C" void* __wrap_realloc(void* p, size_t n) {
    if (n >= kVeWrapFail) {
        LOGE("veMalloc FAIL realloc bytes=%zu >=512MiB", n);
        return nullptr;
    }
    if (n >= 1024ull * 1024ull) LOGI("veMalloc bytes=%zu", n);
    return __real_realloc(p, n);
}

extern "C" JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeVeRssSetPath(
    JNIEnv* env, jobject, jstring path) {
    std::lock_guard<std::mutex> lock(g_rssMu);
    if (!path) {
        g_rssPath[0] = 0;
        return;
    }
    const char* p = env->GetStringUTFChars(path, nullptr);
    if (!p) {
        g_rssPath[0] = 0;
        return;
    }
    std::snprintf(g_rssPath, sizeof(g_rssPath), "%s", p);
    env->ReleaseStringUTFChars(path, p);
}

static constexpr int kRunHistBins = 32;
static constexpr int kSeg7TeleN = 26 + kRunHistBins * 2;

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
    float farL = 0.f;
    float farR = 0.f;
    float nInkSeed = 0.f;
    float nInkBlue = 0.f;
    float nInkYellow = 0.f;
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

static void fillRunHists(
    const cv::Mat& look, int l, int t, int r, int b, double thr,
    int* histH, int* histV
) {
    for (int i = 0; i < kRunHistBins; ++i) {
        histH[i] = 0;
        histV[i] = 0;
    }
    if (look.empty() || look.type() != CV_8UC1) return;
    if (l < 0) l = 0;
    if (t < 0) t = 0;
    if (r > look.cols) r = look.cols;
    if (b > look.rows) b = look.rows;
    if (r <= l || b <= t || !histH || !histV) return;
    const float thrF = static_cast<float>(thr);
    for (int y = t; y < b; ++y) {
        const uint8_t* p = look.ptr<uint8_t>(y);
        int run = 0;
        for (int x = l; x <= r; ++x) {
            const bool on = x < r && static_cast<float>(p[x]) >= thrF;
            if (on) ++run;
            else if (run > 0) {
                addRunHist(run, histH);
                run = 0;
            }
        }
    }
    for (int x = l; x < r; ++x) {
        int run = 0;
        for (int y = t; y <= b; ++y) {
            const bool on = y < b && static_cast<float>(look.ptr<uint8_t>(y)[x]) >= thrF;
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
    dst[21] = t.farL;
    dst[22] = t.farR;
    dst[23] = t.nInkSeed;
    dst[24] = t.nInkBlue;
    dst[25] = t.nInkYellow;
    for (int i = 0; i < kRunHistBins; ++i) {
        dst[26 + i] = static_cast<float>(t.histH[i]);
        dst[26 + kRunHistBins + i] = static_cast<float>(t.histV[i]);
    }
}

static int countInkU8(const cv::Mat& m, int l, int t, int r, int b) {
    if (m.empty() || m.type() != CV_8UC1) return 0;
    if (l < 0) l = 0;
    if (t < 0) t = 0;
    if (r > m.cols) r = m.cols;
    if (b > m.rows) b = m.rows;
    if (r <= l || b <= t) return 0;
    int n = 0;
    for (int y = t; y < b; ++y) {
        const uint8_t* p = m.ptr<uint8_t>(y);
        for (int x = l; x < r; ++x) if (p[x]) ++n;
    }
    return n;
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
    if (mag.type() == CV_8UC1) return static_cast<float>(mag.ptr<uint8_t>(y)[x]);
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
    int inkLo = 0, nextNonInk = 0, seedIndex = 0, nSeeds = 0, classChange = 0;
    char phase[40]{};
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

static void writeSweepArr(JNIEnv* env, jshortArray arr, const std::vector<InkSweepPack>& packs) {
    if (!env || !arr) return;
    const jsize cap = env->GetArrayLength(arr);
    if (cap < 1) return;
    std::vector<jshort> buf;
    buf.push_back(static_cast<jshort>(packs.size()));
    for (const auto& p : packs) {
        const int nV = static_cast<int>(p.vScores.size());
        const int nH = static_cast<int>(p.hScores.size());
        buf.push_back(static_cast<jshort>(std::lround(p.thr * 1000.f)));
        buf.push_back(static_cast<jshort>(std::lround(p.sPx)));
        buf.push_back(static_cast<jshort>(p.minRun));
        buf.push_back(static_cast<jshort>(std::lround(p.energyRatio * 1000.f)));
        buf.push_back(static_cast<jshort>(p.vOrigin));
        buf.push_back(static_cast<jshort>(p.hOrigin));
        buf.push_back(static_cast<jshort>(p.v0));
        buf.push_back(static_cast<jshort>(p.v1));
        buf.push_back(static_cast<jshort>(nV));
        buf.push_back(static_cast<jshort>(p.h0));
        buf.push_back(static_cast<jshort>(p.h1));
        buf.push_back(static_cast<jshort>(nH));
        for (int s : p.vScores) buf.push_back(static_cast<jshort>(s));
        for (int s : p.hScores) buf.push_back(static_cast<jshort>(s));
        const int nJ = static_cast<int>(p.threshJpeg.size());
        buf.push_back(static_cast<jshort>(nJ));
        for (uint8_t b : p.threshJpeg) buf.push_back(static_cast<jshort>(b));
    }
    const jsize n = std::min(cap, static_cast<jsize>(buf.size()));
    env->SetShortArrayRegion(arr, 0, n, buf.data());
}

static cv::Mat g_packJpegU8;

static cv::Mat packJpegScratch(int w, int h) {
    if (w < 1) w = 1;
    if (h < 1) h = 1;
    if (g_packJpegU8.empty() || g_packJpegU8.type() != CV_8UC1 ||
        g_packJpegU8.rows < h || g_packJpegU8.cols < w) {
        const int nr = std::max(h, g_packJpegU8.rows);
        const int nc = std::max(w, g_packJpegU8.cols);
        if (!veAllocLog("packJpegScratch", veMatBytes(nr, nc, CV_8UC1), nr, nc, CV_8UC1)) {
            return cv::Mat();
        }
        g_packJpegU8.create(nr, nc, CV_8UC1);
    }
    if (!g_packJpegU8.data) return cv::Mat();
    return cv::Mat(h, w, CV_8UC1, g_packJpegU8.data);
}

extern "C" bool veEncodeGrayJpegU8(const cv::Mat& u8, std::vector<uint8_t>* out);

static void packSeedBinJpeg(const cv::Mat& bin, InkSweepPack* out) {
    if (!out || bin.empty() || bin.type() != CV_8UC1) return;
    try {
        const int w = bin.cols;
        const int h = bin.rows;
        if (w < 1 || h < 1) return;
        int nw = w;
        int nh = h;
        const int longSide = std::max(w, h);
        if (longSide > 400) {
            const double sc = 400.0 / static_cast<double>(longSide);
            const int64_t nw64 = std::llround(static_cast<double>(w) * sc);
            const int64_t nh64 = std::llround(static_cast<double>(h) * sc);
            if (nw64 < 1 || nh64 < 1 || nw64 > 65000 || nh64 > 65000) return;
            nw = std::max(2, (static_cast<int>(nw64) + 1) / 2 * 2);
            nh = std::max(2, (static_cast<int>(nh64) + 1) / 2 * 2);
        }
        cv::Mat dest = packJpegScratch(nw, nh);
        if (dest.empty() || dest.rows < nh || dest.cols < nw) return;
        for (int y = 0; y < nh; ++y) {
            const int sy = std::min(h - 1, y * h / nh);
            const uint8_t* sp = bin.ptr<uint8_t>(sy);
            uint8_t* dp = dest.ptr<uint8_t>(y);
            for (int x = 0; x < nw; ++x) {
                const int sx = std::min(w - 1, x * w / nw);
                dp[x] = sp[sx];
            }
        }
        std::vector<uint8_t> jpg;
        if (veEncodeGrayJpegU8(dest, &jpg) && !jpg.empty() && jpg.size() <= 16000) {
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

static void fillOrientedEnergySweep(
    const cv::Mat& mag, const Frame& fr,
    float seedCx, float seedCy, float seedBw, float seedBh,
    int walkedH, float energyRatio, double thr, float jumpFrac,
    InkSweepPack* out
);
static bool fillEnergyLookU8(const cv::Mat& gray, cv::Mat* dst);
static cv::Mat* energyLookAs(jlong scratchPtr, const cv::Mat& gray);
static void sobelGxGyU8(const cv::Mat& gray, int x, int y, int* gx, int* gy);
static void countPullYFromU8Gray(
    const cv::Mat& look, int sl, int st, int sr, int sb,
    int el, int et, int er, int eb,
    int imgW, int imgH, bool stopUpEnergy, bool stopDownEnergy,
    int* ct, int* cb, int* pulledT, int* pulledB
);

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

    auto duAbs = [&](float px, float py) -> float {
        int x = static_cast<int>(std::lround(px));
        int y = static_cast<int>(std::lround(py));
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x >= seedFr.imgW) x = seedFr.imgW - 1;
        if (y >= seedFr.imgH) y = seedFr.imgH - 1;
        int gxi = 0, gyi = 0;
        sobelGxGyU8(*gray, x, y, &gxi, &gyi);
        return std::fabs(static_cast<float>(gxi) * ux + static_cast<float>(gyi) * uy);
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

enum : uint8_t {
    kKindNone = 0,
    kKindInk = 1,
    kKindPoisonFat = 2,
    kKindPoisonNoPeak = 3,
    kKindLookBg = 4,
    kKindPepper = 5
};

struct ObjPack {
    uint8_t nextInk = 255;
    uint8_t nextNon = 1;
    uint8_t inkLo = 255;
    bool abort = false;
    int abortSeed = -1;
    int classChange = 0;
    uint8_t kind[256]{};
    uint8_t seedOf[256]{};
    int seenInk = 0;
    int seenNon = 0;
    char phase[40]{};
};

static bool objAlloc(
    ObjPack* p, bool ink, int seedIndex, uint8_t kind, uint8_t* idOut
) {
    if (!p || !idOut) return false;
    if (p->abort) return false;
    if (ink) {
        if (p->nextInk <= p->nextNon) {
            p->abort = true;
            p->abortSeed = seedIndex;
            std::snprintf(p->phase, sizeof(p->phase), "ink");
            return false;
        }
        *idOut = p->nextInk--;
        p->inkLo = *idOut;
        p->seenInk = 1;
    } else {
        if (p->nextNon >= p->nextInk) {
            p->abort = true;
            p->abortSeed = seedIndex;
            std::snprintf(p->phase, sizeof(p->phase), "non-ink");
            return false;
        }
        *idOut = p->nextNon++;
        p->seenNon = 1;
    }
    p->kind[*idOut] = kind;
    p->seedOf[*idOut] = static_cast<uint8_t>(std::max(0, seedIndex) & 255);
    return true;
}

static void objPut(
    cv::Mat* plane, int x, int y, uint8_t id, ObjPack* pack
) {
    if (!plane || plane->empty() || plane->type() != CV_8UC1) return;
    if (y < 0 || x < 0 || y >= plane->rows || x >= plane->cols) return;
    uint8_t& dst = plane->ptr<uint8_t>(y)[x];
    if (pack && dst != 0) {
        const bool oldInk = dst >= pack->inkLo;
        const bool newInk = id >= pack->inkLo;
        if (oldInk != newInk) pack->classChange = 1;
    }
    dst = id;
}

static int objPhaseCode(const char* p) {
    if (!p || !p[0]) return 0;
    if (std::strcmp(p, "ink") == 0) return 1;
    if (std::strcmp(p, "non-ink") == 0) return 2;
    if (std::strcmp(p, "plus-ROI") == 0) return 3;
    if (std::strcmp(p, "poison") == 0) return 4;
    if (std::strcmp(p, "walk") == 0) return 5;
    if (std::strcmp(p, "scratch") == 0) return 6;
    return 7;
}

static void appendObjMeta(PoisonStats* s, const ObjPack& p, int seedIndex, int nSeeds) {
    if (!s) return;
    s->inkLo = p.inkLo;
    s->nextNonInk = p.nextNon;
    s->seedIndex = seedIndex;
    s->nSeeds = nSeeds;
    s->classChange = p.classChange;
    std::snprintf(s->phase, sizeof(s->phase), "%s", p.phase);
    PoisonCcPack c;
    c.x = -1;
    c.y = static_cast<int>(p.inkLo);
    c.w = static_cast<int>(p.nextNon);
    c.h = seedIndex;
    c.noPeak = nSeeds;
    c.thr = p.classChange;
    c.nInk = objPhaseCode(p.phase);
    s->ccs.push_back(c);
}

static bool rowHasInkId(
    const cv::Mat& plane, int y, int x0, int x1, uint8_t inkLo, int minRun, int glareW
) {
    if (y < 0 || y >= plane.rows || plane.type() != CV_8UC1) return false;
    const uint8_t* p = plane.ptr<uint8_t>(y);
    const int xa = std::max(0, x0);
    const int xb = std::min(plane.cols, x1);
    int run = 0;
    for (int x = xa; x <= xb; ++x) {
        const bool on = x < xb && p[x] >= inkLo;
        if (on) ++run;
        else if (run > 0) {
            if (run >= minRun && run <= glareW) return true;
            run = 0;
        }
    }
    return false;
}

/** 8UC1 ROI of [plane] at (x,y). Empty if it does not fit. */
static cv::Mat planeView8u(cv::Mat* plane, int x, int y, int w, int h) {
    if (!plane || plane->empty() || plane->type() != CV_8UC1 || w < 1 || h < 1) {
        return cv::Mat();
    }
    if (x < 0 || y < 0 || x + w > plane->cols || y + h > plane->rows) {
        return cv::Mat();
    }
    return (*plane)(cv::Rect(x, y, w, h));
}

/** Zeroed 8UC1 ROI of [plane] at (x,y). Empty if it does not fit. */
static cv::Mat planeRoi8u(cv::Mat* plane, int x, int y, int w, int h) {
    cv::Mat r = planeView8u(plane, x, y, w, h);
    if (!r.empty()) r.setTo(0);
    return r;
}

/** 8UC1 header, else null. Type check only — not a pointer compare. */
static cv::Mat* asU8(cv::Mat* m) {
    return (m && !m->empty() && m->type() == CV_8UC1) ? m : nullptr;
}

/** 8UC2 UV header, else null. Type check only — not a pointer compare. */
static cv::Mat* asUV(cv::Mat* m) {
    return (m && !m->empty() && m->type() == CV_8UC2) ? m : nullptr;
}

/** NV21 UV view of a packed full-frame Y plane (BufferSet Instance layout). */
static cv::Mat* asUvFromY(cv::Mat* y, cv::Mat* storage) {
    if (!storage || !y || y->empty() || y->type() != CV_8UC1) return nullptr;
    if ((y->rows & 1) || (y->cols & 1)) return nullptr;
    if (y->step[0] != static_cast<size_t>(y->cols)) return nullptr;
    *storage = cv::Mat(
        y->rows / 2, y->cols / 2, CV_8UC2,
        y->data + static_cast<size_t>(y->rows) * static_cast<size_t>(y->cols),
        static_cast<size_t>(y->cols));
    return asUV(storage);
}

/** Prefix of poison IntArray: nextInk, nextNon, inkLo. Zeros keep defaults. */
static void loadObjPack(JNIEnv* env, jintArray arr, ObjPack* p) {
    if (!env || !arr || !p) return;
    const jint cap = env->GetArrayLength(arr);
    if (cap < 3) return;
    jint v[3] = {0, 0, 0};
    env->GetIntArrayRegion(arr, 0, 3, v);
    if (v[0] == 0 && v[1] == 0 && v[2] == 0) return;
    if (v[0] >= 0 && v[0] <= 255) p->nextInk = static_cast<uint8_t>(v[0]);
    if (v[1] >= 0 && v[1] <= 255) p->nextNon = static_cast<uint8_t>(v[1]);
    if (v[2] >= 0 && v[2] <= 255) p->inkLo = static_cast<uint8_t>(v[2]);
}

/** Tail of poison IntArray: nextInk, nextNon, inkLo for the next seed. */
static void storeObjPack(JNIEnv* env, jintArray arr, const ObjPack& p) {
    if (!env || !arr) return;
    const jint cap = env->GetArrayLength(arr);
    if (cap < 3) return;
    jint v[3] = {
        static_cast<jint>(p.nextInk),
        static_cast<jint>(p.nextNon),
        static_cast<jint>(p.inkLo)
    };
    env->SetIntArrayRegion(arr, cap - 3, 3, v);
}

static bool fillChromaMag(const cv::Mat& y, const cv::Mat& uv, cv::Mat* dst) {
    if (y.empty() || y.type() != CV_8UC1 || !dst) return false;
    const int h = y.rows, w = y.cols;
    if (!scratchFits(dst, w, h)) return false;
    if (uv.empty() || uv.type() != CV_8UC2 || uv.rows <= 0 || uv.cols <= 0) {
        (*dst)(cv::Rect(0, 0, w, h)).setTo(0);
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
    int* farL = nullptr, int* farR = nullptr,
    const cv::Mat* lookBin = nullptr, int seedT = 0, int seedB = 0, int minRun = 0,
    int lookOx = 0, int lookOy = 0
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
        if (useInk) {
            return maxInkRunCol(*lookBin, x - lookOx, inkT - lookOy, inkB - lookOy) >= minRun;
        }
        return meanRectF(eng, x, coreT, x + 1, coreB, imgW, imgH) >= thr;
    };
    int probeL = *l;
    int probeR = *r;
    int jumpsL = 0;
    while (*l > 0 && jumpsL < kJumpMax) {
        const int nextL = std::max(0, *l - jx);
        if (nextL >= *l) break;
        probeL = nextL;
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
        probeR = nextR;
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
    if (farL) *farL = probeL;
    if (farR) *farR = probeR;
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

/** 3×3 Sobel weights (ksize=3) at one pixel; gx/gy in registers (about ±1020). */
static void sobelGxGyU8(const cv::Mat& gray, int x, int y, int* gx, int* gy) {
    const int h = gray.rows, w = gray.cols;
    auto at = [&](int yy, int xx) -> int {
        if (xx < 0) xx = 0;
        if (yy < 0) yy = 0;
        if (xx >= w) xx = w - 1;
        if (yy >= h) yy = h - 1;
        return gray.ptr<uint8_t>(yy)[xx];
    };
    const int p00 = at(y - 1, x - 1), p01 = at(y - 1, x), p02 = at(y - 1, x + 1);
    const int p10 = at(y, x - 1), p12 = at(y, x + 1);
    const int p20 = at(y + 1, x - 1), p21 = at(y + 1, x), p22 = at(y + 1, x + 1);
    *gx = -p00 + p02 - 2 * p10 + 2 * p12 - p20 + p22;
    *gy = -p00 - 2 * p01 - p02 + p20 + 2 * p21 + p22;
}

/** Energy look: min(255, (|gx|+|gy|) >> 3) into dst (A.s). No float mag, no dest create. */
static bool fillEnergyLookU8(const cv::Mat& gray, cv::Mat* dst) {
    if (gray.empty() || gray.type() != CV_8UC1 || !dst) return false;
    const int h = gray.rows, w = gray.cols;
    if (dst->empty() || dst->type() != CV_8UC1 || dst->rows < h || dst->cols < w) {
        return false;
    }
    for (int y = 0; y < h; ++y) {
        const uint8_t* r0 = gray.ptr<uint8_t>(y > 0 ? y - 1 : 0);
        const uint8_t* r1 = gray.ptr<uint8_t>(y);
        const uint8_t* r2 = gray.ptr<uint8_t>(y + 1 < h ? y + 1 : h - 1);
        uint8_t* d = dst->ptr<uint8_t>(y);
        for (int x = 0; x < w; ++x) {
            const int xm = x > 0 ? x - 1 : 0;
            const int xp = x + 1 < w ? x + 1 : w - 1;
            const int gx = -r0[xm] + r0[xp] - 2 * r1[xm] + 2 * r1[xp] - r2[xm] + r2[xp];
            const int gy = -r0[xm] - 2 * r0[x] - r0[xp] + r2[xm] + 2 * r2[x] + r2[xp];
            const int mag = (gx < 0 ? -gx : gx) + (gy < 0 ? -gy : gy);
            d[x] = static_cast<uint8_t>(std::min(255, mag >> 3));
        }
    }
    return true;
}

static cv::Mat* energyLookAs(jlong scratchPtr, const cv::Mat& gray) {
    auto* s = reinterpret_cast<cv::Mat*>(scratchPtr);
    const int h = gray.rows, w = gray.cols;
    if (s && !s->empty() && s->type() == CV_8UC1 && s->rows >= h && s->cols >= w) return s;
    return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeFillEnergyLookU8(
    JNIEnv* /*env*/, jobject /*thiz*/,
    jlong grayPtr, jlong destPtr
) {
    auto* gray = reinterpret_cast<cv::Mat*>(grayPtr);
    auto* dest = reinterpret_cast<cv::Mat*>(destPtr);
    if (!gray || !dest) return JNI_FALSE;
    return fillEnergyLookU8(*gray, dest) ? JNI_TRUE : JNI_FALSE;
}

static void countPullYFromU8Gray(
    const cv::Mat& look, int sl, int st, int sr, int sb,
    int el, int et, int er, int eb,
    int imgW, int imgH, bool stopUpEnergy, bool stopDownEnergy,
    int* ct, int* cb, int* pulledT, int* pulledB
) {
    *ct = et;
    *cb = eb;
    *pulledT = 0;
    *pulledB = 0;
    if (sr <= sl || sb <= st || look.empty() || look.type() != CV_8UC1) return;
    int hist[256] = {};
    int nPix = 0;
    for (int y = st; y < sb; ++y) {
        const uint8_t* p = look.ptr<uint8_t>(y);
        for (int x = sl; x < sr; ++x) {
            hist[p[x]]++;
            ++nPix;
        }
    }
    double p90 = 1.0;
    if (nPix >= 2) {
        const int target = static_cast<int>((nPix - 1) * 0.90);
        int acc = 0;
        p90 = 255.0;
        for (int v = 0; v < 256; ++v) {
            acc += hist[v];
            if (acc > target) {
                p90 = static_cast<double>(v);
                break;
            }
        }
    }
    const int thr = std::max(1, static_cast<int>(0.55 * p90));
    const int y0 = std::max(0, std::min(st, et));
    const int y1 = std::min(imgH, std::max(sb, eb));
    const int n = std::max(1, y1 - y0);
    std::vector<double> raw(static_cast<size_t>(n), 0.0);
    constexpr int minRun = 3;
    for (int i = 0; i < n; ++i) {
        const int y = y0 + i;
        const uint8_t* p = look.ptr<uint8_t>(y);
        int cnt = 0, run = 0;
        for (int x = sl; x < sr; ++x) {
            if (p[x] >= thr) ++run;
            else {
                if (run >= minRun) ++cnt;
                run = 0;
            }
        }
        if (run >= minRun) ++cnt;
        raw[static_cast<size_t>(i)] = static_cast<double>(cnt);
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
            const double left = (i == 0) ? sm[static_cast<size_t>(i)] : sm[static_cast<size_t>(i - 1)];
            const double right = (i == n - 1) ? sm[static_cast<size_t>(i)] : sm[static_cast<size_t>(i + 1)];
            if (sm[static_cast<size_t>(i)] < cThr && sm[static_cast<size_t>(i)] <= left &&
                sm[static_cast<size_t>(i)] <= right) {
                top = i;
                pT = 1;
                break;
            }
        }
        for (int i = sbi; i < be; ++i) {
            const double left = (i == 0) ? sm[static_cast<size_t>(i)] : sm[static_cast<size_t>(i - 1)];
            const double right = (i == n - 1) ? sm[static_cast<size_t>(i)] : sm[static_cast<size_t>(i + 1)];
            if (sm[static_cast<size_t>(i)] < cThr && sm[static_cast<size_t>(i)] <= left &&
                sm[static_cast<size_t>(i)] <= right) {
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
    const double tipTop = (te >= 0 && te < n) ? sm[static_cast<size_t>(te)] : 0.0;
    double tipBot = 0.0;
    if (be - 1 >= 0 && be - 1 < n) tipBot = sm[static_cast<size_t>(be - 1)];
    else if (be >= 0 && be < n) tipBot = sm[static_cast<size_t>(be)];
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

static jintArray aabbEnergySeedsOut(JNIEnv* env, const std::vector<jint>& seeds) {
    const int n = static_cast<int>(seeds.size()) / 4;
    if (n <= 0) return env->NewIntArray(0);
    std::vector<jint> out(static_cast<size_t>(n) * 11, 0);
    for (int i = 0; i < n; ++i) {
        const int o = i * 11;
        const int sl = seeds[static_cast<size_t>(i) * 4];
        const int st = seeds[static_cast<size_t>(i) * 4 + 1];
        const int sr = seeds[static_cast<size_t>(i) * 4 + 2];
        const int sb = seeds[static_cast<size_t>(i) * 4 + 3];
        out[static_cast<size_t>(o)] = sl;
        out[static_cast<size_t>(o) + 1] = st;
        out[static_cast<size_t>(o) + 2] = sr;
        out[static_cast<size_t>(o) + 3] = sb;
        out[static_cast<size_t>(o) + 4] = sl;
        out[static_cast<size_t>(o) + 5] = st;
        out[static_cast<size_t>(o) + 6] = sr;
        out[static_cast<size_t>(o) + 7] = sb;
    }
    jintArray arr = env->NewIntArray(static_cast<jint>(out.size()));
    if (!arr) return env->NewIntArray(0);
    env->SetIntArrayRegion(arr, 0, static_cast<jint>(out.size()), out.data());
    return arr;
}

static void walkEnergyExpand(
    const cv::Mat& look, int imgW, int imgH, int cap, double thr, bool freezeHorz,
    int seedL, int seedT, int seedR, int seedB,
    int* l, int* t, int* r, int* b, bool* allowUp, bool* allowDown,
    int* fTop, int* fBot
) {
    (void)seedL; (void)seedR;
    *allowUp = *t > 0 && meanRectF(look, *l, *t - 1, *r, *t, imgW, imgH) >= thr;
    *allowDown = *b < imgH && meanRectF(look, *l, *b, *r, *b + 1, imgW, imgH) >= thr;
    for (int k = 0; k < cap; ++k) {
        bool grew = false;
        if (*allowUp && *t > 0 &&
            meanRectF(look, *l, *t - 1, *r, *t, imgW, imgH) >= thr) {
            --*t;
            grew = true;
        }
        if (*allowDown && *b < imgH &&
            meanRectF(look, *l, *b, *r, *b + 1, imgW, imgH) >= thr) {
            ++*b;
            grew = true;
        }
        if (!freezeHorz) {
            if (*l > 0 && meanRectF(look, *l - 1, *t, *l, *b, imgW, imgH) >= thr) {
                --*l;
                grew = true;
            }
            if (*r < imgW && meanRectF(look, *r, *t, *r + 1, *b, imgW, imgH) >= thr) {
                ++*r;
                grew = true;
            }
        }
        if (!grew) break;
    }
    *fTop = (*t < seedT) ? kFlagNormalExpand : kFlagBlockedGap;
    *fBot = (*b > seedB) ? kFlagNormalExpand : kFlagBlockedGap;
}

static void walkEnergyRetract(
    const cv::Mat& look, int imgW, int imgH, int cap, double thr, bool freezeHorz,
    int seedL, int seedT, int seedR, int seedB,
    int* l, int* t, int* r, int* b, bool* allowUp, bool* allowDown,
    int* fTop, int* fBot
) {
    (void)freezeHorz;
    (void)seedL;
    (void)seedR;
    const int seedH = std::max(1, seedB - seedT);
    const int maxRetractPx = std::max(1, static_cast<int>(std::lround(kVertRetractCapFrac * seedH)));
    auto edgeInk = [&](int sl, int st, int sr, int sb) {
        return meanRectF(look, sl, st, sr, sb, imgW, imgH) >= thr;
    };
    if (edgeInk(*l, *t, *r, *t + 1)) {
        while (*t > 0 && seedT - (*t - 1) <= cap && edgeInk(*l, *t - 1, *r, *t)) --*t;
        *fTop = *t < seedT ? kFlagNormalExpand : kFlagUnchanged;
    } else {
        while (*t < *b - 1 && (*t - seedT) < maxRetractPx && !edgeInk(*l, *t, *r, *t + 1)) ++*t;
        if (*t > seedT && edgeInk(*l, *t, *r, *t + 1)) *fTop = kFlagNormalRetract;
        else if (*t - seedT >= maxRetractPx) *fTop = kFlagBlocked10pct;
        else *fTop = kFlagNormalRetract;
    }
    if (edgeInk(*l, *b - 1, *r, *b)) {
        while (*b < imgH && *b - seedB < cap && edgeInk(*l, *b, *r, *b + 1)) ++*b;
        *fBot = *b > seedB ? kFlagNormalExpand : kFlagUnchanged;
    } else {
        while (*b > *t + 1 && (seedB - *b) < maxRetractPx && !edgeInk(*l, *b - 1, *r, *b)) --*b;
        if (*b < seedB && edgeInk(*l, *b - 1, *r, *b)) *fBot = kFlagNormalRetract;
        else if (seedB - *b >= maxRetractPx) *fBot = kFlagBlocked10pct;
        else *fBot = kFlagNormalRetract;
    }
    *allowUp = *t < seedT;
    *allowDown = *b > seedB;
}

using WalkEnergyFn = void (*)(
    const cv::Mat&, int, int, int, double, bool,
    int, int, int, int,
    int*, int*, int*, int*, bool*, bool*, int*, int*);

static jintArray insetAabbSeeds16(JNIEnv* env, jlong grayPtr, jintArray seedsArr) {
    if (!seedsArr) return nullptr;
    auto* gray = reinterpret_cast<cv::Mat*>(grayPtr);
    const int imgW = (gray && !gray->empty()) ? gray->cols : 0;
    const int imgH = (gray && !gray->empty()) ? gray->rows : 0;
    const jint n4 = env->GetArrayLength(seedsArr);
    if (n4 <= 0 || n4 % 4 != 0) return nullptr;
    std::vector<jint> s(static_cast<size_t>(n4));
    env->GetIntArrayRegion(seedsArr, 0, n4, s.data());
    const int n = n4 / 4;
    const int ins = 16;
    for (int i = 0; i < n; ++i) {
        int l = s[static_cast<size_t>(i) * 4], t = s[static_cast<size_t>(i) * 4 + 1];
        int r = s[static_cast<size_t>(i) * 4 + 2], b = s[static_cast<size_t>(i) * 4 + 3];
        if (imgW > 0) {
            if (l < 0) l = 0;
            if (t < 0) t = 0;
            if (r > imgW) r = imgW;
            if (b > imgH) b = imgH;
        }
        if (r - l > 2 * ins + 2) { l += ins; r -= ins; }
        if (b - t > 2 * ins + 2) { t += ins; b -= ins; }
        s[static_cast<size_t>(i) * 4] = l;
        s[static_cast<size_t>(i) * 4 + 1] = t;
        s[static_cast<size_t>(i) * 4 + 2] = r;
        s[static_cast<size_t>(i) * 4 + 3] = b;
    }
    jintArray out = env->NewIntArray(n4);
    if (!out) return nullptr;
    env->SetIntArrayRegion(out, 0, n4, s.data());
    return out;
}

static void walkEnergyHorz(
    const cv::Mat& look, int imgW, int imgH, int cap, double thr,
    int* l, int* t, int* r, int* b
) {
    for (int k = 0; k < cap; ++k) {
        bool grew = false;
        if (*l > 0 && meanRectF(look, *l - 1, *t, *l, *b, imgW, imgH) >= thr) {
            --*l;
            grew = true;
        }
        if (*r < imgW && meanRectF(look, *r, *t, *r + 1, *b, imgW, imgH) >= thr) {
            ++*r;
            grew = true;
        }
        if (!grew) break;
    }
}

static jintArray energyAabbOnLook(
    JNIEnv* env,
    jlong grayPtr, jlong uvPtr,
    jintArray seedsArr,
    WalkEnergyFn walk,
    jfloat maxFrac, jfloat energyRatio,
    jfloat jumpFrac, jfloat retractClearFrac,
    jfloatArray teleArr, jshortArray sweepArr,
    jlong scratchPtr,
    jboolean freezeHorz = JNI_FALSE
) {
    const jfloat vertPadFrac = 0.f;
    (void)uvPtr;
    if (!seedsArr) return env->NewIntArray(0);
    const jint n4 = env->GetArrayLength(seedsArr);
    if (n4 <= 0 || n4 % 4 != 0) {
        return env->NewIntArray(0);
    }
    const int n = n4 / 4;
    std::vector<jint> seeds(static_cast<size_t>(n4));
    env->GetIntArrayRegion(seedsArr, 0, n4, seeds.data());
    auto* gray = reinterpret_cast<cv::Mat*>(grayPtr);
    if (!gray || gray->empty() || gray->type() != CV_8UC1) {
        return aabbEnergySeedsOut(env, seeds);
    }
    try {
    const int imgW = gray->cols, imgH = gray->rows;

    cv::Mat* look = energyLookAs(scratchPtr, *gray);
    if (!look || !fillEnergyLookU8(*gray, look)) return aabbEnergySeedsOut(env, seeds);
    const cv::Mat& vertEng = *look;
    std::vector<jint> out(static_cast<size_t>(n) * 11, 0);
    std::vector<InkSweepPack> sweeps;
    sweeps.resize(static_cast<size_t>(n));
    for (int i = 0; i < n; ++i) {
        int l = seeds[static_cast<size_t>(i) * 4 + 0];
        int t = seeds[static_cast<size_t>(i) * 4 + 1];
        int r = seeds[static_cast<size_t>(i) * 4 + 2];
        int b = seeds[static_cast<size_t>(i) * 4 + 3];
        if (l < 0) l = 0;
        if (t < 0) t = 0;
        if (r > imgW) r = imgW;
        if (b > imgH) b = imgH;
        if (r <= l) r = std::min(imgW, l + 1);
        if (b <= t) b = std::min(imgH, t + 1);
        const int seedL = l, seedT = t, seedR = r, seedB = b;
        const int seedH = std::max(1, b - t);
        const int seedW = std::max(1, r - l);
        if (seedH < 4 || seedW < 4) {
            const int o = i * 11;
            out[static_cast<size_t>(o) + 0] = seedL;
            out[static_cast<size_t>(o) + 1] = seedT;
            out[static_cast<size_t>(o) + 2] = seedR;
            out[static_cast<size_t>(o) + 3] = seedB;
            out[static_cast<size_t>(o) + 4] = seedL;
            out[static_cast<size_t>(o) + 5] = seedT;
            out[static_cast<size_t>(o) + 6] = seedR;
            out[static_cast<size_t>(o) + 7] = seedB;
            continue;
        }
        const int cap = std::max(1, static_cast<int>(std::lround(maxFrac * seedH)));
        const int il = l + 2, it = t + 2, ir = r - 2, ib = b - 2;
        const double base = (ir > il && ib > it)
            ? meanRectF(vertEng, il, it, ir, ib, imgW, imgH)
            : meanRectF(vertEng, l, t, r, b, imgW, imgH);
        const double thr = energyRatio * std::max(base, 1e-3);
        const double jumpThr = thr;
        bool allowUp = false, allowDown = false;
        int walkT = 0, walkB = 0;
        int fTop = kFlagUnchanged, fBot = kFlagUnchanged;
        walk(vertEng, imgW, imgH, cap, thr, freezeHorz == JNI_TRUE,
            seedL, seedT, seedR, seedB,
            &l, &t, &r, &b, &allowUp, &allowDown, &fTop, &fBot);
        walkT = seedT - t;
        walkB = b - seedB;
        if (vertPadFrac > 0.f) {
            const int extra = std::max(1, static_cast<int>(std::lround(vertPadFrac * seedH)));
            if (allowUp) t = std::max(0, t - extra);
            if (allowDown) b = std::min(imgH, b + extra);
        }
        const int hit = (walkT >= cap || walkB >= cap) ? 1 : 0;
        const bool stopUpE = walkT < cap;
        const bool stopDownE = walkB < cap;
        if (freezeHorz == JNI_TRUE) {
            walkEnergyHorz(vertEng, imgW, imgH, cap, thr, &l, &t, &r, &b);
        }
        int farL = l, farR = r;
        jumpRetractH(vertEng, &l, t, &r, b, imgW, imgH, jumpThr, cap,
                     jumpFrac, retractClearFrac, seedH, &farL, &farR);
        if (l < 0) l = 0;
        if (t < 0) t = 0;
        if (r > imgW) r = imgW;
        if (b > imgH) b = imgH;
        if (r <= l) r = std::min(imgW, l + 1);
        if (b <= t) b = std::min(imgH, t + 1);
        int ct = t, cb = b, pT = 0, pB = 0;
        countPullYFromU8Gray(*look, seedL, seedT, seedR, seedB, l, t, r, b,
                   imgW, imgH, stopUpE, stopDownE, &ct, &cb, &pT, &pB);
        const int o = i * 11;
        out[static_cast<size_t>(o) + 0] = l;
        out[static_cast<size_t>(o) + 1] = t;
        out[static_cast<size_t>(o) + 2] = r;
        out[static_cast<size_t>(o) + 3] = b;
        out[static_cast<size_t>(o) + 4] = l;
        out[static_cast<size_t>(o) + 5] = ct;
        out[static_cast<size_t>(o) + 6] = r;
        out[static_cast<size_t>(o) + 7] = cb;
        out[static_cast<size_t>(o) + 8] = hit;
        out[static_cast<size_t>(o) + 9] = pT;
        out[static_cast<size_t>(o) + 10] = pB;
        (void)fTop;
        (void)fBot;
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
        tele.farL = static_cast<float>(farL);
        tele.farR = static_cast<float>(farR);
        const int ht = std::max(0, t - 2), hb = std::min(imgH, b + 2);
        const int hl = std::max(0, l), hr = std::min(imgW, r);
        if (hb > ht && hr > hl && !vertEng.empty() && vertEng.type() == CV_8UC1) {
            fillRunHists(vertEng, hl, ht, hr, hb, thr, tele.histH, tele.histV);
        }
        storeTeleArr(env, teleArr, i, tele);
        fillAabbEnergySweep(
            vertEng, vertEng, seedL, seedT, seedR, seedB,
            std::max(1, b - t), imgW, imgH, energyRatio, thr, jumpFrac,
            &sweeps[static_cast<size_t>(i)]);
    }
    writeSweepArr(env, sweepArr, sweeps);
    jintArray arr = env->NewIntArray(static_cast<jint>(out.size()));
    if (!arr) return aabbEnergySeedsOut(env, seeds);
    env->SetIntArrayRegion(arr, 0, static_cast<jint>(out.size()), out.data());
    return arr;
    } catch (const cv::Exception&) {
        return aabbEnergySeedsOut(env, seeds);
    } catch (const std::exception&) {
        return aabbEnergySeedsOut(env, seeds);
    }
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeEnergyAabbTight(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr, jlong uvPtr, jintArray seedsArr,
    jfloatArray teleArr, jshortArray sweepArr, jlong scratchPtr
) {
    return energyAabbOnLook(env, grayPtr, uvPtr, seedsArr, walkEnergyExpand, 0.4f, 0.65f, 0.40f, 0.30f, teleArr, sweepArr, scratchPtr, JNI_TRUE);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeEnergyAabbRetract(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr, jlong uvPtr, jintArray seedsArr,
    jfloatArray teleArr, jshortArray sweepArr, jlong scratchPtr
) {
    return energyAabbOnLook(env, grayPtr, uvPtr, seedsArr, walkEnergyRetract, 0.4f, 0.65f, 0.40f, 0.30f, teleArr, sweepArr, scratchPtr, JNI_TRUE);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeEnergyAabbExpand(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr, jlong uvPtr, jintArray seedsArr,
    jfloatArray teleArr, jshortArray sweepArr, jlong scratchPtr
) {
    return energyAabbOnLook(env, grayPtr, uvPtr, seedsArr, walkEnergyExpand, 0.4f, 0.65f, 0.40f, 0.30f, teleArr, sweepArr, scratchPtr);
}

static jfloatArray energyOrientSeedOut(JNIEnv* env, const jfloat pts[8]) {
    Frame fr{};
    if (!frameFromQuad(pts, &fr)) return env->NewFloatArray(0);
    jfloat out[13] = {
        fr.cx, fr.cy, fr.bw, fr.bh, fr.angDeg,
        0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f,
    };
    jfloatArray arr = env->NewFloatArray(13);
    if (!arr) return env->NewFloatArray(0);
    env->SetFloatArrayRegion(arr, 0, 13, out);
    return arr;
}

static void walkEnergyOrientExpand(
    const cv::Mat& mag, const Frame& fr,
    int cap, double thr, bool freezeHorz,
    float* cx, float* cy, float* bw, float* bh,
    int* stepsVNeg, int* stepsVPos,
    bool* allowVNeg, bool* allowVPos
) {
    auto strip = [&](float du, float dv, bool alongU) {
        return stripEnergy(mag, fr, *cx, *cy, *bw, *bh, du, dv, alongU);
    };
    *allowVNeg = strip(0.f, -1.f, false) >= thr;
    *allowVPos = strip(0.f, +1.f, false) >= thr;
    *stepsVNeg = 0;
    *stepsVPos = 0;
    for (int step = 0; step < cap; ++step) {
        bool grew = false;
        if (!freezeHorz) {
            if (strip(-1.f, 0.f, true) >= thr) {
                *cx -= 0.5f * fr.ux; *cy -= 0.5f * fr.uy; *bw += 1.f; grew = true;
            }
            if (strip(+1.f, 0.f, true) >= thr) {
                *cx += 0.5f * fr.ux; *cy += 0.5f * fr.uy; *bw += 1.f; grew = true;
            }
        }
        if (*allowVNeg && strip(0.f, -1.f, false) >= thr) {
            *cx -= 0.5f * fr.vx; *cy -= 0.5f * fr.vy; *bh += 1.f; ++*stepsVNeg; grew = true;
        }
        if (*allowVPos && strip(0.f, +1.f, false) >= thr) {
            *cx += 0.5f * fr.vx; *cy += 0.5f * fr.vy; *bh += 1.f; ++*stepsVPos; grew = true;
        }
        if (!grew) break;
    }
}

static void walkEnergyOrientRetract(
    const cv::Mat& mag, const Frame& fr,
    int cap, double thr, bool /*freezeHorz*/,
    float* cx, float* cy, float* bw, float* bh,
    int* stepsVNeg, int* stepsVPos,
    bool* allowVNeg, bool* allowVPos
) {
    auto strip = [&](float du, float dv, bool alongU) {
        return stripEnergy(mag, fr, *cx, *cy, *bw, *bh, du, dv, alongU);
    };
    auto onEdgeV = [&](float dvSign) {
        return stripEnergy(mag, fr, *cx, *cy, *bw, std::max(2.f, *bh - 1.f),
            0.f, dvSign, false);
    };
    const float seedBh = *bh;
    *stepsVNeg = 0;
    *stepsVPos = 0;
    if (onEdgeV(-1.f) >= thr) {
        while (*stepsVNeg < cap && strip(0.f, -1.f, false) >= thr) {
            *cx -= 0.5f * fr.vx; *cy -= 0.5f * fr.vy; *bh += 1.f; ++*stepsVNeg;
        }
        *allowVNeg = *stepsVNeg > 0;
    } else {
        const int maxRetractPx = std::max(1, static_cast<int>(std::lround(kVertRetractCapFrac * seedBh)));
        int nRetr = 0;
        while (*bh > 2.f && nRetr < maxRetractPx && onEdgeV(-1.f) < thr) {
            *cx += 0.5f * fr.vx; *cy += 0.5f * fr.vy; *bh -= 1.f; ++nRetr;
        }
        *allowVNeg = false;
    }
    if (onEdgeV(+1.f) >= thr) {
        while (*stepsVPos < cap && strip(0.f, +1.f, false) >= thr) {
            *cx += 0.5f * fr.vx; *cy += 0.5f * fr.vy; *bh += 1.f; ++*stepsVPos;
        }
        *allowVPos = *stepsVPos > 0;
    } else {
        const int maxRetractPx = std::max(1, static_cast<int>(std::lround(kVertRetractCapFrac * seedBh)));
        int nRetr = 0;
        while (*bh > 2.f && nRetr < maxRetractPx && onEdgeV(+1.f) < thr) {
            *cx -= 0.5f * fr.vx; *cy -= 0.5f * fr.vy; *bh -= 1.f; ++nRetr;
        }
        *allowVPos = false;
    }
}

using WalkEnergyOrientFn = void (*)(
    const cv::Mat&, const Frame&,
    int, double, bool,
    float*, float*, float*, float*,
    int*, int*, bool*, bool*);

static jfloatArray runEnergyOrientOne(
    JNIEnv* env, jlong grayPtr, jfloatArray seedPts, WalkEnergyOrientFn walk,
    jshortArray sweepArr, jlong scratchPtr, bool freezeHorz
) {
    const jfloat maxFrac = 0.4f;
    const jfloat energyRatio = 0.65f;
    const jfloat jumpFrac = 0.40f;
    const jfloat retractClearFrac = 0.30f;
    const jfloat vertPadFrac = 0.f;
    if (!seedPts || env->GetArrayLength(seedPts) < 8) return env->NewFloatArray(0);
    jfloat pts[8];
    env->GetFloatArrayRegion(seedPts, 0, 8, pts);
    auto* gray = reinterpret_cast<cv::Mat*>(grayPtr);
    if (!gray || gray->empty() || gray->type() != CV_8UC1) {
        return energyOrientSeedOut(env, pts);
    }
    try {
    Frame fr{};
    if (!frameFromQuad(pts, &fr)) return energyOrientSeedOut(env, pts);
    fr.imgW = gray->cols;
    fr.imgH = gray->rows;
    cv::Mat* look = energyLookAs(scratchPtr, *gray);
    if (!look) return energyOrientSeedOut(env, pts);
    const cv::Mat& mag = *look;
    float cx = fr.cx, cy = fr.cy, bw = fr.bw, bh = fr.bh;
    const float seedCx = cx, seedCy = cy, seedBw = bw, seedBh0 = bh;
    if (bw < 4.f || bh < 4.f) {
        jfloat out[13] = { cx, cy, bw, bh, fr.angDeg, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f };
        jfloatArray arr = env->NewFloatArray(13);
        if (!arr) return energyOrientSeedOut(env, pts);
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
    int stepsVNeg = 0, stepsVPos = 0;
    bool allowVNeg = false, allowVPos = false;
    if (walk) {
        walk(mag, fr, cap, thr, freezeHorz,
            &cx, &cy, &bw, &bh, &stepsVNeg, &stepsVPos, &allowVNeg, &allowVPos);
    }
    auto strip = [&](float du, float dv, bool alongU) {
        return stripEnergy(mag, fr, cx, cy, bw, bh, du, dv, alongU);
    };
    int padV = 0;
    if (vertPadFrac > 0.f) {
        padV = std::max(1, static_cast<int>(std::lround(vertPadFrac * seedBh)));
        if (allowVNeg) { cx -= 0.5f * fr.vx * padV; cy -= 0.5f * fr.vy * padV; bh += static_cast<float>(padV); }
        if (allowVPos) { cx += 0.5f * fr.vx * padV; cy += 0.5f * fr.vy * padV; bh += static_cast<float>(padV); }
    }
    {
        const float floorBw = bw, floorCx = cx, floorCy = cy;
        const float j = std::max(1.f, jumpFrac * bh);
        bw += 2.f * j;
        const bool stillText = strip(-1.f, 0.f, true) >= thr || strip(+1.f, 0.f, true) >= thr;
        if (stillText) {
            for (int step = 0; step < cap; ++step) {
                bool grew = false;
                if (strip(-1.f, 0.f, true) >= thr) { cx -= 0.5f * fr.ux; cy -= 0.5f * fr.uy; bw += 1.f; grew = true; }
                if (strip(+1.f, 0.f, true) >= thr) { cx += 0.5f * fr.ux; cy += 0.5f * fr.uy; bw += 1.f; grew = true; }
                if (!grew) break;
            }
        } else {
            while (bw > floorBw + 0.5f && strip(-1.f, 0.f, true) < thr) { cx += 0.5f * fr.ux; cy += 0.5f * fr.uy; bw -= 1.f; }
            while (bw > floorBw + 0.5f && strip(+1.f, 0.f, true) < thr) { cx -= 0.5f * fr.ux; cy -= 0.5f * fr.uy; bw -= 1.f; }
            if (bw < floorBw) { bw = floorBw; cx = floorCx; cy = floorCy; }
            const float clear = std::max(1.f, retractClearFrac * bh);
            bw += 2.f * clear;
        }
    }
    const int hitVertCap = (stepsVNeg >= cap || stepsVPos >= cap) ? 1 : 0;
    const float halfSeed = seedBh * 0.5f;
    auto stripAtSeedV = [&](float offsetFromCenterV) {
        const int n = std::max(4, static_cast<int>(std::lround(fr.bw)));
        double sum = 0.0; int cnt = 0;
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
    const float stopUpE = static_cast<float>(stripAtSeedV(-halfSeed - stepsVNeg - 1.f));
    const float stopDownE = static_cast<float>(stripAtSeedV(halfSeed + stepsVPos + 1.f));
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
    if (!arr) return energyOrientSeedOut(env, pts);
    env->SetFloatArrayRegion(arr, 0, 13, out);
    return arr;
    } catch (const cv::Exception&) {
        return energyOrientSeedOut(env, pts);
    } catch (const std::exception&) {
        return energyOrientSeedOut(env, pts);
    }
}

__attribute__((unused))
static jfloatArray insetEnergyOrientSeeds16(JNIEnv* env, jfloatArray seedsArr) {
    if (!seedsArr) return nullptr;
    const jint n8 = env->GetArrayLength(seedsArr);
    if (n8 <= 0 || n8 % 8 != 0) return nullptr;
    std::vector<jfloat> s(static_cast<size_t>(n8));
    env->GetFloatArrayRegion(seedsArr, 0, n8, s.data());
    const int n = n8 / 8;
    const float ins = 16.f;
    for (int i = 0; i < n; ++i) {
        Frame fr{};
        if (!frameFromQuad(s.data() + i * 8, &fr)) continue;
        if (fr.bw > 2.f * ins + 2.f) fr.bw -= 2.f * ins;
        if (fr.bh > 2.f * ins + 2.f) fr.bh -= 2.f * ins;
        const float hu = fr.bw * 0.5f;
        const float hv = fr.bh * 0.5f;
        auto setc = [&](int k, float u, float v) {
            s[static_cast<size_t>(i) * 8 + k * 2] = fr.cx + u * fr.ux + v * fr.vx;
            s[static_cast<size_t>(i) * 8 + k * 2 + 1] = fr.cy + u * fr.uy + v * fr.vy;
        };
        setc(0, -hu, -hv);
        setc(1, +hu, -hv);
        setc(2, +hu, +hv);
        setc(3, -hu, +hv);
    }
    jfloatArray out = env->NewFloatArray(n8);
    if (!out) return nullptr;
    env->SetFloatArrayRegion(out, 0, n8, s.data());
    return out;
}

static jfloatArray energyOrientOnLook(
    JNIEnv* env, jlong grayPtr, jfloatArray seedsArr, WalkEnergyOrientFn walk, jshortArray sweepArr,
    jlong scratchPtr, bool freezeHorz = false
) {
    if (!seedsArr) return env->NewFloatArray(0);
    const jint n8 = env->GetArrayLength(seedsArr);
    if (n8 <= 0 || n8 % 8 != 0) return env->NewFloatArray(0);
    auto* gray = reinterpret_cast<cv::Mat*>(grayPtr);
    if (!gray || gray->empty() || gray->type() != CV_8UC1) return env->NewFloatArray(0);
    cv::Mat* look = energyLookAs(scratchPtr, *gray);
    if (!look || !fillEnergyLookU8(*gray, look)) return env->NewFloatArray(0);
    const int n = n8 / 8;
    std::vector<jfloat> all(static_cast<size_t>(n) * 13, 0.f);
    for (int i = 0; i < n; ++i) {
        jfloat one[8];
        env->GetFloatArrayRegion(seedsArr, i * 8, 8, one);
        jfloatArray oneArr = env->NewFloatArray(8);
        if (!oneArr) continue;
        env->SetFloatArrayRegion(oneArr, 0, 8, one);
        jfloatArray r = runEnergyOrientOne(
            env, grayPtr, oneArr, walk, i == 0 ? sweepArr : nullptr, scratchPtr, freezeHorz);
        if (r && env->GetArrayLength(r) >= 13) {
            env->GetFloatArrayRegion(r, 0, 13, all.data() + i * 13);
        } else {
            Frame fr{};
            if (frameFromQuad(one, &fr)) {
                jfloat* d = all.data() + static_cast<size_t>(i) * 13;
                d[0] = fr.cx; d[1] = fr.cy; d[2] = fr.bw; d[3] = fr.bh; d[4] = fr.angDeg;
            }
        }
    }
    jfloatArray out = env->NewFloatArray(static_cast<jint>(all.size()));
    if (!out) return env->NewFloatArray(0);
    env->SetFloatArrayRegion(out, 0, static_cast<jint>(all.size()), all.data());
    return out;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeEnergyOrientTight(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr, jfloatArray seedsArr, jshortArray sweepArr, jlong scratchPtr
) {
    return energyOrientOnLook(
        env, grayPtr, seedsArr, walkEnergyOrientExpand, sweepArr, scratchPtr, true);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeEnergyOrientRetract(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr, jfloatArray seedsArr, jshortArray sweepArr, jlong scratchPtr
) {
    return energyOrientOnLook(
        env, grayPtr, seedsArr, walkEnergyOrientRetract, sweepArr, scratchPtr, true);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeEnergyOrientExpand(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr, jfloatArray seedsArr, jshortArray sweepArr, jlong scratchPtr
) {
    return energyOrientOnLook(env, grayPtr, seedsArr, walkEnergyOrientExpand, sweepArr, scratchPtr);
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
    int ovX = 0, int ovY = 0, PoisonStats* statsOut = nullptr,
    cv::Mat* scratch = nullptr,
    cv::Mat* objPlane = nullptr, ObjPack* objPack = nullptr, int seedIndex = 0,
    bool overlayUvMap = false,
    float ovCx = 0.f, float ovCy = 0.f,
    float ovUx = 0.f, float ovUy = 0.f,
    float ovVx = 0.f, float ovVy = 0.f,
    float ovU0 = 0.f, float ovU1 = 0.f, float ovLookV0 = 0.f,
    cv::Mat* lookPoisonOut = nullptr, bool paintOverlay = true,
    cv::Mat* poisonPlane = nullptr,
    cv::Mat* lookInkAtOut = nullptr);
static void aabbJumpOnLook(
    cv::Mat* look, int* l, int t, int* r, int b,
    int imgW, int imgH, int seedT, int seedB, int seedL, int seedR, int sPx,
    int lookOx = 0, int lookOy = 0, int* farL = nullptr, int* farR = nullptr);
static void paintLookOverlay(
    const cv::Mat& lookBin, const cv::Mat& lookPoison,
    cv::Mat* overlayY, cv::Mat* overlayUv,
    bool overlayUvMap, int ovX, int ovY,
    float ovCx, float ovCy, float ovUx, float ovUy,
    float ovVx, float ovVy, float ovU0, float ovU1, float ovLookV0);

static void fillAabbLookSweep(
    const cv::Mat& src, bool srcIsBin, double otsu, bool darkInk, int glareW,
    const cv::Mat& lookBin, int nt,
    int sl, int st, int sr, int sb,
    int walkedT, int walkedB,
    int imgW, int imgH, int minRun, float sPx,
    InkSweepPack* out
) {
    if (!out || src.empty()) return;
    veRssLog("sweep", "enter");
    if (sl < 0) sl = 0;
    if (st < 0) st = 0;
    if (sr > imgW) sr = imgW;
    if (sb > imgH) sb = imgH;
    if (sr <= sl || sb <= st) return;
    const int seedH = std::max(1, sb - st);
    const int capPx = std::max(1, static_cast<int>(std::lround(2.5f * seedH)));
    const int walkedH = std::max(1, walkedB - walkedT);
    const int xPad = std::max(1, static_cast<int>(std::lround(0.50f * walkedH * (kJumpMax + 1))));
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
    veRssLog("sweep", "after packSeedLookRows");
    if (x1 <= x0) return;
    auto pixInk = [&](int y, int x) -> bool {
        if (y < 0 || y >= src.rows || x < 0 || x >= src.cols) return false;
        const uint8_t v = src.ptr<uint8_t>(y)[x];
        if (srcIsBin) return v != 0;
        return darkInk
            ? (static_cast<double>(v) <= otsu)
            : (static_cast<double>(v) > otsu);
    };
    out->hScores.reserve(static_cast<size_t>(x1 - x0));
    for (int x = x0; x < x1; ++x) {
        int best = 0, run = 0;
        for (int y = st; y < sb; ++y) {
            bool on = pixInk(y, x);
            if (on && glareW > 0) {
                int xl = x, xr = x + 1;
                while (xl > x0 && pixInk(y, xl - 1)) --xl;
                while (xr < x1 && pixInk(y, xr)) ++xr;
                if (xr - xl > glareW) on = false;
            }
            if (on) {
                ++run;
                if (run > best) best = run;
            } else {
                run = 0;
            }
        }
        out->hScores.push_back(best);
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

__attribute__((unused))
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

static void dropTallCCs(cv::Mat* bin, int maxH) {
    if (!bin || bin->empty() || maxH <= 0) return;
    cv::Mat labels, stats, centroids;
    const int nLab = cv::connectedComponentsWithStats(*bin, labels, stats, centroids, 8);
    if (nLab <= 1) return;
    std::vector<char> drop(nLab, 0);
    int dropped = 0;
    for (int i = 1; i < nLab; ++i) {
        const int h = stats.at<int>(i, cv::CC_STAT_HEIGHT);
        if (h > maxH) {
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
    int glareMult = 9,
    int boundStrategy = 0,
    int tightInsetPx = 16,
    Seg7Tele* tele = nullptr,
    bool keepColorStats = false,
    InkSweepPack* sweepOut = nullptr,
    cv::Mat* inkDump = nullptr,
    cv::Mat* overlayY = nullptr,
    cv::Mat* overlayUv = nullptr,
    PoisonStats* poisonStats = nullptr,
    cv::Mat* scratch = nullptr,
    ObjPack* objPack = nullptr,
    int seedIndex = 0,
    cv::Mat* poisonPlane = nullptr
) {
    if (boundStrategy == 1) {
        const int ins = std::max(1, tightInsetPx);
        if (sr - sl > 2 * ins + 2) { sl += ins; sr -= ins; }
        if (sb - st > 2 * ins + 2) { st += ins; sb -= ins; }
    }
    *ol = sl; *ot = st; *oright = sr; *ob = sb;
    const int seedH = std::max(1, sb - st);
    const int seedW = std::max(1, sr - sl);
    LOGI("veAllocLog tag=seg7One seedW=%d seedH=%d imgW=%d imgH=%d",
         seedW, seedH, imgW, imgH);
    if (!veAllocLog("seg7One", veMatBytes(imgH, imgW, CV_8UC1), imgH, imgW, CV_8UC1)) {
        return;
    }
    const int fallback = std::max(2, static_cast<int>(std::lround(0.08f * seedH)));
    *sPxOut = fallback;
    *vSWOut = 4;
    *hSWOut = 4;
    *usedFb = 1;
    if (sr <= sl || sb <= st || src.empty() || src.type() != CV_8UC1) return;
    if (seedH < 4 || seedW < 4) return;
    const int gm = glareMult > 0 ? glareMult : 9;
    const int capPx = std::max(1, static_cast<int>(std::lround(2.5f * seedH)));
    const int vLook = capPx + 2;
    const int nt = std::max(0, st - vLook);
    const int nb = std::min(imgH, sb + vLook);
    const int xPad = std::max(1, static_cast<int>(std::lround(
        0.50f * static_cast<float>(seedH) * static_cast<float>(kJumpMax + 1))));
    const int lookL = std::max(0, sl - xPad);
    const int lookR = std::min(imgW, sr + xPad);
    if (lookR <= lookL || nb <= nt) return;
    const int lookH = nb - nt;
    const int lookW = lookR - lookL;
    const int xSeed0 = sl - lookL;
    LOGI("veAllocLog tag=seg7One lookW=%d lookH=%d seedW=%d seedH=%d imgW=%d imgH=%d",
         lookW, lookH, seedW, seedH, imgW, imgH);
    if (!veAllocLog("seg7One_look", veMatBytes(lookH, lookW, CV_8UC1), lookH, lookW, CV_8UC1)) {
        return;
    }
    cv::Mat look = src(cv::Range(nt, nb), cv::Range(lookL, lookR));
    cv::Mat seedY = src(cv::Range(st, sb), cv::Range(sl, sr));
    cv::Mat lookBin;
    cv::Mat* lookPlane = asU8(scratch);
    if (lookPlane) {
        lookBin = planeRoi8u(lookPlane, lookL, nt, lookW, lookH);
    }
    const int localT = st - nt;
    const int localB = sb - nt;
    PoisonStats stLocal;
    cv::Mat* objPlane = asU8(inkDump);
    if (objPlane && (objPlane->cols < imgW || objPlane->rows < imgH)) objPlane = nullptr;
    cv::Mat lookPoison;
    cv::Mat lookInkAtPlane;
    cv::Mat* overlayY8 = asU8(overlayY);
    cv::Mat* overlayUv2 = asUV(overlayUv);
    cv::Mat* pois = asU8(poisonPlane);
    {
        char extra[256];
        std::snprintf(extra, sizeof extra,
            "lookW=%d lookH=%d seedW=%d seedH=%d imgW=%d imgH=%d lookBin=%dx%d t=%d step=%zu scratch=%dx%d t=%d poisonPlane=%dx%d",
            lookW, lookH, seedW, seedH, imgW, imgH,
            lookBin.rows, lookBin.cols, lookBin.type(), lookBin.empty() ? 0 : lookBin.step[0],
            lookPlane ? lookPlane->rows : -1, lookPlane ? lookPlane->cols : -1,
            lookPlane ? lookPlane->type() : -1,
            pois ? pois->rows : -1, pois ? pois->cols : -1);
        veRssLog("seg7_look", extra);
    }
    const int sPx = fillPoisonLookRaster(
        seedY, look, localT, xSeed0, srcIsBin, gm, fallback, &lookBin,
        overlayY8, overlayUv2, lookL, nt, poisonStats ? &stLocal : nullptr, lookPlane,
        objPlane, objPack, seedIndex, false,
        0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, &lookPoison, false,
        pois, &lookInkAtPlane);
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
    const int minRun = usedMinRun(
        sPx, maxInSeedRunRows(lookBin, localT, localB, xSeed0, xSeed0 + seedW));
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
    auto walkV = [&]() {
        t = localT;
        b = localB;
        fTop = kFlagUnchanged;
        fBot = kFlagUnchanged;
        gapJumpTop = 0;
        gapJumpBot = 0;
        landTop = -1;
        landBot = -1;
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
    };
    walkV();
    dropTallCCs(&lookBin, 18 * std::max(1, sPx));
    walkV();
    *ol = sl;
    *ot = nt + t;
    *oright = sr;
    *ob = nt + b;
    if (*ot < 0) *ot = 0;
    if (*ob > imgH) *ob = imgH;
    if (*ob <= *ot) *ob = std::min(imgH, *ot + 1);
    int farL = *ol, farR = *oright;
    aabbJumpOnLook(
        &lookBin, ol, *ot, oright, *ob, imgW, imgH,
        st, sb, sl, sr, sPx, lookL, nt, &farL, &farR);
    veRssLog("walk_paint", nullptr);
    if (*ol < 0) *ol = 0;
    if (*oright > imgW) *oright = imgW;
    if (*oright <= *ol) *oright = std::min(imgW, *ol + 1);
    if (tele) {
        if (!keepColorStats) {
            float yi = 0.f, yb = 0.f;
            const int y0s = std::max(0, localT);
            const int y1s = std::min(lookBin.rows, localB);
            if (y1s > y0s) {
                const int x0s = std::max(0, xSeed0);
                const int x1s = std::min(lookBin.cols, xSeed0 + seedW);
                if (x1s > x0s) {
                    cv::Mat seedInk = lookBin(cv::Range(y0s, y1s), cv::Range(x0s, x1s));
                    fillYInkBg(src, seedInk, sl, st, sPx, &yi, &yb);
                }
            }
            tele->yInk = yi;
            tele->yBg = yb;
            tele->dInk = yi - yb;
            tele->otsuThr = 0.f;
        }
        tele->sPx = static_cast<float>(sPx);
        tele->dTop = static_cast<float>(*ot - st);
        tele->dBot = static_cast<float>(*ob - sb);
        tele->dLeft = static_cast<float>(*ol - sl);
        tele->dRight = static_cast<float>(*oright - sr);
        tele->fTop = static_cast<float>(fTop);
        tele->fBot = static_cast<float>(fBot);
        tele->fLeft = static_cast<float>(kFlagUnchanged);
        tele->fRight = static_cast<float>(kFlagUnchanged);
        tele->gapJumpTop = gapJumpTop ? 1.f : 0.f;
        tele->gapJumpBot = gapJumpBot ? 1.f : 0.f;
        tele->landTop = landTop >= 0 ? static_cast<float>(nt + landTop) : 0.f;
        tele->landBot = landBot >= 0 ? static_cast<float>(nt + landBot) : 0.f;
        tele->farL = static_cast<float>(farL);
        tele->farR = static_cast<float>(farR);
        tele->nInkSeed = static_cast<float>(
            countInkU8(lookInkAtPlane, sl - lookL, localT, sr - lookL, localB));
        tele->nInkBlue = static_cast<float>(
            countInkU8(lookInkAtPlane, *ol - lookL, *ot - nt, *oright - lookL, *ob - nt));
        tele->nInkYellow = static_cast<float>(
            countInkU8(
                lookInkAtPlane, farL - lookL, std::min(st, *ot) - nt,
                farR - lookL, std::max(sb, *ob) - nt));
        fillRunHists(lookBin, tele->histH, tele->histV);
    }
    if (sweepOut && !lookBin.empty()) {
        fillAabbLookSweep(
            src, true, 0.0, true, 0, lookBin, nt,
            sl, st, sr, sb, *ot, *ob, imgW, imgH, minRun,
            static_cast<float>(sPx), sweepOut);
    }
    paintLookOverlay(
        lookBin, lookPoison, overlayY8, overlayUv2, false, lookL, nt,
        0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f);
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

static void fillPoisonMask(
    const cv::Mat& bin, int v0, bool needFb, int seedW, int glareMult, cv::Mat* poison
) {
    const int h = bin.rows, w = bin.cols;
    if (!poison) return;
    if (poison->empty() || poison->rows != h || poison->cols != w || poison->type() != CV_8UC1) {
        return;
    }
    poison->setTo(0);
    const int vRef = std::max(v0, 4);
    const int fat = 3 * vRef;
    const int longH = (glareMult > 0 ? glareMult : 9) * vRef;
    const int thinW = std::max(1, static_cast<int>(std::lround(0.25f * static_cast<float>(seedW))));
    const bool weak = v0 <= 4 || needFb;
    // 0 clean, 1 H-candidate, 255 poison. longH/weak mark 1 (not 255).
    // Fat V promotes 1→255. Thin scratch: H>longH and V<vRef → 255. Leftover 1→0.
    for (int y = 0; y < h; ++y) {
        const uint8_t* bp = bin.ptr<uint8_t>(y);
        uint8_t* pp = poison->ptr<uint8_t>(y);
        int x = 0;
        while (x < w) {
            if (!bp[x]) { ++x; continue; }
            const int x0 = x;
            while (x < w && bp[x]) ++x;
            const int len = x - x0;
            uint8_t mark = 0;
            if (len > longH || (weak && len > thinW) || len > fat) mark = 1;
            if (mark) {
                for (int k = x0; k < x; ++k) pp[k] = mark;
            }
        }
    }
    for (int x = 0; x < w; ++x) {
        int y = 0;
        while (y < h) {
            if (!bin.ptr<uint8_t>(y)[x]) { ++y; continue; }
            const int y0 = y;
            while (y < h && bin.ptr<uint8_t>(y)[x]) ++y;
            const int len = y - y0;
            if (len > fat) {
                for (int k = y0; k < y; ++k) {
                    uint8_t* pp = poison->ptr<uint8_t>(k);
                    if (pp[x] == 1) pp[x] = 255;
                }
            }
        }
    }
    for (int y = 0; y < h; ++y) {
        const uint8_t* bp = bin.ptr<uint8_t>(y);
        uint8_t* pp = poison->ptr<uint8_t>(y);
        int x = 0;
        while (x < w) {
            if (!bp[x]) { ++x; continue; }
            const int x0 = x;
            while (x < w && bp[x]) ++x;
            const int hlen = x - x0;
            if (hlen <= longH) continue;
            for (int k = x0; k < x; ++k) {
                int yt = y;
                int yb = y;
                while (yt > 0 && bin.ptr<uint8_t>(yt - 1)[k]) --yt;
                while (yb + 1 < h && bin.ptr<uint8_t>(yb + 1)[k]) ++yb;
                if (yb - yt + 1 < vRef) pp[k] = 255;
            }
        }
    }
    for (int y = 0; y < h; ++y) {
        uint8_t* pp = poison->ptr<uint8_t>(y);
        for (int x = 0; x < w; ++x) {
            if (pp[x] == 1) pp[x] = 0;
        }
    }
}

static double otsuThrFromHist(const int hist[256], int n) {
    if (n < 2) return 0.0;
    double sum = 0.0;
    for (int i = 0; i < 256; ++i) sum += static_cast<double>(i) * hist[i];
    double sumB = 0.0;
    int wB = 0;
    double maxVar = -1.0;
    int thr = 0;
    for (int t = 0; t < 256; ++t) {
        wB += hist[t];
        if (wB == 0) continue;
        const int wF = n - wB;
        if (wF == 0) break;
        sumB += static_cast<double>(t) * hist[t];
        const double mB = sumB / wB;
        const double mF = (sum - sumB) / wF;
        const double d = mB - mF;
        const double var = static_cast<double>(wB) * static_cast<double>(wF) * d * d;
        if (var >= maxVar) {
            maxVar = var;
            thr = t;
        }
    }
    return static_cast<double>(thr);
}

static bool otsuHistOnKeep(
    const cv::Mat& y, const cv::Mat& mask, bool keepIfMaskOn, double yCeil,
    double* thr, bool* dark, float* inkFrac
) {
    int hist[256] = {};
    int n = 0;
    const int kh = std::min(y.rows, mask.rows);
    const int kw = std::min(y.cols, mask.cols);
    if (kh < 1 || kw < 1) return false;
    for (int yy = 0; yy < kh; ++yy) {
        const uint8_t* yp = y.ptr<uint8_t>(yy);
        const uint8_t* mp = mask.ptr<uint8_t>(yy);
        for (int xx = 0; xx < kw; ++xx) {
            if (keepIfMaskOn) {
                if (!mp[xx]) continue;
            } else {
                if (mp[xx]) continue;
            }
            if (yCeil >= 0.0 && static_cast<double>(yp[xx]) > yCeil) continue;
            hist[yp[xx]]++;
            ++n;
        }
    }
    if (n < 2) return false;
    *thr = otsuThrFromHist(hist, n);
    const int ti = static_cast<int>(*thr);
    int nz = 0;
    for (int i = 0; i <= ti && i < 256; ++i) nz += hist[i];
    *inkFrac = nz / static_cast<float>(n);
    *dark = true;
    if (*inkFrac >= 0.45f) {
        *dark = false;
        nz = n - nz;
        *inkFrac = nz / static_cast<float>(n);
    }
    return true;
}

static bool otsuKeep(
    const cv::Mat& y, const cv::Mat& keep, double* thr, bool* dark, float* inkFrac
) {
    return otsuHistOnKeep(y, keep, true, -1.0, thr, dark, inkFrac);
}

static void applyThrKeep(
    const cv::Mat& y, const cv::Mat& keep, double thr, bool dark, cv::Mat* out
) {
    if (!out) return;
    const int h = y.rows, w = y.cols;
    if (out->empty() || out->rows != h || out->cols != w || out->type() != CV_8UC1) {
        return;
    }
    out->setTo(0);
    const int kh = std::min(h, keep.rows);
    const int kw = std::min(w, keep.cols);
    for (int yy = 0; yy < kh; ++yy) {
        const uint8_t* yp = y.ptr<uint8_t>(yy);
        const uint8_t* kp = keep.ptr<uint8_t>(yy);
        uint8_t* op = out->ptr<uint8_t>(yy);
        for (int xx = 0; xx < kw; ++xx) {
            if (!kp[xx]) continue;
            const bool ink = dark ? (static_cast<double>(yp[xx]) <= thr)
                                  : (static_cast<double>(yp[xx]) > thr);
            if (ink) op[xx] = 255;
        }
    }
}

/** Keep = poison==0. Optional yCeil>=0 also requires y<=yCeil. */
static bool otsuKeepPoison0(
    const cv::Mat& y, const cv::Mat& poison, double* thr, bool* dark, float* inkFrac,
    double yCeil = -1.0
) {
    return otsuHistOnKeep(y, poison, false, yCeil, thr, dark, inkFrac);
}

static void applyThrKeepPoison0(
    const cv::Mat& y, const cv::Mat& poison, double thr, bool dark, cv::Mat* out,
    double yCeil = -1.0
) {
    if (!out) return;
    const int h = y.rows, w = y.cols;
    if (out->empty() || out->rows != h || out->cols != w || out->type() != CV_8UC1) {
        return;
    }
    out->setTo(0);
    const int kh = std::min(h, poison.rows);
    const int kw = std::min(w, poison.cols);
    for (int yy = 0; yy < kh; ++yy) {
        const uint8_t* yp = y.ptr<uint8_t>(yy);
        const uint8_t* pp = poison.ptr<uint8_t>(yy);
        uint8_t* op = out->ptr<uint8_t>(yy);
        for (int xx = 0; xx < kw; ++xx) {
            if (pp[xx]) continue;
            if (yCeil >= 0.0 && static_cast<double>(yp[xx]) > yCeil) continue;
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
    int glareMult = 9,
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
    if (!binOut || binOut->empty() || binOut->type() != CV_8UC1 ||
        binOut->rows != seedH || binOut->cols != seedW) {
        return fallback;
    }
    cv::Mat roi = y(cv::Range(st, sb), cv::Range(sl, sr));
    const int sPx = fillPoisonLookRaster(
        roi, roi, 0, 0, false, glareMult, fallback, binOut);
    if (otsuOut) *otsuOut = 0.0;
    if (invertedOut) *invertedOut = false;
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

static void stampPoisonFlood(
    cv::Mat& pois, cv::Mat* obj, int ox, int oy, int sPx, int seedIndex, ObjPack* pack,
    bool mapUv = false,
    float ovCx = 0.f, float ovCy = 0.f,
    float ovUx = 0.f, float ovUy = 0.f,
    float ovVx = 0.f, float ovVy = 0.f,
    float ovU0 = 0.f, float ovU1 = 0.f, float ovLookV0 = 0.f,
    int xSeed0 = 0, int ySeed0 = 0, int lw = 1
) {
    if (!pack || pois.empty()) return;
    const int h = pois.rows, w = pois.cols;
    const int minWh = std::max(1, sPx / 4);
    uint8_t pepperId = 0;
    bool havePepper = false;
    std::vector<int> st;
    st.reserve(256);
    for (int y = 0; y < h; ++y) {
        uint8_t* pp = pois.ptr<uint8_t>(y);
        for (int x = 0; x < w; ++x) {
            if (pp[x] != 255) continue;
            st.clear();
            st.push_back(y * w + x);
            pp[x] = 128;
            int x0 = x, x1 = x + 1, y0 = y, y1 = y + 1, n = 0;
            while (!st.empty()) {
                const int i = st.back();
                st.pop_back();
                const int cy = i / w, cx = i - cy * w;
                ++n;
                if (cx < x0) x0 = cx;
                if (cx + 1 > x1) x1 = cx + 1;
                if (cy < y0) y0 = cy;
                if (cy + 1 > y1) y1 = cy + 1;
                for (int dy = -1; dy <= 1; ++dy) {
                    for (int dx = -1; dx <= 1; ++dx) {
                        if (!dx && !dy) continue;
                        const int ny = cy + dy, nx = cx + dx;
                        if (ny < 0 || nx < 0 || ny >= h || nx >= w) continue;
                        if (pois.ptr<uint8_t>(ny)[nx] != 255) continue;
                        pois.ptr<uint8_t>(ny)[nx] = 128;
                        st.push_back(ny * w + nx);
                    }
                }
            }
            const int cw = x1 - x0, ch = y1 - y0;
            if (cw < minWh && ch < minWh) continue;
            uint8_t id = 0;
            const bool tiny = cw <= minWh * 2 && ch <= minWh * 2;
            if (tiny) {
                if (!havePepper) {
                    if (!objAlloc(pack, false, seedIndex, kKindPepper, &pepperId)) return;
                    havePepper = true;
                    std::snprintf(pack->phase, sizeof(pack->phase), "plus-ROI");
                }
                id = pepperId;
            } else {
                if (!objAlloc(pack, false, seedIndex, kKindPoisonFat, &id)) return;
                std::snprintf(pack->phase, sizeof(pack->phase), "poison");
            }
            for (int yy = y0; yy < y1; ++yy) {
                uint8_t* row = pois.ptr<uint8_t>(yy);
                for (int xx = x0; xx < x1; ++xx) {
                    if (row[xx] != 128) continue;
                    row[xx] = 64;
                    int ix = ox + xx, iy = oy + yy;
                    if (mapUv) {
                        const float u = ovU0 + (xSeed0 + xx + 0.5f) /
                            static_cast<float>(std::max(1, lw)) * (ovU1 - ovU0);
                        const float v = ovLookV0 + (ySeed0 + yy + 0.5f);
                        ix = static_cast<int>(std::lround(ovCx + u * ovUx + v * ovVx));
                        iy = static_cast<int>(std::lround(ovCy + u * ovUy + v * ovVy));
                    }
                    objPut(obj, ix, iy, id, pack);
                }
            }
        }
    }
}

static void paintLookOverlay(
    const cv::Mat& lookBin, const cv::Mat& lookPoison,
    cv::Mat* overlayY, cv::Mat* overlayUv,
    bool overlayUvMap, int ovX, int ovY,
    float ovCx, float ovCy, float ovUx, float ovUy,
    float ovVx, float ovVy, float ovU0, float ovU1, float ovLookV0
) {
    if (!overlayY || overlayY->empty() || overlayY->type() != CV_8UC1) return;
    const int lh = lookBin.rows, lw = lookBin.cols;
    if (lh < 1 || lw < 1) return;
    auto overlayXY = [&](int x, int y, int* ix, int* iy) {
        if (overlayUvMap) {
            const float u = ovU0 + (x + 0.5f) / static_cast<float>(std::max(1, lw)) * (ovU1 - ovU0);
            const float v = ovLookV0 + (y + 0.5f);
            *ix = static_cast<int>(std::lround(ovCx + u * ovUx + v * ovVx));
            *iy = static_cast<int>(std::lround(ovCy + u * ovUy + v * ovVy));
        } else {
            *ix = ovX + x;
            *iy = ovY + y;
        }
    };
    for (int y = 0; y < lh; ++y) {
        const uint8_t* before = lookBin.ptr<uint8_t>(y);
        const uint8_t* poisRow = lookPoison.empty() ? nullptr : lookPoison.ptr<uint8_t>(y);
        for (int x = 0; x < lw; ++x) {
            const bool pois = poisRow && poisRow[x] != 0;
            const bool inkBefore = before[x] != 0;
            if (!(pois && !inkBefore)) continue;
            int ix = 0, iy = 0;
            overlayXY(x, y, &ix, &iy);
            if (iy < 0 || iy >= overlayY->rows || ix < 0 || ix >= overlayY->cols) continue;
            if (overlayY->ptr<uint8_t>(iy)[ix] >= 140) continue;
            yuvPut(overlayY, overlayUv, ix, iy, 105, 202, 255);
        }
    }
    for (int y = 0; y < lh; ++y) {
        const uint8_t* before = lookBin.ptr<uint8_t>(y);
        const uint8_t* poisRow = lookPoison.empty() ? nullptr : lookPoison.ptr<uint8_t>(y);
        for (int x = 0; x < lw; ++x) {
            if (before[x] == 0) continue;
            const bool pois = poisRow && poisRow[x] != 0;
            int ix = 0, iy = 0;
            overlayXY(x, y, &ix, &iy);
            if (pois) yuvPut(overlayY, overlayUv, ix, iy, 150, 44, 21);
            else yuvPut(overlayY, overlayUv, ix, iy, 255, 128, 128);
        }
    }
}

/** One look raster: clean vs per-poison rule; runs ignore region edges. Returns sPx. */
static int fillPoisonLookRaster(
    const cv::Mat& seedY, const cv::Mat& lookY, int ySeed0, int xSeed0,
    bool srcIsBin, int glareMult, int fallback, cv::Mat* lookBin,
    cv::Mat* overlayY, cv::Mat* overlayUv, int ovX, int ovY, PoisonStats* statsOut,
    cv::Mat* scratch,
    cv::Mat* objPlane, ObjPack* objPack, int seedIndex,
    bool overlayUvMap,
    float ovCx, float ovCy,
    float ovUx, float ovUy,
    float ovVx, float ovVy,
    float ovU0, float ovU1, float ovLookV0,
    cv::Mat* lookPoisonOut, bool paintOverlay,
    cv::Mat* poisonPlane,
    cv::Mat* lookInkAtOut
) {
    const int seedH = seedY.rows, seedW = seedY.cols;
    if (!lookBin) return std::max(1, fallback);
    const int lh0 = lookY.rows, lw0 = lookY.cols;
    if (lookBin->empty() || lookBin->rows != lh0 || lookBin->cols != lw0 ||
        lookBin->type() != CV_8UC1) {
        cv::Mat placed;
        if (overlayUvMap) {
            placed = planeRoi8u(scratch, 0, 0, lw0, lh0);
        } else {
            placed = planeRoi8u(scratch, ovX, ovY, lw0, lh0);
        }
        if (placed.empty()) {
            if (objPack) {
                objPack->abort = true;
                objPack->abortSeed = seedIndex;
                std::snprintf(objPack->phase, sizeof(objPack->phase), "scratch");
            }
            return std::max(1, fallback);
        }
        *lookBin = placed;
    }
    lookBin->setTo(0);
    if (seedH < 1 || seedW < 1 || lookY.empty()) return std::max(1, fallback);
    auto abortScratch = [&]() {
        if (objPack) {
            objPack->abort = true;
            objPack->abortSeed = seedIndex;
            std::snprintf(objPack->phase, sizeof(objPack->phase), "scratch");
        }
    };
    const int lh = lookY.rows, lw = lookY.cols;
    cv::Mat lookPoison;
    cv::Mat* poisHost = asU8(poisonPlane);
    if (overlayUvMap) {
        if (poisHost) lookPoison = planeRoi8u(poisHost, 0, 0, lw, lh);
    } else if (poisHost) {
        lookPoison = planeRoi8u(poisHost, ovX, ovY, lw, lh);
    }
    if (lookPoison.empty()) {
        lookPoison.create(lh, lw, CV_8UC1);
        lookPoison.setTo(0);
    }
    {
        char extra[160];
        std::snprintf(extra, sizeof extra,
            "lookBin=%dx%d t=%d lookPoison=%dx%d t=%d lw=%d lh=%d",
            lookBin->rows, lookBin->cols, lookBin->type(),
            lookPoison.rows, lookPoison.cols, lookPoison.type(), lw, lh);
        veRssLog("poison_placed", extra);
    }
    cv::Mat bin = planeView8u(lookBin, xSeed0, ySeed0, seedW, seedH);
    if (bin.empty()) {
        abortScratch();
        return std::max(1, fallback);
    }
    double otsu = 0.0;
    bool inverted = false;
    float inkFrac = 0.f;
    const int bh = std::min(seedY.rows, bin.rows);
    const int bw = std::min(seedY.cols, bin.cols);
    if (srcIsBin) {
        int nz = 0;
        const int nPix = std::max(1, seedH * seedW);
        for (int yy = 0; yy < bh; ++yy) {
            const uint8_t* yp = seedY.ptr<uint8_t>(yy);
            uint8_t* op = bin.ptr<uint8_t>(yy);
            for (int xx = 0; xx < bw; ++xx) {
                op[xx] = yp[xx];
                if (op[xx]) ++nz;
            }
        }
        inkFrac = nz / static_cast<float>(nPix);
        if (inkFrac >= 0.45f) {
            inverted = true;
            nz = 0;
            for (int yy = 0; yy < bh; ++yy) {
                uint8_t* op = bin.ptr<uint8_t>(yy);
                for (int xx = 0; xx < bw; ++xx) {
                    op[xx] = static_cast<uint8_t>(255 - op[xx]);
                    if (op[xx]) ++nz;
                }
            }
            inkFrac = nz / static_cast<float>(nPix);
        }
    } else {
        int hist[256] = {};
        int n = 0;
        for (int yy = 0; yy < bh; ++yy) {
            const uint8_t* yp = seedY.ptr<uint8_t>(yy);
            for (int xx = 0; xx < bw; ++xx) {
                hist[yp[xx]]++;
                ++n;
            }
        }
        const int nPix = std::max(1, n);
        otsu = n >= 2 ? otsuThrFromHist(hist, n) : 0.0;
        const int ti = static_cast<int>(otsu);
        int nz = 0;
        for (int i = 0; i <= ti && i < 256; ++i) nz += hist[i];
        inkFrac = nz / static_cast<float>(nPix);
        bool dark = true;
        if (inkFrac >= 0.45f) {
            inverted = true;
            dark = false;
            nz = nPix - nz;
            inkFrac = nz / static_cast<float>(nPix);
        }
        for (int yy = 0; yy < bh; ++yy) {
            const uint8_t* yp = seedY.ptr<uint8_t>(yy);
            uint8_t* op = bin.ptr<uint8_t>(yy);
            for (int xx = 0; xx < bw; ++xx) {
                const bool ink = dark
                    ? (static_cast<int>(yp[xx]) <= ti)
                    : (static_cast<int>(yp[xx]) > ti);
                op[xx] = ink ? 255 : 0;
            }
        }
    }
    fillSaltPepper(&bin);
    HorizSW hh0 = horizPeakSW(bin, seedH, seedW);
    const int v0 = hh0.peak;
    const bool needFb0 = strokeNeedFb(hh0, v0, seedW, inkFrac);
    const int sPx0 = (v0 > 4 && !needFb0) ? v0 : fallback;
    cv::Mat poison = planeView8u(&lookPoison, xSeed0, ySeed0, seedW, seedH);
    if (poison.empty()) {
        abortScratch();
        return std::max(1, fallback);
    }
    fillPoisonMask(bin, v0, needFb0, seedW, glareMult, &poison);
    veRssLog("poison_mask", nullptr);
    bool bandTop = false, bandBot = false;
    int bandH = 0;
    if (!srcIsBin) orBrightBands(seedY, bin, sPx0, &poison, &bandTop, &bandBot, &bandH);
    double cleanThr = otsu;
    bool cleanDark = !inverted;
    float cleanInkFrac = 0.f;
    bool haveClean = false;
    const int nFirst = cv::countNonZero(bin);
    if (srcIsBin) {
        for (int yy = 0; yy < seedH; ++yy) {
            const uint8_t* pp = poison.ptr<uint8_t>(yy);
            uint8_t* sp = bin.ptr<uint8_t>(yy);
            for (int xx = 0; xx < seedW; ++xx) if (pp[xx]) sp[xx] = 0;
        }
        haveClean = cv::countNonZero(bin) > 0;
        cleanDark = !inverted;
        cleanThr = 127.0;
    } else {
        haveClean = otsuKeepPoison0(seedY, poison, &cleanThr, &cleanDark, &cleanInkFrac);
        if (haveClean) {
            applyThrKeepPoison0(seedY, poison, cleanThr, cleanDark, &bin);
            fillSaltPepper(&bin);
        } else {
            bin.setTo(0);
        }
    }
    HorizSW hhC = horizPeakSW(bin, seedH, seedW);
    int v0Clean = hhC.peak;
    bool needFbClean = !haveClean || strokeNeedFb(hhC, v0Clean, seedW, srcIsBin
        ? (cv::countNonZero(bin) / static_cast<float>(std::max(1, seedH * seedW)))
        : cleanInkFrac);
    int sPx = (v0Clean > 4 && !needFbClean) ? v0Clean : fallback;
    if (!srcIsBin && inverted && haveClean && !cleanDark) {
        if (nFirst > 0 &&
            cv::countNonZero(poison) >= static_cast<int>(0.60f * static_cast<float>(nFirst))) {
            double thr2 = 0.0;
            bool dark2 = true;
            float frac2 = 0.f;
            if (otsuKeepPoison0(seedY, poison, &thr2, &dark2, &frac2, cleanThr)) {
                applyThrKeepPoison0(seedY, poison, thr2, dark2, &bin, cleanThr);
                fillSaltPepper(&bin);
                if (dark2 && frac2 >= 0.05f && frac2 <= 0.42f) {
                    cleanThr = thr2;
                    cleanDark = dark2;
                    cleanInkFrac = frac2;
                    hhC = horizPeakSW(bin, seedH, seedW);
                    v0Clean = hhC.peak;
                    needFbClean = !haveClean || strokeNeedFb(hhC, v0Clean, seedW, cleanInkFrac);
                    sPx = (v0Clean > 4 && !needFbClean) ? v0Clean : fallback;
                } else {
                    applyThrKeepPoison0(seedY, poison, cleanThr, cleanDark, &bin);
                    fillSaltPepper(&bin);
                }
            }
        }
    }
    bool useSample = v0Clean > 4;
    if (!useSample && !srcIsBin && haveClean && cleanDark &&
        cleanInkFrac >= 0.05f && cleanInkFrac <= 0.40f &&
        dInkFromBin(seedY, bin) <= -10.f) {
        useSample = true;
    }
    if (!useSample) bin.setTo(0);
    cv::Mat combined = bin;
    if (objPack && objPlane) {
        uint8_t inkId = 0;
        if (objAlloc(objPack, true, seedIndex, kKindInk, &inkId)) {
            std::snprintf(objPack->phase, sizeof(objPack->phase), "walk");
            for (int yy = 0; yy < seedH; ++yy) {
                const uint8_t* cp = combined.ptr<uint8_t>(yy);
                for (int xx = 0; xx < seedW; ++xx) {
                    if (cp[xx]) {
                        int ix = ovX + xSeed0 + xx, iy = ovY + ySeed0 + yy;
                        if (overlayUvMap) {
                            const float u = ovU0 + (xSeed0 + xx + 0.5f) /
                                static_cast<float>(std::max(1, lookY.cols)) * (ovU1 - ovU0);
                            const float v = ovLookV0 + (ySeed0 + yy + 0.5f);
                            ix = static_cast<int>(std::lround(ovCx + u * ovUx + v * ovVx));
                            iy = static_cast<int>(std::lround(ovCy + u * ovUy + v * ovVy));
                        }
                        objPut(objPlane, ix, iy, inkId, objPack);
                    }
                }
            }
        }
        stampPoisonFlood(
            poison, objPlane, ovX + xSeed0, ovY + ySeed0, sPx, seedIndex, objPack,
            overlayUvMap, ovCx, ovCy, ovUx, ovUy, ovVx, ovVy, ovU0, ovU1, ovLookV0,
            xSeed0, ySeed0, lookY.cols);
        veRssLog("poison_flood", nullptr);
    }

    if (!srcIsBin && cv::countNonZero(combined) == 0 && haveClean && cleanDark &&
        cleanInkFrac >= 0.05f && cleanInkFrac <= 0.40f) {
        applyThrKeepPoison0(seedY, poison, cleanThr, cleanDark, &combined);
    }
    if (!lookPoison.empty()) {
        for (int yy = 0; yy < seedH; ++yy) {
            const int sy = yy + ySeed0;
            if (sy < 0 || sy >= lh) continue;
            const uint8_t* pp = poison.ptr<uint8_t>(yy);
            uint8_t* lp = lookPoison.ptr<uint8_t>(sy);
            for (int xx = 0; xx < seedW; ++xx) {
                const int sx = xx + xSeed0;
                if (sx < 0 || sx >= lw) continue;
                lp[sx] = pp[xx];
            }
        }
    }
    auto lookInkAt = [&](int y, int x) -> uint8_t {
        const uint8_t v = lookY.ptr<uint8_t>(y)[x];
        if (srcIsBin) {
            return inverted ? static_cast<uint8_t>(255 - v) : v;
        }
        const bool ink = cleanDark ? (static_cast<double>(v) <= cleanThr)
                                   : (static_cast<double>(v) > cleanThr);
        return ink ? 255 : 0;
    };
    auto countSeedLookInk = [&]() -> int {
        int n = 0;
        for (int yy = 0; yy < seedH; ++yy) {
            const int sy = yy + ySeed0;
            if (sy < 0 || sy >= lh) continue;
            for (int xx = 0; xx < seedW; ++xx) {
                const int sx = xx + xSeed0;
                if (sx < 0 || sx >= lw) continue;
                if (lookInkAt(sy, sx)) ++n;
            }
        }
        return n;
    };
    int nInkSeed = countSeedLookInk();
    if (nInkSeed == 0 && !srcIsBin) {
        cleanDark = !cleanDark;
        applyThrKeepPoison0(seedY, poison, cleanThr, cleanDark, &bin);
        fillSaltPepper(&bin);
        hhC = horizPeakSW(bin, seedH, seedW);
        v0Clean = hhC.peak;
        needFbClean = !haveClean || strokeNeedFb(hhC, v0Clean, seedW, cleanInkFrac);
        sPx = (v0Clean > 4 && !needFbClean) ? v0Clean : fallback;
        nInkSeed = countSeedLookInk();
        if (nInkSeed > 0) {
            useSample = true;
        }
    }
    auto stampSeedCombined = [&]() {
        for (int yy = 0; yy < seedH; ++yy) {
            const int sy = yy + ySeed0;
            if (sy < 0 || sy >= lh) continue;
            const uint8_t* cp = combined.ptr<uint8_t>(yy);
            uint8_t* op = lookBin->ptr<uint8_t>(sy);
            for (int xx = 0; xx < seedW; ++xx) {
                const int sx = xx + xSeed0;
                if (sx < 0 || sx >= lw) continue;
                op[sx] = cp[xx];
            }
        }
    };
    const bool plusFill = (ySeed0 > 0 || ySeed0 + seedH < lh);
    int plusN = 0;
    if (plusFill) {
        auto fillPlusRect = [&](int plusT, int plusB, int plusL, int plusR) {
            if (plusB <= plusT || plusR <= plusL) return;
            const int plusH0 = plusB - plusT, plusW0 = plusR - plusL;
            char ptag[32];
            std::snprintf(ptag, sizeof ptag, "plus_%d", plusN);
            ++plusN;
            {
                char extra[64];
                std::snprintf(extra, sizeof extra, "begin plusW=%d plusH=%d", plusW0, plusH0);
                veRssLog(ptag, extra);
            }
            if (!veAllocLog("fillPlusRect", veMatBytes(plusH0, plusW0, CV_8UC1),
                    plusH0, plusW0, CV_8UC1)) {
                return;
            }
            for (int y = plusT; y < plusB; ++y) {
                uint8_t* op = lookBin->ptr<uint8_t>(y);
                uint8_t* pp = lookPoison.empty() ? nullptr : lookPoison.ptr<uint8_t>(y);
                for (int x = plusL; x < plusR; ++x) {
                    const bool inSeed = (y >= ySeed0 && y < ySeed0 + seedH &&
                        x >= xSeed0 && x < xSeed0 + seedW);
                    op[x] = lookInkAt(y, x);
                    if (pp && !inSeed) pp[x] = 0;
                }
            }
            cv::Mat plusBin = (*lookBin)(cv::Range(plusT, plusB), cv::Range(plusL, plusR));
            fillSaltPepper(&plusBin);
            const int plusH = plusB - plusT, plusW = plusR - plusL;
            cv::Mat plusPoison = planeView8u(&lookPoison, plusL, plusT, plusW, plusH);
            if (!plusPoison.empty()) {
                fillPoisonMask(plusBin, std::max(sPx, 4), false, seedW, glareMult, &plusPoison);
            }
            const int runLim = 3 * std::max(1, sPx);
            for (int y = plusT; y < plusB; ++y) {
                uint8_t* op = lookBin->ptr<uint8_t>(y);
                uint8_t* pp = lookPoison.empty() ? nullptr : lookPoison.ptr<uint8_t>(y);
                const uint8_t* fat = plusPoison.empty() ? nullptr : plusPoison.ptr<uint8_t>(y - plusT);
                for (int x = plusL; x < plusR; ++x) {
                    const int px = x - plusL;
                    const bool inSeed = (y >= ySeed0 && y < ySeed0 + seedH &&
                        x >= xSeed0 && x < xSeed0 + seedW);
                    if (fat && fat[px]) {
                        op[x] = 0;
                        if (pp && !inSeed) pp[x] = 255;
                    }
                }
                const int maxRun = maxInkRunRow(*lookBin, y, plusL, plusR);
                int nInk = 0;
                for (int x = plusL; x < plusR; ++x) if (op[x]) ++nInk;
                if (maxRun > runLim ||
                    nInk > static_cast<int>(0.50f * static_cast<float>(plusW))) {
                    for (int x = plusL; x < plusR; ++x) {
                        const bool inSeed = (y >= ySeed0 && y < ySeed0 + seedH &&
                            x >= xSeed0 && x < xSeed0 + seedW);
                        if (inSeed) continue;
                        if (pp && op[x]) pp[x] = 255;
                        op[x] = 0;
                    }
                }
            }
            stampSeedCombined();
            {
                char extra[32];
                std::snprintf(extra, sizeof extra, "end plusW=%d plusH=%d", plusW0, plusH0);
                veRssLog(ptag, extra);
            }
        };
        if (v0Clean > 4) {
            const int ring = 4 * sPx;
            const int capPx = std::max(1, static_cast<int>(std::lround(2.5f * seedH)));
            const int capTop = std::max(0, ySeed0 - capPx);
            const int capBot = std::min(lh, ySeed0 + seedH + capPx);
            int extraTop = ring;
            int extraBot = ring;
            auto plusBounds = [&](int* t, int* b, int* l, int* r) {
                const int plusH = seedH + extraTop + extraBot;
                *t = std::max(capTop, ySeed0 - extraTop);
                *b = std::min(capBot, ySeed0 + seedH + extraBot);
                *l = std::max(0, xSeed0 - plusH);
                *r = std::min(lw, xSeed0 + seedW + plusH);
            };
            int plusT = 0, plusB = 0, plusL = 0, plusR = 0;
            plusBounds(&plusT, &plusB, &plusL, &plusR);
            fillPlusRect(plusT, plusB, plusL, plusR);
            const int glareWHas = (glareMult > 0 ? glareMult : 9) * std::max(sPx, 4);
            const int minRun = usedMinRun(
                sPx, maxInSeedRunRows(*lookBin, ySeed0, ySeed0 + seedH, xSeed0, xSeed0 + seedW));
            auto hasBar = [&](int y) {
                return rowHasStrokeBar(*lookBin, y, minRun, glareWHas);
            };
            for (int g = 0; g < 32; ++g) {
                bool wantTop = plusT > capTop && hasBar(plusT);
                bool wantBot = plusB < capBot && plusB > 0 && hasBar(plusB - 1);
                if (!wantTop && !wantBot) break;
                if (wantTop) extraTop += ring;
                if (wantBot) extraBot += ring;
                int nt = 0, nb = 0, nl = 0, nr = 0;
                plusBounds(&nt, &nb, &nl, &nr);
                if (nt == plusT && nb == plusB && nl == plusL && nr == plusR) break;
                plusT = nt;
                plusB = nb;
                plusL = nl;
                plusR = nr;
                fillPlusRect(plusT, plusB, plusL, plusR);
            }
        } else {
            stampSeedCombined();
        }
        veRssLog("plus_done", nullptr);
    } else {
        for (int y = 0; y < lh; ++y) {
            uint8_t* op = lookBin->ptr<uint8_t>(y);
            const int sy = y - ySeed0;
            for (int x = 0; x < lw; ++x) {
                const int sx = x - xSeed0;
                if (sy >= 0 && sy < seedH && sx >= 0 && sx < seedW) {
                    op[x] = combined.ptr<uint8_t>(sy)[sx];
                    continue;
                }
                if (v0Clean <= 4) { op[x] = 0; continue; }
                op[x] = lookInkAt(y, x);
            }
        }
    }
    fillSaltPepper(lookBin);
    const int glareW = (glareMult > 0 ? glareMult : 9) * std::max(sPx, 4);
    dropWideRuns(lookBin, glareW);
    if (lookPoisonOut) *lookPoisonOut = lookPoison;
    if (paintOverlay) {
        paintLookOverlay(
            *lookBin, lookPoison, overlayY, overlayUv, overlayUvMap, ovX, ovY,
            ovCx, ovCy, ovUx, ovUy, ovVx, ovVy, ovU0, ovU1, ovLookV0);
    }
    if (statsOut) {
        statsOut->bandTop = bandTop ? 1 : 0;
        statsOut->bandBot = bandBot ? 1 : 0;
        statsOut->bandH = bandH;
        statsOut->ccs.clear();
    }
    if (lookInkAtOut) {
        lookInkAtOut->create(lh, lw, CV_8UC1);
        lookInkAtOut->setTo(0);
        for (int y = 0; y < lh; ++y) {
            uint8_t* op = lookInkAtOut->ptr<uint8_t>(y);
            for (int x = 0; x < lw; ++x) op[x] = lookInkAt(y, x);
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
    if (!scratchFits(dst, w, h)) return false;
    dst->setTo(0);
    const int xl = std::max(0, sl - std::max(0, xPad));
    const int xr = std::min(w, sr + std::max(0, xPad));
    if (xr <= xl) return false;
    cv::Mat seedY = y(cv::Range(st, sb), cv::Range(sl, sr));
    cv::Mat lookY = y(cv::Range(st, sb), cv::Range(xl, xr));
    cv::Mat strip = (*dst)(cv::Rect(xl, st, xr - xl, sb - st));
    const int fallback = std::max(2, static_cast<int>(std::lround(0.08f * (sb - st))));
    fillPoisonLookRaster(seedY, lookY, 0, sl - xl, false, 9, fallback, &strip);
    return !strip.empty();
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
    int glareMult = 9,
    int xPad = 0,
    bool adaptive = false,
    Seg7Tele* tele = nullptr
) {
    if (y.empty() || y.type() != CV_8UC1 || !dst) return false;
    const int h = y.rows, w = y.cols;
    const bool reuse = scratchFits(dst, w, h);
    if (!reuse) return false;
    if (sl < 0) sl = 0;
    if (st < 0) st = 0;
    if (sr > w) sr = w;
    if (sb > h) sb = h;
    if (sr <= sl || sb <= st) return false;
    cv::Mat seedBin = (*dst)(cv::Range(st, sb), cv::Range(sl, sr));
    if (seedBin.empty() || seedBin.type() != CV_8UC1) return false;
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

static void aabbJumpOnLook(
    cv::Mat* look, int* l, int t, int* r, int b,
    int imgW, int imgH, int seedT, int seedB, int seedL, int seedR, int sPx,
    int lookOx, int lookOy, int* farL, int* farR
) {
    if (!look || look->empty() || look->type() != CV_8UC1 || sPx < 1) return;
    const int maxIn = maxInSeedRunRows(
        *look, seedT - lookOy, seedB - lookOy, seedL - lookOx, seedR - lookOx);
    const int minRun = usedMinRun(sPx, maxIn);
    if (minRun < 1) return;
    jumpRetractH(
        *look, l, t, r, b, imgW, imgH, 0.0, 1, 0.50f, 0.30f,
        std::max(1, seedB - seedT), farL, farR, look, seedT, seedB, minRun, lookOx, lookOy);
}

}  // namespace

static jintArray aabb7segSeedsOut(JNIEnv* env, const std::vector<jint>& seeds) {
    const int n = static_cast<int>(seeds.size()) / 4;
    std::vector<jint> out(static_cast<size_t>(n) * 8, 0);
    for (int i = 0; i < n; ++i) {
        const int o = i * 8;
        out[static_cast<size_t>(o)] = seeds[static_cast<size_t>(i) * 4];
        out[static_cast<size_t>(o) + 1] = seeds[static_cast<size_t>(i) * 4 + 1];
        out[static_cast<size_t>(o) + 2] = seeds[static_cast<size_t>(i) * 4 + 2];
        out[static_cast<size_t>(o) + 3] = seeds[static_cast<size_t>(i) * 4 + 3];
        out[static_cast<size_t>(o) + 4] = 2;
        out[static_cast<size_t>(o) + 5] = 4;
        out[static_cast<size_t>(o) + 6] = 4;
        out[static_cast<size_t>(o) + 7] = 1;
    }
    jintArray arr = env->NewIntArray(static_cast<jint>(out.size()));
    if (!arr) return env->NewIntArray(0);
    env->SetIntArrayRegion(arr, 0, static_cast<jint>(out.size()), out.data());
    return arr;
}

static jintArray aabbGrayMany(
    JNIEnv* env,
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jintArray seedsArr,
    jint boundStrategy, jint tightInsetPx,
    jfloatArray teleArr, jshortArray sweepArr, jlong dumpPtr,
    jlong overlayYPtr, jlong overlayUvPtr, jintArray poisonArr
) {
    auto* gray = reinterpret_cast<cv::Mat*>(grayPtr);
    if (!gray || gray->empty() || gray->type() != CV_8UC1 || !seedsArr) {
        return env->NewIntArray(0);
    }
    const jint n4 = env->GetArrayLength(seedsArr);
    if (n4 <= 0 || n4 % 4 != 0) return env->NewIntArray(0);
    std::vector<jint> seeds(static_cast<size_t>(n4));
    env->GetIntArrayRegion(seedsArr, 0, n4, seeds.data());
    try {
    const int imgW = gray->cols, imgH = gray->rows;
    const int n = n4 / 4;
    {
        char extra[96];
        std::snprintf(extra, sizeof extra, "imgW=%d imgH=%d nSeeds=%d", imgW, imgH, n);
        veRssLog("aabb_enter", extra);
    }
    const jfloat gapFrac = 0.5f;
    const jfloat minSeedHsToFreeze = 0.f;
    auto* scratch = reinterpret_cast<cv::Mat*>(scratchPtr);
    auto* inkDump = reinterpret_cast<cv::Mat*>(dumpPtr);
    auto* overlayY = reinterpret_cast<cv::Mat*>(overlayYPtr);
    auto* overlayUv = reinterpret_cast<cv::Mat*>(overlayUvPtr);
    auto* uv = reinterpret_cast<cv::Mat*>(uvPtr);
    cv::Mat* poisonPlane = asU8(uv);
    cv::Mat* ovUv = asUV(overlayUv);
    std::vector<PoisonStats> poisonPacks;
    poisonPacks.resize(static_cast<size_t>(n));
    std::vector<jint> out(static_cast<size_t>(n) * 8, 0);
    std::vector<InkSweepPack> sweeps;
    sweeps.resize(static_cast<size_t>(n));
    ObjPack objPack;
    loadObjPack(env, poisonArr, &objPack);
    for (int i = 0; i < n; ++i) {
        int l = seeds[static_cast<size_t>(i) * 4], t = seeds[static_cast<size_t>(i) * 4 + 1];
        int r = seeds[static_cast<size_t>(i) * 4 + 2], b = seeds[static_cast<size_t>(i) * 4 + 3];
        if (l < 0) l = 0;
        if (t < 0) t = 0;
        if (r > imgW) r = imgW;
        if (b > imgH) b = imgH;
        int ol, ot, orr, ob, sPx, vSW, hSW, fb;
        Seg7Tele tele{};
        tele.method = 0.f;
        objPack.classChange = 0;
        objPack.seenInk = 0;
        objPack.seenNon = 0;
        seg7One(*gray, l, t, r, b, imgW, imgH, &ol, &ot, &orr, &ob, &sPx, &vSW, &hSW, &fb,
            false, gapFrac, minSeedHsToFreeze, 9, boundStrategy, tightInsetPx, &tele, false,
            &sweeps[static_cast<size_t>(i)], inkDump, overlayY, ovUv,
            &poisonPacks[static_cast<size_t>(i)], scratch, &objPack, i, poisonPlane);
        {
            char extra[64];
            std::snprintf(extra, sizeof extra, "seed=%d", i);
            veRssLog("seed_done", extra);
        }
        if (objPack.abort) {
            poisonPacks[static_cast<size_t>(i)].bandH = -1;
            appendObjMeta(&poisonPacks[static_cast<size_t>(i)], objPack, i, n);
            break;
        }
        appendObjMeta(&poisonPacks[static_cast<size_t>(i)], objPack, i, n);
        const int o = i * 8;
        out[static_cast<size_t>(o)] = ol;
        out[static_cast<size_t>(o) + 1] = ot;
        out[static_cast<size_t>(o) + 2] = orr;
        out[static_cast<size_t>(o) + 3] = ob;
        out[static_cast<size_t>(o) + 4] = sPx;
        out[static_cast<size_t>(o) + 5] = vSW;
        out[static_cast<size_t>(o) + 6] = hSW;
        out[static_cast<size_t>(o) + 7] = fb;
        storeTeleArr(env, teleArr, i, tele);
    }
    writeSweepArr(env, sweepArr, sweeps);
    writePoisonArr(env, poisonArr, poisonPacks);
    storeObjPack(env, poisonArr, objPack);
    jintArray arr = env->NewIntArray(static_cast<jint>(out.size()));
    if (!arr) return aabb7segSeedsOut(env, seeds);
    env->SetIntArrayRegion(arr, 0, static_cast<jint>(out.size()), out.data());
    return arr;
    } catch (const cv::Exception&) {
        return aabb7segSeedsOut(env, seeds);
    } catch (const std::exception&) {
        return aabb7segSeedsOut(env, seeds);
    }
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeGrayAabbTight(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jintArray seedsArr,
    jfloatArray teleArr, jshortArray sweepArr, jlong dumpPtr,
    jlong overlayYPtr, jlong overlayUvPtr, jintArray poisonArr
) {
    return aabbGrayMany(env, grayPtr, uvPtr, scratchPtr, seedsArr, 0, 16,
        teleArr, sweepArr, dumpPtr, overlayYPtr, overlayUvPtr, poisonArr);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeGrayAabbRetract(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jintArray seedsArr,
    jfloatArray teleArr, jshortArray sweepArr, jlong dumpPtr,
    jlong overlayYPtr, jlong overlayUvPtr, jintArray poisonArr
) {
    return aabbGrayMany(env, grayPtr, uvPtr, scratchPtr, seedsArr, 2, 16,
        teleArr, sweepArr, dumpPtr, overlayYPtr, overlayUvPtr, poisonArr);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeGrayAabbExpand(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jintArray seedsArr,
    jfloatArray teleArr, jshortArray sweepArr, jlong dumpPtr,
    jlong overlayYPtr, jlong overlayUvPtr, jintArray poisonArr
) {
    return aabbGrayMany(env, grayPtr, uvPtr, scratchPtr, seedsArr, 0, 16,
        teleArr, sweepArr, dumpPtr, overlayYPtr, overlayUvPtr, poisonArr);
}

static jintArray aabbColorMany(
    JNIEnv* env,
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jintArray seedsArr,
    jint boundStrategy, jint tightInsetPx,
    jfloatArray teleArr, jshortArray sweepArr, jlong dumpPtr,
    jlong overlayYPtr, jlong overlayUvPtr, jintArray poisonArr
) {
    auto* gray = reinterpret_cast<cv::Mat*>(grayPtr);
    if (!gray || gray->empty() || gray->type() != CV_8UC1 || !seedsArr) {
        return env->NewIntArray(0);
    }
    const jint n4 = env->GetArrayLength(seedsArr);
    if (n4 <= 0 || n4 % 4 != 0) return env->NewIntArray(0);
    std::vector<jint> seeds(static_cast<size_t>(n4));
    env->GetIntArrayRegion(seedsArr, 0, n4, seeds.data());
    try {
    const int imgW = gray->cols, imgH = gray->rows;
    const int n = n4 / 4;
    const int glareMult = 9;
    const jfloat gapFrac = 0.5f;
    const jfloat minSeedHsToFreeze = 0.f;
    auto* uv = reinterpret_cast<cv::Mat*>(uvPtr);
    auto* scratch = reinterpret_cast<cv::Mat*>(scratchPtr);
    auto* inkDump = reinterpret_cast<cv::Mat*>(dumpPtr);
    auto* overlayY = reinterpret_cast<cv::Mat*>(overlayYPtr);
    auto* overlayUv = reinterpret_cast<cv::Mat*>(overlayUvPtr);
    cv::Mat* lookPlane = asU8(scratch);
    if (!lookPlane) lookPlane = asU8(overlayUv);
    cv::Mat* poisonPlane = asU8(overlayUv);
    if (poisonPlane && lookPlane && poisonPlane->data == lookPlane->data) {
        poisonPlane = nullptr;
    }
    cv::Mat yUv;
    cv::Mat* ovUv = asUV(overlayUv);
    if (!ovUv) ovUv = asUvFromY(overlayY, &yUv);
    std::vector<PoisonStats> poisonPacks;
    poisonPacks.resize(static_cast<size_t>(n));
    std::vector<jint> out(static_cast<size_t>(n) * 8, 0);
    std::vector<InkSweepPack> sweeps;
    sweeps.resize(static_cast<size_t>(n));
    ObjPack objPack;
    loadObjPack(env, poisonArr, &objPack);
    for (int i = 0; i < n; ++i) {
        int l = seeds[static_cast<size_t>(i) * 4], t = seeds[static_cast<size_t>(i) * 4 + 1];
        int r = seeds[static_cast<size_t>(i) * 4 + 2], b = seeds[static_cast<size_t>(i) * 4 + 3];
        if (l < 0) l = 0;
        if (t < 0) t = 0;
        if (r > imgW) r = imgW;
        if (b > imgH) b = imgH;
        int ol, ot, orr, ob, sPx, vSW, hSW, fb;
        Seg7Tele tele{};
        tele.method = 4.f;
        const int seedH = std::max(1, b - t);
        const int xPadGuess = std::max(1, static_cast<int>(std::lround(
            0.50f * 2.5f * static_cast<float>(seedH) * static_cast<float>(kJumpMax + 1))));
        cv::Mat* tintDst = asU8(scratch);
        objPack.classChange = 0;
        objPack.seenInk = 0;
        objPack.seenNon = 0;
        const bool ok = uv && fillChromaTintMask(
            *gray, *uv, l, t, r, b, tintDst, glareMult, xPadGuess, true, &tele);
        if (ok && tintDst && !tintDst->empty() && !skipTintWalk(true, tele)) {
            seg7One(*tintDst, l, t, r, b, imgW, imgH,
                &ol, &ot, &orr, &ob, &sPx, &vSW, &hSW, &fb, true,
                gapFrac, minSeedHsToFreeze, glareMult, boundStrategy, tightInsetPx, &tele, true,
                &sweeps[static_cast<size_t>(i)], inkDump, overlayY, ovUv,
                &poisonPacks[static_cast<size_t>(i)], lookPlane, &objPack, i, poisonPlane);
        } else {
            if (ok && skipTintWalk(true, tele)) tele.method = 0.f;
            seg7One(*gray, l, t, r, b, imgW, imgH,
                &ol, &ot, &orr, &ob, &sPx, &vSW, &hSW, &fb, false,
                gapFrac, minSeedHsToFreeze, 9, boundStrategy, tightInsetPx, &tele, ok,
                &sweeps[static_cast<size_t>(i)], inkDump, overlayY, ovUv,
                &poisonPacks[static_cast<size_t>(i)], lookPlane, &objPack, i, poisonPlane);
        }
        if (objPack.abort) {
            poisonPacks[static_cast<size_t>(i)].bandH = -1;
            appendObjMeta(&poisonPacks[static_cast<size_t>(i)], objPack, i, n);
            break;
        }
        appendObjMeta(&poisonPacks[static_cast<size_t>(i)], objPack, i, n);
        const int o = i * 8;
        out[static_cast<size_t>(o)] = ol;
        out[static_cast<size_t>(o) + 1] = ot;
        out[static_cast<size_t>(o) + 2] = orr;
        out[static_cast<size_t>(o) + 3] = ob;
        out[static_cast<size_t>(o) + 4] = sPx;
        out[static_cast<size_t>(o) + 5] = vSW;
        out[static_cast<size_t>(o) + 6] = hSW;
        out[static_cast<size_t>(o) + 7] = fb;
        storeTeleArr(env, teleArr, i, tele);
    }
    writeSweepArr(env, sweepArr, sweeps);
    writePoisonArr(env, poisonArr, poisonPacks);
    storeObjPack(env, poisonArr, objPack);
    jintArray arr = env->NewIntArray(static_cast<jint>(out.size()));
    if (!arr) return aabb7segSeedsOut(env, seeds);
    env->SetIntArrayRegion(arr, 0, static_cast<jint>(out.size()), out.data());
    return arr;
    } catch (const cv::Exception&) {
        return aabb7segSeedsOut(env, seeds);
    } catch (const std::exception&) {
        return aabb7segSeedsOut(env, seeds);
    }
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeColorAabbTight(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jintArray seedsArr,
    jfloatArray teleArr, jshortArray sweepArr, jlong dumpPtr,
    jlong overlayYPtr, jlong overlayUvPtr, jintArray poisonArr
) {
    return aabbColorMany(env, grayPtr, uvPtr, scratchPtr, seedsArr, 0, 16,
        teleArr, sweepArr, dumpPtr, overlayYPtr, overlayUvPtr, poisonArr);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeColorAabbRetract(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jintArray seedsArr,
    jfloatArray teleArr, jshortArray sweepArr, jlong dumpPtr,
    jlong overlayYPtr, jlong overlayUvPtr, jintArray poisonArr
) {
    return aabbColorMany(env, grayPtr, uvPtr, scratchPtr, seedsArr, 2, 16,
        teleArr, sweepArr, dumpPtr, overlayYPtr, overlayUvPtr, poisonArr);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeColorAabbExpand(
    JNIEnv* env, jobject /*thiz*/,
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jintArray seedsArr,
    jfloatArray teleArr, jshortArray sweepArr, jlong dumpPtr,
    jlong overlayYPtr, jlong overlayUvPtr, jintArray poisonArr
) {
    return aabbColorMany(env, grayPtr, uvPtr, scratchPtr, seedsArr, 0, 16,
        teleArr, sweepArr, dumpPtr, overlayYPtr, overlayUvPtr, poisonArr);
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
    auto pixInk = [&](int iy, int ix) -> bool {
        if (iy < 0 || iy >= hv || ix < 0 || ix >= wu) return false;
        const float v = seed.v0 + (iy + 0.5f) / hv * (seed.v1 - seed.v0);
        const float u = static_cast<float>(uStart) + ix + 0.5f;
        const float px = seed.cx + u * seed.ux + v * seed.vx;
        const float py = seed.cy + u * seed.uy + v * seed.vy;
        const int g = sampleU8Trunc(src, px, py, imgW, imgH);
        if (g < 0) return false;
        if (srcIsBin) {
            const uint8_t vv = static_cast<uint8_t>(g);
            const uint8_t b = invertedBin ? static_cast<uint8_t>(255 - vv) : vv;
            return b != 0;
        }
        return dark
            ? (static_cast<double>(g) <= otsu)
            : (static_cast<double>(g) > otsu);
    };
    out->hScores.reserve(static_cast<size_t>(wu));
    for (int x = 0; x < wu; ++x) {
        int best = 0, run = 0;
        for (int y = 0; y < hv; ++y) {
            bool on = pixInk(y, x);
            if (on && glareW > 0) {
                int xl = x, xr = x + 1;
                while (xl > 0 && pixInk(y, xl - 1)) --xl;
                while (xr < wu && pixInk(y, xr)) ++xr;
                if (xr - xl > glareW) on = false;
            }
            if (on) {
                ++run;
                if (run > best) best = run;
            } else {
                run = 0;
            }
        }
        out->hScores.push_back(best);
    }
}

static void jumpOrientedOne(
    const cv::Mat& mag, OriBox* box, int imgW, int imgH,
    float maxFrac, float energyRatio, float jumpFrac, float retractClearFrac,
    float seedBh, const cv::Mat* lookBin, float seedV0, float seedV1, int minRun,
    float lookV0 = 0.f, float* farU0 = nullptr, float* farU1 = nullptr);

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
    PoisonStats* poisonStats = nullptr,
    cv::Mat* scratch = nullptr,
    ObjPack* objPack = nullptr,
    int seedIndex = 0,
    cv::Mat* poisonPlane = nullptr,
    cv::Mat* lookBinPlane = nullptr,
    bool doHorzJump = false
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
    const float cap = 2.5f * seedBh;
    const int vLook = std::max(1, static_cast<int>(std::lround(cap)) + 2);
    const float lookV0 = seed.v0 - static_cast<float>(vLook);
    const float lookV1 = seed.v1 + static_cast<float>(vLook);
    const int lookH = std::max(1, static_cast<int>(std::lround(lookV1 - lookV0)));
    cv::Mat look;
    cv::Mat seedY;
    cv::Mat* ovY = asU8(overlayY);
    if (!ovY) return;
    if (!scratchFits(ovY, wu, lookH)) return;
    look = planeView8u(ovY, 0, 0, wu, lookH);
    if (look.empty()) return;
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
    int ySeed0 = static_cast<int>(std::lround(seed.v0 - lookV0));
    if (ySeed0 < 0) ySeed0 = 0;
    if (ySeed0 >= lookH) ySeed0 = lookH - 1;
    int ySeed1 = ySeed0 + hv;
    if (ySeed1 > lookH) ySeed1 = lookH;
    if (ySeed1 <= ySeed0) ySeed1 = std::min(lookH, ySeed0 + 1);
    seedY = planeView8u(&look, 0, ySeed0, wu, ySeed1 - ySeed0);
    if (seedY.empty()) return;
    cv::Mat* lookBinHost = asU8(scratch);
    if (!lookBinHost) return;
    cv::Mat lookBin = planeRoi8u(lookBinHost, 0, 0, wu, lookH);
    if (lookBin.empty()) return;
    if (look.data == lookBin.data) return;
    PoisonStats stLocal;
    cv::Mat* objPlane = asU8(inkDump);
    if (objPlane && (objPlane->cols < imgW || objPlane->rows < imgH)) objPlane = nullptr;
    cv::Mat* overlayY8 = asU8(overlayY);
    cv::Mat* overlayUv2 = asUV(overlayUv);
    cv::Mat lookPoison;
    cv::Mat lookInkAtPlane;
    const int sPx = fillPoisonLookRaster(
        seedY, look, ySeed0, 0, srcIsBin, 9, fallback, &lookBin,
        overlayY8, overlayUv2, 0, 0, poisonStats ? &stLocal : nullptr, lookBinHost,
        objPlane, objPack, seedIndex,
        true, seed.cx, seed.cy, seed.ux, seed.uy, seed.vx, seed.vy,
        seed.u0, seed.u1, lookV0, &lookPoison, false, poisonPlane, &lookInkAtPlane);
    if (tele && !keepColorStats) {
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
    look.setTo(0);
    if (poisonStats) *poisonStats = stLocal;
    const int glareW = 9 * std::max(sPx, 4);
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
    const float origV0 = seed.v0, origV1 = seed.v1;
    auto walkV = [&]() {
        v0 = origV0;
        v1 = origV1;
        fTop = kFlagUnchanged;
        fBot = kFlagUnchanged;
        gapJumpTop = 0;
        gapJumpBot = 0;
        landTop = 0.f;
        landBot = 0.f;
        if (boundStrategy == 2) {
            if (hasBar(v0)) {
                expandNegOneShot();
            } else {
                while (v0 < v1 - 1.f && (v0 - origV0) < maxRetractPx && !hasBar(v0)) v0 += 1.f;
                if (v0 > origV0 && hasBar(v0)) fTop = kFlagNormalRetract;
                else if (v0 - origV0 >= maxRetractPx) fTop = kFlagBlocked10pct;
                else fTop = kFlagNormalRetract;
            }
            if (hasBar(v1 - 1.f) || hasBar(v1)) {
                expandPosOneShot();
            } else {
                while (v1 > v0 + 1.f && (origV1 - v1) < maxRetractPx && !hasBar(v1 - 1.f)) v1 -= 1.f;
                if (v1 < origV1 && hasBar(v1 - 1.f)) fBot = kFlagNormalRetract;
                else if (origV1 - v1 >= maxRetractPx) fBot = kFlagBlocked10pct;
                else fBot = kFlagNormalRetract;
            }
        } else {
            expandNegOneShot();
            expandPosOneShot();
        }
        if (v1 < v0 + 2.f) v1 = v0 + 2.f;
    };
    walkV();
    dropTallCCs(&lookBin, 18 * std::max(1, sPx));
    walkV();
    const float seedU0 = seed.u0, seedU1 = seed.u1;
    seed.v0 = v0;
    seed.v1 = v1;
    float farU0 = seed.u0, farU1 = seed.u1;
    if (doHorzJump) {
        jumpOrientedOne(
            src, &seed, imgW, imgH, 0.4f, 0.65f, 0.50f, 0.30f, seedBh,
            &lookBin, seed.v0, seed.v1, minRun, lookV0, &farU0, &farU1);
    }
    if (tele) {
        tele->otsuThr = 0.f;
        tele->sPx = *sPxOut;
        tele->dTop = v0 - origV0;
        tele->dBot = v1 - origV1;
        tele->dLeft = seed.u0 - seedU0;
        tele->dRight = seed.u1 - seedU1;
        tele->fTop = static_cast<float>(fTop);
        tele->fBot = static_cast<float>(fBot);
        tele->fLeft = static_cast<float>(kFlagUnchanged);
        tele->fRight = static_cast<float>(kFlagUnchanged);
        tele->gapJumpTop = gapJumpTop ? 1.f : 0.f;
        tele->gapJumpBot = gapJumpBot ? 1.f : 0.f;
        tele->landTop = gapJumpTop ? landTop : 0.f;
        tele->landBot = gapJumpBot ? landBot : 0.f;
        tele->farL = farU0;
        tele->farR = farU1;
        const float uSpan = std::max(1.f, seedU1 - seedU0);
        const int inkW = lookInkAtPlane.cols;
        auto uToX = [&](float u) -> int {
            return static_cast<int>(std::lround((u - seedU0) / uSpan * static_cast<float>(inkW)));
        };
        auto vToY = [&](float v) -> int {
            return static_cast<int>(std::lround(v - lookV0));
        };
        tele->nInkSeed = static_cast<float>(
            countInkU8(lookInkAtPlane, 0, ySeed0, inkW, ySeed1));
        tele->nInkBlue = static_cast<float>(
            countInkU8(lookInkAtPlane, uToX(seed.u0), vToY(v0), uToX(seed.u1), vToY(v1)));
        tele->nInkYellow = static_cast<float>(
            countInkU8(
                lookInkAtPlane, uToX(farU0), vToY(std::min(origV0, v0)),
                uToX(farU1), vToY(std::max(origV1, v1))));
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
    oriToQuad(seed, outPts8);
    paintLookOverlay(
        lookBin, lookPoison, overlayY8, overlayUv2, true, 0, 0,
        seed.cx, seed.cy, seed.ux, seed.uy, seed.vx, seed.vy,
        seed.u0, seed.u1, lookV0);
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
    float seedBh,
    const cv::Mat* lookBin, float seedV0, float seedV1, int minRun,
    float lookV0, float* farU0, float* farU1
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
    const float mapU0 = box->u0;
    const float mapU1 = box->u1;
    const float mapUSpan = std::max(1.f, mapU1 - mapU0);
    const int bw = useInk ? lookBin->cols : 0;
    const int bh = useInk ? lookBin->rows : 0;
    auto faceHas = [&](float u) -> bool {
        if (!useInk || bw < 1 || bh < 1) return false;
        const int n = std::max(4, static_cast<int>(std::lround(sv1 - sv0)));
        int best = 0, run = 0;
        for (int i = 0; i < n; ++i) {
            const float v = sv0 + (i + 0.5f) / n * (sv1 - sv0);
            const int ix = static_cast<int>(std::lround((u - mapU0) / mapUSpan * static_cast<float>(bw)));
            const int iy = static_cast<int>(std::lround(v - lookV0));
            const bool on = ix >= 0 && ix < bw && iy >= 0 && iy < bh &&
                lookBin->ptr<uint8_t>(iy)[ix] != 0;
            if (on) {
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
    float probe0 = u0;
    float probe1 = u1;
    int jumps0 = 0;
    while (jumps0 < kJumpMax) {
        const float next0 = u0 - jx;
        probe0 = next0;
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
        probe1 = next1;
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
    if (farU0) *farU0 = probe0;
    if (farU1) *farU1 = probe1;
}

}  // namespace

static jfloatArray seg7OrientedMany(
    JNIEnv* env,
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jfloatArray seedsArr, jint chromaMode,
    jint boundStrategy, jint tightInsetPx,
    jfloatArray teleArr, jshortArray sweepArr, jlong dumpPtr,
    jlong overlayYPtr, jlong overlayUvPtr, jintArray poisonArr,
    jlong tintPtr = 0,
    bool doHorzJump = false
) {
    auto* gray = reinterpret_cast<cv::Mat*>(grayPtr);
    if (!gray || gray->empty() || gray->type() != CV_8UC1 || !seedsArr) return nullptr;
    const int imgW = gray->cols, imgH = gray->rows;
    const jint n8 = env->GetArrayLength(seedsArr);
    if (n8 <= 0 || n8 % 8 != 0) return env->NewFloatArray(0);
    const int n = n8 / 8;
    std::vector<jfloat> seeds(n8);
    env->GetFloatArrayRegion(seedsArr, 0, n8, seeds.data());
    cv::Mat* cMag = nullptr;
    const bool useChroma = chromaMode == 1;
    const bool useTint = chromaMode == 2 || chromaMode == 3 || chromaMode == 4;
    const bool adaptive = chromaMode == 4;
    auto* uv = reinterpret_cast<cv::Mat*>(uvPtr);
    auto* scratch = reinterpret_cast<cv::Mat*>(scratchPtr);
    auto* inkDump = reinterpret_cast<cv::Mat*>(dumpPtr);
    auto* overlayY = reinterpret_cast<cv::Mat*>(overlayYPtr);
    auto* overlayUv = reinterpret_cast<cv::Mat*>(overlayUvPtr);
    cv::Mat* ovUv = asUV(overlayUv);
    cv::Mat* lookBinHost = asU8(scratch);
    cv::Mat* rotPoison = asU8(uv);
    cv::Mat* tintPlane = asU8(reinterpret_cast<cv::Mat*>(tintPtr));
    std::vector<PoisonStats> poisonPacks;
    poisonPacks.resize(static_cast<size_t>(n));
    if (useChroma) {
        if (asU8(overlayY) && scratchFits(overlayY, imgW, imgH)) {
            fillChromaMag(*gray, uv ? *uv : cv::Mat(), overlayY);
            cMag = overlayY;
        }
    }
    std::vector<jfloat> out(n * 9, 0.f);
    std::vector<InkSweepPack> sweeps;
    sweeps.resize(static_cast<size_t>(n));
    ObjPack objPack;
    loadObjPack(env, poisonArr, &objPack);
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
            cv::Mat* tintDst = tintPlane;
            if (fillChromaTintMask(*gray, *uv, sl, st, sr, sb, tintDst, 9, 0,
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
        objPack.classChange = 0;
        objPack.seenInk = 0;
        objPack.seenNon = 0;
        seg7OrientedOne(*src, box, imgW, imgH, op, &sPx,
            boundStrategy, tightInsetPx, srcIsBin, &tele, keepColor,
            &sweeps[static_cast<size_t>(i)], inkDump, overlayY, ovUv,
            &poisonPacks[static_cast<size_t>(i)], scratch, &objPack, i, rotPoison,
            lookBinHost, doHorzJump);
        if (objPack.abort) {
            poisonPacks[static_cast<size_t>(i)].bandH = -1;
            appendObjMeta(&poisonPacks[static_cast<size_t>(i)], objPack, i, n);
            break;
        }
        appendObjMeta(&poisonPacks[static_cast<size_t>(i)], objPack, i, n);
        op[8] = sPx;
        storeTeleArr(env, teleArr, i, tele);
    }
    writeSweepArr(env, sweepArr, sweeps);
    writePoisonArr(env, poisonArr, poisonPacks);
    storeObjPack(env, poisonArr, objPack);
    jfloatArray arr = env->NewFloatArray(static_cast<jint>(out.size()));
    if (!arr) return nullptr;
    env->SetFloatArrayRegion(arr, 0, static_cast<jint>(out.size()), out.data());
    return arr;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeGrayOrientTight(
    JNIEnv* env, jobject thiz,
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jfloatArray seedsArr,
    jfloatArray teleArr, jshortArray sweepArr, jlong dumpPtr,
    jlong overlayYPtr, jlong overlayUvPtr, jintArray poisonArr
) {
    return seg7OrientedMany(
        env, grayPtr, uvPtr, scratchPtr, seedsArr, 0, 0, 0,
        teleArr, sweepArr, dumpPtr, overlayYPtr, overlayUvPtr, poisonArr, 0, true);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeGrayOrientRetract(
    JNIEnv* env, jobject thiz,
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jfloatArray seedsArr,
    jfloatArray teleArr, jshortArray sweepArr, jlong dumpPtr,
    jlong overlayYPtr, jlong overlayUvPtr, jintArray poisonArr
) {
    return seg7OrientedMany(
        env, grayPtr, uvPtr, scratchPtr, seedsArr, 0, 2, 0,
        teleArr, sweepArr, dumpPtr, overlayYPtr, overlayUvPtr, poisonArr, 0, true);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeGrayOrientExpand(
    JNIEnv* env, jobject thiz,
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jfloatArray seedsArr,
    jfloatArray teleArr, jshortArray sweepArr, jlong dumpPtr,
    jlong overlayYPtr, jlong overlayUvPtr, jintArray poisonArr
) {
    return seg7OrientedMany(
        env, grayPtr, uvPtr, scratchPtr, seedsArr, 0, 0, 16,
        teleArr, sweepArr, dumpPtr, overlayYPtr, overlayUvPtr, poisonArr);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeColorOrientTight(
    JNIEnv* env, jobject thiz,
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jfloatArray seedsArr,
    jfloatArray teleArr, jshortArray sweepArr, jlong dumpPtr,
    jlong overlayYPtr, jlong overlayUvPtr, jintArray poisonArr,
    jlong tintPtr
) {
    return seg7OrientedMany(
        env, grayPtr, uvPtr, scratchPtr, seedsArr, 4, 0, 0,
        teleArr, sweepArr, dumpPtr, overlayYPtr, overlayUvPtr, poisonArr, tintPtr, true);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeColorOrientRetract(
    JNIEnv* env, jobject thiz,
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jfloatArray seedsArr,
    jfloatArray teleArr, jshortArray sweepArr, jlong dumpPtr,
    jlong overlayYPtr, jlong overlayUvPtr, jintArray poisonArr,
    jlong tintPtr
) {
    return seg7OrientedMany(
        env, grayPtr, uvPtr, scratchPtr, seedsArr, 4, 2, 0,
        teleArr, sweepArr, dumpPtr, overlayYPtr, overlayUvPtr, poisonArr, tintPtr, true);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeColorOrientExpand(
    JNIEnv* env, jobject thiz,
    jlong grayPtr, jlong uvPtr, jlong scratchPtr, jfloatArray seedsArr,
    jfloatArray teleArr, jshortArray sweepArr, jlong dumpPtr,
    jlong overlayYPtr, jlong overlayUvPtr, jintArray poisonArr,
    jlong tintPtr
) {
    return seg7OrientedMany(
        env, grayPtr, uvPtr, scratchPtr, seedsArr, 4, 0, 16,
        teleArr, sweepArr, dumpPtr, overlayYPtr, overlayUvPtr, poisonArr, tintPtr);
}

