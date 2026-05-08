#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <opencv2/core.hpp>
#include <cstring>

#define LOG_TAG "MemoryBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * UnifiedHandle stores the pointers and headers for a shared memory block
 * anchored to an Android Bitmap.
 */
struct UnifiedHandle {
    void* pixels;
    cv::Mat* yMat;
    size_t width;
    size_t height;
    size_t stride;
    size_t actualByteCount;

    UnifiedHandle(void* p, size_t w, size_t h, size_t s, size_t total) 
        : pixels(p), width(w), height(h), stride(s), actualByteCount(total) {
        yMat = new cv::Mat((int)h, (int)w, CV_8UC1, p, s);
    }

    ~UnifiedHandle() {
        delete yMat;
    }
};

extern "C" {

/**
 * Note: These methods correspond to top-level external functions in MemoryBridge.kt.
 * Kotlin compiles top-level functions in a file named "MemoryBridge.kt" 
 * into a JVM class named "MemoryBridgeKt".
 */

JNIEXPORT jlong JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridgeKt_nativeLock(
    JNIEnv* env, jclass clazz, jobject bitmap, jint width, jint height) {
    
    if (bitmap == nullptr) return 0;

    AndroidBitmapInfo info;
    void* pixels = nullptr;
    int ret;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) return 0;
    if (info.format != ANDROID_BITMAP_FORMAT_A_8) return 0;
    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) return 0;
    if (pixels == nullptr) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return 0;
    }

    size_t frameSize = (size_t)width * (size_t)height;
    size_t totalSize = frameSize + (frameSize / 2);
    size_t actualByteCount = (size_t)info.stride * (size_t)info.height;

    if (totalSize > actualByteCount) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return 0;
    }

    std::memset(pixels, 0, frameSize);
    std::memset((uint8_t*)pixels + frameSize, 128, totalSize - frameSize);

    auto* handle = new UnifiedHandle(pixels, (size_t)width, (size_t)height, (size_t)info.stride, actualByteCount);
    return (jlong)handle;
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridgeKt_nativeUnlock(
    JNIEnv* env, jclass clazz, jobject bitmap, jlong handlePtr) {
    
    if (bitmap != nullptr) {
        AndroidBitmap_unlockPixels(env, bitmap);
    }
    
    auto* handle = reinterpret_cast<UnifiedHandle*>(handlePtr);
    if (handle != nullptr) {
        delete handle;
    }
}

JNIEXPORT jlong JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridgeKt_nativeGetMatPtr(
    JNIEnv* env, jclass clazz, jlong handlePtr) {
    auto* handle = reinterpret_cast<UnifiedHandle*>(handlePtr);
    if (handle == nullptr) return 0;
    return (jlong)handle->yMat;
}

JNIEXPORT jobject JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridgeKt_nativeGetDirectBuffer(
    JNIEnv* env, jclass clazz, jlong handlePtr) {
    
    auto* handle = reinterpret_cast<UnifiedHandle*>(handlePtr);
    if (handle == nullptr || handle->pixels == nullptr) return nullptr;
    
    return env->NewDirectByteBuffer(handle->pixels, (jlong)handle->actualByteCount);
}

}
