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
#include "paddle/paddle_api.h"
#include "BufferSetHandle.h"
#include <android/log.h>
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "NativeImageUtils", __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "NativeImageUtils", __VA_ARGS__)
#include "../libraw_config.h"
#include <libraw/libraw.h>

static const char base64_chars[] = 
             "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
             "abcdefghijklmnopqrstuvwxyz"
             "0123456789+/";

std::string base64_encode(unsigned char const* bytes_to_encode, unsigned int in_len) {
    std::string ret;
    int i = 0;
    int j = 0;
    unsigned char char_array_3[3];
    unsigned char char_array_4[4];

    while (in_len--) {
        char_array_3[i++] = *(bytes_to_encode++);
        if (i == 3) {
            char_array_4[0] = (char_array_3[0] & 0xfc) >> 2;
            char_array_4[1] = ((char_array_3[0] & 0x03) << 4) + ((char_array_3[1] & 0xf0) >> 4);
            char_array_4[2] = ((char_array_3[1] & 0x0f) << 2) + ((char_array_3[2] & 0xc0) >> 6);
            char_array_4[3] = char_array_3[2] & 0x3f;
            for(i = 0; (i <4) ; i++) ret += base64_chars[char_array_4[i]];
            i = 0;
        }
    }

    if (i) {
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

// Refactored helper: only calculates angle, no longer returns struct
float calculateAngle(const cv::RotatedRect& rect) {
    cv::Point2f pts[4];
    rect.points(pts);
    float minAbsAngle = 180.0f;
    float resAngle = 0.0f;

    for (int i = 0; i < 4; ++i) {
        cv::Point2f p1 = pts[i];
        cv::Point2f p2 = pts[(i + 1) % 4];
        float dx = p2.x - p1.x;
        float dy = p2.y - p1.y;
        float ang = std::atan2(dy, dx) * 180.0f / 3.1415926535f;
        float normAng = ang;
        while (normAng <= -45.0f) normAng += 90.0f;
        while (normAng > 45.0f) normAng -= 90.0f;
        if (std::abs(normAng) < minAbsAngle) {
            minAbsAngle = std::abs(normAng);
            resAngle = normAng;
        }
    }
    return resAngle;
}

extern "C" {
// ... (keep nativeSyncMatFromArgb etc)


JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeSyncMatFromArgb(
    JNIEnv* env, jobject thiz, jobject bitmap, jlong dstMatPtr) {
    
    AndroidBitmapInfo info;
    void* pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;
    
    auto* dstMat = reinterpret_cast<cv::Mat*>(dstMatPtr);
    uint8_t* src = (uint8_t*)pixels;
    uint8_t* dst = dstMat->data;
    
    if (src != nullptr && dst != nullptr && dstMat->cols == (int)info.width && dstMat->rows == (int)info.height) {
        size_t count = (size_t)info.width * info.height;
        for (size_t i = 0; i < count; ++i) {
            dst[i] = src[i * 4]; // Extract Red channel
        }
    }
    
    AndroidBitmap_unlockPixels(env, bitmap);
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeSyncMatToArgb(
    JNIEnv* env, jobject thiz, jlong srcMatPtr, jobject bitmap) {
    
    AndroidBitmapInfo info;
    void* pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;
    
    auto* srcMat = reinterpret_cast<cv::Mat*>(srcMatPtr);
    uint8_t* src = srcMat->data;
    uint8_t* dst = (uint8_t*)pixels;
    
    if (src != nullptr && dst != nullptr && srcMat->cols == (int)info.width && srcMat->rows == (int)info.height) {
        size_t count = (size_t)info.width * info.height;
        for (size_t i = 0; i < count; ++i) {
            uint8_t v = src[i];
            size_t base = i * 4;
            dst[base]     = v;
            dst[base + 1] = v;
            dst[base + 2] = v;
            dst[base + 3] = 0xFF;
        }
    }
    
    AndroidBitmap_unlockPixels(env, bitmap);
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeIngestArgbToYuv(
    JNIEnv* env, jobject thiz, jobject bitmap, jlong handlePtr) {
    
    AndroidBitmapInfo info;
    void* pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;
    
    auto* handle = reinterpret_cast<BufferSetHandle*>(handlePtr);
    
    if (pixels != nullptr && handle != nullptr && handle->yMat != nullptr) {
        // Wrap the Bitmap pixels in a Mat header (Zero-Copy)
        cv::Mat src(info.height, info.width, CV_8UC4, pixels);
        
        // Use OpenCV's optimized conversion directly into the BufferSet Mat
        // handle->yMat is already allocated at the correct size
        if (handle->yMat->cols == (int)info.width && handle->yMat->rows == (int)info.height) {
            cv::cvtColor(src, *(handle->yMat), cv::COLOR_RGBA2GRAY);
        }
    }
    
    AndroidBitmap_unlockPixels(env, bitmap);
}

JNIEXPORT jstring JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeTestImread(
    JNIEnv* env, jobject thiz, jstring path) {
    const char* nativePath = env->GetStringUTFChars(path, nullptr);
    cv::Mat m = cv::imread(nativePath, cv::IMREAD_UNCHANGED);
    env->ReleaseStringUTFChars(path, nativePath);
    
    if (m.empty()) return env->NewStringUTF("FAILED_TO_LOAD");
    
    char buf[128];
    snprintf(buf, sizeof(buf), "%dx%d channels:%d", m.cols, m.rows, m.channels());
    return env->NewStringUTF(buf);
}

JNIEXPORT jboolean JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeIngestJpegToYuv(
    JNIEnv* env, jobject thiz, jstring path, jlong handlePtr) {
    
    const char* nativePath = env->GetStringUTFChars(path, nullptr);
    cv::Mat bgr = cv::imread(nativePath, cv::IMREAD_COLOR);
    env->ReleaseStringUTFChars(path, nativePath);
    
    if (bgr.empty()) return JNI_FALSE;
    
    auto* handle = reinterpret_cast<BufferSetHandle*>(handlePtr);
    if (!handle) return JNI_FALSE;
    
    if (handle->width != (size_t)bgr.cols || handle->height != (size_t)bgr.rows) {
        return JNI_FALSE;
    }
    
    // 1. Convert to YUV I420 (YYYY U V)
    cv::Mat i420;
    cv::cvtColor(bgr, i420, cv::COLOR_BGR2YUV_I420);
    
    // 2. Perform in-place C++ Interleaving into NV21
    size_t ySize = handle->width * handle->height;
    size_t uvSize = ySize / 4;
    
    // Copy Y Plane directly
    std::memcpy(handle->data, i420.data, ySize);
    
    // Interleave U and V planes into VUVU...
    uint8_t* dst_uv = handle->data + ySize;
    uint8_t* src_u = i420.data + ySize;
    uint8_t* src_v = src_u + uvSize;
    
    for (size_t i = 0; i < uvSize; ++i) {
        dst_uv[i * 2] = src_v[i];
        dst_uv[i * 2 + 1] = src_u[i];
    }
    
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeProbeDngResolution(
    JNIEnv* env, jobject thiz, jstring path) {
    const char* nativePath = env->GetStringUTFChars(path, nullptr);
    LibRaw RawProcessor;
    int ret = RawProcessor.open_file(nativePath);
    env->ReleaseStringUTFChars(path, nativePath);
    
    if (ret != LIBRAW_SUCCESS) return env->NewStringUTF("FAILED");
    
    // We adjust for LibRaw's internal development size (iwidth/iheight)
    // which accounts for margins/black area removal.
    char buf[64];
    snprintf(buf, sizeof(buf), "%dx%d", RawProcessor.imgdata.sizes.iwidth, RawProcessor.imgdata.sizes.iheight);
    RawProcessor.recycle();
    return env->NewStringUTF(buf);
}

JNIEXPORT jboolean JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeIngestDngToYuv(
    JNIEnv* env, jobject thiz, jstring path, jlong handlePtr) {
    
    auto* handle = reinterpret_cast<BufferSetHandle*>(handlePtr);
    if (!handle) return JNI_FALSE;
    
    const char* nativePath = env->GetStringUTFChars(path, nullptr);
    
    auto t0 = std::chrono::high_resolution_clock::now();
    
    LibRaw RawProcessor;
    int ret = RawProcessor.open_file(nativePath);
    env->ReleaseStringUTFChars(path, nativePath);
    
    if (ret != LIBRAW_SUCCESS) {
        LOGE("LibRaw open_file failed: %s", libraw_strerror(ret));
        return JNI_FALSE;
    }

    auto t1 = std::chrono::high_resolution_clock::now();

    // Use internal dimensions (iwidth/iheight) which reflect the developed image size
    if (handle->width != (size_t)RawProcessor.imgdata.sizes.iwidth || 
        handle->height != (size_t)RawProcessor.imgdata.sizes.iheight) {
        LOGE("LibRaw size mismatch. Buffer: %dx%d, LibRaw iwidth: %dx%d", 
             (int)handle->width, (int)handle->height, 
             RawProcessor.imgdata.sizes.iwidth, RawProcessor.imgdata.sizes.iheight);
        RawProcessor.recycle();
        return JNI_FALSE;
    }
    
    ret = RawProcessor.unpack();
    if (ret != LIBRAW_SUCCESS) {
        LOGE("LibRaw unpack failed: %s", libraw_strerror(ret));
        RawProcessor.recycle();
        return JNI_FALSE;
    }
    
    auto t2 = std::chrono::high_resolution_clock::now();
    
    // Configure development parameters
    RawProcessor.imgdata.params.output_color = 1; // sRGB
    RawProcessor.imgdata.params.use_camera_wb = 1; // Use camera white balance
    RawProcessor.imgdata.params.half_size = 0; // Full resolution
    
    // OPTIMIZATION: Linear demosaicing (Fastest)
    // 0 is linear, 1 is VNG, 2 is PPG, 3 is AHD (default)
    RawProcessor.imgdata.params.user_qual = 0; 
    
    ret = RawProcessor.dcraw_process();
    if (ret != LIBRAW_SUCCESS) {
        LOGE("LibRaw dcraw_process failed: %s", libraw_strerror(ret));
        RawProcessor.recycle();
        return JNI_FALSE;
    }
    
    auto t3 = std::chrono::high_resolution_clock::now();
    
    libraw_processed_image_t *image = RawProcessor.dcraw_make_mem_image(&ret);
    if (!image || ret != LIBRAW_SUCCESS) {
        RawProcessor.recycle();
        return JNI_FALSE;
    }
    
    // We now have RGB bytes. Wrap them in a Mat.
    cv::Mat rgb(image->height, image->width, CV_8UC3, image->data);
    
    // 1. Convert to YUV I420 (Planar 4:2:0)
    cv::Mat i420;
    cv::cvtColor(rgb, i420, cv::COLOR_RGB2YUV_I420);
    
    // 2. Perform in-place C++ Interleaving into NV21
    size_t ySize = handle->width * handle->height;
    size_t uvSize = ySize / 4;
    
    // Copy Y Plane directly
    std::memcpy(handle->data, i420.data, ySize);
    
    // Interleave U and V planes into VUVU...
    uint8_t* dst_uv = handle->data + ySize;
    uint8_t* src_u = i420.data + ySize;
    uint8_t* src_v = src_u + uvSize;
    
    for (size_t i = 0; i < uvSize; ++i) {
        dst_uv[i * 2] = src_v[i];
        dst_uv[i * 2 + 1] = src_u[i];
    }
    
    auto t4 = std::chrono::high_resolution_clock::now();
    
    auto dOpen = std::chrono::duration_cast<std::chrono::milliseconds>(t1 - t0).count();
    auto dUnpack = std::chrono::duration_cast<std::chrono::milliseconds>(t2 - t1).count();
    auto dProcess = std::chrono::duration_cast<std::chrono::milliseconds>(t3 - t2).count();
    auto dInterleave = std::chrono::duration_cast<std::chrono::milliseconds>(t4 - t3).count();
    
    LOGI("DNG Ingest Profiling: Total=%lldms (Open=%lldms, Unpack=%lldms, Process=%lldms, Interleave=%lldms)",
         (long long)(dOpen + dUnpack + dProcess + dInterleave),
         (long long)dOpen, (long long)dUnpack, (long long)dProcess, (long long)dInterleave);

    LibRaw::dcraw_clear_mem(image);
    RawProcessor.recycle();
    
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeCompressYuvToBase64(
    JNIEnv* env, jobject thiz, jobject yBuf, jobject uBuf, jobject vBuf, jint w, jint h, jint stride, jint quality) {
    
    uint8_t* yData = (uint8_t*)env->GetDirectBufferAddress(yBuf);
    uint8_t* uData = (uint8_t*)env->GetDirectBufferAddress(uBuf);
    uint8_t* vData = (uint8_t*)env->GetDirectBufferAddress(vBuf);

    if (!yData || !uData || !vData) return env->NewStringUTF("");

    cv::Mat bgr(h, w, CV_8UC3);
    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            uint8_t Y = yData[y * stride + x];
            uint8_t V = vData[(y/2) * stride + (x/2)*2];
            uint8_t U = uData[(y/2) * stride + (x/2)*2];
            
            int r = Y + 1.402 * (V - 128);
            int g = Y - 0.344136 * (U - 128) - 0.714136 * (V - 128);
            int b = Y + 1.772 * (U - 128);
            
            cv::Vec3b& pixel = bgr.at<cv::Vec3b>(y, x);
            pixel[0] = (uint8_t)std::max(0, std::min(255, b));
            pixel[1] = (uint8_t)std::max(0, std::min(255, g));
            pixel[2] = (uint8_t)std::max(0, std::min(255, r));
        }
    }

    std::vector<uint8_t> buf;
    std::vector<int> params = {cv::IMWRITE_JPEG_QUALITY, quality};
    cv::imencode(".jpg", bgr, buf, params);

    std::string b64 = base64_encode(buf.data(), buf.size());
    return env->NewStringUTF(b64.c_str());
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativePopulateMonoTensor(
    JNIEnv* env, jobject thiz, jlong srcMatPtr, jfloatArray dstTensor, jint tensorW, jint tensorH, jfloat mean, jfloat std) {
    
    auto* src = reinterpret_cast<cv::Mat*>(srcMatPtr);
    if (!src || src->empty()) return;

    if (src->cols > tensorW || src->rows > tensorH) return;

    jfloat* dst = env->GetFloatArrayElements(dstTensor, nullptr);
    if (!dst) return;

    int w = src->cols;
    int h = src->rows;

    // Fixed: Always use row-by-row population to support filling the top-left corner
    // of a larger tensor. isContinuous() optimization is only safe if src fills the full tensor.
    for (int y = 0; y < h; ++y) {
        const uint8_t* row = src->ptr<uint8_t>(y);
        jfloat* dst_row = dst + (y * tensorW);
        for (int x = 0; x < w; ++x) {
            dst_row[x] = ((float)row[x] / 255.0f - mean) / std;
        }
    }

    env->ReleaseFloatArrayElements(dstTensor, dst, 0);
}

JNIEXPORT jintArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeExpandByValley(
    JNIEnv* env, jobject thiz, jlong matPtr, jint L, jint T, jint R, jint B, jfloat thresholdFactor) {
    
    auto* mat = reinterpret_cast<cv::Mat*>(matPtr);
    if (!mat || mat->empty() || mat->type() != CV_8UC1) return nullptr;

    int maxW = mat->cols;
    int maxH = mat->rows;

    // 1. Clamp input rect to valid image boundaries
    int safeL = std::max(0, std::min(L, maxW - 1));
    int safeT = std::max(0, std::min(T, maxH - 1));
    int safeR = std::max(safeL + 1, std::min(R, maxW));
    int safeB = std::max(safeT + 1, std::min(B, maxH));

    // 2. Calculate baseline "Hill" brightness
    cv::Rect roi(safeL, safeT, safeR - safeL, safeB - safeT);
    cv::Scalar meanVal = cv::mean((*mat)(roi));
    double hillBrightness = meanVal[0];
    double valleyThreshold = std::max(15.0, hillBrightness * (double)thresholdFactor);
    double minX = L, maxX = R, minY = T, maxY = B;
    double sX = minX, sXX = maxX, sY = minY, sYY = maxY;
    double hL = (maxX - minX) * 12.0; 
    double vL = (maxY - minY) * 1.5;

    double contentThreshold = std::max(15.0, hillBrightness * (double)thresholdFactor);
    int minContentPixels = 2; // Require at least 2 bright pixels to consider it content

    int minRunLength = 3; // Require a contiguous stroke of at least 3 pixels

    auto isValley = [&](int start, int end, int fixed, bool horizontal) -> bool {
        int currentRun = 0, maxRun = 0;
        if (horizontal) {
            if (fixed < 0 || fixed >= maxH) return true;
            const uint8_t* rowPtr = mat->ptr<uint8_t>(fixed);
            int startIdx = std::max(0, start);
            int endIdx = std::min(maxW, end);
            for (int i = startIdx; i < endIdx; ++i) {
                if (rowPtr[i] > contentThreshold) {
                    currentRun++;
                    if (currentRun > maxRun) maxRun = currentRun;
                } else {
                    currentRun = 0;
                }
            }
            // Vertical expansion: Row must be between 3px and 30% of width
            return (maxRun < 3 || maxRun > (maxW * 0.30));
        } else {
            if (fixed < 0 || fixed >= maxW) return true;
            int startIdx = std::max(0, start);
            int endIdx = std::min(maxH, end);
            for (int i = startIdx; i < endIdx; ++i) {
                if (mat->at<uint8_t>(i, fixed) > contentThreshold) {
                    currentRun++;
                    if (currentRun > maxRun) maxRun = currentRun;
                } else {
                    currentRun = 0;
                }
            }
            // Horizontal expansion: Column must be between 2px and 80% of height
            return (maxRun < 2 || maxRun > (maxH * 0.80));
        }
    };

    // 3. First Vertical Expansion Pass (Simple Stop)
    while (minY > 0 && (sY - minY) < vL) {
        if (isValley((int)minX, (int)maxX, (int)minY - 1, true)) break;
        minY -= 1.0;
    }
    while (maxY < maxH - 1 && (maxY - sYY) < vL) {
        if (isValley((int)minX, (int)maxX, (int)maxY + 1, true)) break;
        maxY += 1.0;
    }

    // Recalculate horizontal lookAhead based on the newly expanded vertical height
    double lookAhead = (maxY - minY) * 0.5;

    // 4. Horizontal Expansion (Jump and Collapse) with 10-column streak
    double walkL = minX;
    double lastGoodL = minX;
    int streakL = 0;
    while (walkL > 0 && (sX - walkL) < hL) {
        walkL -= 1.0;
        if (!isValley((int)minY, (int)maxY, (int)walkL, false)) {
            streakL++;
            if (streakL >= 10) {
                lastGoodL = walkL;
            }
        } else {
            streakL = 0;
        }
        if ((lastGoodL - walkL) > lookAhead) break;
    }

    double walkR = maxX;
    double lastGoodR = maxX;
    int streakR = 0;
    while (walkR < maxW - 1 && (walkR - sXX) < hL) {
        walkR += 1.0;
        if (!isValley((int)minY, (int)maxY, (int)walkR, false)) {
            streakR++;
            if (streakR >= 10) {
                lastGoodR = walkR;
            }
        } else {
            streakR = 0;
        }
        if ((walkR - lastGoodR) > lookAhead) break;
    }

    // Collapse boundaries exactly back to the last detected content columns (retraction)
    minX = lastGoodL;
    maxX = lastGoodR;

    // 5. Second Vertical Expansion Pass (using newly expanded horizontal bounds)
    while (minY > 0 && (sY - minY) < vL) {
        if (isValley((int)minX, (int)maxX, (int)minY - 1, true)) break;
        minY -= 1.0;
    }
    while (maxY < maxH - 1 && (maxY - sYY) < vL) {
        if (isValley((int)minX, (int)maxX, (int)maxY + 1, true)) break;
        maxY += 1.0;
    }

    jintArray result = env->NewIntArray(4);
    jint dims[4] = {(jint)minX, (jint)minY, (jint)maxX, (jint)maxY};
    env->SetIntArrayRegion(result, 0, 4, dims);
    return result;
}

JNIEXPORT jobjectArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeExpandByValleyDiagnostic(
    JNIEnv* env, jobject thiz, jlong matPtr, jint L, jint T, jint R, jint B, jfloat thresholdFactor) {
    
    auto* mat = reinterpret_cast<cv::Mat*>(matPtr);
    if (!mat || mat->empty() || mat->type() != CV_8UC1) return nullptr;

    int maxW = mat->cols;
    int maxH = mat->rows;

    int safeL = std::max(0, std::min(L, maxW - 1));
    int safeT = std::max(0, std::min(T, maxH - 1));
    int safeR = std::max(safeL + 1, std::min(R, maxW));
    int safeB = std::max(safeT + 1, std::min(B, maxH));

    cv::Rect roi(safeL, safeT, safeR - safeL, safeB - safeT);
    cv::Scalar meanVal = cv::mean((*mat)(roi));
    double hillBrightness = meanVal[0];
    double contentThreshold = std::max(15.0, hillBrightness * (double)thresholdFactor);
    int minRunLength = 3; 

    double minX = L, maxX = R, minY = T, maxY = B;
    double sX = minX, sXX = maxX, sY = minY, sYY = maxY;
    double hL = (maxX - minX) * 12.0; 
    double vL = (maxY - minY) * 1.5;

    std::ostringstream oss;
    auto isValleyDiag = [&](int start, int end, int fixed, bool horizontal, int* outMaxRun, int* outPeak) -> bool {
        int currentRun = 0, maxRun = 0, peak = 0;
        bool isValley = true;
        if (horizontal) {
            if (fixed < 0 || fixed >= maxH) return true;
            const uint8_t* rowPtr = mat->ptr<uint8_t>(fixed);
            int startIdx = std::max(0, start);
            int endIdx = std::min(maxW, end);
            for (int i = startIdx; i < endIdx; ++i) {
                uint8_t val = rowPtr[i];
                if (val > peak) peak = val;
                if (val > contentThreshold) {
                    currentRun++;
                    if (currentRun > maxRun) maxRun = currentRun;
                } else {
                    currentRun = 0;
                }
            }
            // Vertical expansion: Row must be between 3px and 30% of width
            isValley = (maxRun < 3 || maxRun > (maxW * 0.30));
        } else {
            if (fixed < 0 || fixed >= maxW) return true;
            int startIdx = std::max(0, start);
            int endIdx = std::min(maxH, end);
            for (int i = startIdx; i < endIdx; ++i) {
                uint8_t val = mat->at<uint8_t>(i, fixed);
                if (val > peak) peak = val;
                if (val > contentThreshold) {
                    currentRun++;
                    if (currentRun > maxRun) maxRun = currentRun;
                } else {
                    currentRun = 0;
                }
            }
            // Horizontal expansion: Column must be between 2px and 90% of height
            isValley = (maxRun < 2 || maxRun > (maxH * 0.90));
        }
        if (outMaxRun) *outMaxRun = maxRun;
        if (outPeak) *outPeak = peak;
        return isValley;
    };

    auto trace = [&](const char* code, int coord, int maxRun, int peak) {
        if (oss.tellp() > 0) oss << ",";
        oss << code << ":" << coord << ":" << maxRun << ":" << peak;
    };

    // 3. First Vertical Expansion Pass
    while (minY > 0 && (sY - minY) < vL) {
        int mr = 0, pk = 0;
        if (isValleyDiag((int)minX, (int)maxX, (int)minY - 1, true, &mr, &pk)) {
            trace("VT", (int)minY - 1, mr, pk);
            break;
        }
        trace("VT", (int)minY - 1, mr, pk);
        minY -= 1.0;
    }
    while (maxY < maxH - 1 && (maxY - sYY) < vL) {
        int mr = 0, pk = 0;
        if (isValleyDiag((int)minX, (int)maxX, (int)maxY + 1, true, &mr, &pk)) {
            trace("VB", (int)maxY + 1, mr, pk);
            break;
        }
        trace("VB", (int)maxY + 1, mr, pk);
        maxY += 1.0;
    }

    double lookAhead = (maxY - minY) * 0.5;

    // 4. Horizontal Expansion (Jump and Collapse) with 10-column streak
    double walkL = minX;
    double lastGoodL = minX;
    int streakL = 0;
    while (walkL > 0 && (sX - walkL) < hL) {
        walkL -= 1.0;
        int mr = 0, pk = 0;
        if (!isValleyDiag((int)minY, (int)maxY, (int)walkL, false, &mr, &pk)) {
            streakL++;
            if (streakL >= 10) lastGoodL = walkL;
            trace("HL", (int)walkL, mr, pk);
        } else {
            streakL = 0;
            trace("HL", (int)walkL, mr, pk);
            if ((lastGoodL - walkL) > lookAhead) break;
        }
    }

    double walkR = maxX;
    double lastGoodR = maxX;
    int streakR = 0;
    while (walkR < maxW - 1 && (walkR - sXX) < hL) {
        walkR += 1.0;
        int mr = 0, pk = 0;
        if (!isValleyDiag((int)minY, (int)maxY, (int)walkR, false, &mr, &pk)) {
            streakR++;
            if (streakR >= 10) lastGoodR = walkR;
            trace("HR", (int)walkR, mr, pk);
        } else {
            streakR = 0;
            trace("HR", (int)walkR, mr, pk);
            if ((walkR - lastGoodR) > lookAhead) break;
        }
    }

    int probedL = (int)walkL, probedR = (int)walkR;
    minX = lastGoodL;
    maxX = lastGoodR;

    // 5. Second Vertical Expansion Pass
    double walkT = minY;
    double lastGoodT = minY;
    while (walkT > 0 && (sY - walkT) < vL) {
        walkT -= 1.0;
        int mr = 0, pk = 0;
        if (!isValleyDiag((int)minX, (int)maxX, (int)walkT, true, &mr, &pk)) {
            lastGoodT = walkT;
            trace("VT2", (int)walkT, mr, pk);
        } else {
            trace("VT2", (int)walkT, mr, pk);
            break; // Standard vertical pass still uses simple stop for now, but we trace it
        }
    }
    double walkB = maxY;
    double lastGoodB = maxY;
    while (walkB < maxH - 1 && (walkB - sYY) < vL) {
        walkB += 1.0;
        int mr = 0, pk = 0;
        if (!isValleyDiag((int)minX, (int)maxX, (int)walkB, true, &mr, &pk)) {
            lastGoodB = walkB;
            trace("VB2", (int)walkB, mr, pk);
        } else {
            trace("VB2", (int)walkB, mr, pk);
            break;
        }
    }
    int probedT = (int)walkT, probedB = (int)walkB;
    minY = lastGoodT;
    maxY = lastGoodB;

    // Return results
    jintArray summary = env->NewIntArray(16);
    jint s[16] = {
        (jint)L, (jint)T, (jint)R, (jint)B,
        (jint)probedL, (jint)probedT, (jint)probedR, (jint)probedB,
        (jint)minX, (jint)minY, (jint)maxX, (jint)maxY,
        (jint)contentThreshold, (jint)minRunLength, (jint)(lookAhead * 100), (jint)maxW
    };
    env->SetIntArrayRegion(summary, 0, 16, s);

    jstring traceStr = env->NewStringUTF(oss.str().c_str());
    jclass objClass = env->FindClass("java/lang/Object");
    jobjectArray resultArr = env->NewObjectArray(2, objClass, nullptr);
    env->SetObjectArrayElement(resultArr, 0, summary);
    env->SetObjectArrayElement(resultArr, 1, traceStr);

    return resultArr;
}

JNIEXPORT jintArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeExpandByUniformity(
    JNIEnv* env, jobject thiz, jlong matPtr, jint L, jint T, jint R, jint B, jfloat thresholdFactor) {
    
    auto* mat = reinterpret_cast<cv::Mat*>(matPtr);
    if (!mat || mat->empty() || mat->type() != CV_8UC1) return nullptr;

    int maxW = mat->cols;
    int maxH = mat->rows;

    if (L < 0 || R > maxW || T < 0 || B > maxH || L >= R || T >= B) {
        LOGE("EXPAND_FATAL: Coordinate Overflow (%d,%d)-(%d,%d) for img %dx%d. Mapping failure likely in upstream ICRS conversion.", L, T, R, B, maxW, maxH);
    }

    LOGE("EXPAND: Start (%d,%d)-(%d,%d) img=%dx%d", L, T, R, B, maxW, maxH);

    int safeL = std::max(0, std::min(L, maxW - 1));
    int safeT = std::max(0, std::min(T, maxH - 1));
    int safeR = std::max(safeL + 1, std::min(R, maxW));
    int safeB = std::max(safeT + 1, std::min(B, maxH));

    double minX = L, maxX = R, minY = T, maxY = B;
    double hL = (maxX - minX) * 12.0; 
    double vL = (maxY - minY) * 1.0;

    auto getRange = [&](int start, int end, int fixed, bool horizontal) -> int {
        uint8_t minV = 255;
        uint8_t maxV = 0;
        int count = 0;
        if (horizontal) {
            if (fixed < 0 || fixed >= maxH) return 0;
            const uint8_t* rowPtr = mat->ptr<uint8_t>(fixed);
            int startIdx = std::max(0, start);
            int endIdx = std::min(maxW, end);
            for (int i = startIdx; i < endIdx; ++i) {
                uint8_t v = rowPtr[i];
                if (v < minV) minV = v;
                if (v > maxV) maxV = v;
                count++;
            }
        } else {
            if (fixed < 0 || fixed >= maxW) return 0;
            int startIdx = std::max(0, start);
            int endIdx = std::min(maxH, end);
            for (int i = startIdx; i < endIdx; ++i) {
                uint8_t v = mat->at<uint8_t>(i, fixed);
                if (v < minV) minV = v;
                if (v > maxV) maxV = v;
                count++;
            }
        }
        return (count == 0) ? 0 : (maxV - minV);
    };

    const int THRESHOLD = 50;
    double floorL = L, floorR = R, floorT = T, floorB = B;

    // --- PHASE 1: EXPAND OUT (Move while range is high) ---
    while (minY > 0) {
        int r = getRange((int)minX, (int)maxX, (int)minY - 1, true);
        LOGE("EXPAND_TRACE: [OUT] Y=%d (H) range=%d", (int)minY - 1, r);
        if (r < THRESHOLD) break;
        minY -= 1.0;
    }
    while (maxY < maxH - 1) {
        int r = getRange((int)minX, (int)maxX, (int)maxY + 1, true);
        LOGE("EXPAND_TRACE: [OUT] Y=%d (H) range=%d", (int)maxY + 1, r);
        if (r < THRESHOLD) break;
        maxY += 1.0;
    }
    while (minX > 0) {
        int r = getRange((int)minY, (int)maxY, (int)minX - 1, false);
        LOGE("EXPAND_TRACE: [OUT] X=%d (V) range=%d", (int)minX - 1, r);
        if (r < THRESHOLD) break;
        minX -= 1.0;
    }
    while (maxX < maxW - 1) {
        int r = getRange((int)minY, (int)maxY, (int)maxX + 1, false);
        LOGE("EXPAND_TRACE: [OUT] X=%d (V) range=%d", (int)maxX + 1, r);
        if (r < THRESHOLD) break;
        maxX += 1.0;
    }

    // --- JUMP OUT (Forcefully move into potential background) ---
    double jumpH = (maxY - minY) * 0.40;
    minY = std::max(0.0, minY - 4.0);
    maxY = std::min((double)maxH - 1, maxY + 4.0);
    minX = std::max(0.0, minX - jumpH);
    maxX = std::min((double)maxW - 1, maxX + jumpH);

    double maxExtentL = minX, maxExtentT = minY, maxExtentR = maxX, maxExtentB = maxY;

    // --- PHASE 2: PULL BACK IN (Stop on content or original floor) ---
    while (minY < maxY && minY < floorT) {
        int r = getRange((int)minX, (int)maxX, (int)minY, true);
        LOGE("EXPAND_TRACE: [IN] Y=%d (H) range=%d", (int)minY, r);
        if (r >= THRESHOLD) break;
        minY += 1.0;
    }
    while (maxY > minY && maxY > floorB) {
        int r = getRange((int)minX, (int)maxX, (int)maxY, true);
        LOGE("EXPAND_TRACE: [IN] Y=%d (H) range=%d", (int)maxY, r);
        if (r >= THRESHOLD) break;
        maxY -= 1.0;
    }
    while (minX < maxX && minX < floorL) {
        int r = getRange((int)minY, (int)maxY, (int)minX, false);
        LOGE("EXPAND_TRACE: [IN] X=%d (V) range=%d", (int)minX, r);
        if (r >= THRESHOLD) break;
        minX += 1.0;
    }
    while (maxX > minX && maxX > floorR) {
        int r = getRange((int)minY, (int)maxY, (int)maxX, false);
        LOGE("EXPAND_TRACE: [IN] X=%d (V) range=%d", (int)maxX, r);
        if (r >= THRESHOLD) break;
        maxX -= 1.0;
    }

    LOGE("EXPAND: Result (%d,%d)-(%d,%d)", (int)minX, (int)minY, (int)maxX, (int)maxY);

    jintArray result = env->NewIntArray(8);
    jint dims[8] = {
        (jint)minX, (jint)minY, (jint)maxX, (jint)maxY,
        (jint)maxExtentL, (jint)maxExtentT, (jint)maxExtentR, (jint)maxExtentB
    };
    env->SetIntArrayRegion(result, 0, 8, dims);
    return result;
}

JNIEXPORT jfloat JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeHeatmapToAngle(
    JNIEnv* env, jobject thiz, jobject tensor, jfloat threshold) {

    // 1. Get Native Tensor
    jclass cls = env->GetObjectClass(tensor);
    jfieldID fid = env->GetFieldID(cls, "cppTensorPointer", "J");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return 0.0f;
    }
    jlong nativePtr = env->GetLongField(tensor, fid);
    if (nativePtr == 0) return 0.0f;

    auto* uptr = reinterpret_cast<std::unique_ptr<paddle::lite_api::Tensor>*>(nativePtr);
    if (!uptr || !(*uptr)) return 0.0f;

    paddle::lite_api::Tensor* nativeTensor = uptr->get();
    auto shape = nativeTensor->shape();
    if (shape.size() < 3) return 0.0f;

    int h = (int)shape[shape.size() - 2];
    int w = (int)shape[shape.size() - 1];
    const float* data = nativeTensor->data<float>();
    if (!data) return 0.0f;

    cv::Mat heatmap(h, w, CV_32F, const_cast<float*>(data));
    cv::Mat mask = heatmap > threshold;

    cv::Mat labels, stats, centroids;
    int numLabels = cv::connectedComponentsWithStats(mask, labels, stats, centroids, 8, CV_32S);

    if (numLabels <= 1) {
        return 0.0f;
    }

    // Weighted Consensus Voting (0.5 degree buckets)
    std::map<int, double> buckets;
    for (int l = 1; l < numLabels; ++l) {
        int area = stats.at<int>(l, cv::CC_STAT_AREA);
        if (area < 10) continue;

        int left = stats.at<int>(l, cv::CC_STAT_LEFT);
        int top = stats.at<int>(l, cv::CC_STAT_TOP);
        int width = stats.at<int>(l, cv::CC_STAT_WIDTH);
        int height = stats.at<int>(l, cv::CC_STAT_HEIGHT);

        cv::Mat points(area, 1, CV_32SC2);
        int idx = 0;
        double sumHeatmap = 0.0;
        for (int y = top; y < top + height; ++y) {
            for (int x = left; x < left + width; ++x) {
                if (labels.at<int>(y, x) == l) {
                    if (idx < area) {
                        points.at<cv::Point>(idx++) = cv::Point(x, y);
                    }
                    sumHeatmap += (double)heatmap.at<float>(y, x);
                }
            }
        }

        if (idx < area) {
            points = points.rowRange(0, idx);
        }
        if (points.empty()) continue;

        cv::RotatedRect rrect = cv::minAreaRect(points);
        float angle = calculateAngle(rrect);
        double confidence = sumHeatmap / (double)idx;

        int bucketIdx = (int)std::round(angle * 2.0f);
        double weight = (double)cv::arcLength(points, true) * confidence;
        buckets[bucketIdx] += weight;
    }

    int bestBucket = 0;
    double maxWeight = -1.0;
    for (const auto& entry : buckets) {
        if (entry.second > maxWeight) {
            maxWeight = entry.second;
            bestBucket = entry.first;
        }
    }

    return (float)bestBucket / 2.0f;
}

JNIEXPORT jfloatArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeProcessHeatmap(
    JNIEnv* env, jobject thiz, jobject tensor, jfloat threshold, jfloat minArea) {

    // 1. Get Native Tensor
    jclass cls = env->GetObjectClass(tensor);
    jfieldID fid = env->GetFieldID(cls, "cppTensorPointer", "J");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return nullptr;
    }
    jlong nativePtr = env->GetLongField(tensor, fid);
    if (nativePtr == 0) return nullptr;

    auto* uptr = reinterpret_cast<std::unique_ptr<paddle::lite_api::Tensor>*>(nativePtr);
    if (!uptr || !(*uptr)) return nullptr;

    paddle::lite_api::Tensor* nativeTensor = uptr->get();
    auto shape = nativeTensor->shape();
    if (shape.size() < 3) return nullptr;

    int h = (int)shape[shape.size() - 2];
    int w = (int)shape[shape.size() - 1];
    const float* data = nativeTensor->data<float>();
    if (!data) return nullptr;

    LOGI("nativeProcessHeatmap: shape=[%d dims], h=%d, w=%d, ptr=%p, first4=[%.4f,%.4f,%.4f,%.4f]",
         (int)shape.size(), h, w, data, data[0], data[1], data[2], data[3]);

    // 2. Thresholding
    cv::Mat heatmap(h, w, CV_32F, const_cast<float*>(data));
    cv::Mat mask;
    cv::threshold(heatmap, mask, threshold, 255.0, cv::THRESH_BINARY);
    mask.convertTo(mask, CV_8U);

    // 3. Connected Components with Stats (ABI-Safe replacement for findContours)
    cv::Mat labels, stats, centroids;
    int numLabels = cv::connectedComponentsWithStats(mask, labels, stats, centroids, 8, CV_32S);

    // 4. Geometry Extraction
    std::vector<float> results;
    int count = 0;
    for (int l = 1; l < numLabels; ++l) { // Start from 1 (skip background label 0)
        if (count >= 200) break; // Hard safety limit
        
        int area = stats.at<int>(l, cv::CC_STAT_AREA);
        if (area < minArea) continue;

        int left = stats.at<int>(l, cv::CC_STAT_LEFT);
        int top = stats.at<int>(l, cv::CC_STAT_TOP);
        int width = stats.at<int>(l, cv::CC_STAT_WIDTH);
        int height = stats.at<int>(l, cv::CC_STAT_HEIGHT);

        // Populate a flat cv::Mat of points instead of std::vector to guarantee ABI Parity
        cv::Mat points(area, 1, CV_32SC2);
        int idx = 0;
        for (int y = top; y < top + height; ++y) {
            for (int x = left; x < left + width; ++x) {
                if (labels.at<int>(y, x) == l) {
                    if (idx < area) {
                        points.at<cv::Point>(idx++) = cv::Point(x, y);
                    }
                }
            }
        }

        // If for some reason we gathered fewer points than expected, truncate Mat
        if (idx < area) {
            points = points.rowRange(0, idx);
        }

        if (points.empty()) continue;

        cv::RotatedRect rect = cv::minAreaRect(points);
        cv::Point2f vertices[4];
        rect.points(vertices);

        if (count < 3) {
            LOGI("nativeProcessHeatmap: box[%d] label=%d vertices=(%.1f,%.1f)(%.1f,%.1f)(%.1f,%.1f)(%.1f,%.1f) area=%d",
                 count, l, vertices[0].x, vertices[0].y, vertices[1].x, vertices[1].y,
                 vertices[2].x, vertices[2].y, vertices[3].x, vertices[3].y, area);
        }

        // Calculate average confidence within the bounding box
        float avgConf = 0.0f;
        int bx1 = std::max(0, left);
        int by1 = std::max(0, top);
        int bx2 = std::min(w, left + width);
        int by2 = std::min(h, top + height);
        
        if (bx2 > bx1 && by2 > by1) {
            cv::Mat roi = heatmap(cv::Rect(bx1, by1, bx2 - bx1, by2 - by1));
            cv::Scalar meanVal = cv::mean(roi);
            avgConf = (float)meanVal[0];
        }

        // Pack [x1, y1, x2, y2, x3, y3, x4, y4, conf]
        for (int i = 0; i < 4; ++i) {
            results.push_back(vertices[i].x);
            results.push_back(vertices[i].y);
        }
        results.push_back(avgConf);
        count++;
    }

    // 5. Serialization
    if (results.empty()) return nullptr;
    jfloatArray jres = env->NewFloatArray(results.size());
    env->SetFloatArrayRegion(jres, 0, results.size(), results.data());
    return jres;
}

JNIEXPORT jintArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeExpandByCharacterAware(
    JNIEnv* env, jobject thiz, jlong matPtr, jint L, jint T, jint R, jint B, jfloat thresholdFactor) {
    LOGI("CHAR_AWARE: Start (%d,%d)-(%d,%d) img=%dx%d\", L, T, R, B, maxW, maxH);
    
    auto* mat = reinterpret_cast<cv::Mat*>(matPtr);
    if (!mat || mat->empty() || mat->type() != CV_8UC1) return nullptr;

    int maxW = mat->cols;
    int maxH = mat->rows;

    int safeL = std::max(0, std::min(L, maxW - 1));
    int safeT = std::max(0, std::min(T, maxH - 1));
    int safeR = std::max(safeL + 1, std::min(R, maxW));
    int safeB = std::max(safeT + 1, std::min(B, maxH));

    cv::Rect roi(safeL, safeT, safeR - safeL, safeB - safeT);
    cv::Scalar meanVal = cv::mean((*mat)(roi));
    double hillBrightness = meanVal[0];
    double contentThreshold = std::max(15.0, hillBrightness * (double)thresholdFactor);

    double minX = L, maxX = R, minY = T, maxY = B;
    double sX = minX, sXX = maxX, sY = minY, sYY = maxY;
    double hL = (maxX - minX) * 12.0; 
    double vL = (maxY - minY) * 1.5;

    auto getMaxRun = [&](int start, int end, int fixed, bool horizontal) -> int {
        int currentRun = 0, maxRun = 0;
        if (horizontal) {
            if (fixed < 0 || fixed >= maxH) return 0;
            const uint8_t* rowPtr = mat->ptr<uint8_t>(fixed);
            for (int i = std::max(0, start); i < std::min(maxW, end); ++i) {
                if (rowPtr[i] > contentThreshold) {
                    currentRun++;
                    if (currentRun > maxRun) maxRun = currentRun;
                } else currentRun = 0;
            }
        } else {
            if (fixed < 0 || fixed >= maxW) return 0;
            for (int i = std::max(0, start); i < std::min(maxH, end); ++i) {
                if (mat->at<uint8_t>(i, fixed) > contentThreshold) {
                    currentRun++;
                    if (currentRun > maxRun) maxRun = currentRun;
                } else currentRun = 0;
            }
        }
        return maxRun;
    };

    auto isValley = [&](int start, int end, int fixed, bool horizontal) -> bool {
        int mr = getMaxRun(start, end, fixed, horizontal);
        if (horizontal) return (mr < 3 || mr > (maxW * 0.30));
        return (mr < 2 || mr > (maxH * 0.80));
    };

    // 1. Vertical Expansion (Current logic)
    while (minY > 0 && (sY - minY) < vL) {
        if (isValley((int)minX, (int)maxX, (int)minY - 1, true)) break;
        minY -= 1.0;
    }
    while (maxY < maxH - 1 && (maxY - sYY) < vL) {
        if (isValley((int)minX, (int)maxX, (int)maxY + 1, true)) break;
        maxY += 1.0;
    }

    // 2. Strict Horizontal Walk (To first valley)
    double strictL = minX, strictR = maxX;
    while (strictL > 0 && !isValley((int)minY, (int)maxY, (int)strictL - 1, false)) strictL -= 1.0;
    while (strictR < maxW - 1 && !isValley((int)minY, (int)maxY, (int)strictR + 1, false)) strictR += 1.0;

    // 3. Analyze Content for Digit Width and Stroke Mass
    int boxH = (int)(maxY - minY);
    std::vector<int> colMaxRuns;
    for (int x = (int)strictL; x <= (int)strictR; ++x) {
        colMaxRuns.push_back(getMaxRun((int)minY, (int)maxY, x, false));
    }

    // Find minStrokeWidth (narrowest sequence of columns with run > 50% height)
    int minStrokeW = 999, currentStrokeW = 0;
    for (int mr : colMaxRuns) {
        if (mr > boxH * 0.5) currentStrokeW++;
        else {
            if (currentStrokeW > 0 && currentStrokeW < minStrokeW) minStrokeW = currentStrokeW;
            currentStrokeW = 0;
        }
    }
    if (minStrokeW == 999) minStrokeW = std::max(2, (int)(boxH * 0.15));
    
    // Estimate digitWidth (horizontal pitch)
    int digitWidth = (int)(boxH * 0.7); // Fallback
    
    LOGI("CHAR_AWARE: Analysis: strict=(%d-%d) boxH=%d minStrokeW=%d digitWidth=%d", (int)strictL, (int)strictR, boxH, minStrokeW, digitWidth);
    double mass1 = minStrokeW * boxH;
    double mass8 = 2.5 * mass1;

    auto getBlockDensity = [&](int startX, int endX) -> int {
        int count = 0;
        for (int x = std::max(0, startX); x < std::min(maxW, endX); ++x) {
            for (int y = (int)minY; y < (int)maxY; ++y) {
                if (mat->at<uint8_t>(y, x) > contentThreshold) count++;
            }
        }
        return count;
    };

    // 4. Density Probes (Left and Right)
    double lastGoodL = strictL, lastGoodR = strictR;
    
    // Probe Left
    int curL = (int)strictL;
    while (curL > 0) {
        int p = getBlockDensity(curL - digitWidth, curL);
        LOGI("CHAR_AWARE: Probe L: [%d to %d] pixels=%d range=(%.0f to %.0f) MATCH=%d", curL - digitWidth, curL, p, 0.5 * mass1, 1.5 * mass8, (p >= 0.5 * mass1 && p <= 1.5 * mass8));
        if (p >= 0.5 * mass1 && p <= 1.5 * mass8) {
            curL -= digitWidth;
            lastGoodL = curL;
        } else break;
    }
    
    // Probe Right
    int curR = (int)strictR;
    while (curR < maxW) {
        int p = getBlockDensity(curR, curR + digitWidth);
        LOGI("CHAR_AWARE: Probe R: [%d to %d] pixels=%d range=(%.0f to %.0f) MATCH=%d", curR, curR + digitWidth, p, 0.5 * mass1, 1.5 * mass8, (p >= 0.5 * mass1 && p <= 1.5 * mass8));
        if (p >= 0.5 * mass1 && p <= 1.5 * mass8) {
            curR += digitWidth;
            lastGoodR = curR;
        } else break;
    }

    // 5. Retraction (with 10-column streak)
    minX = lastGoodL;
    maxX = lastGoodR;
    
    int streakL = 0;
    for (int x = (int)lastGoodL; x < (int)strictL; ++x) {
        if (!isValley((int)minY, (int)maxY, x, false)) streakL++;
        else streakL = 0;
        if (streakL >= 10) { minX = x - 9; break; }
    }
    
    int streakR = 0;
    for (int x = (int)lastGoodR; x > (int)strictR; --x) {
        if (!isValley((int)minY, (int)maxY, x, false)) streakR++;
        else streakR = 0;
        if (streakR >= 10) { maxX = x + 9; break; }
    }

    jintArray result = env->NewIntArray(4);
    jint dims[4] = {(jint)minX, (jint)minY, (jint)maxX, (jint)maxY};
    env->SetIntArrayRegion(result, 0, 4, dims);
    LOGI("CHAR_AWARE: Final: (%d,%d)-(%d,%d)", (int)minX, (int)minY, (int)maxX, (int)maxY);
    return result;
}
}


