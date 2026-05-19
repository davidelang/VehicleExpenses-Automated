#include <jni.h>
#include <android/log.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <set>
#include <mutex>
#include <cstring>

#define LOG_TAG "BufferSet"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * Modern Handle for Phase 25 architecture.
 */
struct BufferSetHandle {
    uint8_t* data;
    cv::Mat* yMat;
    cv::Mat* uvMat;
    cv::Mat* nv21Mat;
    size_t width;
    size_t height;
    size_t actualByteCount;
    jobject globalBuffer;

    BufferSetHandle(uint8_t* p, size_t w, size_t h, size_t total, jobject buf) 
        : data(p), width(w), height(h), actualByteCount(total), globalBuffer(buf) {
        yMat = new cv::Mat((int)h, (int)w, CV_8UC1, p, w);
        uvMat = new cv::Mat((int)h / 2, (int)w / 2, CV_8UC2, p + (w * h), w);
        nv21Mat = new cv::Mat((int)h * 3 / 2, (int)w, CV_8UC1, p, w);
    }

    ~BufferSetHandle() {
        delete[] data;
        delete yMat;
        delete uvMat;
        delete nv21Mat;
    }
};

static std::set<BufferSetHandle*> validHandles;
static std::mutex registryMutex;

extern "C" {

// --- MODERN JNI BINDINGS (BufferSet) ---

JNIEXPORT jlong JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeSetup(
    JNIEnv* env, jobject thiz, jint width, jint height) {
    size_t frameSize = (size_t)width * (size_t)height;
    size_t totalSize = frameSize + (frameSize / 2);
    uint8_t* data = new uint8_t[totalSize];
    if (data == nullptr) return 0;
    std::memset(data, 0, frameSize);
    std::memset(data + frameSize, 128, totalSize - frameSize);
    jobject localBuffer = env->NewDirectByteBuffer(data, totalSize);
    if (localBuffer == nullptr) { delete[] data; return 0; }
    jobject globalBuffer = env->NewGlobalRef(localBuffer);
    auto* handle = new BufferSetHandle(data, (size_t)width, (size_t)height, totalSize, globalBuffer);
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        validHandles.insert(handle);
    }
    LOGI("BufferSet nativeSetup: %dx%d", width, height);
    return (jlong)handle;
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeRelease(
    JNIEnv* env, jobject thiz, jlong handlePtr) {
    auto* handle = reinterpret_cast<BufferSetHandle*>(handlePtr);
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        if (validHandles.find(handle) == validHandles.end()) return;
        validHandles.erase(handle);
    }
    if (handle != nullptr) {
        if (handle->globalBuffer != nullptr) env->DeleteGlobalRef(handle->globalBuffer);
        delete handle;
    }
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeResize(
    JNIEnv* env, jobject thiz, jlong handlePtr, jint width, jint height) {
    auto* handle = reinterpret_cast<BufferSetHandle*>(handlePtr);
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        if (validHandles.find(handle) == validHandles.end()) return;
    }
    size_t frameSize = (size_t)width * (size_t)height;
    size_t totalSize = frameSize + (frameSize / 2);
    uint8_t* newData = new uint8_t[totalSize];
    if (newData == nullptr) return;
    std::memset(newData, 0, frameSize);
    std::memset(newData + frameSize, 128, totalSize - frameSize);
    delete[] handle->data;
    handle->data = newData;
    handle->width = width;
    handle->height = height;
    handle->actualByteCount = totalSize;
    *(handle->yMat) = cv::Mat((int)height, (int)width, CV_8UC1, newData, (size_t)width);
    *(handle->uvMat) = cv::Mat((int)height / 2, (int)width / 2, CV_8UC2, newData + (width * height), (size_t)width);
    *(handle->nv21Mat) = cv::Mat((int)height * 3 / 2, (int)width, CV_8UC1, newData, (size_t)width);
    if (handle->globalBuffer != nullptr) env->DeleteGlobalRef(handle->globalBuffer);
    jobject localBuffer = env->NewDirectByteBuffer(newData, totalSize);
    handle->globalBuffer = env->NewGlobalRef(localBuffer);
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeClear(
    JNIEnv* env, jobject thiz, jlong handlePtr) {
    auto* handle = reinterpret_cast<BufferSetHandle*>(handlePtr);
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        if (validHandles.find(handle) == validHandles.end()) return;
    }
    if (handle != nullptr && handle->data != nullptr) {
        std::memset(handle->data, 0, handle->width * handle->height);
    }
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeClearChroma(
    JNIEnv* env, jobject thiz, jlong handlePtr) {
    auto* handle = reinterpret_cast<BufferSetHandle*>(handlePtr);
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        if (validHandles.find(handle) == validHandles.end()) return;
    }
    if (handle != nullptr && handle->data != nullptr) {
        size_t frameSize = handle->width * handle->height;
        std::memset(handle->data + frameSize, 128, handle->actualByteCount - frameSize);
    }
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

JNIEXPORT jlong JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeGetUVMatPtr(
    JNIEnv* env, jobject thiz, jlong handlePtr) {
    auto* handle = reinterpret_cast<BufferSetHandle*>(handlePtr);
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        if (validHandles.find(handle) == validHandles.end()) return 0;
    }
    return (jlong)handle->uvMat;
}

JNIEXPORT jlong JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeGetNv21MatPtr(
    JNIEnv* env, jobject thiz, jlong handlePtr) {
    auto* handle = reinterpret_cast<BufferSetHandle*>(handlePtr);
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        if (validHandles.find(handle) == validHandles.end()) return 0;
    }
    return (jlong)handle->nv21Mat;
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

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeDisarmMat(
    JNIEnv* env, jclass clazz, jobject matObj) {
    if (matObj == nullptr) return;
    jclass matClass = env->GetObjectClass(matObj);
    jfieldID nativeObjField = env->GetFieldID(matClass, "nativeObj", "J");
    if (nativeObjField != nullptr) env->SetLongField(matObj, nativeObjField, 0);
}

}
