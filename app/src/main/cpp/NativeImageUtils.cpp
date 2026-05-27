#include <jni.h>
#include <android/bitmap.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/imgcodecs.hpp>
#include <string>
#include <vector>
#include <algorithm>
#include <map>
#include <cmath>
#include <chrono>
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
    double vL = (maxY - minY) * 1.0;
    double lookAhead = (maxY - minY) * 4.0;

    auto isValley = [&](int start, int end, int fixed, bool horizontal) -> bool {
        double sum = 0;
        int count = 0;
        if (horizontal) {
            if (fixed < 0 || fixed >= maxH) return true;
            const uint8_t* rowPtr = mat->ptr<uint8_t>(fixed);
            int startIdx = std::max(0, start);
            int endIdx = std::min(maxW, end);
            for (int i = startIdx; i < endIdx; ++i) {
                sum += rowPtr[i];
                count++;
            }
        } else {
            if (fixed < 0 || fixed >= maxW) return true;
            int startIdx = std::max(0, start);
            int endIdx = std::min(maxH, end);
            for (int i = startIdx; i < endIdx; ++i) {
                sum += mat->at<uint8_t>(i, fixed);
                count++;
            }
        }
        double avg = (count > 0) ? (sum / count) : 0.0;
        return (avg < 15.0 || avg < valleyThreshold);
    };

    // 3. Vertical Expansion (Simple Stop)
    while (minY > 0 && (sY - minY) < vL) {
        if (isValley((int)minX, (int)maxX, (int)minY - 1, true)) break;
        minY -= 1.0;
    }
    while (maxY < maxH - 1 && (maxY - sYY) < vL) {
        if (isValley((int)minX, (int)maxX, (int)maxY + 1, true)) break;
        maxY += 1.0;
    }

    // 4. Horizontal Expansion (Jump and Collapse)
    double walkL = minX;
    double lastGoodL = minX;
    while (walkL > 0 && (sX - walkL) < hL) {
        walkL -= 1.0;
        if (!isValley((int)minY, (int)maxY, (int)walkL, false)) {
            minX = walkL;
            lastGoodL = walkL;
        } else {
            if ((lastGoodL - walkL) > lookAhead) break;
        }
    }

    double walkR = maxX;
    double lastGoodR = maxX;
    while (walkR < maxW - 1 && (walkR - sXX) < hL) {
        walkR += 1.0;
        if (!isValley((int)minY, (int)maxY, (int)walkR, false)) {
            maxX = walkR;
            lastGoodR = walkR;
        } else {
            if ((walkR - lastGoodR) > lookAhead) break;
        }
    }

    jintArray result = env->NewIntArray(4);
    jint dims[4] = {(jint)minX, (jint)minY, (jint)maxX, (jint)maxY};
    env->SetIntArrayRegion(result, 0, 4, dims);
    return result;
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

    // --- JUMP OUT (Forcefully move into the gap) ---
    minY = std::max(0.0, minY - 4.0);
    maxY = std::min((double)maxH - 1, maxY + 4.0);
    minX = std::max(0.0, minX - 4.0);
    maxX = std::min((double)maxW - 1, maxX + 4.0);

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

    jintArray result = env->NewIntArray(4);
    jint dims[4] = {(jint)minX, (jint)minY, (jint)maxX, (jint)maxY};
    env->SetIntArrayRegion(result, 0, 4, dims);
    return result;
}

JNIEXPORT jfloat JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeHeatmapToAngle(
    JNIEnv* env, jobject thiz, jfloatArray heatmapArr, jint w, jint h, jfloat threshold) {

    jfloat* heatmapData = env->GetFloatArrayElements(heatmapArr, nullptr);
    if (!heatmapData) return 0.0f;

    cv::Mat heatmap(h, w, CV_32F, heatmapData);
    cv::Mat mask = heatmap > threshold;

    std::vector<std::vector<cv::Point>> contours;
    std::vector<cv::Vec4i> hierarchy;
    cv::findContours(mask, contours, hierarchy, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_SIMPLE);

    if (contours.empty()) {
        env->ReleaseFloatArrayElements(heatmapArr, heatmapData, JNI_ABORT);
        return 0.0f;
    }

    // Weighted Consensus Voting (0.5 degree buckets)
    std::map<int, double> buckets;
    for (const auto& contour : contours) {
        if (cv::contourArea(contour) < 10) continue;
        cv::RotatedRect rrect = cv::minAreaRect(contour);
        float angle = calculateAngle(rrect);
        
        // Calculate Confidence: Mean heatmap value inside the contour
        cv::Rect bounds = rrect.boundingRect();
        bounds &= cv::Rect(0, 0, heatmap.cols, heatmap.rows);
        float confidence = 0.0f;
        if (bounds.width > 0 && bounds.height > 0) {
            cv::Mat mask = cv::Mat::zeros(bounds.size(), CV_8U);
            std::vector<std::vector<cv::Point>> polys = {{contour}};
            for (auto& p : polys[0]) p -= bounds.tl();
            cv::fillPoly(mask, polys, cv::Scalar(255));
            cv::Scalar mean = cv::mean(heatmap(bounds), mask);
            confidence = (float)mean[0];
        }

        int bucketIdx = (int)std::round(angle * 2.0f);
        double weight = (double)cv::arcLength(contour, true) * (double)confidence;
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

    env->ReleaseFloatArrayElements(heatmapArr, heatmapData, JNI_ABORT);
    return (float)bestBucket / 2.0f;
}

}

