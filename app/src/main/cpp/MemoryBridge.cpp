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

    UnifiedHandle(void* p, size_t w, size_t h, size_t s) : pixels(p), width(w), height(h), stride(s) {
        // Create Mat header using the bitmap's stride (step)
        yMat = new cv::Mat((int)h, (int)w, CV_8UC1, p, s);
    }

    ~UnifiedHandle() {
        delete yMat;
    }
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridge_nativeLock(
    JNIEnv* env, jobject thiz, jobject bitmap, jint width, jint height) {
    
    if (bitmap == nullptr) {
        LOGE("nativeLock: bitmap is null");
        return 0;
    }

    AndroidBitmapInfo info;
    void* pixels = nullptr;
    int ret;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        LOGE("nativeLock: AndroidBitmap_getInfo() failed! error=%d", ret);
        return 0;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_A_8) {
        LOGE("nativeLock: Bitmap format must be ALPHA_8! format=%d", info.format);
        return 0;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        LOGE("nativeLock: AndroidBitmap_lockPixels() failed! error=%d", ret);
        return 0;
    }

    if (pixels == nullptr) {
        LOGE("nativeLock: locked pixels is null");
        AndroidBitmap_unlockPixels(env, bitmap);
        return 0;
    }

    // Initialize: Y = 0 (black), UV = 128 (neutral)
    size_t frameSize = (size_t)width * (size_t)height;
    size_t totalSize = frameSize + (frameSize / 2);
    
    if (totalSize > (info.stride * info.height)) {
        LOGE("nativeLock: requested size %zu exceeds bitmap capacity %zu", totalSize, (size_t)(info.stride * info.height));
        AndroidBitmap_unlockPixels(env, bitmap);
        return 0;
    }

    std::memset(pixels, 0, frameSize);
    std::memset((uint8_t*)pixels + frameSize, 128, totalSize - frameSize);

    auto* handle = new UnifiedHandle(pixels, (size_t)width, (size_t)height, (size_t)info.stride);
    LOGI("nativeLock: Success. Handle=%p, Ptr=%p, Stride=%zu", handle, pixels, (size_t)info.stride);
    return (jlong)handle;
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridge_nativeUnlock(
    JNIEnv* env, jobject thiz, jobject bitmap, jlong handlePtr) {
    
    if (bitmap != nullptr) {
        AndroidBitmap_unlockPixels(env, bitmap);
    }
    
    auto* handle = reinterpret_cast<UnifiedHandle*>(handlePtr);
    if (handle != nullptr) {
        delete handle;
    }
    LOGI("nativeUnlock: Handle freed");
}

JNIEXPORT jlong JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridge_nativeGetMatPtr(
    JNIEnv* env, jobject thiz, jlong handlePtr) {
    auto* handle = reinterpret_cast<UnifiedHandle*>(handlePtr);
    if (handle == nullptr) return 0;
    return (jlong)handle->yMat;
}

JNIEXPORT jobject JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridge_nativeGetDirectBuffer(
    JNIEnv* env, jobject thiz, jobject bitmap) {
    
    if (bitmap == nullptr) return nullptr;

    void* pixels = nullptr;
    AndroidBitmapInfo info;
    
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return nullptr;
    
    // Safety: we do NOT unlock here; the caller must use MemoryBridge.release()
    size_t totalSize = (size_t)info.stride * (size_t)info.height;
    return env->NewDirectByteBuffer(pixels, (jlong)totalSize);
}

}
