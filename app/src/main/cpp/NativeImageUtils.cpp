#include <jni.h>
#include <android/bitmap.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/imgcodecs.hpp>
#include <string>
#include <vector>
#include <algorithm>
#include "BufferSetHandle.h"
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

extern "C" {

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

JNIEXPORT jboolean JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativeIngestDngToYuv(
    JNIEnv* env, jobject thiz, jstring path, jlong handlePtr) {
    
    auto* handle = reinterpret_cast<BufferSetHandle*>(handlePtr);
    if (!handle) return JNI_FALSE;
    
    const char* nativePath = env->GetStringUTFChars(path, nullptr);
    
    LibRaw RawProcessor;
    int ret = RawProcessor.open_file(nativePath);
    env->ReleaseStringUTFChars(path, nativePath);
    
    if (ret != LIBRAW_SUCCESS) return JNI_FALSE;
    
    // Check if the sensor resolution matches our BufferSet
    if (handle->width != (size_t)RawProcessor.imgdata.sizes.width || 
        handle->height != (size_t)RawProcessor.imgdata.sizes.height) {
        // If it doesn't match exactly, LibRaw allows half_size output
        // but for now, we expect exact sizing based on our probe.
        return JNI_FALSE;
    }
    
    ret = RawProcessor.unpack();
    if (ret != LIBRAW_SUCCESS) return JNI_FALSE;
    
    // Configure development parameters
    RawProcessor.imgdata.params.output_color = 1; // sRGB
    RawProcessor.imgdata.params.use_camera_wb = 1; // Use camera white balance
    RawProcessor.imgdata.params.half_size = 0; // Full resolution
    
    ret = RawProcessor.dcraw_process();
    if (ret != LIBRAW_SUCCESS) return JNI_FALSE;
    
    libraw_processed_image_t *image = RawProcessor.dcraw_make_mem_image(&ret);
    if (!image || ret != LIBRAW_SUCCESS) return JNI_FALSE;
    
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

}
