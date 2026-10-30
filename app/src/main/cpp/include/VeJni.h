#pragma once

#include <jni.h>

#include <memory>
#include <mutex>

namespace paddle {
namespace lite_api {
class PaddlePredictor;
}
}

void veThrowIllegalState(JNIEnv* env, const char* what);
void vePredictorGuardEnter();
void vePredictorGuardExit();

/** Copies sharedRecognizerV3 under the predictor guard. Does not clear a pending exception. */
std::shared_ptr<paddle::lite_api::PaddlePredictor> veLoadSharedRecognizerV3(JNIEnv* env);

struct VePredictorGuard {
    VePredictorGuard() { vePredictorGuardEnter(); }
    ~VePredictorGuard() { vePredictorGuardExit(); }
    VePredictorGuard(const VePredictorGuard&) = delete;
    VePredictorGuard& operator=(const VePredictorGuard&) = delete;
};
