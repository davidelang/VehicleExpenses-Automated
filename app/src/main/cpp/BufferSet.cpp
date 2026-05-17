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
    JNIEnv* env, jobject thiz, jlong handlePtr, jobject matObj) {
    
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

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeDisarmMat(
    JNIEnv* env, jclass clazz, jobject matObj) {
    
    if (matObj == nullptr) return;
    
    jclass matClass = env->GetObjectClass(matObj);
    jfieldID nativeObjField = env->GetFieldID(matClass, "nativeObj", "J");
    if (nativeObjField != nullptr) {
        env->SetLongField(matObj, nativeObjField, 0);
        LOGI("nativeDisarmMat: Mat successfully disarmed from GC");
    }
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeClear(
    JNIEnv* env, jobject thiz, jlong handlePtr) {
    
    auto* handle = reinterpret_cast<BufferSetHandle*>(handlePtr);
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        if (validHandles.find(handle) == validHandles.end()) {
            LOGE("nativeClear: Invalid handle pointer %p", handle);
            return;
        }
    }

    if (handle != nullptr && handle->data != nullptr) {
        size_t frameSize = handle->width * handle->height;
        std::memset(handle->data, 0, frameSize);
        std::memset(handle->data + frameSize, 128, handle->actualByteCount - frameSize);
        LOGI("nativeClear: Buffer cleared (W=%zu, H=%zu)", handle->width, handle->height);
    }
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

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeRotate(
    JNIEnv* env, jobject thiz, jlong srcPtr, jlong dstPtr, jfloat angle) {
    
    auto* srcHandle = reinterpret_cast<BufferSetHandle*>(srcPtr);
    auto* dstHandle = reinterpret_cast<BufferSetHandle*>(dstPtr);
    
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        if (validHandles.find(srcHandle) == validHandles.end() || 
            validHandles.find(dstHandle) == validHandles.end()) {
            LOGE("nativeRotate: Invalid handle pointer(s)");
            return;
        }
    }

    if (srcHandle != nullptr && dstHandle != nullptr) {
        cv::Point2f center(srcHandle->width / 2.0f, srcHandle->height / 2.0f);
        cv::Mat rotMat = cv::getRotationMatrix2D(center, (double)angle, 1.0);
        
        cv::warpAffine(*(srcHandle->yMat), *(dstHandle->yMat), rotMat, srcHandle->yMat->size(), 
                       cv::INTER_CUBIC, cv::BORDER_CONSTANT, cv::Scalar(0));
        
        LOGI("nativeRotate: Successfully rotated %.2f degrees (Cubic)", angle);
    }
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_BufferSet_nativeAnnotate(
    JNIEnv* env, jobject thiz, jlong handlePtr, jintArray annotationsArr) {
    
    auto* handle = reinterpret_cast<BufferSetHandle*>(handlePtr);
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        if (validHandles.find(handle) == validHandles.end()) return;
    }

    jsize len = env->GetArrayLength(annotationsArr);
    if (len % 7 != 0) return; // (x1, y1, x2, y2, shape, color, width)
    
    jint* elements = env->GetIntArrayElements(annotationsArr, nullptr);
    uint8_t* data = handle->data;
    int w = (int)handle->width;
    int h = (int)handle->height;
    size_t frameSize = handle->width * handle->height;

    for (int i = 0; i < len; i += 7) {
        int x1 = elements[i];
        int y1 = elements[i+1];
        int x2 = elements[i+2];
        int y2 = elements[i+3];
        int shape = elements[i+4];
        int color = elements[i+5];
        int stroke = elements[i+6];

        // Round to 2-pixel grid
        x1 = (x1 + 1) / 2 * 2; y1 = (y1 + 1) / 2 * 2;
        x2 = (x2 + 1) / 2 * 2; y2 = (y2 + 1) / 2 * 2;
        stroke = (stroke + 1) / 2 * 2;

        // Map ARGB color to YUV (Red/Orange/Blue simplified)
        uint8_t Y = 255, U = 128, V = 128;
        if ((color & 0x00FFFFFF) == 0xFF0000) { // Red
            Y = 76; U = 84; V = 255;
        } else if ((color & 0x00FFFFFF) == 0xFFA500) { // Orange
            Y = 173; U = 42; V = 191;
        } else if ((color & 0x00FFFFFF) == 0x0000FF) { // Blue
            Y = 29; U = 255; V = 107;
        }

        if (shape == 1) { // RECTANGLE
            // Implement a simple thick rectangle draw
            for (int s = 0; s < stroke; ++s) {
                // Top/Bottom
                for (int x = x1; x <= x2; ++x) {
                    if (x < 0 || x >= w) continue;
                    if (y1+s >= 0 && y1+s < h) data[(y1+s)*w + x] = Y;
                    if (y2-s >= 0 && y2-s < h) data[(y2-s)*w + x] = Y;
                }
                // Left/Right
                for (int y = y1; y <= y2; ++y) {
                    if (y < 0 || y >= h) continue;
                    if (x1+s >= 0 && x1+s < w) data[y*w + (x1+s)] = Y;
                    if (x2-s >= 0 && x2-s < w) data[y*w + (x2-s)] = Y;
                }
            }
            // Update UV for entire rect perimeter (simplified)
            for (int y = y1; y <= y2; y += 2) {
                for (int x = x1; x <= x2; x += 2) {
                    bool edge = (y < y1+stroke || y > y2-stroke || x < x1+stroke || x > x2-stroke);
                    if (!edge) continue;
                    if (x >= 0 && x < w && y >= 0 && y < h) {
                        size_t uvIdx = frameSize + (y/2)*w + (x/2)*2;
                        data[uvIdx] = V; data[uvIdx+1] = U;
                    }
                }
            }
        } else { // LINE
            // Simple horizontal/vertical line support for now
            if (y1 == y2) { // Horizontal
                for (int x = x1; x <= x2; ++x) {
                    for (int s = 0; s < stroke; ++s) {
                        if (x >= 0 && x < w && y1+s >= 0 && y1+s < h) {
                            data[(y1+s)*w + x] = Y;
                            size_t uvIdx = frameSize + ((y1+s)/2)*w + (x/2)*2;
                            data[uvIdx] = V; data[uvIdx+1] = U;
                        }
                    }
                }
            }
        }
    }

    env->ReleaseIntArrayElements(annotationsArr, elements, JNI_ABORT);
}

}
