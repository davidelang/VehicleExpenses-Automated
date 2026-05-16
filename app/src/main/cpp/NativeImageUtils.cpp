#include <jni.h>
#include <android/bitmap.h>
#include <opencv2/core.hpp>

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

}
