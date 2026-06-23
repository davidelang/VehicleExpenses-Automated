#ifndef BUFFER_SET_HANDLE_H
#define BUFFER_SET_HANDLE_H

#include <jni.h>
#include <opencv2/core.hpp>

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
    bool isBorrowed;

    BufferSetHandle(uint8_t* p, size_t w, size_t h, size_t total, size_t allocated, jobject buf) 
        : data(p), width(w), height(h), actualByteCount(total), allocatedByteCount(allocated), globalBuffer(buf), isBorrowed(false) {
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

#endif // BUFFER_SET_HANDLE_H
