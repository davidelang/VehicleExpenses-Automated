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

std::string matToBase64(const cv::Mat& mat) {
    if (mat.empty()) return "";
    std::vector<uint8_t> buf;
    cv::imencode(".png", mat, buf);
    return base64_encode(buf.data(), buf.size());
}

void filterComponents(cv::Mat& mat, float vSW, float hSW, int mode) {
    cv::Mat labels, stats, centroids;
    int nLabels = cv::connectedComponentsWithStats(mat, labels, stats, centroids, 8);
    for (int i = 1; i < nLabels; ++i) {
        int w = stats.at<int>(i, cv::CC_STAT_WIDTH);
        int h = stats.at<int>(i, cv::CC_STAT_HEIGHT);
        bool remove = false;
        if (mode == 0) remove = (w < 0.5 * vSW && h < 0.5 * hSW);
        else if (mode == 1) remove = (w < 0.5 * vSW);
        else if (mode == 2) remove = (h < 0.5 * hSW);
        
        if (remove) {
            cv::Rect rect(stats.at<int>(i, cv::CC_STAT_LEFT), stats.at<int>(i, cv::CC_STAT_TOP), w, h);
            cv::Mat mask = (labels(rect) == i);
            mat(rect).setTo(0, mask);
        }
    }
}

std::string renderHistogramB64(const std::map<int, int>& hHist, const std::map<int, int>& vHist, int width, int height) {
    cv::Mat canvas(height, width, CV_8UC3, cv::Scalar(255, 255, 255));
    int maxH = 1, maxV = 1;
    for (auto const& [k, v] : hHist) if (v > maxH) maxH = v;
    for (auto const& [k, v] : vHist) if (v > maxV) maxV = v;

    int halfW = width / 2;
    for (int i = 0; i < 128; ++i) {
        int valH = 0;
        if (hHist.count(i*2)) valH += hHist.at(i*2);
        if (hHist.count(i*2+1)) valH += hHist.at(i*2+1);
        float hLine = (float)valH / maxH * (height - 35);
        cv::rectangle(canvas, cv::Point(i*2, height - 25 - (int)hLine), cv::Point(i*2+1, height - 25), cv::Scalar(255, 0, 0), -1);

        int valV = 0;
        if (vHist.count(i*2)) valV += vHist.at(i*2);
        if (vHist.count(i*2+1)) valV += vHist.at(i*2+1);
        float vLine = (float)valV / maxV * (height - 35);
        cv::rectangle(canvas, cv::Point(halfW + 5 + i*2, height - 25 - (int)vLine), cv::Point(halfW + 5 + i*2 + 1, height - 25), cv::Scalar(0, 0, 255), -1);
    }
    
    cv::line(canvas, cv::Point(0, height - 25), cv::Point(width, height - 25), cv::Scalar(0,0,0), 1);
    for (int i = 0; i <= 256; i += 25) {
        bool isLong = (i % 100 == 0);
        int ticH = isLong ? 15 : 8;
        int x1 = (int)(i * 255.0f / 256.0f); // Map 256px range to buckets
        int x2 = halfW + 5 + x1;
        cv::line(canvas, cv::Point(x1, height - 25), cv::Point(x1, height - 25 + ticH), cv::Scalar(0,0,0), 2);
        cv::line(canvas, cv::Point(x2, height - 25), cv::Point(x2, height - 25 + ticH), cv::Scalar(0,0,0), 2);
    }
    
    return matToBase64(canvas);
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
    LOGI("CHAR_AWARE: Start (%d,%d)-(%d,%d) img=%dx%d", L, T, R, B, maxW, maxH);

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
    LOGI("CHAR_AWARE: Start (%d,%d)-(%d,%d) img=%dx%d", L, T, R, B, maxW, maxH);

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
    LOGI("CHAR_AWARE: Start (%d,%d)-(%d,%d) img=%dx%d", L, T, R, B, maxW, maxH);

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
    double contentThreshold = std::max(15.0, meanVal[0] * (double)thresholdFactor);

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

    auto isValley = [&](int x, int minY, int maxY) -> bool {
        return getMaxRun((int)minY, (int)maxY, x, false) < 5;
    };

    double minX = L, maxX = R, minY = T, maxY = B;
    double vL = (maxY - minY) * 1.5;

    // 1. Vertical Expansion
    while (minY > 0 && (T - minY) < vL) {
        if (getMaxRun((int)minX, (int)maxX, (int)minY - 1, true) < 3) break;
        minY -= 1.0;
    }
    while (maxY < maxH - 1 && (maxY - B) < vL) {
        if (getMaxRun((int)minX, (int)maxX, (int)maxY + 1, true) < 3) break;
        maxY += 1.0;
    }

    // 2. Bidirectional Snapping Walk
    if (isValley((int)minX, (int)minY, (int)maxY)) {
        while (minX < maxX && isValley((int)minX, (int)minY, (int)maxY)) minX += 1.0;
    } else {
        while (minX > 0 && !isValley((int)minX - 1, (int)minY, (int)maxY)) minX -= 1.0;
    }
    if (isValley((int)maxX - 1, (int)minY, (int)maxY)) {
        while (maxX > minX && isValley((int)maxX - 1, (int)minY, (int)maxY)) maxX -= 1.0;
    } else {
        while (maxX < maxW && !isValley((int)maxX, (int)minY, (int)maxY)) maxX += 1.0;
    }

    jintArray result = env->NewIntArray(4);
    jint dims[4] = {(jint)minX, (jint)minY, (jint)maxX, (jint)maxY};
    env->SetIntArrayRegion(result, 0, 4, dims);
    return result;
}
}




extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeExpandByCharacterAwareDiagnostic(
    JNIEnv* env, jobject thiz, jlong matPtr, jint L, jint T, jint R, jint B, jfloat thresholdFactor) {

    auto* mat = reinterpret_cast<cv::Mat*>(matPtr);
    if (!mat || mat->empty() || mat->type() != CV_8UC1) return nullptr;

    int maxW = mat->cols;
    int maxH = mat->rows;
    std::ostringstream oss;

    int safeL = std::max(0, std::min(L, maxW - 1));
    int safeT = std::max(0, std::min(T, maxH - 1));
    int safeR = std::max(safeL + 1, std::min(R, maxW));
    int safeB = std::max(safeT + 1, std::min(B, maxH));

    cv::Rect roi(safeL, safeT, safeR - safeL, safeB - safeT);
    cv::Scalar meanVal = cv::mean((*mat)(roi));
    double contentThreshold = std::max(15.0, meanVal[0] * (double)thresholdFactor);

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

    auto isValley = [&](int x, int minY, int maxY) -> bool {
        int h = maxY - minY;
        int cut = std::max(1, (int)(h * 0.10));
        return getMaxRun((int)minY + cut, (int)maxY - cut, x, false) < 5;
    };

    double minX = L, maxX = R, minY = T, maxY = B;
    double vL = (maxY - minY) * 1.5;

    while (minY > 0 && (T - minY) < vL) {
        if (getMaxRun((int)minX, (int)maxX, (int)minY - 1, true) < 3) break;
        minY -= 1.0;
    }
    while (maxY < maxH - 1 && (maxY - B) < vL) {
        if (getMaxRun((int)minX, (int)maxX, (int)maxY + 1, true) < 3) break;
        maxY += 1.0;
    }

    if (isValley((int)minX, (int)minY, (int)maxY)) {
        while (minX < maxX && isValley((int)minX, (int)minY, (int)maxY)) minX += 1.0;
    } else {
        while (minX > 0 && !isValley((int)minX - 1, (int)minY, (int)maxY)) minX -= 1.0;
    }
    if (isValley((int)maxX - 1, (int)minY, (int)maxY)) {
        while (maxX > minX && isValley((int)maxX - 1, (int)minY, (int)maxY)) maxX -= 1.0;
    } else {
        while (maxX < maxW && !isValley((int)maxX, (int)minY, (int)maxY)) maxX += 1.0;
    }

    std::map<int, int> horizRunHist, vertRunHist;
    for (int y = (int)minY; y < (int)maxY; ++y) {
        const uint8_t* rowPtr = mat->ptr<uint8_t>(y);
        int run = 0;
        for (int x = (int)minX; x < (int)maxX; ++x) {
            if (rowPtr[x] > contentThreshold) run++;
            else { if (run > 0) horizRunHist[std::min(255, run)]++; run = 0; }
        }
        if (run > 0) horizRunHist[std::min(255, run)]++;
    }
    for (int x = (int)minX; x < (int)maxX; ++x) {
        int run = 0;
        for (int y = (int)minY; y < (int)maxY; ++y) {
            if (mat->at<uint8_t>(y, x) > contentThreshold) run++;
            else { if (run > 0) vertRunHist[std::min(255, run)]++; run = 0; }
        }
        if (run > 0) vertRunHist[std::min(255, run)]++;
    }

    auto getPeak = [](const std::map<int, int>& h) -> int {
        int bestIdx = 10, bestVal = -1;
        for (auto const& [k, v] : h) { if (k > 3 && v > bestVal) { bestVal = v; bestIdx = k; } }
        return bestIdx;
    };
    int vSW = getPeak(horizRunHist); 
    int hSW = getPeak(vertRunHist);  
    long oneStrokeMass = vSW * (maxY - minY);
    oss << "vSW=" << vSW << " hSW=" << hSW << " OneStrokeM=" << oneStrokeMass << " ";

    std::vector<std::pair<int, int>> sortedHist(horizRunHist.begin(), horizRunHist.end());
    std::sort(sortedHist.begin(), sortedHist.end(), [](const auto& a, const auto& b) { return a.second > b.second; });

    // Forensic Filter Images
    cv::Mat core(mat->rows, mat->cols, CV_8UC1, cv::Scalar(0));
    cv::Mat temp; cv::threshold((*mat), temp, contentThreshold, 255, cv::THRESH_BINARY);
    temp.copyTo(core);
    cv::Mat passA = core.clone(), passB = core.clone(), passC = core.clone();
    filterComponents(passA, (float)vSW, (float)hSW, 0);
    filterComponents(passB, (float)vSW, (float)hSW, 1);
    filterComponents(passC, (float)vSW, (float)hSW, 2);

    std::vector<int> colMaxRuns;
    for (int x = (int)minX; x < (int)maxX; ++x) {
        colMaxRuns.push_back(getMaxRun((int)minY, (int)maxY, x, false));
    }
    struct Blob { int startX, endX; long mass; int maxRun; };
    std::vector<Blob> blobs; bool inBlob = false; int blobStart = 0; long currentMass = 0; int currentMaxRun = 0;
    for (int x = 0; x < (int)colMaxRuns.size(); ++x) {
        int mr = colMaxRuns[x]; bool valley = isValley(x + (int)minX, (int)minY, (int)maxY);
        if (!inBlob && !valley) { inBlob = true; blobStart = x + (int)minX; currentMass = 0; currentMaxRun = 0; }
        if (inBlob) {
            if (valley) { blobs.push_back({blobStart, x + (int)minX, currentMass, currentMaxRun}); inBlob = false; }
            else {
                currentMaxRun = std::max(currentMaxRun, mr);
                for (int y = (int)minY; y < (int)maxY; ++y) { if (mat->at<uint8_t>(y, x + (int)minX) > contentThreshold) currentMass++; }
            }
        }
    }
    if (inBlob) blobs.push_back({blobStart, (int)maxX, currentMass, currentMaxRun});

    int medianPitch = 0; bool rightAnchored = false;
    if (blobs.size() > 1) {
        std::vector<int> centers, rights;
        for (size_t i = 0; i < blobs.size() - 1; ++i) {
            centers.push_back(((blobs[i+1].startX + blobs[i+1].endX)/2) - ((blobs[i].startX + blobs[i].endX)/2));
            rights.push_back(blobs[i+1].endX - blobs[i].endX);
        }
        std::sort(centers.begin(), centers.end()); std::sort(rights.begin(), rights.end());
        int rRange = rights.back() - rights.front();
        if (rRange < 15) rightAnchored = true;
        medianPitch = rightAnchored ? rights[rights.size()/2] : centers[centers.size()/2];
        oss << "Pitch=" << medianPitch << " Anchor=" << (rightAnchored ? "RIGHT" : "CENTER") << " ";
    }

    // Grid Alignment Phase Shift
    int bestShift = 0;
    if (medianPitch > 0) {
        long minBoundaryDensity = 9999999;
        for (int shift = -10; shift <= 10; ++shift) {
            long currentDensity = 0;
            if (rightAnchored) {
                for (int x = (int)maxX + shift; x > (int)minX; x -= medianPitch) {
                    currentDensity += getMaxRun((int)minY, (int)maxY, x, false);
                }
            } else {
                int startC = (blobs[0].startX + blobs[0].endX) / 2;
                int startX = startC - (medianPitch / 2) + shift;
                for (int x = startX; x < (int)maxX; x += medianPitch) {
                    currentDensity += getMaxRun((int)minY, (int)maxY, x, false);
                }
            }
            if (currentDensity < minBoundaryDensity) {
                minBoundaryDensity = currentDensity;
                bestShift = shift;
            }
        }
    }

    std::vector<cv::Rect> matchedSlots, failedSlots;
    if (medianPitch > 0 && blobs.size() > 0) {
        if (rightAnchored) {
            for (int x = (int)maxX + bestShift; x > (int)minX; x -= medianPitch) {
                matchedSlots.push_back(cv::Rect(x - medianPitch, (int)minY, medianPitch, (int)maxY - (int)minY));
            }
        } else {
            int startC = (blobs[0].startX + blobs[0].endX) / 2;
            int startX = startC - (medianPitch / 2) + bestShift;
            for (int x = startX; x < (int)maxX; x += medianPitch) {
                matchedSlots.push_back(cv::Rect(x, (int)minY, medianPitch, (int)maxY - (int)minY));
            }
        }
    }

    auto getBoxMass = [&](int startX, int endX, int minY, int maxY) -> long {
        long m = 0;
        for (int x = std::max(0, startX); x < std::min(maxW, endX); ++x) {
            for (int y = std::max(0, minY); y < std::min(maxH, maxY); ++y) {
                if (mat->at<uint8_t>(y, x) > contentThreshold) m++;
            }
        }
        return m;
    };

    if (medianPitch > 0 && oneStrokeMass > 0) {
        int curL = (int)minX + bestShift;
        while (curL - medianPitch >= 0) {
            int pL = curL - medianPitch, pR = curL;
            long pm = getBoxMass(pL, pR, (int)minY, (int)maxY);
            bool match = (pm >= 0.5 * oneStrokeMass && pm <= 4.0 * oneStrokeMass);
            oss << "ProbeL[" << pL << "-" << pR << "]:m=" << pm << (match ? " (MATCH) " : " (STOP) ");
            if (match) {
                int contentL = pR, contentR = pL;
                for (int x = pL; x < pR; ++x) {
                    if (!isValley(x, (int)minY, (int)maxY)) { contentL = std::min(contentL, x); contentR = std::max(contentR, x); }
                }
                minX = contentL; curL = pL;
                matchedSlots.push_back(cv::Rect(pL, (int)minY, pR - pL, (int)maxY - (int)minY));
            } else {
                failedSlots.push_back(cv::Rect(pL, (int)minY, pR - pL, (int)maxY - (int)minY));
                break;
            }
        }
        int curR = (int)maxX + bestShift;
        while (curR + medianPitch <= maxW) {
            int pL = curR, pR = curR + medianPitch;
            long pm = getBoxMass(pL, pR, (int)minY, (int)maxY);
            bool match = (pm >= 0.5 * oneStrokeMass && pm <= 4.0 * oneStrokeMass);
            oss << "ProbeR[" << pL << "-" << pR << "]:m=" << pm << (match ? " (MATCH) " : " (STOP) ");
            if (match) {
                int contentL = pR, contentR = pL;
                for (int x = pL; x < pR; ++x) {
                    if (!isValley(x, (int)minY, (int)maxY)) { contentL = std::min(contentL, x); contentR = std::max(contentR, x); }
                }
                maxX = contentR + 1; curR = pR;
                matchedSlots.push_back(cv::Rect(pL, (int)minY, pR - pL, (int)maxY - (int)minY));
            } else {
                failedSlots.push_back(cv::Rect(pL, (int)minY, pR - pL, (int)maxY - (int)minY));
                break;
            }
        }
    }

    oss << "HistPeaks: ";
    for (int i = 0; i < std::min(5, (int)sortedHist.size()); ++i) {
        oss << sortedHist[i].first << "(" << sortedHist[i].second << ") ";
    }

    auto packSlots = [&](const std::vector<cv::Rect>& v) -> jintArray {
        jintArray arr = env->NewIntArray(v.size() * 4);
        if (!v.empty()) {
            jint* data = new jint[v.size() * 4];
            for (size_t i = 0; i < v.size(); ++i) {
                data[i*4+0] = v[i].x; data[i*4+1] = v[i].y; data[i*4+2] = v[i].x + v[i].width; data[i*4+3] = v[i].y + v[i].height;
            }
            env->SetIntArrayRegion(arr, 0, v.size() * 4, data);
            delete[] data;
        }
        return arr;
    };

    jintArray matchedArr = packSlots(matchedSlots);
    jintArray failedArr = packSlots(failedSlots);

    jintArray summary = env->NewIntArray(16);
    jint s[16] = { (jint)L, (jint)T, (jint)R, (jint)B, (jint)minX, (jint)minY, (jint)maxX, (jint)maxY, (jint)minX, (jint)minY, (jint)maxX, (jint)maxY, (jint)contentThreshold, (jint)vSW, (jint)hSW, (jint)medianPitch };
    env->SetIntArrayRegion(summary, 0, 16, s);

    jstring histB64 = env->NewStringUTF(renderHistogramB64(horizRunHist, vertRunHist, 521, 150).c_str());
    jstring imgAB64 = env->NewStringUTF(matToBase64(passA).c_str());
    jstring imgBB64 = env->NewStringUTF(matToBase64(passB).c_str());
    jstring imgCB64 = env->NewStringUTF(matToBase64(passC).c_str());
    jstring traceStr = env->NewStringUTF(oss.str().c_str());

    jclass objClass = env->FindClass("java/lang/Object");
    jobjectArray resultArr = env->NewObjectArray(8, objClass, nullptr);
    env->SetObjectArrayElement(resultArr, 0, summary);
    env->SetObjectArrayElement(resultArr, 1, traceStr);
    env->SetObjectArrayElement(resultArr, 2, histB64);
    env->SetObjectArrayElement(resultArr, 3, packSlots(matchedSlots));
    env->SetObjectArrayElement(resultArr, 4, packSlots(failedSlots));
    env->SetObjectArrayElement(resultArr, 5, imgAB64);
    env->SetObjectArrayElement(resultArr, 6, imgBB64);
    env->SetObjectArrayElement(resultArr, 7, imgCB64);

    return resultArr;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeCalculateHistogramB64(
    JNIEnv* env, jobject thiz, jlong matPtr, jintArray rects) {
    
    auto* mat = reinterpret_cast<cv::Mat*>(matPtr);
    if (!mat || mat->empty() || mat->type() != CV_8UC1) return nullptr;

    jsize len = env->GetArrayLength(rects);
    if (len % 4 != 0 || len == 0) return nullptr;

    jint* rData = env->GetIntArrayElements(rects, nullptr);
    int minL = mat->cols, minT = mat->rows, maxR = 0, maxB = 0;
    for (int i = 0; i < len; i += 4) {
        minL = std::min(minL, (int)rData[i]); minT = std::min(minT, (int)rData[i+1]);
        maxR = std::max(maxR, (int)rData[i+2]); maxB = std::max(maxB, (int)rData[i+3]);
    }
    env->ReleaseIntArrayElements(rects, rData, JNI_ABORT);

    minL = std::max(0, std::min(minL, mat->cols - 1)); minT = std::max(0, std::min(minT, mat->rows - 1));
    maxR = std::max(minL + 1, std::min(maxR, mat->cols)); maxB = std::max(minT + 1, std::min(maxB, mat->rows));

    cv::Rect roi(minL, minT, maxR - minL, maxB - minT);
    cv::Scalar meanVal = cv::mean((*mat)(roi));
    double contentThreshold = std::max(15.0, meanVal[0] * 0.40);

    std::map<int, int> horizHist, vertHist;
    for (int y = minT; y < maxB; ++y) {
        const uint8_t* rowPtr = mat->ptr<uint8_t>(y);
        int run = 0;
        for (int x = minL; x < maxR; ++x) {
            if (rowPtr[x] > contentThreshold) run++;
            else { if (run > 0) horizHist[std::min(255, run)]++; run = 0; }
        }
        if (run > 0) horizHist[std::min(255, run)]++;
    }
    for (int x = minL; x < maxR; ++x) {
        int run = 0;
        for (int y = minT; y < maxB; ++y) {
            if (mat->at<uint8_t>(y, x) > contentThreshold) run++;
            else { if (run > 0) vertHist[std::min(255, run)]++; run = 0; }
        }
        if (run > 0) vertHist[std::min(255, run)]++;
    }

    auto getPeak = [](const std::map<int, int>& h) -> int {
        int bestIdx = 10, bestVal = -1;
        for (auto const& [k, v] : h) { if (k > 3 && v > bestVal) { bestVal = v; bestIdx = k; } }
        return bestIdx;
    };
    int vSW = getPeak(horizHist);
    int hSW = getPeak(vertHist);

    jintArray metaArr = env->NewIntArray(4);
    jint m[4] = { (jint)vSW, (jint)hSW, 0, (jint)contentThreshold };
    env->SetIntArrayRegion(metaArr, 0, 4, m);

    jstring b64 = env->NewStringUTF(renderHistogramB64(horizHist, vertHist, 521, 150).c_str());
    jclass objClass = env->FindClass("java/lang/Object");
    jobjectArray resultArr = env->NewObjectArray(2, objClass, nullptr);
    env->SetObjectArrayElement(resultArr, 0, b64);
    env->SetObjectArrayElement(resultArr, 1, metaArr);

    return resultArr;
}
