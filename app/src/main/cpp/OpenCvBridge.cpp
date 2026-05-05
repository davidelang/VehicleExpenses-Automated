#include <jni.h>
#include <android/bitmap.h>
#include <opencv2/core.hpp>
#include <android/log.h>

#define LOG_TAG "OpenCvBridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_OpenCvBridge_lockBitmapToMat(
        JNIEnv* env, jobject thiz, jobject bitmap) {

    AndroidBitmapInfo info;
    void* pixels = nullptr;
    int ret;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
        return 0;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 &&
        info.format != ANDROID_BITMAP_FORMAT_A_8) {
        LOGE("Bitmap format is not RGBA_8888 or ALPHA_8 !");
        return 0;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
        return 0;
    }

    int cv_type = (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) ? CV_8UC4 : CV_8UC1;
    
    // Create a new cv::Mat header that wraps the bitmap pixels directly
    cv::Mat* mat = new cv::Mat(info.height, info.width, cv_type, pixels);

    return (jlong)mat;
}

JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_OpenCvBridge_unlockBitmap(
        JNIEnv* env, jobject thiz, jobject bitmap, jlong mat_ptr) {

    AndroidBitmap_unlockPixels(env, bitmap);

    cv::Mat* mat = (cv::Mat*)mat_ptr;
    if (mat) {
        delete mat;
    }
}

}
