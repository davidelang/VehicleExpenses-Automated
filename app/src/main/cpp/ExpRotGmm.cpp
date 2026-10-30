#include <jni.h>
#include <android/log.h>

#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/imgproc/imgproc_c.h>

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <limits>
#include <string>
#include <vector>

#include "paddle/paddle_api.h"

#include "CropPlan.h"
#include "VeJni.h"

#include <stdexcept>

namespace {

constexpr int kRecH = 48;
constexpr int kAllowedDecimal[] = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 93};


struct BoxUv {
    float cx, cy, ux, uy, vx, vy;
    float u0, u1, v0, v1;
    bool ok;
};

struct RecRow {
    std::string asis;
    std::string asisProbs;
    std::string digits;
    std::string digitsProbs;
    int recW = 0;
    int recH = 0;
    int recCropId = -1;
};





BoxUv boxFromQuad(const float* p) {
    BoxUv b{};
    b.ok = false;
    double bestAng = 1e9;
    double bestLen = 0.0;
    float ux = 1.f;
    float uy = 0.f;
    for (int i = 0; i < 4; ++i) {
        const int j = (i + 1) % 4;
        const double dx = static_cast<double>(p[j * 2] - p[i * 2]);
        const double dy = static_cast<double>(p[j * 2 + 1] - p[i * 2 + 1]);
        const double len = std::hypot(dx, dy);
        if (len < 1e-3) continue;
        double ang = std::fabs(std::atan2(dy, dx));
        if (ang > 3.141592653589793 / 2.0) ang = 3.141592653589793 - ang;
        const bool closer = ang < bestAng - 1e-6;
        const bool tieLonger = std::fabs(ang - bestAng) <= 1e-6 && len > bestLen;
        if (closer || tieLonger) {
            bestAng = ang;
            bestLen = len;
            ux = static_cast<float>(dx / len);
            uy = static_cast<float>(dy / len);
        }
    }
    if (bestLen < 2.0) return b;
    float vx = -uy;
    float vy = ux;
    if (vy < 0.f) {
        vx = -vx;
        vy = -vy;
    }
    float cx = 0.f;
    float cy = 0.f;
    for (int i = 0; i < 4; ++i) {
        cx += p[i * 2];
        cy += p[i * 2 + 1];
    }
    cx /= 4.f;
    cy /= 4.f;
    float u0 = std::numeric_limits<float>::infinity();
    float u1 = -std::numeric_limits<float>::infinity();
    float v0 = std::numeric_limits<float>::infinity();
    float v1 = -std::numeric_limits<float>::infinity();
    for (int i = 0; i < 4; ++i) {
        const float dx = p[i * 2] - cx;
        const float dy = p[i * 2 + 1] - cy;
        const float u = dx * ux + dy * uy;
        const float v = dx * vx + dy * vy;
        if (u < u0) u0 = u;
        if (u > u1) u1 = u;
        if (v < v0) v0 = v;
        if (v > v1) v1 = v;
    }
    if (u1 - u0 < 2.f || v1 - v0 < 2.f) return b;
    if (ux < 0.f) {
        ux = -ux;
        uy = -uy;
        const float t = u0;
        u0 = -u1;
        u1 = -t;
    }
    b.cx = cx;
    b.cy = cy;
    b.ux = ux;
    b.uy = uy;
    b.vx = vx;
    b.vy = vy;
    b.u0 = u0;
    b.u1 = u1;
    b.v0 = v0;
    b.v1 = v1;
    b.ok = true;
    return b;
}

float longAngleDeg(const BoxUv& b) {
    float a = static_cast<float>(std::atan2(static_cast<double>(b.uy), static_cast<double>(b.ux)) * (180.0 / 3.141592653589793));
    while (a > 90.f) a -= 180.f;
    while (a < -90.f) a += 180.f;
    return a;
}

void expandQuadFrame(const float* pts, float angleDeg, float minusU, float plusU, float minusV, float plusV, float* out) {
    if (minusU == 0.f && plusU == 0.f && minusV == 0.f && plusV == 0.f) {
        std::memcpy(out, pts, sizeof(float) * 8);
        return;
    }
    const double ang = static_cast<double>(angleDeg) * (3.141592653589793 / 180.0);
    const float ux = static_cast<float>(std::cos(ang));
    const float uy = static_cast<float>(std::sin(ang));
    float vx = -uy;
    float vy = ux;
    if (vy < 0.f) {
        vx = -vx;
        vy = -vy;
    }
    float us[4];
    float vs[4];
    for (int i = 0; i < 4; ++i) {
        us[i] = pts[i * 2] * ux + pts[i * 2 + 1] * uy;
        vs[i] = pts[i * 2] * vx + pts[i * 2 + 1] * vy;
    }
    float uMin = us[0];
    float uMax = us[0];
    float vMin = vs[0];
    float vMax = vs[0];
    for (int i = 1; i < 4; ++i) {
        if (us[i] < uMin) uMin = us[i];
        if (us[i] > uMax) uMax = us[i];
        if (vs[i] < vMin) vMin = vs[i];
        if (vs[i] > vMax) vMax = vs[i];
    }
    const float uMid = (uMin + uMax) * 0.5f;
    const float vMid = (vMin + vMax) * 0.5f;
    for (int i = 0; i < 4; ++i) {
        const float du = us[i] <= uMid ? -minusU : plusU;
        const float dv = vs[i] <= vMid ? -minusV : plusV;
        out[i * 2] = pts[i * 2] + du * ux + dv * vx;
        out[i * 2 + 1] = pts[i * 2 + 1] + du * uy + dv * vy;
    }
}

bool warpToStrip(const cv::Mat& gray, const float* q, cv::Mat& dest) {
    if (gray.empty() || dest.empty()) return false;
    const int outW = dest.cols;
    const int outH = dest.rows;
    const float x1 = std::max(0.f, static_cast<float>(outW - 1));
    const float y1 = std::max(0.f, static_cast<float>(outH - 1));
    CvMat srcMat = cvMat(gray);
    CvMat dstMat = cvMat(dest);
    double mdata[9];
    CvMat map = cvMat(3, 3, CV_64FC1, mdata);
    const CvPoint2D32f src[4] = {
        {q[0], q[1]}, {q[2], q[3]}, {q[4], q[5]}, {q[6], q[7]},
    };
    const CvPoint2D32f dst[4] = {{0.f, 0.f}, {x1, 0.f}, {x1, y1}, {0.f, y1}};
    cvGetPerspectiveTransform(src, dst, &map);
    cvWarpPerspective(&srcMat, &dstMat, &map, CV_INTER_CUBIC + CV_WARP_FILL_OUTLIERS, cvScalarAll(0));
    return !dest.empty() && dest.cols >= 1 && dest.rows >= 1;
}

void scaleArea(const cv::Mat& src, cv::Mat& dest, float s) {
    const int dw = dest.cols;
    const int dh = dest.rows;
    const int sw = src.cols;
    const int sh = src.rows;
    if (dw < 1 || dh < 1 || sw < 1 || sh < 1 || !(s > 0.f)) return;
    for (int y = 0; y < dh; ++y) {
        const float fy0 = static_cast<float>(y) / s;
        const float fy1 = static_cast<float>(y + 1) / s;
        int sy0 = static_cast<int>(std::floor(static_cast<double>(fy0)));
        int sy1e = static_cast<int>(std::ceil(static_cast<double>(fy1)));
        if (sy0 < 0) sy0 = 0;
        if (sy0 > sh - 1) sy0 = sh - 1;
        if (sy1e < 1) sy1e = 1;
        if (sy1e > sh) sy1e = sh;
        uint8_t* drow = dest.ptr<uint8_t>(y);
        for (int x = 0; x < dw; ++x) {
            const float fx0 = static_cast<float>(x) / s;
            const float fx1 = static_cast<float>(x + 1) / s;
            int sx0 = static_cast<int>(std::floor(static_cast<double>(fx0)));
            int sx1e = static_cast<int>(std::ceil(static_cast<double>(fx1)));
            if (sx0 < 0) sx0 = 0;
            if (sx0 > sw - 1) sx0 = sw - 1;
            if (sx1e < 1) sx1e = 1;
            if (sx1e > sw) sx1e = sw;
            double acc = 0.0;
            double wsum = 0.0;
            for (int sy = sy0; sy < sy1e; ++sy) {
                const float yA = std::max(fy0, static_cast<float>(sy));
                const float yB = std::min(fy1, static_cast<float>(sy + 1));
                const double wy = static_cast<double>(yB - yA);
                if (wy <= 0.0) continue;
                const uint8_t* row = src.ptr<uint8_t>(sy);
                for (int sx = sx0; sx < sx1e; ++sx) {
                    const float xA = std::max(fx0, static_cast<float>(sx));
                    const float xB = std::min(fx1, static_cast<float>(sx + 1));
                    const double wx = static_cast<double>(xB - xA);
                    if (wx <= 0.0) continue;
                    const double wt = wx * wy;
                    acc += static_cast<double>(row[sx]) * wt;
                    wsum += wt;
                }
            }
            int iv = 0;
            if (wsum > 0.0) {
                iv = static_cast<int>(acc / wsum + 0.5);
                if (iv < 0) iv = 0;
                if (iv > 255) iv = 255;
            }
            drow[x] = static_cast<uint8_t>(iv);
        }
    }
}

void appendProb(std::string* probs, const std::string& ch, float v) {
    if (!probs->empty()) probs->push_back(',');
    char buf[32];
    std::snprintf(buf, sizeof(buf), ":%.2f", v);
    probs->append(ch);
    probs->append(buf);
}

std::string decodeFull(const float* data, int seqLen, int dictSize, const std::vector<std::string>& dict, std::string* probs) {
    // dict = dictionaryV3, which already carries the appended use_space_char ' ' (classes = dict + 1).
    static std::atomic<bool> warnedClassCount{false};
    if (dictSize != static_cast<int>(dict.size()) + 1 && !warnedClassCount.exchange(true)) {
        __android_log_print(ANDROID_LOG_WARN, "ExpRotGmm", "rec class count %d != dict.size()+1 (%d); space class may be wrong",
                            dictSize, static_cast<int>(dict.size()) + 1);
    }
    std::string result;
    int lastIdx = -1;
    for (int i = 0; i < seqLen; ++i) {
        int maxIdx = 0;
        float maxVal = -1.f;
        const float* row = data + static_cast<size_t>(i) * static_cast<size_t>(dictSize);
        for (int j = 0; j < dictSize; ++j) {
            const float v = row[j];
            if (v > maxVal) {
                maxVal = v;
                maxIdx = j;
            }
        }
        if (maxIdx > 0 && maxIdx != lastIdx && maxIdx <= static_cast<int>(dict.size())) {
            const std::string& ch = dict[maxIdx - 1];
            result.append(ch);
            appendProb(probs, ch, maxVal);
        }
        lastIdx = maxIdx;
    }
    // Full text only: drop leading/trailing spaces from the model's space class.
    const size_t b = result.find_first_not_of(' ');
    if (b == std::string::npos) return std::string();
    return result.substr(b, result.find_last_not_of(' ') - b + 1);
}

bool isLandmarkDictChar(const std::string& ch) {
    if (ch.size() != 1) return false;
    const char c = ch[0];
    if (c == ' ') return true;
    if (c >= '0' && c <= '9') return true;
    if (c >= 'A' && c <= 'Z') return true;
    if (c >= 'a' && c <= 'z') return true;
    return false;
}

std::vector<int> landmarkAllowedClassIndices(const std::vector<std::string>& dict) {
    std::vector<int> allowed;
    allowed.reserve(dict.size());
    for (size_t i = 0; i < dict.size(); ++i) {
        if (isLandmarkDictChar(dict[i])) allowed.push_back(static_cast<int>(i) + 1);
    }
    return allowed;
}

std::string decodeLandmark(
    const float* data,
    int seqLen,
    int dictSize,
    const std::vector<std::string>& dict,
    const std::vector<int>& allowed,
    std::string* probs
) {
    std::string result;
    int lastIdx = -1;
    for (int i = 0; i < seqLen; ++i) {
        const float* row = data + static_cast<size_t>(i) * static_cast<size_t>(dictSize);
        int maxIdx = 0;
        float maxVal = row[0];
        for (int classIdx : allowed) {
            if (classIdx >= dictSize) continue;
            const float v = row[classIdx];
            if (v > maxVal) {
                maxVal = v;
                maxIdx = classIdx;
            }
        }
        if (maxIdx > 0 && maxIdx != lastIdx && maxIdx <= static_cast<int>(dict.size())) {
            const std::string& ch = dict[maxIdx - 1];
            result.append(ch);
            appendProb(probs, ch, maxVal);
        }
        lastIdx = maxIdx;
    }
    return result;
}

std::string decodeDecimal(const float* data, int seqLen, int dictSize, const std::vector<std::string>& dict, std::string* probs) {
    std::string result;
    int lastIdx = -1;
    float lastConf = 1.f;
    for (int i = 0; i < seqLen; ++i) {
        const float* row = data + static_cast<size_t>(i) * static_cast<size_t>(dictSize);
        int maxIdx = 0;
        float maxVal = row[0];
        for (int allowed : kAllowedDecimal) {
            if (allowed >= dictSize) continue;
            const float v = row[allowed];
            if (v > maxVal) {
                maxVal = v;
                maxIdx = allowed;
            }
        }
        if (maxIdx > 0 && maxIdx != lastIdx && maxIdx <= static_cast<int>(dict.size())) {
            const std::string& ch = dict[maxIdx - 1];
            const float ratioThr = 0.30f * lastConf;
            const bool isSafe = result.size() < 5;
            const bool pass = isSafe || maxVal >= ratioThr;
            __android_log_print(
                ANDROID_LOG_INFO, "PaddleOCR",
                "Decoded: '%s' Len: %d Conf: %.3f Thr: %.3f Safe: %s Pass: %s",
                ch.c_str(), static_cast<int>(result.size()), maxVal, ratioThr,
                isSafe ? "true" : "false", pass ? "true" : "false");
            if (pass) {
                result.append(ch);
                lastConf = maxVal;
                appendProb(probs, ch, maxVal);
            } else {
                __android_log_print(
                    ANDROID_LOG_WARN, "PaddleOCR",
                    "Pruning: Confidence drop too high for '%s' (Ratio: %.3f < %.3f)",
                    ch.c_str(), maxVal, ratioThr);
                break;
            }
        }
        lastIdx = maxIdx;
    }
    return result;
}

bool checkExc(JNIEnv* env) {
    return env->ExceptionCheck();
}

std::shared_ptr<paddle::lite_api::PaddlePredictor> loadRecognizer(JNIEnv* env) {
    return veLoadSharedRecognizerV3(env);
}

std::vector<std::string> loadDictionary(JNIEnv* env) {
    std::vector<std::string> dict;
    jclass eng = env->FindClass("com/davidlang/vehicleexpensesautomated/ui/util/NativePaddleEngine");
    if (!eng || checkExc(env)) return dict;
    jfieldID fid = env->GetStaticFieldID(eng, "dictionaryV3", "Ljava/util/List;");
    if (!fid || checkExc(env)) {
        env->DeleteLocalRef(eng);
        return dict;
    }
    jobject list = env->GetStaticObjectField(eng, fid);
    env->DeleteLocalRef(eng);
    if (checkExc(env) || !list) return dict;
    jclass listCls = env->GetObjectClass(list);
    jmethodID sizeM = env->GetMethodID(listCls, "size", "()I");
    jmethodID getM = env->GetMethodID(listCls, "get", "(I)Ljava/lang/Object;");
    env->DeleteLocalRef(listCls);
    if (!sizeM || !getM || checkExc(env)) {
        env->DeleteLocalRef(list);
        return dict;
    }
    const jint n = env->CallIntMethod(list, sizeM);
    if (checkExc(env)) {
        env->DeleteLocalRef(list);
        return dict;
    }
    dict.reserve(n > 0 ? static_cast<size_t>(n) : 0);
    for (jint i = 0; i < n; ++i) {
        jobject item = env->CallObjectMethod(list, getM, i);
        if (checkExc(env) || !item) {
            dict.emplace_back();
            continue;
        }
        const char* utf = env->GetStringUTFChars(static_cast<jstring>(item), nullptr);
        dict.emplace_back(utf ? utf : "");
        if (utf) env->ReleaseStringUTFChars(static_cast<jstring>(item), utf);
        env->DeleteLocalRef(item);
    }
    env->DeleteLocalRef(list);
    return dict;
}

bool bufferPrimarySize(JNIEnv* env, jobject recBuffer, int* w, int* h) {
    jclass bufCls = env->GetObjectClass(recBuffer);
    jmethodID getP = env->GetMethodID(bufCls, "getP", "()Lcom/davidlang/vehicleexpensesautomated/ui/util/BufferSet$Slice;");
    env->DeleteLocalRef(bufCls);
    if (!getP || checkExc(env)) return false;
    jobject slice = env->CallObjectMethod(recBuffer, getP);
    if (checkExc(env) || !slice) return false;
    jclass sliceCls = env->GetObjectClass(slice);
    jmethodID getW = env->GetMethodID(sliceCls, "getWidth", "()I");
    jmethodID getH = env->GetMethodID(sliceCls, "getHeight", "()I");
    env->DeleteLocalRef(sliceCls);
    if (!getW || !getH || checkExc(env)) {
        env->DeleteLocalRef(slice);
        return false;
    }
    *w = env->CallIntMethod(slice, getW);
    *h = env->CallIntMethod(slice, getH);
    env->DeleteLocalRef(slice);
    return !checkExc(env);
}

void clearPrimary(JNIEnv* env, jobject recBuffer) {
    jclass bufCls = env->GetObjectClass(recBuffer);
    jmethodID getP = env->GetMethodID(bufCls, "getP", "()Lcom/davidlang/vehicleexpensesautomated/ui/util/BufferSet$Slice;");
    env->DeleteLocalRef(bufCls);
    if (!getP || checkExc(env)) return;
    jobject slice = env->CallObjectMethod(recBuffer, getP);
    if (checkExc(env) || !slice) return;
    jclass sliceCls = env->GetObjectClass(slice);
    jmethodID clear = env->GetMethodID(sliceCls, "clear", "()V");
    env->DeleteLocalRef(sliceCls);
    if (clear && !checkExc(env)) env->CallVoidMethod(slice, clear);
    checkExc(env);
    env->DeleteLocalRef(slice);
}

int createRecCrop(JNIEnv* env, jobject recBuffer, int x, int y, int w, int h) {
    jclass bufCls = env->GetObjectClass(recBuffer);
    jmethodID create = env->GetMethodID(bufCls, "createCrop", "(IIIILjava/lang/Integer;)I");
    env->DeleteLocalRef(bufCls);
    if (!create || checkExc(env)) return -1;
    const jint id = env->CallIntMethod(recBuffer, create, x, y, w, h, nullptr);
    if (checkExc(env)) return -1;
    return id;
}

bool cropMat(JNIEnv* env, jobject recBuffer, int id, cv::Mat* out, int* w, int* h) {
    jclass bufCls = env->GetObjectClass(recBuffer);
    jmethodID getC = env->GetMethodID(bufCls, "getC", "()Lcom/davidlang/vehicleexpensesautomated/ui/util/BufferSet$CropRegistry;");
    env->DeleteLocalRef(bufCls);
    if (!getC || checkExc(env)) return false;
    jobject reg = env->CallObjectMethod(recBuffer, getC);
    if (checkExc(env) || !reg) return false;
    jclass regCls = env->GetObjectClass(reg);
    jmethodID get = env->GetMethodID(regCls, "get", "(I)Lcom/davidlang/vehicleexpensesautomated/ui/util/BufferSet$Slice;");
    env->DeleteLocalRef(regCls);
    if (!get || checkExc(env)) {
        env->DeleteLocalRef(reg);
        return false;
    }
    jobject slice = env->CallObjectMethod(reg, get, id);
    env->DeleteLocalRef(reg);
    if (checkExc(env) || !slice) return false;
    jclass sliceCls = env->GetObjectClass(slice);
    jmethodID getMat = env->GetMethodID(sliceCls, "getMat", "()Lorg/opencv/core/Mat;");
    jmethodID getW = env->GetMethodID(sliceCls, "getWidth", "()I");
    jmethodID getH = env->GetMethodID(sliceCls, "getHeight", "()I");
    env->DeleteLocalRef(sliceCls);
    if (!getMat || !getW || !getH || checkExc(env)) {
        env->DeleteLocalRef(slice);
        return false;
    }
    jobject matObj = env->CallObjectMethod(slice, getMat);
    *w = env->CallIntMethod(slice, getW);
    *h = env->CallIntMethod(slice, getH);
    env->DeleteLocalRef(slice);
    if (checkExc(env) || !matObj) return false;
    jclass matCls = env->GetObjectClass(matObj);
    jfieldID nativeObj = env->GetFieldID(matCls, "nativeObj", "J");
    env->DeleteLocalRef(matCls);
    if (!nativeObj || checkExc(env)) {
        env->DeleteLocalRef(matObj);
        return false;
    }
    const jlong ptr = env->GetLongField(matObj, nativeObj);
    env->DeleteLocalRef(matObj);
    if (ptr == 0) return false;
    *out = *reinterpret_cast<cv::Mat*>(ptr);
    return !out->empty();
}

void bothDecodes(
    paddle::lite_api::PaddlePredictor& pred,
    const cv::Mat& mat,
    const std::vector<std::string>& dict,
    RecRow* row,
    bool landmarkAsis
) {
    if (mat.cols < 1 || mat.rows != kRecH || mat.type() != CV_8UC1) {
        throw std::runtime_error("bothDecodes: bad crop");
    }
    try {
        std::unique_ptr<paddle::lite_api::Tensor> input = pred.GetInput(0);
        if (!input) throw std::runtime_error("bothDecodes: no input tensor");
        input->Resize({1, 1, static_cast<int64_t>(mat.rows), static_cast<int64_t>(mat.cols)});
        int8_t* dst = input->mutable_data<int8_t>();
        if (!dst) throw std::runtime_error("bothDecodes: no input data");
        for (int y = 0; y < mat.rows; ++y) {
            std::memcpy(
                dst + static_cast<size_t>(y) * static_cast<size_t>(mat.cols),
                mat.ptr<uint8_t>(y),
                static_cast<size_t>(mat.cols));
        }
        pred.Run();
        std::unique_ptr<const paddle::lite_api::Tensor> output = pred.GetOutput(0);
        if (!output) throw std::runtime_error("bothDecodes: no output tensor");
        const float* data = output->data<float>();
        const paddle::lite_api::shape_t shape = output->shape();
        if (!data || shape.size() < 3 || shape[1] < 1 || shape[2] < 1) {
            throw std::runtime_error("bothDecodes: bad output");
        }
        const int seqLen = static_cast<int>(shape[1]);
        const int dictSize = static_cast<int>(shape[2]);
        if (landmarkAsis) {
            const std::vector<int> allowed = landmarkAllowedClassIndices(dict);
            row->asis = decodeLandmark(data, seqLen, dictSize, dict, allowed, &row->asisProbs);
        } else {
            row->asis = decodeFull(data, seqLen, dictSize, dict, &row->asisProbs);
        }
        row->digits = decodeDecimal(data, seqLen, dictSize, dict, &row->digitsProbs);
    } catch (const std::runtime_error&) {
        throw;
    } catch (const std::exception& ex) {
        throw std::runtime_error(std::string("bothDecodes: ") + ex.what());
    }
}

void recognizeOne(
    JNIEnv* env,
    const cv::Mat& gray,
    const float* quad,
    jobject recBuffer,
    int scratchW,
    int scratchH,
    int bufW,
    int bufH,
    int rowIndex,
    paddle::lite_api::PaddlePredictor* pred,
    const std::vector<std::string>& dict,
    RecRow* row,
    bool landmarkAsis
) {
    (void)scratchW;
    (void)scratchH;
    const BoxUv box = boxFromQuad(quad);
    if (!box.ok) throw std::runtime_error("recognizeOne: bad quad");
    const float bw = std::max(1.f, box.u1 - box.u0);
    const float bh = std::max(1.f, box.v1 - box.v0);
    if (bw < 2.f || bh < 2.f) throw std::runtime_error("recognizeOne: crop too small");
    const Plan40 plan = planContent40(bw, bh, kRecH);
    const int srcW = plan.srcW;
    const int srcH = plan.srcH;
    float expanded[8];
    const float plusU = static_cast<float>(srcW) - plan.marginSrc - bw;
    const float plusV = static_cast<float>(srcH) - plan.marginSrc - bh;
    expandQuadFrame(quad, longAngleDeg(box), plan.marginSrc, plusU, plan.marginSrc, plusV, expanded);
    cv::Mat strip(srcH, srcW, CV_8UC1);
    bool warped = false;
    try {
        warped = warpToStrip(gray, expanded, strip);
    } catch (const cv::Exception& ex) {
        __android_log_print(ANDROID_LOG_ERROR, "ExpRotGmm", "warp failed: %s", ex.what());
        throw std::runtime_error(std::string("recognizeOne: warp failed: ") + ex.what());
    }
    if (!warped) throw std::runtime_error("recognizeOne: warp failed");
    const int y = rowIndex * kRecH;
    if (y < 0) throw std::runtime_error("recognizeOne: bad row");
    if (plan.destW > bufW || y + plan.destH > bufH) {
        jclass cls = env->GetObjectClass(recBuffer);
        jmethodID resize = env->GetMethodID(cls, "resize", "(II)V");
        env->DeleteLocalRef(cls);
        if (!resize || env->ExceptionCheck()) throw std::runtime_error("recognizeOne: resize missing");
        const int nw = std::max(bufW, plan.destW);
        const int nh = std::max(bufH, y + plan.destH);
        env->CallVoidMethod(recBuffer, resize, nw, nh);
        if (env->ExceptionCheck()) throw std::runtime_error("recognizeOne: resize failed");
        bufW = nw;
        bufH = nh;
    }
    const int id = createRecCrop(env, recBuffer, 0, y, plan.destW, plan.destH);
    if (id < 0) throw std::runtime_error("recognizeOne: create crop failed");
    cv::Mat dest;
    int dw = 0;
    int dh = 0;
    if (!cropMat(env, recBuffer, id, &dest, &dw, &dh)) {
        throw std::runtime_error("recognizeOne: crop mat missing");
    }
    row->recCropId = id;
    row->recW = dw;
    row->recH = dh;
    scaleArea(strip, dest, plan.s);
    if (!pred) throw std::runtime_error("recognizeOne: no predictor");
    bothDecodes(*pred, dest, dict, row, landmarkAsis);
}

jstring toJString(JNIEnv* env, const std::string& s) {
    if (env->ExceptionCheck()) return nullptr;
    jstring js = env->NewStringUTF(s.c_str());
    if (!js || env->ExceptionCheck()) {
        veThrowIllegalState(env, "toJString: NewStringUTF failed");
        return nullptr;
    }
    return js;
}

jobject newOut(JNIEnv* env, const std::vector<RecRow>& rows, const char* outClass) {
    const jsize n = static_cast<jsize>(rows.size());
    jclass stringCls = env->FindClass("java/lang/String");
    jobjectArray asis = env->NewObjectArray(n, stringCls, nullptr);
    jobjectArray asisProbs = env->NewObjectArray(n, stringCls, nullptr);
    jobjectArray digits = env->NewObjectArray(n, stringCls, nullptr);
    jobjectArray digitsProbs = env->NewObjectArray(n, stringCls, nullptr);
    jintArray recW = env->NewIntArray(n);
    jintArray recH = env->NewIntArray(n);
    jintArray recId = env->NewIntArray(n);
    std::vector<jint> w(rows.size());
    std::vector<jint> h(rows.size());
    std::vector<jint> id(rows.size());
    for (jsize i = 0; i < n; ++i) {
        jstring a = toJString(env, rows[i].asis);
        jstring ap = toJString(env, rows[i].asisProbs);
        jstring d = toJString(env, rows[i].digits);
        jstring dp = toJString(env, rows[i].digitsProbs);
        env->SetObjectArrayElement(asis, i, a);
        env->SetObjectArrayElement(asisProbs, i, ap);
        env->SetObjectArrayElement(digits, i, d);
        env->SetObjectArrayElement(digitsProbs, i, dp);
        env->DeleteLocalRef(a);
        env->DeleteLocalRef(ap);
        env->DeleteLocalRef(d);
        env->DeleteLocalRef(dp);
        w[i] = rows[i].recW;
        h[i] = rows[i].recH;
        id[i] = rows[i].recCropId;
    }
    if (n > 0) {
        env->SetIntArrayRegion(recW, 0, n, w.data());
        env->SetIntArrayRegion(recH, 0, n, h.data());
        env->SetIntArrayRegion(recId, 0, n, id.data());
    }
    env->DeleteLocalRef(stringCls);
    jclass outCls = env->FindClass(outClass);
    if (!outCls || checkExc(env)) return nullptr;
    jmethodID ctor = env->GetMethodID(
        outCls, "<init>",
        "([Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[I[I[I)V");
    if (!ctor || checkExc(env)) {
        env->DeleteLocalRef(outCls);
        return nullptr;
    }
    jobject out = env->NewObject(outCls, ctor, asis, asisProbs, digits, digitsProbs, recW, recH, recId);
    env->DeleteLocalRef(outCls);
    env->DeleteLocalRef(asis);
    env->DeleteLocalRef(asisProbs);
    env->DeleteLocalRef(digits);
    env->DeleteLocalRef(digitsProbs);
    env->DeleteLocalRef(recW);
    env->DeleteLocalRef(recH);
    env->DeleteLocalRef(recId);
    return out;
}

jobject runRecognize(
    JNIEnv* env,
    jlong grayPtr,
    jfloatArray quadPts,
    jobject recBuffer,
    jint scratchW,
    jint scratchH,
    jboolean landmarkAlphabet,
    const char* outClass
) {
    const bool landmarkAsis = landmarkAlphabet == JNI_TRUE;
    const jsize len = quadPts ? env->GetArrayLength(quadPts) : 0;
    const int n = len >= 8 ? static_cast<int>(len / 8) : 0;
    std::vector<RecRow> rows(static_cast<size_t>(n));
    if (n == 0 || !recBuffer) return newOut(env, rows, outClass);
    std::vector<float> pts(static_cast<size_t>(n) * 8);
    env->GetFloatArrayRegion(quadPts, 0, n * 8, pts.data());
    if (checkExc(env)) return newOut(env, rows, outClass);
    int bufW = 0;
    int bufH = 0;
    if (!bufferPrimarySize(env, recBuffer, &bufW, &bufH)) return newOut(env, rows, outClass);
    clearPrimary(env, recBuffer);
    std::shared_ptr<paddle::lite_api::PaddlePredictor> pred = loadRecognizer(env);
    const std::vector<std::string> dict = loadDictionary(env);
    cv::Mat gray;
    if (grayPtr != 0) gray = *reinterpret_cast<cv::Mat*>(grayPtr);
    for (int i = 0; i < n; ++i) {
        if (env->PushLocalFrame(32) < 0) {
            checkExc(env);
            break;
        }
        recognizeOne(
            env, gray, pts.data() + static_cast<size_t>(i) * 8, recBuffer,
            scratchW, scratchH, bufW, bufH, i, pred.get(), dict, &rows[i], landmarkAsis);
        env->PopLocalFrame(nullptr);
    }
    return newOut(env, rows, outClass);
}

}  // namespace

#if VE_EMBED_EXP_DET
extern "C" JNIEXPORT jobject JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_experiment_ExperimentPumpScreenKt_expRotGmmRecognize(
    JNIEnv* env,
    jclass,
    jlong grayPtr,
    jfloatArray quadPts,
    jobject recBuffer,
    jint scratchW,
    jint scratchH,
    jboolean landmarkAlphabet
) {
    VePredictorGuard guard;
    try {
    return runRecognize(
        env, grayPtr, quadPts, recBuffer, scratchW, scratchH, landmarkAlphabet,
        "com/davidlang/vehicleexpensesautomated/ui/experiment/ExpRotGmmOut");
    } catch (const std::exception& ex) {
        veThrowIllegalState(env, ex.what());
        return nullptr;
    }
}
#endif

extern "C" JNIEXPORT jobject JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_ocr_OcrRotGmmKt_ocrRotGmmRecognize(
    JNIEnv* env,
    jclass,
    jlong grayPtr,
    jfloatArray quadPts,
    jobject recBuffer,
    jint scratchW,
    jint scratchH,
    jboolean landmarkAlphabet
) {
    VePredictorGuard guard;
    try {
    return runRecognize(
        env, grayPtr, quadPts, recBuffer, scratchW, scratchH, landmarkAlphabet,
        "com/davidlang/vehicleexpensesautomated/ui/ocr/OcrRotGmmOut");
    } catch (const std::exception& ex) {
        veThrowIllegalState(env, ex.what());
        return nullptr;
    }
}
