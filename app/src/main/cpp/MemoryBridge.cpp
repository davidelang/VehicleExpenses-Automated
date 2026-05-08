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

// Thread-Safe JNI Caching
static jclass handleClassGlobal = nullptr;
static jmethodID handleConstructor = nullptr;

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    // Cache the NativeHandle class and constructor once
    jclass localClass = env->FindClass("com/davidlang/vehicleexpensesautomated/ui/util/MemoryBridge$NativeHandle");
    if (localClass == nullptr) {
        LOGE("JNI_OnLoad: Failed to find NativeHandle class");
        return JNI_ERR;
    }
    handleClassGlobal = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
    handleConstructor = env->GetMethodID(handleClassGlobal, "<init>", "(JLjava/nio/ByteBuffer;)V");
    if (handleConstructor == nullptr) {
        LOGE("JNI_OnLoad: Failed to find NativeHandle constructor");
        return JNI_ERR;
    }

    LOGI("JNI_OnLoad: MemoryBridge symbols cached successfully");
    return JNI_VERSION_1_6;
}

JNIEXPORT jobject JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridge_nativeLock(
    JNIEnv* env, jobject thiz, jobject bitmap, jint width, jint height) {
    
    if (bitmap == nullptr) {
        LOGE("nativeLock: bitmap is null");
        return nullptr;
    }

    AndroidBitmapInfo info;
    void* pixels = nullptr;
    int ret;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        LOGE("nativeLock: AndroidBitmap_getInfo() failed! error=%d", ret);
        return nullptr;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_A_8) {
        LOGE("nativeLock: Bitmap format must be ALPHA_8! format=%d", info.format);
        return nullptr;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        LOGE("nativeLock: AndroidBitmap_lockPixels() failed! error=%d", ret);
        return nullptr;
    }

    if (pixels == nullptr) {
        LOGE("nativeLock: locked pixels is null");
        AndroidBitmap_unlockPixels(env, bitmap);
        return nullptr;
    }

    // Initialize: Y = 0 (black), UV = 128 (neutral)
    size_t frameSize = (size_t)width * (size_t)height;
    size_t totalSize = frameSize + (frameSize / 2);
    size_t actualByteCount = (size_t)info.stride * (size_t)info.height;

    if (totalSize > actualByteCount) {
        LOGE("nativeLock: requested size %zu exceeds bitmap capacity %zu", totalSize, actualByteCount);
        AndroidBitmap_unlockPixels(env, bitmap);
        return nullptr;
    }

    std::memset(pixels, 0, frameSize);
    std::memset((uint8_t*)pixels + frameSize, 128, totalSize - frameSize);

    auto* handle = new UnifiedHandle(pixels, (size_t)width, (size_t)height, (size_t)info.stride);
    
    // Safety check for cached symbols
    if (handleClassGlobal == nullptr || handleConstructor == nullptr) {
        LOGE("nativeLock: Cached JNI symbols are missing!");
        AndroidBitmap_unlockPixels(env, bitmap);
        delete handle;
        return nullptr;
    }

    jobject directBuffer = env->NewDirectByteBuffer(pixels, (jlong)actualByteCount);
    jobject result = env->NewObject(handleClassGlobal, handleConstructor, (jlong)handle, directBuffer);

    LOGI("nativeLock: Success. Handle=%p, Ptr=%p, Stride=%zu", handle, pixels, (size_t)info.stride);
    return result;
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

}
