#include "VeJni.h"

#include <mutex>
#include <string>

#include "paddle/paddle_api.h"

static std::recursive_mutex g_predictor_mu;

void veThrowIllegalState(JNIEnv* env, const char* what) {
    if (env->ExceptionCheck()) return;
    jclass ex = env->FindClass("java/lang/IllegalStateException");
    if (!ex) return;
    env->ThrowNew(ex, what);
}

void vePredictorGuardEnter() { g_predictor_mu.lock(); }

void vePredictorGuardExit() { g_predictor_mu.unlock(); }

std::shared_ptr<paddle::lite_api::PaddlePredictor> veLoadSharedRecognizerV3(JNIEnv* env) {
    if (env->ExceptionCheck()) return {};
    jclass eng = env->FindClass("com/davidlang/vehicleexpensesautomated/ui/util/NativePaddleEngine");
    if (env->ExceptionCheck()) return {};
    if (!eng) {
        veThrowIllegalState(env, "veLoadSharedRecognizerV3: NativePaddleEngine class missing");
        return {};
    }
    jfieldID fid = env->GetStaticFieldID(
        eng, "sharedRecognizerV3", "Lcom/baidu/paddle/lite/PaddlePredictor;");
    if (env->ExceptionCheck()) {
        env->DeleteLocalRef(eng);
        return {};
    }
    if (!fid) {
        env->DeleteLocalRef(eng);
        veThrowIllegalState(env, "veLoadSharedRecognizerV3: sharedRecognizerV3 missing");
        return {};
    }
    jobject predObj = env->GetStaticObjectField(eng, fid);
    env->DeleteLocalRef(eng);
    if (env->ExceptionCheck()) return {};
    if (!predObj) {
        veThrowIllegalState(env, "veLoadSharedRecognizerV3: sharedRecognizerV3 is null");
        return {};
    }
    jclass predCls = env->GetObjectClass(predObj);
    jfieldID ptrFid = env->GetFieldID(predCls, "cppPaddlePredictorPointer", "J");
    env->DeleteLocalRef(predCls);
    if (env->ExceptionCheck()) {
        env->DeleteLocalRef(predObj);
        return {};
    }
    if (!ptrFid) {
        env->DeleteLocalRef(predObj);
        veThrowIllegalState(env, "veLoadSharedRecognizerV3: cppPaddlePredictorPointer missing");
        return {};
    }
    std::lock_guard<std::recursive_mutex> lock(g_predictor_mu);
    const jlong raw = env->GetLongField(predObj, ptrFid);
    env->DeleteLocalRef(predObj);
    if (env->ExceptionCheck()) return {};
    if (raw == 0) {
        veThrowIllegalState(env, "veLoadSharedRecognizerV3: predictor pointer is 0");
        return {};
    }
    auto* sp = reinterpret_cast<std::shared_ptr<paddle::lite_api::PaddlePredictor>*>(raw);
    if (!sp || !*sp) {
        veThrowIllegalState(env, "veLoadSharedRecognizerV3: predictor empty");
        return {};
    }
    return *sp;
}

extern "C" JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativePredictorGuardEnter(
    JNIEnv*, jclass) {
    vePredictorGuardEnter();
}

extern "C" JNIEXPORT void JNICALL
Java_com_davidlang_vehicleexpensesautomated_ui_util_NativeImageUtils_nativePredictorGuardExit(
    JNIEnv*, jclass) {
    vePredictorGuardExit();
}
