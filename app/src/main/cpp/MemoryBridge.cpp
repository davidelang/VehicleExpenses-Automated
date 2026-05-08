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
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridge_00024Companion_nativeLock(
    JNIEnv* env, jobject thiz, jobject bitmap, jint width, jint height) {
    
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    int ret;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
        return 0;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_A_8) {
        LOGE("Bitmap format must be ALPHA_8 for unified memory bridge!");
        return 0;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
        return 0;
    }

    // Initialize: Y = 0 (black), UV = 128 (neutral)
    // We assume the bitmap was allocated at height * 1.5
    size_t frameSize = (size_t)width * (size_t)height;
    size_t totalSize = frameSize + (frameSize / 2);
    
    // Note: If stride > width, the initialization might need to be row-by-row,
    // but for our 320px buffers, stride will match width.
    std::memset(pixels, 0, frameSize);
    std::memset((uint8_t*)pixels + frameSize, 128, totalSize - frameSize);

    auto* handle = new UnifiedHandle(pixels, (size_t)width, (size_t)height, (size_t)info.stride);
    return (jlong)handle;
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridge_00024Companion_nativeUnlock(
    JNIEnv* env, jobject thiz, jobject bitmap, jlong handlePtr) {
    
    AndroidBitmap_unlockPixels(env, bitmap);
    auto* handle = reinterpret_cast<UnifiedHandle*>(handlePtr);
    delete handle;
}

JNIEXPORT jlong JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridge_00024Companion_nativeGetMatPtr(
    JNIEnv* env, jobject thiz, jlong handlePtr) {
    return (jlong)reinterpret_cast<UnifiedHandle*>(handlePtr)->yMat;
}

JNIEXPORT jobject JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridge_00024Companion_nativeGetDirectBuffer(
    JNIEnv* env, jobject thiz, jobject bitmap) {
    
    void* pixels = nullptr;
    AndroidBitmapInfo info;
    
    AndroidBitmap_getInfo(env, bitmap, &info);
    AndroidBitmap_lockPixels(env, bitmap, &pixels);
    // Note: We leave it locked for the duration of the bridge,
    // which is why MemoryBridge.release() calls unlock.
    
    size_t totalSize = info.stride * info.height;
    return env->NewDirectByteBuffer(pixels, (jlong)totalSize);
}

}
