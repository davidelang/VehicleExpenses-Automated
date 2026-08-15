/* Thin JNI wrapper over product libpaddle_light_api_shared.so.
 * Catches std::exception from Run/Create so the app process does not SIGABRT
 * when Lite LOG(FATAL)/CHECK throws bare std::exception (tiny-publish + exception).
 *
 * Does NOT catch SIGSEGV (e.g. server det @ ≥704 on arm64).
 */
#include <jni.h>

#include <cstdint>
#include <cstring>
#include <exception>
#include <memory>
#include <string>
#include <typeinfo>
#include <vector>

#include "paddle_api.h"

using paddle::lite_api::CreatePaddlePredictor;
using paddle::lite_api::MobileConfig;
using paddle::lite_api::PaddlePredictor;
using paddle::lite_api::PowerMode;
using paddle::lite_api::Tensor;

namespace {

void throw_java(JNIEnv *env, const char *where, const std::exception *e,
                const char *type_name) {
  if (!env || env->ExceptionCheck()) return;
  jclass cls = env->FindClass("java/lang/RuntimeException");
  if (!cls) return;
  std::string msg = std::string("paddle_lite_jni ") + where + ": ";
  if (type_name && type_name[0]) {
    msg += type_name;
    msg += ": ";
  }
  msg += (e && e->what() && e->what()[0]) ? e->what() : "(no what())";
  env->ThrowNew(cls, msg.c_str());
  env->DeleteLocalRef(cls);
}

template <typename Fn>
auto jni_try(JNIEnv *env, const char *where, Fn &&fn) -> decltype(fn()) {
  using R = decltype(fn());
  try {
    return fn();
  } catch (const std::exception &e) {
    throw_java(env, where, &e, typeid(e).name());
  } catch (...) {
    throw_java(env, where, nullptr, "unknown");
  }
  return R{};
}

std::string jstr(JNIEnv *env, jstring js) {
  if (!js) return {};
  const char *c = env->GetStringUTFChars(js, nullptr);
  std::string s = c ? c : "";
  if (c) env->ReleaseStringUTFChars(js, c);
  return s;
}

std::shared_ptr<PaddlePredictor> *pred_ptr(JNIEnv *env, jobject obj) {
  jclass c = env->GetObjectClass(obj);
  jfieldID f = env->GetFieldID(c, "cppPaddlePredictorPointer", "J");
  jlong p = env->GetLongField(obj, f);
  env->DeleteLocalRef(c);
  return reinterpret_cast<std::shared_ptr<PaddlePredictor> *>(p);
}

std::unique_ptr<Tensor> *w_tensor(JNIEnv *env, jobject obj) {
  jclass c = env->GetObjectClass(obj);
  jfieldID f = env->GetFieldID(c, "cppTensorPointer", "J");
  jlong p = env->GetLongField(obj, f);
  env->DeleteLocalRef(c);
  return reinterpret_cast<std::unique_ptr<Tensor> *>(p);
}

std::unique_ptr<const Tensor> *r_tensor(JNIEnv *env, jobject obj) {
  jclass c = env->GetObjectClass(obj);
  jfieldID f = env->GetFieldID(c, "cppTensorPointer", "J");
  jlong p = env->GetLongField(obj, f);
  env->DeleteLocalRef(c);
  return reinterpret_cast<std::unique_ptr<const Tensor> *>(p);
}

bool read_only(JNIEnv *env, jobject obj) {
  jclass c = env->GetObjectClass(obj);
  jfieldID f = env->GetFieldID(c, "readOnly", "Z");
  jboolean v = env->GetBooleanField(obj, f);
  env->DeleteLocalRef(c);
  return v;
}

int64_t product(const std::vector<int64_t> &d) {
  int64_t p = 1;
  for (auto x : d) p *= x;
  return p;
}

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_baidu_paddle_lite_PaddlePredictor_run(JNIEnv *env, jobject thiz) {
  return jni_try(env, "PaddlePredictor.run", [&]() -> jboolean {
    auto *p = pred_ptr(env, thiz);
    if (!p || !*p) return JNI_FALSE;
    (*p)->Run();
    return JNI_TRUE;
  });
}

JNIEXPORT jstring JNICALL
Java_com_baidu_paddle_lite_PaddlePredictor_getVersion(JNIEnv *env,
                                                      jobject thiz) {
  return jni_try(env, "getVersion", [&]() -> jstring {
    auto *p = pred_ptr(env, thiz);
    std::string v = (p && *p) ? (*p)->GetVersion() : "";
    return env->NewStringUTF(v.c_str());
  });
}

JNIEXPORT jboolean JNICALL
Java_com_baidu_paddle_lite_PaddlePredictor_saveOptimizedModel(JNIEnv *env,
                                                              jobject thiz,
                                                              jstring dir) {
  return jni_try(env, "saveOptimizedModel", [&]() -> jboolean {
    auto *p = pred_ptr(env, thiz);
    if (!p || !*p) return JNI_FALSE;
    (*p)->SaveOptimizedModel(jstr(env, dir));
    return JNI_TRUE;
  });
}

JNIEXPORT jlong JNICALL
Java_com_baidu_paddle_lite_PaddlePredictor_getInputCppTensorPointer(
    JNIEnv *env, jobject thiz, jint offset) {
  return jni_try(env, "getInput", [&]() -> jlong {
    auto *p = pred_ptr(env, thiz);
    if (!p || !*p) return 0;
    auto t = (*p)->GetInput(static_cast<int>(offset));
    return reinterpret_cast<jlong>(new std::unique_ptr<Tensor>(std::move(t)));
  });
}

JNIEXPORT jlong JNICALL
Java_com_baidu_paddle_lite_PaddlePredictor_getOutputCppTensorPointer(
    JNIEnv *env, jobject thiz, jint offset) {
  return jni_try(env, "getOutput", [&]() -> jlong {
    auto *p = pred_ptr(env, thiz);
    if (!p || !*p) return 0;
    auto t = (*p)->GetOutput(static_cast<int>(offset));
    return reinterpret_cast<jlong>(
        new std::unique_ptr<const Tensor>(std::move(t)));
  });
}

JNIEXPORT jlong JNICALL
Java_com_baidu_paddle_lite_PaddlePredictor_getCppTensorPointerByName(
    JNIEnv *env, jobject thiz, jstring name) {
  return jni_try(env, "getTensor", [&]() -> jlong {
    auto *p = pred_ptr(env, thiz);
    if (!p || !*p) return 0;
    auto t = (*p)->GetTensor(jstr(env, name));
    return reinterpret_cast<jlong>(
        new std::unique_ptr<const Tensor>(std::move(t)));
  });
}

JNIEXPORT jlong JNICALL
Java_com_baidu_paddle_lite_PaddlePredictor_newCppPaddlePredictor__Lcom_baidu_paddle_lite_CxxConfig_2(
    JNIEnv *, jobject, jobject) {
  return 0;  // tiny publish: CxxConfig unsupported
}

JNIEXPORT jlong JNICALL
Java_com_baidu_paddle_lite_PaddlePredictor_newCppPaddlePredictor__Lcom_baidu_paddle_lite_MobileConfig_2(
    JNIEnv *env, jobject /*thiz*/, jobject jcfg) {
  return jni_try(env, "createMobile", [&]() -> jlong {
    jclass c = env->GetObjectClass(jcfg);
    MobileConfig cfg;
    auto call_str = [&](const char *m) -> std::string {
      jmethodID id =
          env->GetMethodID(c, m, "()Ljava/lang/String;");
      if (!id) {
        env->ExceptionClear();
        return {};
      }
      jstring js = (jstring)env->CallObjectMethod(jcfg, id);
      return jstr(env, js);
    };
    std::string model_file = call_str("getModelFromFile");
    if (!model_file.empty()) cfg.set_model_from_file(model_file);
    std::string model_dir = call_str("getModelDir");
    if (!model_dir.empty()) cfg.set_model_dir(model_dir);
    std::string model_buf = call_str("getModelFromBuffer");
    if (!model_buf.empty()) cfg.set_model_from_buffer(model_buf);

    jmethodID thr = env->GetMethodID(c, "getThreads", "()I");
    int nthr = 1;
    if (thr) nthr = env->CallIntMethod(jcfg, thr);
    if (nthr < 1) nthr = 1;
    cfg.set_threads(nthr);
#if defined(LITE_WITH_X86) || defined(__x86_64__) || defined(__i386__)
    // Emulator x86_64: OpenBLAS/MKL thread count comes from this, not set_threads.
    // Arm product light SO does not export this symbol — never call it on arm.
    cfg.set_x86_math_num_threads(nthr);
#endif
    jmethodID pm = env->GetMethodID(c, "getPowerModeInt", "()I");
    if (pm)
      cfg.set_power_mode(
          static_cast<PowerMode>(env->CallIntMethod(jcfg, pm)));
    env->DeleteLocalRef(c);

    auto pred = CreatePaddlePredictor<MobileConfig>(cfg);
    if (!pred) return 0;
    return reinterpret_cast<jlong>(new std::shared_ptr<PaddlePredictor>(pred));
  });
}

JNIEXPORT jboolean JNICALL
Java_com_baidu_paddle_lite_PaddlePredictor_deleteCppPaddlePredictor(
    JNIEnv *env, jobject, jlong ptr) {
  return jni_try(env, "delete", [&]() -> jboolean {
    if (!ptr) return JNI_FALSE;
    auto *p = reinterpret_cast<std::shared_ptr<PaddlePredictor> *>(ptr);
    p->reset();
    delete p;
    return JNI_TRUE;
  });
}

// --- Tensor ---

JNIEXPORT jboolean JNICALL Java_com_baidu_paddle_lite_Tensor_nativeResize(
    JNIEnv *env, jobject thiz, jlongArray dims) {
  return jni_try(env, "Tensor.resize", [&]() -> jboolean {
    auto *t = w_tensor(env, thiz);
    if (!t || !*t) return JNI_FALSE;
    jsize n = env->GetArrayLength(dims);
    std::vector<int64_t> shape(n);
    env->GetLongArrayRegion(dims, 0, n, reinterpret_cast<jlong *>(shape.data()));
    (*t)->Resize(shape);
    return JNI_TRUE;
  });
}

JNIEXPORT jlongArray JNICALL Java_com_baidu_paddle_lite_Tensor_shape(
    JNIEnv *env, jobject thiz) {
  return jni_try(env, "Tensor.shape", [&]() -> jlongArray {
    std::vector<int64_t> shape;
    if (read_only(env, thiz)) {
      auto *t = r_tensor(env, thiz);
      if (!t || !*t) return nullptr;
      shape = (*t)->shape();
    } else {
      auto *t = w_tensor(env, thiz);
      if (!t || !*t) return nullptr;
      shape = (*t)->shape();
    }
    jlongArray arr = env->NewLongArray(static_cast<jsize>(shape.size()));
    env->SetLongArrayRegion(arr, 0, static_cast<jsize>(shape.size()),
                            reinterpret_cast<const jlong *>(shape.data()));
    return arr;
  });
}

JNIEXPORT jboolean JNICALL Java_com_baidu_paddle_lite_Tensor_nativeSetData___3F(
    JNIEnv *env, jobject thiz, jfloatArray buf) {
  return jni_try(env, "Tensor.setFloat", [&]() -> jboolean {
    auto *t = w_tensor(env, thiz);
    if (!t || !*t) return JNI_FALSE;
    int64_t n = env->GetArrayLength(buf);
    if (n != product((*t)->shape())) return JNI_FALSE;
    float *dst = (*t)->mutable_data<float>();
    env->GetFloatArrayRegion(buf, 0, n, dst);
    return JNI_TRUE;
  });
}

JNIEXPORT jboolean JNICALL Java_com_baidu_paddle_lite_Tensor_nativeSetData___3B(
    JNIEnv *env, jobject thiz, jbyteArray buf) {
  return jni_try(env, "Tensor.setByte", [&]() -> jboolean {
    auto *t = w_tensor(env, thiz);
    if (!t || !*t) return JNI_FALSE;
    int64_t n = env->GetArrayLength(buf);
    if (n != product((*t)->shape())) return JNI_FALSE;
    int8_t *dst = (*t)->mutable_data<int8_t>();
    env->GetByteArrayRegion(buf, 0, n, dst);
    return JNI_TRUE;
  });
}

JNIEXPORT jboolean JNICALL Java_com_baidu_paddle_lite_Tensor_nativeSetData___3I(
    JNIEnv *env, jobject thiz, jintArray buf) {
  return jni_try(env, "Tensor.setInt", [&]() -> jboolean {
    auto *t = w_tensor(env, thiz);
    if (!t || !*t) return JNI_FALSE;
    int64_t n = env->GetArrayLength(buf);
    if (n != product((*t)->shape())) return JNI_FALSE;
    int32_t *dst = (*t)->mutable_data<int32_t>();
    env->GetIntArrayRegion(buf, 0, n, dst);
    return JNI_TRUE;
  });
}

JNIEXPORT jboolean JNICALL Java_com_baidu_paddle_lite_Tensor_nativeSetData___3J(
    JNIEnv *env, jobject thiz, jlongArray buf) {
  return jni_try(env, "Tensor.setLong", [&]() -> jboolean {
    auto *t = w_tensor(env, thiz);
    if (!t || !*t) return JNI_FALSE;
    int64_t n = env->GetArrayLength(buf);
    if (n != product((*t)->shape())) return JNI_FALSE;
    int64_t *dst = (*t)->mutable_data<int64_t>();
    env->GetLongArrayRegion(buf, 0, n, reinterpret_cast<jlong *>(dst));
    return JNI_TRUE;
  });
}

JNIEXPORT jfloatArray JNICALL Java_com_baidu_paddle_lite_Tensor_getFloatData(
    JNIEnv *env, jobject thiz) {
  return jni_try(env, "Tensor.getFloat", [&]() -> jfloatArray {
    const Tensor *t = nullptr;
    if (read_only(env, thiz)) {
      auto *p = r_tensor(env, thiz);
      if (!p || !*p) return nullptr;
      t = p->get();
    } else {
      auto *p = w_tensor(env, thiz);
      if (!p || !*p) return nullptr;
      t = p->get();
    }
    auto shape = t->shape();
    int64_t n = product(shape);
    jfloatArray arr = env->NewFloatArray(static_cast<jsize>(n));
    env->SetFloatArrayRegion(arr, 0, static_cast<jsize>(n), t->data<float>());
    return arr;
  });
}

JNIEXPORT jbyteArray JNICALL Java_com_baidu_paddle_lite_Tensor_getByteData(
    JNIEnv *env, jobject thiz) {
  return jni_try(env, "Tensor.getByte", [&]() -> jbyteArray {
    const Tensor *t = nullptr;
    if (read_only(env, thiz)) {
      auto *p = r_tensor(env, thiz);
      if (!p || !*p) return nullptr;
      t = p->get();
    } else {
      auto *p = w_tensor(env, thiz);
      if (!p || !*p) return nullptr;
      t = p->get();
    }
    auto shape = t->shape();
    int64_t n = product(shape);
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(n));
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(n),
                            reinterpret_cast<const jbyte *>(t->data<int8_t>()));
    return arr;
  });
}

JNIEXPORT jintArray JNICALL Java_com_baidu_paddle_lite_Tensor_getIntData(
    JNIEnv *env, jobject thiz) {
  return jni_try(env, "Tensor.getInt", [&]() -> jintArray {
    const Tensor *t = nullptr;
    if (read_only(env, thiz)) {
      auto *p = r_tensor(env, thiz);
      if (!p || !*p) return nullptr;
      t = p->get();
    } else {
      auto *p = w_tensor(env, thiz);
      if (!p || !*p) return nullptr;
      t = p->get();
    }
    auto shape = t->shape();
    int64_t n = product(shape);
    jintArray arr = env->NewIntArray(static_cast<jsize>(n));
    env->SetIntArrayRegion(arr, 0, static_cast<jsize>(n), t->data<int32_t>());
    return arr;
  });
}

JNIEXPORT jlongArray JNICALL Java_com_baidu_paddle_lite_Tensor_getLongData(
    JNIEnv *env, jobject thiz) {
  return jni_try(env, "Tensor.getLong", [&]() -> jlongArray {
    const Tensor *t = nullptr;
    if (read_only(env, thiz)) {
      auto *p = r_tensor(env, thiz);
      if (!p || !*p) return nullptr;
      t = p->get();
    } else {
      auto *p = w_tensor(env, thiz);
      if (!p || !*p) return nullptr;
      t = p->get();
    }
    auto shape = t->shape();
    int64_t n = product(shape);
    jlongArray arr = env->NewLongArray(static_cast<jsize>(n));
    env->SetLongArrayRegion(arr, 0, static_cast<jsize>(n),
                            reinterpret_cast<const jlong *>(t->data<int64_t>()));
    return arr;
  });
}

JNIEXPORT jboolean JNICALL Java_com_baidu_paddle_lite_Tensor_deleteCppTensor(
    JNIEnv *env, jobject, jlong ptr) {
  return jni_try(env, "Tensor.delete", [&]() -> jboolean {
    if (!ptr) return JNI_FALSE;
    // Could be const or non-const unique_ptr; both delete the same way as void*
    delete reinterpret_cast<std::unique_ptr<Tensor> *>(ptr);
    return JNI_TRUE;
  });
}

}  // extern "C"
