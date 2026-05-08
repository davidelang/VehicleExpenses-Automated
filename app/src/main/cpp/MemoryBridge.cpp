#include <jni.h>
#include <android/log.h>
#include <opencv2/core.hpp>
#include <cstring>

#define LOG_TAG "MemoryBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * UnifiedHandle stores the pointers and headers for a shared memory block
 * anchored to a Direct ByteBuffer.
 */
struct UnifiedHandle {
    void* data;
    cv::Mat* yMat;
    size_t width;
    size_t height;
    size_t actualByteCount;

    UnifiedHandle(void* p, size_t w, size_t h, size_t total) 
        : data(p), width(w), height(h), actualByteCount(total) {
        // Create Mat header pointing to the buffer. Stride matches width for linear buffer.
        yMat = new cv::Mat((int)h, (int)w, CV_8UC1, p, w);
    }

    ~UnifiedHandle() {
        delete yMat;
    }
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridgeKt_nativeSetup(
    JNIEnv* env, jclass clazz, jobject buffer, jint width, jint height) {
    
    if (buffer == nullptr) return 0;

    void* data = env->GetDirectBufferAddress(buffer);
    if (data == nullptr) {
        LOGE("nativeSetup: Failed to get direct buffer address");
        return 0;
    }

    size_t frameSize = (size_t)width * (size_t)height;
    size_t totalSize = frameSize + (frameSize / 2);
    
    // Initialize: Y = 0 (black), UV = 128 (neutral)
    std::memset(data, 0, frameSize);
    std::memset((uint8_t*)data + frameSize, 128, totalSize - frameSize);

    auto* handle = new UnifiedHandle(data, (size_t)width, (size_t)height, totalSize);
    LOGI("nativeSetup: Success. Handle=%p, Ptr=%p, W=%d, H=%d", handle, data, width, height);
    return (jlong)handle;
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridgeKt_nativeRelease(
    JNIEnv* env, jclass clazz, jlong handlePtr) {
    
    auto* handle = reinterpret_cast<UnifiedHandle*>(handlePtr);
    if (handle != nullptr) {
        delete handle;
    }
    LOGI("nativeRelease: Handle freed");
}

JNIEXPORT jlong JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridgeKt_nativeGetMatPtr(
    JNIEnv* env, jclass clazz, jlong handlePtr) {
    auto* handle = reinterpret_cast<UnifiedHandle*>(handlePtr);
    if (handle == nullptr) return 0;
    return (jlong)handle->yMat;
}

}
