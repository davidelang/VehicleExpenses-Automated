#include <jni.h>
#include <android/log.h>
#include <opencv2/core.hpp>
#include <set>
#include <mutex>
#include <cstring>

#define LOG_TAG "BufferSet"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct BufferSetHandle {
    uint8_t* data;
    cv::Mat* yMat;
    size_t width;
    size_t height;
    size_t actualByteCount;
    jobject globalBuffer;

    BufferSetHandle(uint8_t* p, size_t w, size_t h, size_t total, jobject buf) 
        : data(p), width(w), height(h), actualByteCount(total), globalBuffer(buf) {
        yMat = new cv::Mat((int)h, (int)w, CV_8UC1, p, w);
    }

    ~BufferSetHandle() {
        delete[] data;
        delete yMat;
    }
};

static std::set<BufferSetHandle*> validHandles;
static std::mutex registryMutex;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeSetup(
    JNIEnv* env, jobject thiz, jint width, jint height) {
    
    size_t frameSize = (size_t)width * (size_t)height;
    size_t totalSize = frameSize + (frameSize / 2);
    
    uint8_t* data = new uint8_t[totalSize];
    if (data == nullptr) {
        LOGE("nativeSetup: Failed to allocate native buffer");
        return 0;
    }

    std::memset(data, 0, frameSize);
    std::memset(data + frameSize, 128, totalSize - frameSize);

    jobject localBuffer = env->NewDirectByteBuffer(data, totalSize);
    if (localBuffer == nullptr) {
        LOGE("nativeSetup: NewDirectByteBuffer failed");
        delete[] data;
        return 0;
    }
    jobject globalBuffer = env->NewGlobalRef(localBuffer);

    auto* handle = new BufferSetHandle(data, (size_t)width, (size_t)height, totalSize, globalBuffer);
    
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        validHandles.insert(handle);
    }

    LOGI("nativeSetup: Success. Handle=%p, Ptr=%p, W=%d, H=%d", handle, data, width, height);
    return (jlong)handle;
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeRelease(
    JNIEnv* env, jobject thiz, jlong handlePtr) {
    
    auto* handle = reinterpret_cast<BufferSetHandle*>(handlePtr);
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        if (validHandles.find(handle) == validHandles.end()) {
            LOGE("nativeRelease: Invalid handle pointer %p", handle);
            return;
        }
        validHandles.erase(handle);
    }

    if (handle != nullptr) {
        if (handle->globalBuffer != nullptr) {
            env->DeleteGlobalRef(handle->globalBuffer);
        }
        delete handle;
    }
    LOGI("nativeRelease: Handle freed");
}

JNIEXPORT jboolean JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeResize(
    JNIEnv* env, jobject thiz, jlong handlePtr, jint width, jint height) {
    
    auto* handle = reinterpret_cast<BufferSetHandle*>(handlePtr);
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        if (validHandles.find(handle) == validHandles.end()) {
            LOGE("nativeResize: Invalid handle pointer %p", handle);
            return JNI_FALSE;
        }
    }

    size_t frameSize = (size_t)width * (size_t)height;
    size_t totalSize = frameSize + (frameSize / 2);

    // Reallocate data
    uint8_t* newData = new uint8_t[totalSize];
    if (newData == nullptr) {
        LOGE("nativeResize: Allocation failed");
        return JNI_FALSE;
    }

    std::memset(newData, 0, frameSize);
    std::memset(newData + frameSize, 128, totalSize - frameSize);

    // Update BufferSetHandle
    delete[] handle->data;
    handle->data = newData;
    handle->width = width;
    handle->height = height;
    handle->actualByteCount = totalSize;

    // Pointer Stability: Update Mat in-place
    // Placement-assignment: *handle->yMat = cv::Mat(...)
    // This keeps the handle->yMat object instance address stable.
    *(handle->yMat) = cv::Mat((int)height, (int)width, CV_8UC1, newData, (size_t)width);

    // Update DirectByteBuffer
    if (handle->globalBuffer != nullptr) {
        env->DeleteGlobalRef(handle->globalBuffer);
    }
    jobject localBuffer = env->NewDirectByteBuffer(newData, totalSize);
    handle->globalBuffer = env->NewGlobalRef(localBuffer);

    LOGI("nativeResize: Success. Handle=%p, Ptr=%p, W=%d, H=%d", handle, newData, width, height);
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeGetMatPtr(
    JNIEnv* env, jobject thiz, jlong handlePtr) {
    
    auto* handle = reinterpret_cast<BufferSetHandle*>(handlePtr);
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        if (validHandles.find(handle) == validHandles.end()) return 0;
    }
    return (jlong)handle->yMat;
}

JNIEXPORT jobject JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeGetBuffer(
    JNIEnv* env, jobject thiz, jlong handlePtr) {
    
    auto* handle = reinterpret_cast<BufferSetHandle*>(handlePtr);
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        if (validHandles.find(handle) == validHandles.end()) return nullptr;
    }
    return handle->globalBuffer;
}

}
