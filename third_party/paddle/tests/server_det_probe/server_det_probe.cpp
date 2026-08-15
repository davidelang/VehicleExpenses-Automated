// server_det_probe — minimal Lite det Run for phone/QEMU diagnosis.
//
// Loads one .nb, feeds NCHW uint8 mono (default 1x1xSxS zeros or PGM),
// runs predictor with try/catch so CHECK/LOG(FATAL) (bare std::exception
// on product tiny SO) does not SIGABRT the process.
//
// Usage:
//   server_det_probe --model path.nb [--side 768] [--threads 1]
//                    [--image mono.pgm] [--runs 1] [--abi arm64-v8a]
//
// Build: ./build.sh (arm64-v8a primary). Run on device:
//   adb push out/arm64-v8a /data/local/tmp/server_det_probe
//   adb shell 'cd /data/local/tmp/server_det_probe && ./paddle_ocr_functional \
//     --model /sdcard/.../PP-OCRv4_server_det_armv8.nb --side 768'

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <exception>
#include <fstream>
#include <string>
#include <typeinfo>
#include <vector>

#include <paddle_api.h>

using namespace paddle::lite_api;

struct Args {
  std::string model;
  std::string image;
  std::string abi = "arm64-v8a";
  int side = 768;
  int threads = 1;
  int runs = 1;
  bool gray128 = false;
  bool force_float = false;
  /** Absolute Arm L3 workspace MB (0 = device default). Try large values if
   * large spatial sides SEGV due to undersized workspace_. */
  int l3_mb = 0;
};

static void usage(const char *argv0) {
  std::fprintf(stderr,
               "Usage: %s --model <det.nb> [--side 768] [--threads 1] "
               "[--runs 1] [--image mono.pgm] [--gray128] [--abi arm64-v8a]\n",
               argv0);
}

static bool parse_args(int argc, char **argv, Args *a) {
  for (int i = 1; i < argc; ++i) {
    std::string s = argv[i];
    auto need = [&](const char *flag) -> const char * {
      if (i + 1 >= argc) {
        std::fprintf(stderr, "missing value for %s\n", flag);
        return nullptr;
      }
      return argv[++i];
    };
    if (s == "--model") {
      const char *v = need("--model");
      if (!v) return false;
      a->model = v;
    } else if (s == "--image") {
      const char *v = need("--image");
      if (!v) return false;
      a->image = v;
    } else if (s == "--side") {
      const char *v = need("--side");
      if (!v) return false;
      a->side = std::atoi(v);
    } else if (s == "--threads") {
      const char *v = need("--threads");
      if (!v) return false;
      a->threads = std::atoi(v);
    } else if (s == "--runs") {
      const char *v = need("--runs");
      if (!v) return false;
      a->runs = std::atoi(v);
    } else if (s == "--abi") {
      const char *v = need("--abi");
      if (!v) return false;
      a->abi = v;
    } else if (s == "--gray128") {
      a->gray128 = true;
    } else if (s == "--float") {
      a->force_float = true;
    } else if (s == "--l3-mb") {
      const char *v = need("--l3-mb");
      if (!v) return false;
      a->l3_mb = std::atoi(v);
    } else if (s == "-h" || s == "--help") {
      usage(argv[0]);
      return false;
    } else {
      std::fprintf(stderr, "unknown arg: %s\n", s.c_str());
      usage(argv[0]);
      return false;
    }
  }
  if (a->model.empty() || a->side < 1 || a->runs < 1) {
    usage(argv[0]);
    return false;
  }
  return true;
}

static bool load_pgm(const std::string &path, std::vector<uint8_t> *y, int *w,
                     int *h) {
  std::ifstream f(path, std::ios::binary);
  if (!f) return false;
  std::string magic;
  f >> magic;
  if (magic != "P5") return false;
  int maxv = 0;
  f >> *w >> *h >> maxv;
  f.get();
  if (*w <= 0 || *h <= 0) return false;
  y->resize(static_cast<size_t>(*w) * *h);
  f.read(reinterpret_cast<char *>(y->data()),
         static_cast<std::streamsize>(y->size()));
  return static_cast<bool>(f) || f.eof();
}

// Letterbox content into side×side top-left (app-like mono pad).
static std::vector<uint8_t> make_feed(const Args &a) {
  const size_t n = static_cast<size_t>(a.side) * a.side;
  std::vector<uint8_t> feed(n, a.gray128 ? 128 : 0);
  if (a.image.empty()) return feed;
  std::vector<uint8_t> y;
  int w = 0, h = 0;
  if (!load_pgm(a.image, &y, &w, &h)) {
    std::fprintf(stderr, "WARN: failed to load PGM %s — using solid feed\n",
                 a.image.c_str());
    return feed;
  }
  const int cw = std::min(w, a.side);
  const int ch = std::min(h, a.side);
  for (int row = 0; row < ch; ++row) {
    std::memcpy(feed.data() + static_cast<size_t>(row) * a.side,
                y.data() + static_cast<size_t>(row) * w, cw);
  }
  std::fprintf(stderr, "image: %dx%d letterbox into %d (top-left)\n", w, h,
               a.side);
  return feed;
}

static bool feed_u8(PaddlePredictor *p, const std::vector<uint8_t> &data,
                    int side, const std::string &abi, bool force_float) {
  auto in = p->GetInput(0);
  in->Resize({1, 1, side, side});
  TargetType targets[4];
  int nt = 0;
  if (abi.find("x86") != std::string::npos) {
    targets[nt++] = TARGET(kX86);
    targets[nt++] = TARGET(kHost);
    targets[nt++] = TARGET(kARM);
  } else {
    targets[nt++] = TARGET(kARM);
    targets[nt++] = TARGET(kHost);
    targets[nt++] = TARGET(kX86);
  }
  if (!force_float) {
    for (int ti = 0; ti < nt; ++ti) {
      uint8_t *u = in->mutable_data<uint8_t>(targets[ti]);
      if (u) {
        std::memcpy(u, data.data(), data.size());
        in->SetPrecision(PRECISION(kUInt8));
        std::fprintf(stderr, "feed: uint8 target=%d ptr=%p n=%zu\n",
                     (int)targets[ti], (void *)u, data.size());
        return true;
      }
    }
    int8_t *dst = in->mutable_data<int8_t>(TARGET(kHost));
    if (!dst) dst = in->mutable_data<int8_t>();
    if (dst) {
      std::memcpy(dst, data.data(), data.size());
      in->SetPrecision(PRECISION(kUInt8));
      std::fprintf(stderr, "feed: int8 buffer + kUInt8\n");
      return true;
    }
  }
  float *f = in->mutable_data<float>(TARGET(kHost));
  if (!f) f = in->mutable_data<float>();
  if (!f) {
    std::fprintf(stderr, "FAIL: no mutable input buffer\n");
    return false;
  }
  for (size_t i = 0; i < data.size(); ++i) f[i] = data[i] / 255.f;
  in->SetPrecision(PRECISION(kFloat));
  std::fprintf(stderr, "feed: float path n=%zu\n", data.size());
  return true;
}

extern "C" int paddle_ocr_functional_run(int argc, char **argv);

int main(int argc, char **argv) {
  Args a;
  if (!parse_args(argc, argv, &a)) return 2;

  std::fprintf(stderr,
               "server_det_probe model=%s side=%d threads=%d runs=%d abi=%s\n",
               a.model.c_str(), a.side, a.threads, a.runs, a.abi.c_str());
  std::fflush(stderr);

  std::shared_ptr<PaddlePredictor> pred;
  try {
    MobileConfig cfg;
    cfg.set_threads(a.threads);
    cfg.set_power_mode(LITE_POWER_NO_BIND);
    if (a.l3_mb > 0) {
      const int bytes = a.l3_mb * 1024 * 1024;
      cfg.SetArmL3CacheSize(L3CacheSetMethod::kAbsolute, bytes);
      std::fprintf(stderr, "SetArmL3CacheSize absolute %d MB (%d bytes)\n",
                   a.l3_mb, bytes);
    }
    cfg.set_model_from_file(a.model);
    auto t0 = std::chrono::steady_clock::now();
    pred = CreatePaddlePredictor<MobileConfig>(cfg);
    auto t1 = std::chrono::steady_clock::now();
    double ms =
        std::chrono::duration<double, std::milli>(t1 - t0).count();
    if (!pred) {
      std::fprintf(stderr, "FAIL: CreatePaddlePredictor returned null (%.1f ms)\n",
                   ms);
      return 3;
    }
    std::fprintf(stderr, "OK: CreatePaddlePredictor in %.1f ms\n", ms);
  } catch (const std::exception &e) {
    std::fprintf(stderr,
                 "FAIL: CreatePaddlePredictor threw %s: %s\n",
                 typeid(e).name(), e.what());
    return 4;
  } catch (...) {
    std::fprintf(stderr, "FAIL: CreatePaddlePredictor threw non-std exception\n");
    return 4;
  }

  const auto feed = make_feed(a);
  if (!feed_u8(pred.get(), feed, a.side, a.abi, a.force_float)) return 5;

  for (int r = 0; r < a.runs; ++r) {
    try {
      auto t0 = std::chrono::steady_clock::now();
      pred->Run();
      auto t1 = std::chrono::steady_clock::now();
      double ms =
          std::chrono::duration<double, std::milli>(t1 - t0).count();
      auto out = pred->GetOutput(0);
      auto shape = out->shape();
      std::fprintf(stderr, "OK: Run[%d] %.1f ms out_shape=[", r, ms);
      for (size_t i = 0; i < shape.size(); ++i) {
        if (i) std::fprintf(stderr, ",");
        std::fprintf(stderr, "%lld", (long long)shape[i]);
      }
      std::fprintf(stderr, "] prec=%d\n", (int)out->precision());
      // mass of uint8 or float heat
      if (out->precision() == PRECISION(kUInt8) ||
          out->precision() == PRECISION(kInt8)) {
        const auto *p = out->data<uint8_t>();
        size_t n = 1;
        for (auto d : shape) n *= static_cast<size_t>(d);
        uint64_t sum = 0;
        uint8_t mx = 0;
        for (size_t i = 0; i < n; ++i) {
          sum += p[i];
          if (p[i] > mx) mx = p[i];
        }
        std::fprintf(stderr, "  heat_u8 n=%zu sum=%llu max=%u\n", n,
                     (unsigned long long)sum, (unsigned)mx);
      } else {
        const float *p = out->data<float>();
        size_t n = 1;
        for (auto d : shape) n *= static_cast<size_t>(d);
        double sum = 0;
        float mx = 0;
        for (size_t i = 0; i < n; ++i) {
          sum += p[i];
          if (p[i] > mx) mx = p[i];
        }
        std::fprintf(stderr, "  heat_f32 n=%zu sum=%.3f max=%.4f\n", n, sum, mx);
      }
    } catch (const std::exception &e) {
      std::fprintf(stderr,
                   "FAIL: Run[%d] threw type=%s what=%s\n", r, typeid(e).name(),
                   e.what());
      std::fprintf(stderr,
                   "NOTE: product Lite often builds LOG off → bare "
                   "std::exception; real CHECK text was discarded.\n");
      return 10;
    } catch (...) {
      std::fprintf(stderr, "FAIL: Run[%d] threw non-std exception\n", r);
      return 11;
    }
  }

  std::fprintf(stderr, "PASS server_det_probe\n");
  return 0;
}

// Loader entry used by ocr_functional-style dlopen harness.
extern "C" int paddle_ocr_functional_run(int argc, char **argv) {
  return main(argc, argv);
}
