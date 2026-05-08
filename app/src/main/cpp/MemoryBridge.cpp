#include <jni.h>
#include <android/log.h>
#include <opencv2/core.hpp>
#include <cstring>

#define LOG_TAG "MemoryBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct UnifiedHandle {
    uint8_t* data;
    cv::Mat* yMat;
    size_t width;
    size_t height;
    size_t actualByteCount;
    jobject globalBuffer;   // <-- NEW: global ref to keep buffer alive across threads

    UnifiedHandle(uint8_t* p, size_t w, size_t h, size_t total, jobject buf) 
        : data(p), width(w), height(h), actualByteCount(total), globalBuffer(buf) {
        yMat = new cv::Mat((int)h, (int)w, CV_8UC1, p, w);
    }

    ~UnifiedHandle() {
        delete[] data;
        delete yMat;
        // globalBuffer is deleted in nativeRelease via DeleteGlobalRef
    }
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridgeKt_nativeSetup(
    JNIEnv* env, jclass clazz, jint width, jint height) {
    
    size_t frameSize = (size_t)width * (size_t)height;
    size_t totalSize = frameSize + (frameSize / 2);
    
    uint8_t* data = new uint8_t[totalSize];
    if (data == nullptr) {
        LOGE("nativeSetup: Failed to allocate native buffer");
        return 0;
    }

    std::memset(data, 0, frameSize);
    std::memset(data + frameSize, 128, totalSize - frameSize);

    // Create ByteBuffer once and make it a global ref (safe for ML Kit threads)
    jobject localBuffer = env->NewDirectByteBuffer(data, totalSize);
    if (localBuffer == nullptr) {
        LOGE("nativeSetup: NewDirectByteBuffer failed");
        delete[] data;
        return 0;
    }
    jobject globalBuffer = env->NewGlobalRef(localBuffer);

    auto* handle = new UnifiedHandle(data, (size_t)width, (size_t)height, totalSize, globalBuffer);
    LOGI("nativeSetup: Success. Handle=%p, Ptr=%p, W=%d, H=%d", handle, data, width, height);
    return (jlong)handle;
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridgeKt_nativeRelease(
    JNIEnv* env, jclass clazz, jlong handlePtr) {
    
    auto* handle = reinterpret_cast<UnifiedHandle*>(handlePtr);
    if (handle != nullptr) {
        if (handle->globalBuffer != nullptr) {
            env->DeleteGlobalRef(handle->globalBuffer);
        }
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

JNIEXPORT jobject JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_MemoryBridgeKt_nativeGetMasterBuffer(
    JNIEnv* env, jclass clazz, jlong handlePtr) {
    auto* handle = reinterpret_cast<UnifiedHandle*>(handlePtr);
    if (handle == nullptr || handle->globalBuffer == nullptr) return nullptr;
    return handle->globalBuffer;   // return the stored global ref
}

}
