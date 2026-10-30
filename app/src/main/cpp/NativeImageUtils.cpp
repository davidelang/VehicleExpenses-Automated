#include <jni.h>
#include <android/bitmap.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/imgcodecs.hpp>
#include <string>
#include <sstream>
#include <vector>
#include <algorithm>
#include <map>
#include <cmath>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <mutex>
#include "DngDevelop.h"
#include "paddle/paddle_api.h"
#include "BufferSetHandle.h"
#include "OrientedQuadOrder.h"
#include <android/log.h>
#include <zlib.h>
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "NativeImageUtils", __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "NativeImageUtils", __VA_ARGS__)

static void veAllocLog(const char* tag, size_t bytes, int rows, int cols, int type) {
    LOGI("veAllocLog tag=%s bytes=%zu rows=%d cols=%d type=%d", tag, bytes, rows, cols, type);
}

static cv::Mat g_jpegBgr;
static std::mutex g_jpegMu;
static cv::Mat jpegBgrScratch(int w, int h);



static void logMatHeader(const char* tag, const cv::Mat* m) {
    if (!m) { LOGI("MAT_HEADER: %s null", tag); return; }
    LOGI("MAT_HEADER: %s cols=%d rows=%d dims=%d type=%d ch=%d flags=0x%x step0=%zu step1=%zu data=%p datastart=%p dataend=%p cont=%d empty=%d",
         tag, m->cols, m->rows, m->dims, m->type(), m->channels(), m->flags,
         m->step[0], (m->dims > 1 ? m->step[1] : 0), (void*)m->data,
         (void*)m->datastart, (void*)m->dataend, m->isContinuous(), m->empty());
}

#include "../libraw_config.h"
#include <libraw/libraw.h>

static const char base64_chars[] = 
             "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
             "abcdefghijklmnopqrstuvwxyz"
             "0123456789+/";

std::string base64_encode(unsigned char const* bytes_to_encode, unsigned int in_len) {
    std::string ret;
    int i = 0;
    unsigned char char_array_3[3];
    unsigned char char_array_4[4];

    while (in_len--) {
        char_array_3[i++] = *(bytes_to_encode++);
        if (i == 3) {
            char_array_4[0] = (char_array_3[0] & 0xfc) >> 2;
            char_array_4[1] = ((char_array_3[0] & 0x03) << 4) + ((char_array_3[1] & 0xf0) >> 4);
            char_array_4[2] = ((char_array_3[1] & 0x0f) << 2) + ((char_array_3[2] & 0xc0) >> 6);
            char_array_4[3] = char_array_3[2] & 0x3f;
            for(int k = 0; k < 4; k++) ret += base64_chars[char_array_4[k]];
            i = 0;
        }
    }

    if (i) {
        int j = 0;
        for(j = i; j < 3; j++) char_array_3[j] = '\0';
        char_array_4[0] = (char_array_3[0] & 0xfc) >> 2;
        char_array_4[1] = ((char_array_3[0] & 0x03) << 4) + ((char_array_3[1] & 0xf0) >> 4);
        char_array_4[2] = ((char_array_3[1] & 0x0f) << 2) + ((char_array_3[2] & 0xc0) >> 6);
        char_array_4[3] = char_array_3[2] & 0x3f;
        for (j = 0; (j < i + 1); j++) ret += base64_chars[char_array_4[j]];
        while((i++ < 3)) ret += '=';
    }
    return ret;
}

std::string matToBase64(const cv::Mat& mat, int quality = 95) {
    if (mat.empty()) return "";
    std::vector<uint8_t> buf;
    std::vector<int> params = {cv::IMWRITE_JPEG_QUALITY, quality};
    cv::imencode(".jpg", mat, buf, params);
    return base64_encode(buf.data(), buf.size());
}

static void throwIllegalState(JNIEnv* env, const char* what) {
    jclass ex = env->FindClass("java/lang/IllegalStateException");
    if (!ex) return;
    env->ThrowNew(ex, what);
}



extern "C" {
// ... (keep nativeSyncMatFromArgb etc)


JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeSyncMatFromArgb(
    JNIEnv* env, jobject thiz, jobject bitmap, jlong dstMatPtr) {
    
    AndroidBitmapInfo info;
    void* pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        throwIllegalState(env, "nativeSyncMatFromArgb: AndroidBitmap_getInfo failed");
        return;
    }
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        throwIllegalState(env, "nativeSyncMatFromArgb: AndroidBitmap_lockPixels failed");
        return;
    }
    
    auto* dstMat = reinterpret_cast<cv::Mat*>(dstMatPtr);
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 || !dstMat || !dstMat->data ||
        dstMat->cols != (int)info.width || dstMat->rows != (int)info.height || !pixels) {
        AndroidBitmap_unlockPixels(env, bitmap);
        throwIllegalState(env, "nativeSyncMatFromArgb: format or size mismatch");
        return;
    }
    uint8_t* src = (uint8_t*)pixels;
    uint8_t* dst = dstMat->data;
    for (uint32_t y = 0; y < info.height; ++y) {
        const uint8_t* row = src + static_cast<size_t>(y) * info.stride;
        uint8_t* out = dst + static_cast<size_t>(y) * dstMat->step;
        for (uint32_t x = 0; x < info.width; ++x) {
            out[x] = row[x * 4];
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeSyncMatToArgb(
    JNIEnv* env, jobject thiz, jlong srcMatPtr, jobject bitmap) {
    
    AndroidBitmapInfo info;
    void* pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        throwIllegalState(env, "nativeSyncMatToArgb: AndroidBitmap_getInfo failed");
        return;
    }
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        throwIllegalState(env, "nativeSyncMatToArgb: AndroidBitmap_lockPixels failed");
        return;
    }
    
    auto* srcMat = reinterpret_cast<cv::Mat*>(srcMatPtr);
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 || !srcMat || !srcMat->data ||
        srcMat->cols != (int)info.width || srcMat->rows != (int)info.height || !pixels) {
        AndroidBitmap_unlockPixels(env, bitmap);
        throwIllegalState(env, "nativeSyncMatToArgb: format or size mismatch");
        return;
    }
    uint8_t* src = srcMat->data;
    uint8_t* dst = (uint8_t*)pixels;
    for (uint32_t y = 0; y < info.height; ++y) {
        const uint8_t* row = src + static_cast<size_t>(y) * srcMat->step;
        uint8_t* out = dst + static_cast<size_t>(y) * info.stride;
        for (uint32_t x = 0; x < info.width; ++x) {
            const uint8_t v = row[x];
            uint8_t* px = out + static_cast<size_t>(x) * 4;
            px[0] = v;
            px[1] = v;
            px[2] = v;
            px[3] = 0xFF;
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}









/** Standing JPEG BGR plane; grows, never released per call. Dest-crop sized. */
static cv::Mat jpegBgrScratch(int w, int h) {
    std::lock_guard<std::mutex> lock(g_jpegMu);
    if (w < 1) w = 1;
    if (h < 1) h = 1;
    if (g_jpegBgr.empty() || g_jpegBgr.type() != CV_8UC3 ||
        g_jpegBgr.rows < h || g_jpegBgr.cols < w) {
        const int nr = std::max(h, g_jpegBgr.rows);
        const int nc = std::max(w, g_jpegBgr.cols);
        g_jpegBgr.create(nr, nc, CV_8UC3);
    }
    if (!g_jpegBgr.data) return cv::Mat();
    return cv::Mat(h, w, CV_8UC3, g_jpegBgr.data);
}

static void yuvToBgrPixel(int Y, int U, int V, cv::Vec3b* out) {
    int r = Y + static_cast<int>(1.402 * (V - 128));
    int g = Y - static_cast<int>(0.344136 * (U - 128) + 0.714136 * (V - 128));
    int b = Y + static_cast<int>(1.772 * (U - 128));
    (*out)[0] = static_cast<uint8_t>(std::max(0, std::min(255, b)));
    (*out)[1] = static_cast<uint8_t>(std::max(0, std::min(255, g)));
    (*out)[2] = static_cast<uint8_t>(std::max(0, std::min(255, r)));
}

static bool encodeYuvMatJpeg(const cv::Mat& y, const cv::Mat& uv, int quality, std::vector<uint8_t>* out) {
    if (!out || y.empty() || y.type() != CV_8UC1) return false;
    const int w = y.cols, h = y.rows;
    if (w < 1 || h < 1) return false;
    cv::Mat bgr = jpegBgrScratch(w, h);
    if (bgr.empty() || bgr.rows < h || bgr.cols < w) return false;
    const bool hasUv = !uv.empty() && uv.type() == CV_8UC2 && uv.rows >= (h + 1) / 2 && uv.cols >= (w + 1) / 2;
    for (int yy = 0; yy < h; ++yy) {
        const uint8_t* yp = y.ptr<uint8_t>(yy);
        const uint8_t* uvp = hasUv ? uv.ptr<uint8_t>(yy / 2) : nullptr;
        cv::Vec3b* dp = bgr.ptr<cv::Vec3b>(yy);
        for (int xx = 0; xx < w; ++xx) {
            int U = 128, V = 128;
            if (uvp) {
                const int ux = (xx / 2) * 2;
                V = uvp[ux];
                U = uvp[ux + 1];
            }
            yuvToBgrPixel(yp[xx], U, V, &dp[xx]);
        }
    }
    if (!bgr.isContinuous()) return false;
    std::vector<int> params = {cv::IMWRITE_JPEG_QUALITY, quality};
    return cv::imencode(".jpg", bgr, *out, params);
}


extern "C" bool veEncodeYuvMatJpeg(const cv::Mat& y, const cv::Mat& uv, std::vector<uint8_t>* out) {
    return encodeYuvMatJpeg(y, uv, 80, out);
}

static bool encodeYuvPlanesJpeg(
    const uint8_t* yData, const uint8_t* uData, const uint8_t* vData,
    int w, int h, int stride, int quality, std::vector<uint8_t>* out
) {
    if (!out || !yData || w < 1 || h < 1) return false;
    cv::Mat bgr = jpegBgrScratch(w, h);
    if (bgr.empty() || bgr.rows < h || bgr.cols < w) return false;
    for (int y = 0; y < h; ++y) {
        cv::Vec3b* dp = bgr.ptr<cv::Vec3b>(y);
        const uint8_t* yp = yData + y * stride;
        for (int x = 0; x < w; ++x) {
            int U = 128, V = 128;
            if (uData && vData) {
                const int i = (y / 2) * stride + (x / 2) * 2;
                V = vData[i];
                U = uData[i];
            }
            yuvToBgrPixel(yp[x], U, V, &dp[x]);
        }
    }
    if (!bgr.isContinuous()) return false;
    std::vector<int> params = {cv::IMWRITE_JPEG_QUALITY, quality};
    return cv::imencode(".jpg", bgr, *out, params);
}

static void scalePlaneU8(const cv::Mat& src, int sl, int st, int sr, int sb, cv::Mat* dst) {
    const int dw = dst->cols, dh = dst->rows;
    const int sw = std::max(1, sr - sl);
    const int sh = std::max(1, sb - st);
    const int srcW = src.cols, srcH = src.rows;
    for (int y = 0; y < dh; ++y) {
        int y0 = st + y * sh / dh;
        int y1 = st + (y + 1) * sh / dh;
        if (y1 <= y0) y1 = y0 + 1;
        if (y0 < 0) y0 = 0;
        if (y1 > srcH) y1 = srcH;
        if (y0 >= y1) { y0 = std::max(0, srcH - 1); y1 = srcH; }
        uint8_t* d = dst->ptr<uint8_t>(y);
        for (int x = 0; x < dw; ++x) {
            int x0 = sl + x * sw / dw;
            int x1 = sl + (x + 1) * sw / dw;
            if (x1 <= x0) x1 = x0 + 1;
            if (x0 < 0) x0 = 0;
            if (x1 > srcW) x1 = srcW;
            if (x0 >= x1) { x0 = std::max(0, srcW - 1); x1 = srcW; }
            int sum = 0, n = 0;
            for (int yy = y0; yy < y1; ++yy) {
                const uint8_t* p = src.ptr<uint8_t>(yy);
                for (int xx = x0; xx < x1; ++xx) {
                    sum += p[xx];
                    ++n;
                }
            }
            d[x] = n > 0 ? static_cast<uint8_t>(sum / n) : 0;
        }
    }
}

static void scalePlaneUv(const cv::Mat& src, int sl, int st, int sr, int sb, cv::Mat* dst) {
    const int dw = dst->cols, dh = dst->rows;
    const int sw = std::max(1, sr - sl);
    const int sh = std::max(1, sb - st);
    const int srcW = src.cols, srcH = src.rows;
    for (int y = 0; y < dh; ++y) {
        int y0 = st + y * sh / dh;
        int y1 = st + (y + 1) * sh / dh;
        if (y1 <= y0) y1 = y0 + 1;
        if (y0 < 0) y0 = 0;
        if (y1 > srcH) y1 = srcH;
        if (y0 >= y1) { y0 = std::max(0, srcH - 1); y1 = srcH; }
        uint8_t* d = dst->ptr<uint8_t>(y);
        for (int x = 0; x < dw; ++x) {
            int x0 = sl + x * sw / dw;
            int x1 = sl + (x + 1) * sw / dw;
            if (x1 <= x0) x1 = x0 + 1;
            if (x0 < 0) x0 = 0;
            if (x1 > srcW) x1 = srcW;
            if (x0 >= x1) { x0 = std::max(0, srcW - 1); x1 = srcW; }
            int sumU = 0, sumV = 0, n = 0;
            for (int yy = y0; yy < y1; ++yy) {
                const uint8_t* p = src.ptr<uint8_t>(yy);
                for (int xx = x0; xx < x1; ++xx) {
                    sumV += p[xx * 2];
                    sumU += p[xx * 2 + 1];
                    ++n;
                }
            }
            if (n > 0) {
                d[x * 2] = static_cast<uint8_t>(sumV / n);
                d[x * 2 + 1] = static_cast<uint8_t>(sumU / n);
            } else {
                d[x * 2] = 128;
                d[x * 2 + 1] = 128;
            }
        }
    }
}

JNIEXPORT jboolean JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeScalePackedU8(
    JNIEnv* env, jobject /*thiz*/,
    jlong srcYPtr, jobject dstBuf, jint S, jint innerW, jint innerH
) {
    auto* srcY = reinterpret_cast<cv::Mat*>(srcYPtr);
    if (!srcY || srcY->empty() || srcY->type() != CV_8UC1 || !dstBuf || S < 1) return JNI_FALSE;
    if (innerW < 1) innerW = 1;
    if (innerH < 1) innerH = 1;
    if (innerW > S || innerH > S) return JNI_FALSE;
    void* data = env->GetDirectBufferAddress(dstBuf);
    if (!data) return JNI_FALSE;
    const jlong cap = env->GetDirectBufferCapacity(dstBuf);
    const jlong need = static_cast<jlong>(S) * static_cast<jlong>(S);
    if (cap < need) return JNI_FALSE;
    std::memset(data, 0, static_cast<size_t>(need));
    cv::Mat inner(innerH, innerW, CV_8UC1, data, static_cast<size_t>(S));
    scalePlaneU8(*srcY, 0, 0, srcY->cols, srcY->rows, &inner);
    return JNI_TRUE;
}


JNIEXPORT jboolean JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeScaleYuvRoi(
    JNIEnv* /*env*/, jobject /*thiz*/,
    jlong srcYPtr, jlong srcUvPtr,
    jint sl, jint st, jint sr, jint sb,
    jlong dstYPtr, jlong dstUvPtr
) {
    auto* srcY = reinterpret_cast<cv::Mat*>(srcYPtr);
    auto* dstY = reinterpret_cast<cv::Mat*>(dstYPtr);
    auto* dstUv = reinterpret_cast<cv::Mat*>(dstUvPtr);
    if (!srcY || srcY->empty() || srcY->type() != CV_8UC1 || !dstY || dstY->empty() ||
        dstY->type() != CV_8UC1) {
        return JNI_FALSE;
    }
    const int imgW = srcY->cols, imgH = srcY->rows;
    int l = sl, t = st, r = sr, b = sb;
    if (l < 0) l = 0;
    if (t < 0) t = 0;
    if (r > imgW) r = imgW;
    if (b > imgH) b = imgH;
    if (r <= l) r = std::min(imgW, l + 1);
    if (b <= t) b = std::min(imgH, t + 1);
    scalePlaneU8(*srcY, l, t, r, b, dstY);
    if (dstUv && !dstUv->empty() && dstUv->type() == CV_8UC2) {
        auto* srcUv = reinterpret_cast<cv::Mat*>(srcUvPtr);
        if (srcUv && !srcUv->empty() && srcUv->type() == CV_8UC2) {
            const int ul = l / 2, ut = t / 2, ur = (r + 1) / 2, ub = (b + 1) / 2;
            scalePlaneUv(*srcUv, ul, ut, ur, ub, dstUv);
        } else {
            dstUv->setTo(cv::Scalar(128, 128));
        }
    }
    return JNI_TRUE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeEncodeYuvMatJpeg(
    JNIEnv* env, jobject /*thiz*/,
    jlong yPtr, jlong uvPtr, jint quality
) {
    auto* y = reinterpret_cast<cv::Mat*>(yPtr);
    auto* uv = reinterpret_cast<cv::Mat*>(uvPtr);
    std::vector<uint8_t> buf;
    cv::Mat empty;
    if (!encodeYuvMatJpeg(y ? *y : empty, uv ? *uv : empty, quality, &buf) || buf.empty()) {
        return env->NewByteArray(0);
    }
    jbyteArray arr = env->NewByteArray(static_cast<jint>(buf.size()));
    if (!arr) return env->NewByteArray(0);
    env->SetByteArrayRegion(arr, 0, static_cast<jint>(buf.size()),
        reinterpret_cast<const jbyte*>(buf.data()));
    return arr;
}


// Shared: read paddle tensor as float heatmap.
// Supports float32, uint8 (u/255), and float16 (if ENABLE_ARM_FP16 / half bits).
// Never call Java Tensor.getFloatData on non-float tensors — that SEGV/aborts.
static bool tensorToFloatHeatmap(paddle::lite_api::Tensor* nativeTensor, int h, int w,
                                 std::vector<float>* out) {
    if (!nativeTensor || !out) return false;
    out->assign(static_cast<size_t>(h) * static_cast<size_t>(w), 0.f);
    using paddle::lite_api::PrecisionType;
    const auto prec = nativeTensor->precision();
    if (prec == PrecisionType::kFloat) {
        const float* data = nativeTensor->data<float>();
        if (!data) return false;
        std::memcpy(out->data(), data, out->size() * sizeof(float));
        return true;
    }
    if (prec == PrecisionType::kUInt8) {
        const uint8_t* data = nativeTensor->data<uint8_t>();
        if (!data) return false;
        for (size_t i = 0; i < out->size(); ++i) {
            (*out)[i] = static_cast<float>(data[i]) * (1.0f / 255.0f);
        }
        return true;
    }
    if (prec == PrecisionType::kFP16) {
        // Store as uint16 IEEE half; convert via float bit-cast helpers when available.
        const uint16_t* data = reinterpret_cast<const uint16_t*>(
            nativeTensor->data<int16_t>());
        if (!data) return false;
        for (size_t i = 0; i < out->size(); ++i) {
            // Simple half->float (IEEE 754 binary16), no denorm special path needed for heatmaps.
            const uint16_t hbits = data[i];
            const uint32_t sign = (hbits >> 15) & 1u;
            const uint32_t exp = (hbits >> 10) & 0x1fu;
            const uint32_t mant = hbits & 0x3ffu;
            uint32_t fbits;
            if (exp == 0) {
                fbits = (sign << 31);
            } else if (exp == 31) {
                fbits = (sign << 31) | 0x7f800000u | (mant << 13);
            } else {
                fbits = (sign << 31) | ((exp + 112u) << 23) | (mant << 13);
            }
            float f;
            std::memcpy(&f, &fbits, sizeof(f));
            (*out)[i] = f;
        }
        return true;
    }
    // Fallback: try float pointer (legacy models / unknown precision)
    const float* data = nativeTensor->data<float>();
    if (!data) return false;
    std::memcpy(out->data(), data, out->size() * sizeof(float));
    return true;
}



// Raw uint8 greyscale feed for kUInt8 + uint8_to_fp* graphs (no XOR).
// Still a host buffer copy into the tensor ByteArray for Tensor.setData;
// eliminates the xor rewrite vs int8 path.
JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativePopulateMonoUInt8(
    JNIEnv* env, jobject thiz, jlong srcMatPtr, jbyteArray dstTensor, jint tensorW, jint tensorH) {
    cv::Mat* src = reinterpret_cast<cv::Mat*>(srcMatPtr);
    if (!src || src->empty() || !dstTensor || tensorW < 1 || tensorH < 1) {
        throwIllegalState(env, "nativePopulateMonoUInt8: bad args");
        return;
    }
    if (src->cols != tensorW || src->rows != tensorH || src->type() != CV_8UC1) {
        throwIllegalState(env, "nativePopulateMonoUInt8: size mismatch");
        return;
    }
    jbyte* dst = env->GetByteArrayElements(dstTensor, nullptr);
    if (!dst) {
        throwIllegalState(env, "nativePopulateMonoUInt8: null array");
        return;
    }
    const jsize n = env->GetArrayLength(dstTensor);
    const jlong need = static_cast<jlong>(tensorW) * static_cast<jlong>(tensorH);
    if (need > n) {
        env->ReleaseByteArrayElements(dstTensor, dst, JNI_ABORT);
        throwIllegalState(env, "nativePopulateMonoUInt8: destination shorter than the tensor");
        return;
    }
    for (int y = 0; y < tensorH; ++y) {
        const uint8_t* row = src->ptr<uint8_t>(y);
        std::memcpy(dst + static_cast<jlong>(y) * tensorW, row, static_cast<size_t>(tensorW));
    }
    env->ReleaseByteArrayElements(dstTensor, dst, 0);
}

// ── Lite input ShareExternal for H-span zero-copy (pin held until release) ──
// Tensor Java object holds cppTensorPointer = std::unique_ptr<Tensor>*.
// Pin is process-global (one shared input at a time; multi-scale is single-threaded det).
static struct {
    jbyteArray arr_global;
    jbyte* elems;
    jboolean is_copy;
    jobject buf_global;
    int held;
} g_tensor_u8_share = {nullptr, nullptr, JNI_FALSE, nullptr, 0};

static void release_tensor_u8_share(JNIEnv* env) {
    if (!g_tensor_u8_share.held) return;
    if (g_tensor_u8_share.arr_global && g_tensor_u8_share.elems) {
        env->ReleaseByteArrayElements(
            g_tensor_u8_share.arr_global, g_tensor_u8_share.elems, JNI_ABORT);
    }
    if (g_tensor_u8_share.arr_global) {
        env->DeleteGlobalRef(g_tensor_u8_share.arr_global);
    }
    if (g_tensor_u8_share.buf_global) {
        env->DeleteGlobalRef(g_tensor_u8_share.buf_global);
    }
    g_tensor_u8_share.arr_global = nullptr;
    g_tensor_u8_share.elems = nullptr;
    g_tensor_u8_share.is_copy = JNI_FALSE;
    g_tensor_u8_share.buf_global = nullptr;
    g_tensor_u8_share.held = 0;
}

/**
 * Point Lite input tensor at canvas[offset .. offset+nbytes).
 * Keeps a pin on [arr] until nativeReleaseSharedTensorInputU8.
 * Returns: 1 ok zero-copy pin, 2 ok but JVM copied full array (not ideal), 0 fail.
 * On isCopy==true we still ShareExternal the copy so Run works, but log warns.
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeShareTensorInputU8(
    JNIEnv* env, jclass, jobject jtensor, jbyteArray arr, jint offset, jint nbytes) {
    if (!jtensor || !arr || offset < 0 || nbytes <= 0) return 0;
    release_tensor_u8_share(env);

    jclass cls = env->GetObjectClass(jtensor);
    jfieldID fid = env->GetFieldID(cls, "cppTensorPointer", "J");
    if (env->ExceptionCheck()) return 0;
    if (!fid) {
        throwIllegalState(env, "nativeShareTensorInputU8: cppTensorPointer missing");
        return 0;
    }
    jlong nativePtr = env->GetLongField(jtensor, fid);
    if (nativePtr == 0) {
        throwIllegalState(env, "nativeShareTensorInputU8: pointer is 0");
        return 0;
    }
    auto* uptr = reinterpret_cast<std::unique_ptr<paddle::lite_api::Tensor>*>(nativePtr);
    if (!uptr || !(*uptr)) {
        throwIllegalState(env, "nativeShareTensorInputU8: empty tensor");
        return 0;
    }

    jboolean isCopy = JNI_FALSE;
    jbyte* elems = env->GetByteArrayElements(arr, &isCopy);
    if (!elems) {
        throwIllegalState(env, "nativeShareTensorInputU8: GetByteArrayElements failed");
        return 0;
    }
    if (isCopy) {
        env->ReleaseByteArrayElements(arr, elems, JNI_ABORT);
        throwIllegalState(env, "nativeShareTensorInputU8: isCopy");
        return 0;
    }
    const jsize len = env->GetArrayLength(arr);
    if (static_cast<jlong>(offset) + static_cast<jlong>(nbytes) > static_cast<jlong>(len)) {
        env->ReleaseByteArrayElements(arr, elems, JNI_ABORT);
        throwIllegalState(env, "nativeShareTensorInputU8: range");
        return 0;
    }

    void* data = reinterpret_cast<uint8_t*>(elems) + static_cast<size_t>(offset);
    (*uptr)->ShareExternalMemory(
        data, static_cast<size_t>(nbytes), paddle::lite_api::TargetType::kHost);

    g_tensor_u8_share.arr_global = reinterpret_cast<jbyteArray>(env->NewGlobalRef(arr));
    g_tensor_u8_share.elems = elems;
    g_tensor_u8_share.is_copy = isCopy;
    g_tensor_u8_share.held = 1;

    LOGI("shareTensorInputU8 offset=%d nbytes=%d isCopy=%d", (int)offset, (int)nbytes, (int)isCopy);
    return isCopy ? 2 : 1;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeShareTensorInputU8Direct(
    JNIEnv* env, jclass, jobject jtensor, jobject buf, jint offset, jint nbytes) {
    if (!jtensor || !buf || offset < 0 || nbytes <= 0) return 0;
    release_tensor_u8_share(env);

    jclass cls = env->GetObjectClass(jtensor);
    jfieldID fid = env->GetFieldID(cls, "cppTensorPointer", "J");
    if (env->ExceptionCheck()) return 0;
    if (!fid) {
        throwIllegalState(env, "nativeShareTensorInputU8Direct: cppTensorPointer missing");
        return 0;
    }
    jlong nativePtr = env->GetLongField(jtensor, fid);
    if (nativePtr == 0) {
        throwIllegalState(env, "nativeShareTensorInputU8Direct: pointer is 0");
        return 0;
    }
    auto* uptr = reinterpret_cast<std::unique_ptr<paddle::lite_api::Tensor>*>(nativePtr);
    if (!uptr || !(*uptr)) {
        throwIllegalState(env, "nativeShareTensorInputU8Direct: empty tensor");
        return 0;
    }

    void* base = env->GetDirectBufferAddress(buf);
    if (!base) {
        throwIllegalState(env, "nativeShareTensorInputU8Direct: not a direct buffer");
        return 0;
    }
    const jlong cap = env->GetDirectBufferCapacity(buf);
    if (static_cast<jlong>(offset) + static_cast<jlong>(nbytes) > cap) {
        throwIllegalState(env, "nativeShareTensorInputU8Direct: range");
        return 0;
    }

    void* data = reinterpret_cast<uint8_t*>(base) + static_cast<size_t>(offset);
    (*uptr)->ShareExternalMemory(
        data, static_cast<size_t>(nbytes), paddle::lite_api::TargetType::kHost);

    g_tensor_u8_share.buf_global = env->NewGlobalRef(buf);
    g_tensor_u8_share.held = 1;

    LOGI("shareTensorInputU8Direct offset=%d nbytes=%d", (int)offset, (int)nbytes);
    return 1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeReleaseSharedTensorInputU8(
    JNIEnv* env, jclass) {
    release_tensor_u8_share(env);
}










// Return full heatmap as float[] for Java (safe for float32 / uint8 / fp16).
JNIEXPORT jfloatArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeHeatmapToFloatArray(
    JNIEnv* env, jobject thiz, jobject tensor) {
    jclass cls = env->GetObjectClass(tensor);
    jfieldID fid = env->GetFieldID(cls, "cppTensorPointer", "J");
    if (env->ExceptionCheck()) return nullptr;
    if (!fid) {
        throwIllegalState(env, "nativeHeatmapToFloatArray: cppTensorPointer missing");
        return nullptr;
    }
    jlong nativePtr = env->GetLongField(tensor, fid);
    if (nativePtr == 0) {
        throwIllegalState(env, "nativeHeatmapToFloatArray: pointer is 0");
        return nullptr;
    }
    auto* uptr = reinterpret_cast<std::unique_ptr<paddle::lite_api::Tensor>*>(nativePtr);
    if (!uptr || !(*uptr)) {
        throwIllegalState(env, "nativeHeatmapToFloatArray: empty tensor");
        return nullptr;
    }
    paddle::lite_api::Tensor* nativeTensor = uptr->get();
    auto shape = nativeTensor->shape();
    if (shape.size() < 2) {
        throwIllegalState(env, "nativeHeatmapToFloatArray: shape");
        return nullptr;
    }
    int h = (int)shape[shape.size() - 2];
    int w = (int)shape[shape.size() - 1];
    std::vector<float> heatBuf;
    if (!tensorToFloatHeatmap(nativeTensor, h, w, &heatBuf)) {
        throwIllegalState(env, "nativeHeatmapToFloatArray: precision");
        return nullptr;
    }
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(heatBuf.size()));
    if (!out) return nullptr;
    env->SetFloatArrayRegion(out, 0, static_cast<jsize>(heatBuf.size()), heatBuf.data());
    return out;
}

// Raw product heatmap as uint8 plane (w*h). kUInt8 copied; float/fp16 quantized via round(x*255).
JNIEXPORT jbyteArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeHeatmapToUInt8Array(
    JNIEnv* env, jobject thiz, jobject tensor) {
    jclass cls = env->GetObjectClass(tensor);
    jfieldID fid = env->GetFieldID(cls, "cppTensorPointer", "J");
    if (env->ExceptionCheck() || !fid) {
        throwIllegalState(env, "nativeHeatmapToUInt8Array: cppTensorPointer");
        return nullptr;
    }
    jlong nativePtr = env->GetLongField(tensor, fid);
    if (nativePtr == 0) {
        throwIllegalState(env, "nativeHeatmapToUInt8Array: pointer is 0");
        return nullptr;
    }
    auto* uptr = reinterpret_cast<std::unique_ptr<paddle::lite_api::Tensor>*>(nativePtr);
    if (!uptr || !(*uptr)) {
        throwIllegalState(env, "nativeHeatmapToUInt8Array: empty tensor");
        return nullptr;
    }
    paddle::lite_api::Tensor* nativeTensor = uptr->get();
    auto shape = nativeTensor->shape();
    if (shape.size() < 2) {
        throwIllegalState(env, "nativeHeatmapToUInt8Array: shape");
        return nullptr;
    }
    int h = (int)shape[shape.size() - 2];
    int w = (int)shape[shape.size() - 1];
    const size_t n = static_cast<size_t>(h) * static_cast<size_t>(w);
    if (n == 0) {
        throwIllegalState(env, "nativeHeatmapToUInt8Array: empty");
        return nullptr;
    }

    jbyteArray out = env->NewByteArray(static_cast<jsize>(n));
    if (!out) {
        throwIllegalState(env, "nativeHeatmapToUInt8Array: alloc");
        return nullptr;
    }
    jbyte* dst = env->GetByteArrayElements(out, nullptr);
    if (!dst) {
        throwIllegalState(env, "nativeHeatmapToUInt8Array: elements");
        return nullptr;
    }

    using paddle::lite_api::PrecisionType;
    const auto prec = nativeTensor->precision();
    bool ok = false;
    if (prec == PrecisionType::kUInt8) {
        const uint8_t* data = nativeTensor->data<uint8_t>();
        if (data) {
            std::memcpy(dst, data, n);
            ok = true;
        }
    } else {
        std::vector<float> heatBuf;
        if (tensorToFloatHeatmap(nativeTensor, h, w, &heatBuf) && heatBuf.size() >= n) {
            for (size_t i = 0; i < n; ++i) {
                float v = heatBuf[i];
                if (v < 0.f) v = 0.f;
                if (v > 1.f) v = 1.f;
                int q = (int)(v * 255.f + 0.5f);
                if (q < 0) q = 0;
                if (q > 255) q = 255;
                dst[i] = (jbyte)q;
            }
            ok = true;
        }
    }
    env->ReleaseByteArrayElements(out, dst, 0);
    if (!ok) {
        throwIllegalState(env, "nativeHeatmapToUInt8Array: precision");
        return nullptr;
    }
    return out;
}

// Last processHeatmap post path for Kotlin metadata (not ABI-stable across threads;
// det is single-threaded per engine in practice). Values: "u8" | "float".
static char g_lastHeatmapPostPath[16] = "unknown";

// Flood AABB + area for one 8-connected heat blob. id is 1..N (0 = background).
struct HeatBlob {
    int id;
    int area;
    int left;
    int top;
    int width;
    int height;
};

// Shared geometry pack from 8-connected u8 flood AABBs.
// confScale: multiply mean of confMat ROI (1.0 for CV_32F heat in [0,1]; 1/255 for CV_8U heat).
// labels is CV_8UC1 (onLab reads uint8). GrowCells / heatToPhoto unchanged.
static void packHeatmapBoxes(
    const cv::Mat& labels,
    const std::vector<HeatBlob>& blobs,
    int w,
    int h,
    float minArea,
    bool useAabb,
    const cv::Mat& confMat,
    float confScale,
    std::vector<float>* results,
    int maxBoxes,
    int growCells = 1,
    float heatToPhoto = 0.f,
    int photoW = 0,
    int photoH = 0) {
    (void)maxBoxes;
    int gc = growCells;
    if (gc < 0) gc = 0;
    if (gc > 1) gc = 1;
    const int cell = gc * 4;
    int count = 0;
    for (size_t bi = 0; bi < blobs.size(); ++bi) {

        const HeatBlob& blob = blobs[bi];
        const int l = (int)blob.id;
        int area = blob.area;
        if (area < minArea) continue;

        int left = blob.left;
        int top = blob.top;
        int width = blob.width;
        int height = blob.height;

        cv::Point2f vertices[4];
        if (useAabb) {
            // Axis-aligned min/max of on-mask pixels, then growCells heat pixels outward (0 = tight).
            const float x0 = (float)std::max(0, left - cell);
            const float y0 = (float)std::max(0, top - cell);
            const float x1 = (float)std::min(w, left + width + cell);
            const float y1 = (float)std::min(h, top + height + cell);
            vertices[0] = cv::Point2f(x0, y0);
            vertices[1] = cv::Point2f(x1, y0);
            vertices[2] = cv::Point2f(x1, y1);
            vertices[3] = cv::Point2f(x0, y1);
        } else {
            // Boundary pixels of this CC in the stats bbox → stack hull (no points(area) Mat).
            auto onLab = [&](int x, int y) -> bool {
                if ((unsigned)x >= (unsigned)w || (unsigned)y >= (unsigned)h) return false;
                return labels.ptr<int>(y)[x] == l;
            };
            auto isEdge = [&](int x, int y) -> bool {
                return !onLab(x - 1, y) || !onLab(x + 1, y) ||
                       !onLab(x, y - 1) || !onLab(x, y + 1);
            };
            int nBound = 0;
            for (int y = top; y < top + height; ++y) {
                const int* row = labels.ptr<int>(y);
                for (int x = left; x < left + width; ++x) {
                    if (row[x] != l) continue;
                    if (isEdge(x, y)) ++nBound;
                }
            }
            if (nBound < 1) continue;
            std::vector<cv::Point> pts;
            pts.reserve(static_cast<size_t>(nBound));
            for (int y = top; y < top + height; ++y) {
                const int* row = labels.ptr<int>(y);
                for (int x = left; x < left + width; ++x) {
                    if (row[x] != l) continue;
                    if (!isEdge(x, y)) continue;
                    pts.emplace_back(x, y);
                }
            }
            if (pts.empty()) continue;
            cv::Mat pointsHdr(static_cast<int>(pts.size()), 1, CV_32SC2, pts.data());
            cv::RotatedRect rect = cv::minAreaRect(pointsHdr);
            // Grow along the quad axes (not AABB) so tilted reds get the same stride-4 cell.
            rect.size.width += 2.f * (float)cell;
            rect.size.height += 2.f * (float)cell;
            rect.points(vertices);
            for (int i = 0; i < 4; ++i) {
                if (vertices[i].x < 0.f) vertices[i].x = 0.f;
                if (vertices[i].y < 0.f) vertices[i].y = 0.f;
                if (vertices[i].x > (float)w) vertices[i].x = (float)w;
                if (vertices[i].y > (float)h) vertices[i].y = (float)h;
            }
        }
        if (heatToPhoto > 0.f) {
            const float maxX = photoW > 0 ? (float)photoW : 0.f;
            const float maxY = photoH > 0 ? (float)photoH : 0.f;
            for (int i = 0; i < 4; ++i) {
                vertices[i].x *= heatToPhoto;
                vertices[i].y *= heatToPhoto;
                if (vertices[i].x < 0.f) vertices[i].x = 0.f;
                if (vertices[i].y < 0.f) vertices[i].y = 0.f;
                if (vertices[i].x > maxX) vertices[i].x = maxX;
                if (vertices[i].y > maxY) vertices[i].y = maxY;
            }
        }
        // OpenCV minAreaRect corner order is not TL/TR/BR/BL and does not pick u≈horizontal.
        // Canonicalize at the JNI source so Kotlin seeds are already image-oriented.
        canonicalizeVerticesTlTrBrBl(vertices);

        if (count < 3) {
            LOGI("nativeProcessHeatmap: box[%d] label=%d aabb=%d vertices=(%.1f,%.1f)(%.1f,%.1f)(%.1f,%.1f)(%.1f,%.1f) area=%d",
                 count, l, useAabb ? 1 : 0, vertices[0].x, vertices[0].y, vertices[1].x, vertices[1].y,
                 vertices[2].x, vertices[2].y, vertices[3].x, vertices[3].y, area);
        }

        float avgConf = 0.0f;
        int bx1 = std::max(0, left);
        int by1 = std::max(0, top);
        int bx2 = std::min(w, left + width);
        int by2 = std::min(h, top + height);
        if (bx2 > bx1 && by2 > by1 && !confMat.empty()) {
            cv::Mat roi = confMat(cv::Rect(bx1, by1, bx2 - bx1, by2 - by1));
            avgConf = (float)cv::mean(roi)[0] * confScale;
        }

        for (int i = 0; i < 4; ++i) {
            results->push_back(vertices[i].x);
            results->push_back(vertices[i].y);
        }
        results->push_back(avgConf);
        count++;
    }
}

// Optional binary mask grow before CC. Each pass = one 3x3 max-filter (~+1 heat px radius).
// Bounded by pass count (not a free heat walk). Merges nearby components intentionally.
// Does NOT use newly-added pixels as a license to pull more heat values — pure morph on mask.
//
// **Do not call OpenCV dilate/getStructuringElement here.** Our NDK+OpenCV 4.10 link throws
// cv::Exception normalizeAnchor from both getStructuringElement and dilate(Mat::ones(3,3))
// (v0.98-71 abort; v0.98-72+ try/catch left L/M as silent no-ops → identical reds). Pure C++
// 3x3 max avoids that ABI path. Scale note: growth is in *heatmap* pixels then mapped to
// full-res (e.g. ~2 heat-px at 224 → tens of full-image px after ICRS upscale).
static void dilateMaskPasses(cv::Mat& mask, cv::Mat& buf, int passes) {
    if (passes <= 0 || mask.empty() || mask.type() != CV_8UC1) return;
    if (buf.empty() || buf.type() != CV_8UC1 || buf.rows != mask.rows || buf.cols != mask.cols) {
        LOGE("dilateMaskPasses: wrap ping-pong missing size=%dx%d", mask.cols, mask.rows);
        return;
    }
    if (passes > 32) passes = 32;  // hard safety
    cv::Mat* a = &mask;
    cv::Mat* b = &buf;
    for (int p = 0; p < passes; ++p) {
        const int rows = a->rows;
        const int cols = a->cols;
        for (int y = 0; y < rows; ++y) {
            const int y0 = y > 0 ? y - 1 : 0;
            const int y2 = y + 1 < rows ? y + 1 : rows - 1;
            const uchar* row0 = a->ptr<uchar>(y0);
            const uchar* row1 = a->ptr<uchar>(y);
            const uchar* row2 = a->ptr<uchar>(y2);
            uchar* out = b->ptr<uchar>(y);
            for (int x = 0; x < cols; ++x) {
                const int x0 = x > 0 ? x - 1 : 0;
                const int x2 = x + 1 < cols ? x + 1 : cols - 1;
                uchar m = row0[x0];
                if (row0[x] > m) m = row0[x];
                if (row0[x2] > m) m = row0[x2];
                if (row1[x0] > m) m = row1[x0];
                if (row1[x] > m) m = row1[x];
                if (row1[x2] > m) m = row1[x2];
                if (row2[x0] > m) m = row2[x0];
                if (row2[x] > m) m = row2[x];
                if (row2[x2] > m) m = row2[x2];
                out[x] = m;
            }
        }
        std::swap(a, b);
    }
    if (a != &mask) {
        a->copyTo(mask);
    }
    LOGI("dilateMaskPasses: pure 3x3 max applied passes=%d size=%dx%d", passes, mask.cols, mask.rows);
}



// 8-connected BFS (same neighbor walk as poison flood255LookIds). Ids 1..254; 0 = background.
// Stops starting new blobs at 254 or boxCap. Heat test is in-predicate (no packed threshold mask).
// kind: 0 = heatU > thrU, 1 = heatF > thrF, 2 = mask plane != 0. Non-template: this TU is extern "C".
static bool heatOnAt(int kind, int x, int y, int w,
                     const uint8_t* heatU, double thrU, const float* heatF, float thrF,
                     const uint8_t* mask) {
    const size_t i = static_cast<size_t>(y) * static_cast<size_t>(w) + static_cast<size_t>(x);
    if (kind == 2) return mask[i] != 0;
    if (kind == 0) return static_cast<double>(heatU[i]) > thrU;
    return heatF[i] > thrF;
}

static void floodHeatBlobsU8(
    cv::Mat& labels, int w, int h, int kind,
    const uint8_t* heatU, double thrU, const float* heatF, float thrF, const uint8_t* mask,
    std::vector<HeatBlob>* blobs) {
    labels.setTo(0);
    blobs->clear();
    std::vector<int> st;
    st.reserve(256);
    int nextId = 1;
    for (int y = 0; y < h; ++y) {
        int* row = labels.ptr<int>(y);
        for (int x = 0; x < w; ++x) {
            if (row[x] != 0) continue;
            if (!heatOnAt(kind, x, y, w, heatU, thrU, heatF, thrF, mask)) continue;
            const int id = nextId++;
            st.clear();
            st.push_back(y * w + x);
            row[x] = id;
            int x0 = x, x1 = x + 1, y0 = y, y1 = y + 1;
            int area = 0;
            while (!st.empty()) {
                const int i = st.back();
                st.pop_back();
                const int cy = i / w;
                const int cx = i - cy * w;
                ++area;
                if (cx < x0) x0 = cx;
                if (cx + 1 > x1) x1 = cx + 1;
                if (cy < y0) y0 = cy;
                if (cy + 1 > y1) y1 = cy + 1;
                for (int dy = -1; dy <= 1; ++dy) {
                    for (int dx = -1; dx <= 1; ++dx) {
                        if (!dx && !dy) continue;
                        const int nx = cx + dx, ny = cy + dy;
                        if ((unsigned)nx >= (unsigned)w || (unsigned)ny >= (unsigned)h) continue;
                        int* np = labels.ptr<int>(ny) + nx;
                        if (*np != 0) continue;
                        if (!heatOnAt(kind, nx, ny, w, heatU, thrU, heatF, thrF, mask)) continue;
                        *np = id;
                        st.push_back(ny * w + nx);
                    }
                }
            }
            HeatBlob b;
            b.id = id;
            b.area = area;
            b.left = x0;
            b.top = y0;
            b.width = x1 - x0;
            b.height = y1 - y0;
            blobs->push_back(b);
        }
    }
}

// dilate=0: flood heat in-predicate (no mask, no edges). dilate>0: heap ping-pong then flood into labels.
// JNI has one scratchY (labels); packedSet.p is not a second JNI arg (NativeImageUtils.kt unlisted).
static bool heatMaybeDilateFlood(
    cv::Mat& labels, int w, int h, int dilatePasses,
    const uint8_t* heatU, double thrU, const float* heatF, float thrF,
    std::vector<HeatBlob>* blobs) {
    const size_t n = static_cast<size_t>(h) * static_cast<size_t>(w);
    if (dilatePasses <= 0) {
        if (heatU) {
            floodHeatBlobsU8(labels, w, h, 0, heatU, thrU, nullptr, 0.f, nullptr, blobs);
        } else if (heatF) {
            floodHeatBlobsU8(labels, w, h, 1, nullptr, 0.0, heatF, thrF, nullptr, blobs);
        } else {
            return false;
        }
        return true;
    }
    veAllocLog("heatDilateMask", n, h, w, CV_8UC1);
    veAllocLog("heatDilateBuf", n, h, w, CV_8UC1);
    cv::Mat mask(h, w, CV_8UC1);
    cv::Mat buf(h, w, CV_8UC1);
    uchar* mptr = mask.ptr<uchar>(0);
    if (heatU) {
        for (size_t i = 0; i < n; ++i) mptr[i] = heatU[i] > thrU ? (uchar)255 : (uchar)0;
    } else if (heatF) {
        for (size_t i = 0; i < n; ++i) mptr[i] = heatF[i] > thrF ? (uchar)255 : (uchar)0;
    } else {
        return false;
    }
    dilateMaskPasses(mask, buf, dilatePasses);
    floodHeatBlobsU8(labels, w, h, 2, nullptr, 0.0, nullptr, 0.f, mask.ptr<uchar>(0), blobs);
    return true;
}




// boxMode: 0 = minAreaRect on on-mask pixels (production); 1 = axis-aligned CC stats box (AABB).
// maskDilatePasses: morph dilate iterations on binary thr mask before flood (experiment L/M).
// kUInt8 product heatmaps: thr/flood/geometry/hist entirely on u8 — no float heat buffer.
// float/fp16: convert once via tensorToFloatHeatmap (legacy path).
// scratchPtr: u8 label plane (packed det square .s after run(); need h*w). Missing wrap → fail.
JNIEXPORT jfloatArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeProcessHeatmap(
    JNIEnv* env, jobject thiz, jobject tensor, jfloat threshold, jfloat minArea, jint boxMode,
    jint maskDilatePasses, jint maxBoxes, jint growCells, jlong scratchPtr,
    jfloat heatToPhoto, jint photoW, jint photoH) {

    (void)scratchPtr;
    jclass cls = env->GetObjectClass(tensor);
    jfieldID fid = env->GetFieldID(cls, "cppTensorPointer", "J");
    if (env->ExceptionCheck() || !fid) {
        throwIllegalState(env, "nativeProcessHeatmap: cppTensorPointer");
        return nullptr;
    }
    jlong nativePtr = env->GetLongField(tensor, fid);
    if (nativePtr == 0) {
        throwIllegalState(env, "nativeProcessHeatmap: pointer is 0");
        return nullptr;
    }

    auto* uptr = reinterpret_cast<std::unique_ptr<paddle::lite_api::Tensor>*>(nativePtr);
    if (!uptr || !(*uptr)) {
        throwIllegalState(env, "nativeProcessHeatmap: empty tensor");
        return nullptr;
    }

    paddle::lite_api::Tensor* nativeTensor = uptr->get();
    auto shape = nativeTensor->shape();
    if (shape.size() < 3) {
        throwIllegalState(env, "nativeProcessHeatmap: shape");
        return nullptr;
    }

    int h = (int)shape[shape.size() - 2];
    int w = (int)shape[shape.size() - 1];
    if (h <= 0 || w <= 0) {
        throwIllegalState(env, "nativeProcessHeatmap: size");
        return nullptr;
    }
    const size_t n = static_cast<size_t>(h) * static_cast<size_t>(w);

    using paddle::lite_api::PrecisionType;
    const auto prec = nativeTensor->precision();
    const bool useAabb = (boxMode == 1);
    (void)maxBoxes;
    std::vector<float> results;
    results.reserve(128);

    cv::Mat labels(h, w, CV_32S);
    std::vector<HeatBlob> blobs;

    if (prec == PrecisionType::kUInt8) {
        // --- u8-native path (product armv8 / phone prod_u8fp16 heat is kUInt8) ---
        const uint8_t* data = nativeTensor->data<uint8_t>();
        if (!data) {
            throwIllegalState(env, "nativeProcessHeatmap: null u8");
            return nullptr;
        }
        std::snprintf(g_lastHeatmapPostPath, sizeof(g_lastHeatmapPostPath), "u8");

        LOGI("nativeProcessHeatmap: path=u8 h=%d w=%d boxMode=%d thr=%.4f minArea=%.1f maxBoxes=%d first4=[%u,%u,%u,%u]",
             h, w, (int)boxMode, (float)threshold, (float)minArea, (int)maxBoxes,
             (unsigned)data[0], (unsigned)(n > 1 ? data[1] : 0),
             (unsigned)(n > 2 ? data[2] : 0), (unsigned)(n > 3 ? data[3] : 0));

        // Float thr t on (u/255): on iff u/255 > t iff u > t*255.
        // t=0 → thrU=0 → u≥1 (matches prior float path on u/255).
        const double thrU = static_cast<double>(threshold) * 255.0;
        cv::Mat heatU8(h, w, CV_8UC1, const_cast<uint8_t*>(data));
        if (!heatMaybeDilateFlood(labels, w, h, (int)maskDilatePasses,
                                  data, thrU, nullptr, 0.f, &blobs)) {
            throwIllegalState(env, "nativeProcessHeatmap: flood");
            return nullptr;
        }
        if (maskDilatePasses > 0) {
            LOGI("nativeProcessHeatmap: u8 maskDilatePasses=%d", (int)maskDilatePasses);
        }
        // conf in [0,1] like float path: mean(u8)/255
        packHeatmapBoxes(labels, blobs, w, h, minArea, useAabb, heatU8, 1.0f / 255.0f, &results, (int)maxBoxes, (int)growCells, (float)heatToPhoto, (int)photoW, (int)photoH);

        float hist[100] = {0};
        for (size_t i = 0; i < n; ++i) {
            // Match float hist: bin = min(99, (int)((u/255)*100)) = min(99, u*100/255)
            int b = static_cast<int>(data[i]) * 100 / 255;
            if (b < 0) b = 0;
            if (b > 99) b = 99;
            hist[b] += 1.0f;
        }
        for (int i = 0; i < 100; ++i) results.push_back(hist[i]);
    } else {
        // --- float path (fp32 heat, or fp16/other converted once) ---
        std::vector<float> heatBuf;
        if (!tensorToFloatHeatmap(nativeTensor, h, w, &heatBuf)) {
            throwIllegalState(env, "nativeProcessHeatmap: precision");
            return nullptr;
        }
        const float* data = heatBuf.data();
        std::snprintf(g_lastHeatmapPostPath, sizeof(g_lastHeatmapPostPath), "float");

        LOGI("nativeProcessHeatmap: path=float h=%d w=%d boxMode=%d thr=%.4f minArea=%.1f first4=[%.4f,%.4f,%.4f,%.4f]",
             h, w, (int)boxMode, (float)threshold, (float)minArea,
             data[0], data[1], data[2], data[3]);

        cv::Mat heatmap(h, w, CV_32F, const_cast<float*>(data));
        if (!heatMaybeDilateFlood(labels, w, h, (int)maskDilatePasses,
                                  nullptr, 0.0, data, (float)threshold, &blobs)) {
            throwIllegalState(env, "nativeProcessHeatmap: flood");
            return nullptr;
        }
        if (maskDilatePasses > 0) {
            LOGI("nativeProcessHeatmap: float maskDilatePasses=%d", (int)maskDilatePasses);
        }
        packHeatmapBoxes(labels, blobs, w, h, minArea, useAabb, heatmap, 1.0f, &results, (int)maxBoxes, (int)growCells, (float)heatToPhoto, (int)photoW, (int)photoH);

        float hist[100] = {0};
        for (size_t i = 0; i < n; ++i) {
            int b = std::max(0, std::min(99, (int)(data[i] * 100)));
            hist[b] += 1.0f;
        }
        for (int i = 0; i < 100; ++i) results.push_back(hist[i]);
    }

    if (results.empty()) return nullptr;
    jfloatArray jres = env->NewFloatArray(static_cast<jsize>(results.size()));
    if (!jres) return nullptr;
    env->SetFloatArrayRegion(jres, 0, static_cast<jsize>(results.size()), results.data());
    return jres;
}

// Same post as product u8 tensor path, but from a host u8 plane (tiled max-merge heat).
JNIEXPORT jfloatArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeProcessHeatmapU8(
    JNIEnv* env, jobject thiz, jbyteArray heatU8, jint w, jint h, jfloat threshold,
    jfloat minArea, jint boxMode, jint maskDilatePasses, jint maxBoxes, jint growCells,
    jlong scratchPtr, jfloat heatToPhoto, jint photoW, jint photoH) {
    (void)scratchPtr;
    if (!heatU8 || w <= 0 || h <= 0) {
        throwIllegalState(env, "nativeProcessHeatmapU8: size");
        return nullptr;
    }
    const size_t n = static_cast<size_t>(h) * static_cast<size_t>(w);
    if (env->GetArrayLength(heatU8) < static_cast<jsize>(n)) {
        throwIllegalState(env, "nativeProcessHeatmapU8: short array");
        return nullptr;
    }

    jbyte* raw = env->GetByteArrayElements(heatU8, nullptr);
    if (!raw) {
        throwIllegalState(env, "nativeProcessHeatmapU8: elements");
        return nullptr;
    }
    const uint8_t* data = reinterpret_cast<const uint8_t*>(raw);

    std::snprintf(g_lastHeatmapPostPath, sizeof(g_lastHeatmapPostPath), "u8");
    const bool useAabb = (boxMode == 1);
    (void)maxBoxes;
    std::vector<float> results;
    results.reserve(128);

    cv::Mat labels(h, w, CV_32S);
    const double thrU = static_cast<double>(threshold) * 255.0;
    cv::Mat heatMat(h, w, CV_8UC1, const_cast<uint8_t*>(data));
    std::vector<HeatBlob> blobs;
    if (!heatMaybeDilateFlood(labels, w, h, (int)maskDilatePasses,
                              data, thrU, nullptr, 0.f, &blobs)) {
        env->ReleaseByteArrayElements(heatU8, raw, JNI_ABORT);
        throwIllegalState(env, "nativeProcessHeatmapU8: flood");
        return nullptr;
    }
    packHeatmapBoxes(labels, blobs, w, h, minArea, useAabb, heatMat, 1.0f / 255.0f, &results, (int)maxBoxes, (int)growCells, (float)heatToPhoto, (int)photoW, (int)photoH);

    float hist[100] = {0};
    for (size_t i = 0; i < n; ++i) {
        int b = static_cast<int>(data[i]) * 100 / 255;
        if (b < 0) b = 0;
        if (b > 99) b = 99;
        hist[b] += 1.0f;
    }
    for (int i = 0; i < 100; ++i) results.push_back(hist[i]);

    env->ReleaseByteArrayElements(heatU8, raw, JNI_ABORT);
    if (results.empty()) return nullptr;
    jfloatArray jres = env->NewFloatArray(static_cast<jsize>(results.size()));
    if (!jres) return nullptr;
    env->SetFloatArrayRegion(jres, 0, static_cast<jsize>(results.size()), results.data());
    return jres;
}


JNIEXPORT jstring JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeLastHeatmapPostPath(
    JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(g_lastHeatmapPostPath);
}


JNIEXPORT jintArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeDevelopDngToJpeg(
    JNIEnv* env, jobject, jint inFd, jstring outPath, jint quality) {
    try {
        std::string err;
        cv::Mat rgb;
        if (!veDngDevelopRgb(inFd, &rgb, &err)) {
            throwIllegalState(env, err.empty() ? "dng jpeg" : err.c_str());
            return nullptr;
        }
        cv::Mat bgr;
        cv::cvtColor(rgb, bgr, cv::COLOR_RGB2BGR);
        std::vector<uchar> buf;
        std::vector<int> params = {cv::IMWRITE_JPEG_QUALITY, (int)quality};
        if (!cv::imencode(".jpg", bgr, buf, params) || buf.empty()) {
            throwIllegalState(env, "dng jpeg: imencode");
            return nullptr;
        }
        const char* path = env->GetStringUTFChars(outPath, nullptr);
        if (!path) {
            throwIllegalState(env, "dng jpeg: path");
            return nullptr;
        }
        FILE* f = std::fopen(path, "wb");
        if (!f) {
            env->ReleaseStringUTFChars(outPath, path);
            throwIllegalState(env, "dng jpeg: open");
            return nullptr;
        }
        const size_t nw = std::fwrite(buf.data(), 1, buf.size(), f);
        std::fclose(f);
        env->ReleaseStringUTFChars(outPath, path);
        if (nw != buf.size()) {
            throwIllegalState(env, "dng jpeg: write");
            return nullptr;
        }
        jintArray out = env->NewIntArray(2);
        if (!out) {
            throwIllegalState(env, "dng jpeg: result");
            return nullptr;
        }
        jint wh[2] = {bgr.cols, bgr.rows};
        env->SetIntArrayRegion(out, 0, 2, wh);
        return out;
    } catch (const std::bad_alloc&) {
        throwIllegalState(env, "dng jpeg: bad_alloc");
        return nullptr;
    } catch (const cv::Exception& ex) {
        throwIllegalState(env, ex.what());
        return nullptr;
    } catch (const std::exception& ex) {
        throwIllegalState(env, ex.what());
        return nullptr;
    }
}

JNIEXPORT jobject JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeDevelopDngToBitmap(
    JNIEnv* env, jobject, jint inFd) {
    try {
        std::string err;
        cv::Mat rgb;
        if (!veDngDevelopRgb(inFd, &rgb, &err)) {
            throwIllegalState(env, err.empty() ? "dng bitmap" : err.c_str());
            return nullptr;
        }
        if (!rgb.isContinuous()) rgb = rgb.clone();
        jclass cfgCls = env->FindClass("android/graphics/Bitmap$Config");
        jfieldID argb = env->GetStaticFieldID(cfgCls, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
        jobject cfg = env->GetStaticObjectField(cfgCls, argb);
        jclass bmpCls = env->FindClass("android/graphics/Bitmap");
        jmethodID create = env->GetStaticMethodID(
            bmpCls, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
        jobject bmp = env->CallStaticObjectMethod(bmpCls, create, rgb.cols, rgb.rows, cfg);
        if (env->ExceptionCheck() || !bmp) {
            if (!env->ExceptionCheck()) {
                throwIllegalState(env, "dng bitmap: createBitmap");
            }
            return nullptr;
        }
        AndroidBitmapInfo info;
        if (AndroidBitmap_getInfo(env, bmp, &info) < 0) {
            throwIllegalState(env, "dng bitmap: info");
            return nullptr;
        }
        void* pixels = nullptr;
        if (AndroidBitmap_lockPixels(env, bmp, &pixels) < 0 || !pixels) {
            throwIllegalState(env, "dng bitmap: lock");
            return nullptr;
        }
        for (int y = 0; y < rgb.rows; ++y) {
            const cv::Vec3b* src = rgb.ptr<cv::Vec3b>(y);
            auto* dst = reinterpret_cast<uint8_t*>(pixels) + static_cast<size_t>(y) * info.stride;
            for (int x = 0; x < rgb.cols; ++x) {
                dst[x * 4 + 0] = src[x][0];
                dst[x * 4 + 1] = src[x][1];
                dst[x * 4 + 2] = src[x][2];
                dst[x * 4 + 3] = 255;
            }
        }
        AndroidBitmap_unlockPixels(env, bmp);
        return bmp;
    } catch (const std::bad_alloc&) {
        throwIllegalState(env, "dng bitmap: bad_alloc");
        return nullptr;
    } catch (const cv::Exception& ex) {
        throwIllegalState(env, ex.what());
        return nullptr;
    } catch (const std::exception& ex) {
        throwIllegalState(env, ex.what());
        return nullptr;
    }
}

} // extern "C"



















// 3b. Decoupled H-variants for Set H with stroke-width aware logic

extern "C" JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeLogMatHeader(
    JNIEnv* env, jobject thiz, jlong matPtr, jstring tagJ) {
    auto* mat = reinterpret_cast<cv::Mat*>(matPtr);
    const char* tag = env->GetStringUTFChars(tagJ, nullptr);
    if (tag) {
        logMatHeader(tag, mat);
        env->ReleaseStringUTFChars(tagJ, tag);
    }
}












// RecBufferFeed area kernel: Float window edges, Double weights, round-half-up.
// fp-contract off so the mul/add is not fused (JVM does not fuse this).
#pragma clang fp contract(off)
static void scaleIsotropicAreaWrite(
    const cv::Mat& src, cv::Mat* dest, float s, int dw, int dh
) {
    const int sw = src.cols;
    const int sh = src.rows;
    auto clampi = [](int v, int lo, int hi) -> int {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    };
    for (int y = 0; y < dh; ++y) {
        const float fy0 = static_cast<float>(y) / s;
        const float fy1 = static_cast<float>(y + 1) / s;
        const int sy0 = clampi(static_cast<int>(std::floor(static_cast<double>(fy0))), 0, sh - 1);
        const int sy1e = clampi(static_cast<int>(std::ceil(static_cast<double>(fy1))), 1, sh);
        uint8_t* drow = dest->ptr<uint8_t>(y);
        for (int x = 0; x < dw; ++x) {
            const float fx0 = static_cast<float>(x) / s;
            const float fx1 = static_cast<float>(x + 1) / s;
            const int sx0 = clampi(static_cast<int>(std::floor(static_cast<double>(fx0))), 0, sw - 1);
            const int sx1e = clampi(static_cast<int>(std::ceil(static_cast<double>(fx1))), 1, sw);
            double acc = 0.0;
            double wsum = 0.0;
            for (int sy = sy0; sy < sy1e; ++sy) {
                const float syf = static_cast<float>(sy);
                const float yA = fy0 > syf ? fy0 : syf;
                const float yNext = static_cast<float>(sy + 1);
                const float yB = fy1 < yNext ? fy1 : yNext;
                const double wy = static_cast<double>(yB - yA);
                if (wy <= 0.0) continue;
                const uint8_t* row = src.ptr<uint8_t>(sy);
                for (int sx = sx0; sx < sx1e; ++sx) {
                    const float sxf = static_cast<float>(sx);
                    const float xA = fx0 > sxf ? fx0 : sxf;
                    const float xNext = static_cast<float>(sx + 1);
                    const float xB = fx1 < xNext ? fx1 : xNext;
                    const double wx = static_cast<double>(xB - xA);
                    if (wx <= 0.0) continue;
                    const double wt = wx * wy;
                    const double pix = static_cast<double>(row[sx]);
                    const double term = pix * wt;
                    acc = acc + term;
                    wsum = wsum + wt;
                }
            }
            if (wsum > 0.0) {
                int iv = static_cast<int>(acc / wsum + 0.5);
                if (iv < 0) iv = 0;
                if (iv > 255) iv = 255;
                drow[x] = static_cast<uint8_t>(iv);
            } else {
                drow[x] = 0;
            }
        }
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeScaleIsotropicArea(
    JNIEnv* /*env*/, jobject /*thiz*/,
    jlong srcPtr, jlong destPtr, jfloat s, jint writeW, jint writeH
) {
    auto* src = reinterpret_cast<cv::Mat*>(srcPtr);
    auto* dest = reinterpret_cast<cv::Mat*>(destPtr);
    if (!src || !dest || src->empty() || dest->empty()) return JNI_FALSE;
    if (src->type() != CV_8UC1 || dest->type() != CV_8UC1) return JNI_FALSE;
    if (!(s > 0.f) || writeW < 1 || writeH < 1) return JNI_FALSE;
    const int dw = writeW < dest->cols ? writeW : dest->cols;
    const int dh = writeH < dest->rows ? writeH : dest->rows;
    if (dw < 1 || dh < 1 || src->cols < 1 || src->rows < 1) return JNI_FALSE;
    if (static_cast<size_t>(dw) > dest->step) return JNI_FALSE;
    scaleIsotropicAreaWrite(*src, dest, s, dw, dh);
    return JNI_TRUE;
}

