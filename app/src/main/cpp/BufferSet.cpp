#include <jni.h>
#include <android/log.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <set>
#include <mutex>
#include <cstring>
#include "BufferSetHandle.h"

#define LOG_TAG "BufferSet"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

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
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeUpdateMatData(
    JNIEnv* env, jobject thiz, jlong matPtr, jlong parentMatPtr, jint byteOffset) {
    auto* mat = reinterpret_cast<cv::Mat*>(matPtr);
    auto* parentMat = reinterpret_cast<cv::Mat*>(parentMatPtr);
    if (mat != nullptr && parentMat != nullptr) {
        mat->data = parentMat->data + byteOffset;
    }
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeUpdateCropMat(
    JNIEnv* env, jobject thiz, jlong cropMatPtr, jlong parentMatPtr, jint x, jint y, jint w, jint h) {
    auto* cropMat = reinterpret_cast<cv::Mat*>(cropMatPtr);
    auto* parentMat = reinterpret_cast<cv::Mat*>(parentMatPtr);
    if (cropMat != nullptr && parentMat != nullptr) {
        *cropMat = (*parentMat)(cv::Rect(x, y, w, h));
    }
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeDisarmMat(
    JNIEnv* env, jclass clazz, jobject matObj) {
    if (matObj == nullptr) return;
    jclass matClass = env->GetObjectClass(matObj);
    jfieldID nativeObjField = env->GetFieldID(matClass, "nativeObj", "J");
    if (nativeObjField != nullptr) env->SetLongField(matObj, nativeObjField, 0);
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeNormalizeYUV(
    JNIEnv* env, jobject thiz, 
    jobject yBuf, jobject uBuf, jobject vBuf,
    jint yRStride, jint uRStride, jint vRStride,
    jint yPStride, jint uPStride, jint vPStride,
    jint w, jint h, jlong dstHandlePtr) {
    
    auto* dstHandle = reinterpret_cast<BufferSetHandle*>(dstHandlePtr);
    if (!dstHandle || dstHandle->width != (size_t)w || dstHandle->height != (size_t)h) return;

    uint8_t* yDataSrc = (uint8_t*)env->GetDirectBufferAddress(yBuf);
    uint8_t* uDataSrc = (uint8_t*)env->GetDirectBufferAddress(uBuf);
    uint8_t* vDataSrc = (uint8_t*)env->GetDirectBufferAddress(vBuf);

    if (!yDataSrc || !uDataSrc || !vDataSrc) return;

    uint8_t* dstData = dstHandle->data;
    size_t ySize = (size_t)w * h;
    
    // Copy Y Plane (Removing padding/stride gaps)
    for (int r = 0; r < h; ++r) {
        // Fast path for contiguous Y
        if (yRStride == w && yPStride == 1) {
            std::memcpy(dstData + (r * w), yDataSrc + (r * yRStride), w);
        } else {
            for (int c = 0; c < w; ++c) {
                dstData[(r * w) + c] = yDataSrc[(r * yRStride) + (c * yPStride)];
            }
        }
    }

    // Copy U/V Planes into NV21 (VUVU... interleaved)
    uint8_t* uvDst = dstData + ySize;
    int halfW = w / 2;
    int halfH = h / 2;
    
    // Fast path for NV21 -> NV21 copy
    if (uPStride == 2 && vPStride == 2 && (vDataSrc + 1) == uDataSrc && vRStride == w) {
        for (int r = 0; r < halfH; ++r) {
            std::memcpy(uvDst + (r * w), vDataSrc + (r * vRStride), w);
        }
    } else {
        // Universal Planar/Semi-Planar to Interleaved Converter
        for (int r = 0; r < halfH; ++r) {
            for (int c = 0; c < halfW; ++c) {
                uint8_t v = vDataSrc[(r * vRStride) + (c * vPStride)];
                uint8_t u = uDataSrc[(r * uRStride) + (c * uPStride)];
                uvDst[(r * w) + (c * 2)]     = v;
                uvDst[(r * w) + (c * 2) + 1] = u;
            }
        }
    }
}

}
