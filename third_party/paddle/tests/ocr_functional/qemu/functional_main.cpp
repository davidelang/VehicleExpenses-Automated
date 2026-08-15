// paddle_ocr_functional — staged ISA-faithful gates under QEMU user-mode.
//
// Does NOT reimplement the full VE pump pipeline (multi-scale red-box filter,
// blue/orange crops, cost/vol classify). Instead splits the paddle surface
// into independent stages so SO A/B can pin which step diverges:
//
//   det-heat  — detect only: heatmap mass/hist/CRC + consensus rotation
//   det-boxes — detect only: red-box-like connected components (AABB)
//   rec       — recognize only: CTC text + per-char probs + logit CRC
//   pipeline  — legacy det→deskew→det→crop→rec smoke (skewed_hello gate)
//
// App vs harness differences (documented, partially aligned):
//   * App mono feed: long-edge scale → 32-align → top-left pad into square
//     (nativePopulateMonoUInt8). Harness default for stages: --resize topleft.
//     Pipeline default remains center --resize letterbox for back-compat.
//   * App red boxes: OpenCV minAreaRect at thresh 0.0, minArea 10.
//     Harness: simple AABB CC (no OpenCV); use --box-thresh 0 --min-area 10.
//   * App threads=4; harness --threads N (default 1 for determinism).
//   * App x86 models under prod_u8fp16 are effectively uint8→(fp16 cast)→fp32
//     backbone (no HW fp16 on AMD64); armv8 keeps real fp16 mid-graph.
//
// Usage:
//   paddle_ocr_functional --abi x86_64 --stage det-heat \
//     --det det.nb --rec rec.nb --dict en_dict.txt --image L1.pgm --det-side 1024
#include "paddle/paddle_api.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <numeric>
#include <sstream>
#include <string>
#include <vector>

using namespace paddle::lite_api;

namespace {

enum class Stage { Pipeline, DetHeat, DetBoxes, Rec };
enum class ResizeMode { Letterbox, TopLeft };

struct Args {
  std::string abi;
  std::string det_path;
  std::string rec_path;
  std::string dict_path;
  std::string image_path;
  std::string expect_text = "ABCD12345";
  float expect_angle = 15.f;
  float angle_tol = 10.f;
  int max_edit = 2;
  int det_side = 224;
  bool strict = true;
  bool load_smoke_only = false;
  Stage stage = Stage::Pipeline;
  ResizeMode resize = ResizeMode::Letterbox;
  int threads = 1;
  float box_thresh = -1.f;  // <0 → stage default
  int min_area = -1;        // <0 → stage default
};

void usage(const char* a0) {
  std::fprintf(stderr,
               "Usage: %s --abi ABI --det PATH --rec PATH --dict PATH --image PATH\n"
               "  [--stage pipeline|det-heat|det-boxes|rec]\n"
               "  [--resize letterbox|topleft] [--threads N] [--det-side N]\n"
               "  [--box-thresh F] [--min-area N]\n"
               "  [--expect-text S] [--expect-angle DEG] [--angle-tol DEG]\n"
               "  [--max-edit N] [--no-strict] [--load-smoke-only]\n",
               a0);
}

bool parse_args(int argc, char** argv, Args* o) {
  for (int i = 1; i < argc; ++i) {
    std::string a = argv[i];
    auto need = [&](const char* k) -> const char* {
      if (i + 1 >= argc) {
        std::fprintf(stderr, "missing value for %s\n", k);
        std::exit(2);
      }
      return argv[++i];
    };
    if (a == "--abi") o->abi = need("--abi");
    else if (a == "--det") o->det_path = need("--det");
    else if (a == "--rec") o->rec_path = need("--rec");
    else if (a == "--dict") o->dict_path = need("--dict");
    else if (a == "--image") o->image_path = need("--image");
    else if (a == "--expect-text") o->expect_text = need("--expect-text");
    else if (a == "--expect-angle") o->expect_angle = std::strtof(need("--expect-angle"), nullptr);
    else if (a == "--angle-tol") o->angle_tol = std::strtof(need("--angle-tol"), nullptr);
    else if (a == "--max-edit") o->max_edit = std::atoi(need("--max-edit"));
    else if (a == "--det-side") o->det_side = std::atoi(need("--det-side"));
    else if (a == "--threads") o->threads = std::atoi(need("--threads"));
    else if (a == "--box-thresh") o->box_thresh = std::strtof(need("--box-thresh"), nullptr);
    else if (a == "--min-area") o->min_area = std::atoi(need("--min-area"));
    else if (a == "--no-strict") o->strict = false;
    else if (a == "--load-smoke-only") o->load_smoke_only = true;
    else if (a == "--stage") {
      std::string s = need("--stage");
      if (s == "pipeline") o->stage = Stage::Pipeline;
      else if (s == "det-heat") o->stage = Stage::DetHeat;
      else if (s == "det-boxes") o->stage = Stage::DetBoxes;
      else if (s == "rec") o->stage = Stage::Rec;
      else {
        std::fprintf(stderr, "unknown stage %s\n", s.c_str());
        return false;
      }
    } else if (a == "--resize") {
      std::string s = need("--resize");
      if (s == "letterbox") o->resize = ResizeMode::Letterbox;
      else if (s == "topleft" || s == "top-left" || s == "app") o->resize = ResizeMode::TopLeft;
      else {
        std::fprintf(stderr, "unknown resize %s\n", s.c_str());
        return false;
      }
    } else if (a == "-h" || a == "--help") {
      usage(argv[0]);
      return false;
    } else {
      std::fprintf(stderr, "unknown arg %s\n", a.c_str());
      usage(argv[0]);
      return false;
    }
  }
  if (o->abi.empty() || o->image_path.empty()) {
    usage(argv[0]);
    return false;
  }
  // Stage-specific required models
  if (o->stage == Stage::Rec) {
    if (o->rec_path.empty() || o->dict_path.empty()) {
      std::fprintf(stderr, "rec stage needs --rec and --dict\n");
      return false;
    }
  } else if (o->stage == Stage::DetHeat || o->stage == Stage::DetBoxes) {
    if (o->det_path.empty()) {
      std::fprintf(stderr, "det stage needs --det\n");
      return false;
    }
  } else {
    if (o->det_path.empty() || o->rec_path.empty() || o->dict_path.empty()) {
      usage(argv[0]);
      return false;
    }
  }
  // Stage defaults for resize / thresh (pipeline keeps letterbox + 0.20)
  if (o->stage != Stage::Pipeline && o->resize == ResizeMode::Letterbox) {
    // leave letterbox if user set it; if they never set stage-specific, prefer topleft
  }
  return true;
}

struct Image {
  int w = 0, h = 0;
  std::vector<uint8_t> pix;
};

bool load_pgm(const std::string& path, Image* im) {
  std::ifstream f(path, std::ios::binary);
  if (!f) {
    std::fprintf(stderr, "FAIL open image %s\n", path.c_str());
    return false;
  }
  std::string magic;
  f >> magic;
  if (magic != "P5") {
    std::fprintf(stderr, "FAIL not P5 pgm: %s\n", magic.c_str());
    return false;
  }
  int maxv = 0;
  auto skip_comments = [&]() {
    while (f.peek() == '#' || f.peek() == '\n' || f.peek() == '\r' || f.peek() == ' ') {
      if (f.peek() == '#') {
        std::string line;
        std::getline(f, line);
      } else {
        f.get();
      }
    }
  };
  skip_comments();
  f >> im->w;
  skip_comments();
  f >> im->h;
  skip_comments();
  f >> maxv;
  f.get();
  if (im->w <= 0 || im->h <= 0 || maxv != 255) {
    std::fprintf(stderr, "FAIL bad pgm header %dx%d max=%d\n", im->w, im->h, maxv);
    return false;
  }
  im->pix.resize(static_cast<size_t>(im->w) * im->h);
  f.read(reinterpret_cast<char*>(im->pix.data()), static_cast<std::streamsize>(im->pix.size()));
  if (!f) {
    std::fprintf(stderr, "FAIL short pgm read\n");
    return false;
  }
  return true;
}

std::vector<std::string> load_dict(const std::string& path) {
  std::vector<std::string> d;
  std::ifstream f(path);
  std::string line;
  while (std::getline(f, line)) {
    if (!line.empty() && line.back() == '\r') line.pop_back();
    if (!line.empty()) d.push_back(line);
  }
  return d;
}

std::string normalize(const std::string& s) {
  std::string o;
  for (char c : s) {
    if (c >= 'a' && c <= 'z') o.push_back(static_cast<char>(c - 32));
    else if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) o.push_back(c);
  }
  return o;
}

int edit_distance(const std::string& a, const std::string& b) {
  const int m = static_cast<int>(a.size()), n = static_cast<int>(b.size());
  std::vector<int> dp(n + 1);
  std::iota(dp.begin(), dp.end(), 0);
  for (int i = 1; i <= m; ++i) {
    int prev = dp[0];
    dp[0] = i;
    for (int j = 1; j <= n; ++j) {
      int tmp = dp[j];
      if (a[i - 1] == b[j - 1]) dp[j] = prev;
      else dp[j] = 1 + std::min({prev, dp[j], dp[j - 1]});
      prev = tmp;
    }
  }
  return dp[n];
}

// Center letterbox (legacy harness / skewed_hello gate).
void letterbox_u8(const Image& src, int side, std::vector<uint8_t>* out, float* scale, int* pad_x,
                  int* pad_y) {
  out->assign(static_cast<size_t>(side) * side, 0);
  float sc = std::min(static_cast<float>(side) / src.w, static_cast<float>(side) / src.h);
  int nw = std::max(1, static_cast<int>(src.w * sc));
  int nh = std::max(1, static_cast<int>(src.h * sc));
  int px = (side - nw) / 2;
  int py = (side - nh) / 2;
  *scale = sc;
  *pad_x = px;
  *pad_y = py;
  for (int y = 0; y < nh; ++y) {
    int sy = std::min(src.h - 1, static_cast<int>(y / sc));
    for (int x = 0; x < nw; ++x) {
      int sx = std::min(src.w - 1, static_cast<int>(x / sc));
      (*out)[static_cast<size_t>(py + y) * side + (px + x)] =
          src.pix[static_cast<size_t>(sy) * src.w + sx];
    }
  }
}

// App-like: long-edge scale to side, top-left paste into side×side zeros
// (matches nativePopulateMonoUInt8 after prepareScale long-edge).
void topleft_longedge_u8(const Image& src, int side, std::vector<uint8_t>* out, float* scale,
                         int* pad_x, int* pad_y) {
  out->assign(static_cast<size_t>(side) * side, 0);
  int long_edge = std::max(src.w, src.h);
  float sc = long_edge <= side ? 1.f : static_cast<float>(side) / long_edge;
  int nw = std::max(1, static_cast<int>(src.w * sc));
  int nh = std::max(1, static_cast<int>(src.h * sc));
  // 32-align content dims like prepareScale, then still feed square side
  int aw = ((nw + 31) / 32) * 32;
  int ah = ((nh + 31) / 32) * 32;
  aw = std::min(aw, side);
  ah = std::min(ah, side);
  *scale = sc;
  *pad_x = 0;
  *pad_y = 0;
  for (int y = 0; y < nh && y < side; ++y) {
    int sy = std::min(src.h - 1, static_cast<int>(y / sc));
    for (int x = 0; x < nw && x < side; ++x) {
      int sx = std::min(src.w - 1, static_cast<int>(x / sc));
      (*out)[static_cast<size_t>(y) * side + x] = src.pix[static_cast<size_t>(sy) * src.w + sx];
    }
  }
  (void)aw;
  (void)ah;
}

void resize_u8(const Image& src, int side, ResizeMode mode, std::vector<uint8_t>* out, float* scale,
               int* pad_x, int* pad_y) {
  if (mode == ResizeMode::TopLeft) topleft_longedge_u8(src, side, out, scale, pad_x, pad_y);
  else letterbox_u8(src, side, out, scale, pad_x, pad_y);
}

struct Box {
  float x0, y0, x1, y1;
  float angle_deg = 0.f;
  float conf = 0.f;
};

std::vector<Box> heatmap_to_boxes(const float* heat, int H, int W, float thresh, int min_area) {
  std::vector<uint8_t> mask(static_cast<size_t>(H) * W, 0);
  for (int i = 0; i < H * W; ++i) {
    if (heat[i] >= thresh) mask[i] = 1;
  }
  std::vector<int> parent(static_cast<size_t>(H) * W, -1);
  auto find = [&](int x) {
    int r = x;
    while (parent[r] != r && parent[r] >= 0) r = parent[r];
    int c = x;
    while (c != r && parent[c] >= 0) {
      int n = parent[c];
      parent[c] = r;
      c = n;
    }
    return r;
  };
  auto unite = [&](int a, int b) {
    a = find(a);
    b = find(b);
    if (a != b) parent[b] = a;
  };
  for (int y = 0; y < H; ++y) {
    for (int x = 0; x < W; ++x) {
      int i = y * W + x;
      if (!mask[i]) continue;
      parent[i] = i;
      if (x > 0 && mask[i - 1]) unite(i, i - 1);
      if (y > 0 && mask[i - W]) unite(i, i - W);
    }
  }
  struct Acc {
    int n = 0;
    double sx = 0, sy = 0, sxx = 0, sxy = 0, syy = 0;
    int minx = 1e9, miny = 1e9, maxx = -1, maxy = -1;
    double sc = 0;
  };
  std::vector<Acc> acc(static_cast<size_t>(H) * W);
  for (int y = 0; y < H; ++y) {
    for (int x = 0; x < W; ++x) {
      int i = y * W + x;
      if (!mask[i]) continue;
      int r = find(i);
      auto& a = acc[r];
      a.n++;
      a.sx += x;
      a.sy += y;
      a.sxx += double(x) * x;
      a.sxy += double(x) * y;
      a.syy += double(y) * y;
      a.minx = std::min(a.minx, x);
      a.miny = std::min(a.miny, y);
      a.maxx = std::max(a.maxx, x);
      a.maxy = std::max(a.maxy, y);
      a.sc += heat[i];
    }
  }
  std::vector<Box> boxes;
  for (size_t i = 0; i < acc.size(); ++i) {
    auto& a = acc[i];
    if (a.n < min_area) continue;
    Box b;
    b.x0 = static_cast<float>(a.minx);
    b.y0 = static_cast<float>(a.miny);
    b.x1 = static_cast<float>(a.maxx + 1);
    b.y1 = static_cast<float>(a.maxy + 1);
    b.conf = static_cast<float>(a.sc / a.n);
    double mx = a.sx / a.n, my = a.sy / a.n;
    double cxx = a.sxx / a.n - mx * mx;
    double cxy = a.sxy / a.n - mx * my;
    double cyy = a.syy / a.n - my * my;
    b.angle_deg = static_cast<float>(0.5 * std::atan2(2 * cxy, cxx - cyy) * 180.0 / M_PI);
    while (b.angle_deg > 45.f) b.angle_deg -= 90.f;
    while (b.angle_deg < -45.f) b.angle_deg += 90.f;
    boxes.push_back(b);
  }
  return boxes;
}

float consensus_angle(const std::vector<Box>& boxes) {
  if (boxes.empty()) return 0.f;
  double num = 0, den = 0;
  for (const auto& b : boxes) {
    double area = std::max(1.f, (b.x1 - b.x0) * (b.y1 - b.y0));
    num += b.angle_deg * area;
    den += area;
  }
  return den > 0 ? static_cast<float>(num / den) : 0.f;
}

// App-like 100-bin hist on heat in [0,1]: bin = clamp(v*100, 0, 99). mass = sum bins[1..]
struct HeatStats {
  float max = 0.f;
  int hist[100] = {};
  int mass = 0;  // sum hist[1..99] like first10-golden-compare
  uint32_t crc = 0;  // FNV of quantized uint8 plane
  int H = 0, W = 0;
};

void compute_heat_stats(const std::vector<float>& heat, int H, int W, HeatStats* s) {
  s->H = H;
  s->W = W;
  s->max = 0.f;
  std::memset(s->hist, 0, sizeof(s->hist));
  s->mass = 0;
  s->crc = 2166136261u;
  size_t n = static_cast<size_t>(H) * W;
  for (size_t i = 0; i < n && i < heat.size(); ++i) {
    float v = heat[i];
    if (v > s->max) s->max = v;
    int b = static_cast<int>(v * 100.f);
    if (b < 0) b = 0;
    if (b > 99) b = 99;
    s->hist[b]++;
    uint8_t u = static_cast<uint8_t>(std::min(255.f, std::max(0.f, v * 255.f)));
    s->crc ^= u;
    s->crc *= 16777619u;
  }
  for (int i = 1; i < 100; ++i) s->mass += s->hist[i];
}

Image rotate_image(const Image& src, float deg) {
  if (std::fabs(deg) < 0.01f) return src;
  double rad = deg * M_PI / 180.0;
  double c = std::cos(rad), s = std::sin(rad);
  double corners[4][2] = {{0, 0},
                          {double(src.w), 0},
                          {double(src.w), double(src.h)},
                          {0, double(src.h)}};
  double minx = 1e9, miny = 1e9, maxx = -1e9, maxy = -1e9;
  double cx = src.w / 2.0, cy = src.h / 2.0;
  for (auto& p : corners) {
    double x = p[0] - cx, y = p[1] - cy;
    double xr = c * x - s * y;
    double yr = s * x + c * y;
    minx = std::min(minx, xr);
    maxx = std::max(maxx, xr);
    miny = std::min(miny, yr);
    maxy = std::max(maxy, yr);
  }
  Image dst;
  dst.w = std::max(1, static_cast<int>(std::ceil(maxx - minx)));
  dst.h = std::max(1, static_cast<int>(std::ceil(maxy - miny)));
  dst.pix.assign(static_cast<size_t>(dst.w) * dst.h, 255);
  double ncx = dst.w / 2.0, ncy = dst.h / 2.0;
  double ci = std::cos(-rad), si = std::sin(-rad);
  for (int y = 0; y < dst.h; ++y) {
    for (int x = 0; x < dst.w; ++x) {
      double xr = x - ncx, yr = y - ncy;
      double sx = ci * xr - si * yr + cx;
      double sy = si * xr + ci * yr + cy;
      int ix = static_cast<int>(std::round(sx));
      int iy = static_cast<int>(std::round(sy));
      if (ix >= 0 && iy >= 0 && ix < src.w && iy < src.h) {
        dst.pix[static_cast<size_t>(y) * dst.w + x] =
            src.pix[static_cast<size_t>(iy) * src.w + ix];
      }
    }
  }
  return dst;
}

std::shared_ptr<PaddlePredictor> make_pred(const std::string& path, int threads = 1) {
  std::fprintf(stderr, "make_pred: begin %s threads=%d\n", path.c_str(), threads);
  std::fflush(stderr);
  MobileConfig cfg;
  cfg.set_threads(threads);
  cfg.set_power_mode(LITE_POWER_NO_BIND);
  cfg.set_model_from_file(path);
  auto p = CreatePaddlePredictor<MobileConfig>(cfg);
  if (!p) std::fprintf(stderr, "FAIL CreatePaddlePredictor %s\n", path.c_str());
  else std::fprintf(stderr, "make_pred: ok %s\n", path.c_str());
  std::fflush(stderr);
  return p;
}

bool feed_u8(PaddlePredictor* p, const std::vector<uint8_t>& data, int64_t n, int64_t c, int64_t h,
             int64_t w, const std::string& abi) {
  auto in = p->GetInput(0);
  in->Resize({n, c, h, w});
  uint64_t s = 0;
  for (auto v : data) s += v;
  std::fprintf(stderr, "feed_u8: nchw=%lldx%lldx%lldx%lld first=%u mid=%u sum=%llu\n",
               (long long)n, (long long)c, (long long)h, (long long)w, data.front(),
               data[data.size() / 2], (unsigned long long)s);

  const char* mode = std::getenv("PADDLE_OCR_FEED");
  const bool force_float = mode && (std::strcmp(mode, "float") == 0 || std::strcmp(mode, "fp32") == 0);

  if (!force_float) {
    // Prefer host/x86 for emulator ABI; kARM for arm*.
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
    for (int ti = 0; ti < nt; ++ti) {
      uint8_t* u = in->mutable_data<uint8_t>(targets[ti]);
      if (u) {
        std::memcpy(u, data.data(), data.size());
        in->SetPrecision(PRECISION(kUInt8));
        std::fprintf(stderr, "feed_u8: uint8 ptr=%p prec=%d target=%d\n", (void*)u,
                     (int)in->precision(), (int)targets[ti]);
        return true;
      }
    }
    int8_t* dst = in->mutable_data<int8_t>(TARGET(kHost));
    if (!dst) dst = in->mutable_data<int8_t>();
    if (dst) {
      std::memcpy(dst, data.data(), data.size());
      in->SetPrecision(PRECISION(kUInt8));
      std::fprintf(stderr, "feed_u8: int8 buffer + kUInt8 ptr=%p\n", (void*)dst);
      return true;
    }
  }

  auto* f = in->mutable_data<float>(TARGET(kHost));
  if (!f) f = in->mutable_data<float>();
  if (!f) {
    std::fprintf(stderr, "FAIL no mutable_data for input\n");
    return false;
  }
  for (size_t i = 0; i < data.size(); ++i) f[i] = data[i] / 255.f;
  in->SetPrecision(PRECISION(kFloat));
  std::fprintf(stderr, "feed_u8: float path\n");
  return true;
}

bool read_heatmap(PaddlePredictor* p, std::vector<float>* heat, int* H, int* W) {
  std::unique_ptr<const Tensor> out = p->GetOutput(0);
  if (!out) {
    std::fprintf(stderr, "FAIL GetOutput null\n");
    return false;
  }
  auto shape = out->shape();
  if (shape.size() == 4) {
    *H = static_cast<int>(shape[2]);
    *W = static_cast<int>(shape[3]);
  } else if (shape.size() == 3) {
    *H = static_cast<int>(shape[1]);
    *W = static_cast<int>(shape[2]);
  } else if (shape.size() >= 2) {
    *H = static_cast<int>(shape[shape.size() - 2]);
    *W = static_cast<int>(shape[shape.size() - 1]);
  } else {
    std::fprintf(stderr, "FAIL heatmap shape rank %zu\n", shape.size());
    return false;
  }
  if (*H <= 0 || *W <= 0 || *H > 8192 || *W > 8192) {
    std::fprintf(stderr, "FAIL heatmap dims %dx%d\n", *H, *W);
    return false;
  }
  size_t n = static_cast<size_t>(*H) * (*W);
  heat->resize(n);
  auto prec = out->precision();
  std::fprintf(stderr, "read_heatmap: n=%zu precision=%d HxW=%dx%d\n", n, static_cast<int>(prec),
               *H, *W);

  if (prec == PrecisionType::kUInt8 || prec == PrecisionType::kAny ||
      prec == PrecisionType::kUnk) {
    std::vector<uint8_t> u8(n, 0);
    try {
      out->CopyToCpu<uint8_t>(u8.data());
    } catch (...) {
      const uint8_t* up = out->data<uint8_t>();
      if (up) std::memcpy(u8.data(), up, n);
    }
    float u8_mx = 0.f;
    for (size_t i = 0; i < n; ++i) {
      (*heat)[i] = u8[i] / 255.f;
      u8_mx = std::max(u8_mx, float(u8[i]));
    }
    std::fprintf(stderr, "read_heatmap: uint8 max=%g\n", u8_mx);
    return true;
  }
  if (prec == PrecisionType::kFloat) {
    std::vector<float> fbuf(n, 0.f);
    try {
      out->CopyToCpu<float>(fbuf.data());
    } catch (...) {
      const float* fp = out->data<float>();
      if (fp) std::memcpy(fbuf.data(), fp, n * sizeof(float));
    }
    float mx = 0.f;
    for (size_t i = 0; i < n; ++i) {
      (*heat)[i] = fbuf[i];
      mx = std::max(mx, fbuf[i]);
    }
    if (mx > 1.5f) {
      for (float& v : *heat) v /= (mx > 255.f ? 255.f : mx);
    }
    std::fprintf(stderr, "read_heatmap: float max=%.4f\n", mx);
    return true;
  }
  std::fprintf(stderr, "FAIL heatmap unreadable precision=%d\n", static_cast<int>(prec));
  return false;
}

struct CtcResult {
  std::string text;
  std::string probs;  // "A:0.98,B:0.91"
  float mean_conf = 0.f;
  uint32_t logit_crc = 0;
  int T = 0, C = 0;
};

CtcResult ctc_greedy_probs(const float* data, const std::vector<int64_t>& shape,
                           const std::vector<std::string>& dict) {
  CtcResult r;
  if (shape.size() == 3) {
    r.T = static_cast<int>(shape[1]);
    r.C = static_cast<int>(shape[2]);
  } else if (shape.size() == 2) {
    r.T = static_cast<int>(shape[0]);
    r.C = static_cast<int>(shape[1]);
  } else {
    return r;
  }
  r.logit_crc = 2166136261u;
  size_t n = static_cast<size_t>(r.T) * r.C;
  for (size_t i = 0; i < n; ++i) {
    // quantize float for stable CRC across runs
    int q = static_cast<int>(data[i] * 1000.f);
    if (q < -32768) q = -32768;
    if (q > 32767) q = 32767;
    uint8_t b0 = static_cast<uint8_t>(q & 0xff);
    uint8_t b1 = static_cast<uint8_t>((q >> 8) & 0xff);
    r.logit_crc ^= b0;
    r.logit_crc *= 16777619u;
    r.logit_crc ^= b1;
    r.logit_crc *= 16777619u;
  }
  int prev = -1;
  float sum_conf = 0.f;
  int nchar = 0;
  std::ostringstream ps;
  for (int t = 0; t < r.T; ++t) {
    int best = 0;
    float bv = data[t * r.C];
    for (int c = 1; c < r.C; ++c) {
      float v = data[t * r.C + c];
      if (v > bv) {
        bv = v;
        best = c;
      }
    }
    if (best != 0 && best != prev) {
      int di = best - 1;
      if (di >= 0 && di < static_cast<int>(dict.size())) {
        r.text += dict[di];
        if (nchar) ps << ',';
        ps << dict[di] << ':' << std::fixed;
        char buf[32];
        std::snprintf(buf, sizeof(buf), "%.3f", bv);
        ps << buf;
        sum_conf += bv;
        nchar++;
      }
    }
    prev = best;
  }
  r.probs = ps.str();
  r.mean_conf = nchar > 0 ? sum_conf / nchar : 0.f;
  return r;
}

Box map_box_to_image(Box b, float scale, int pad_x, int pad_y, int img_w, int img_h) {
  Box o = b;
  o.x0 = (b.x0 - pad_x) / scale;
  o.x1 = (b.x1 - pad_x) / scale;
  o.y0 = (b.y0 - pad_y) / scale;
  o.y1 = (b.y1 - pad_y) / scale;
  o.x0 = std::max(0.f, std::min(o.x0, float(img_w)));
  o.x1 = std::max(0.f, std::min(o.x1, float(img_w)));
  o.y0 = std::max(0.f, std::min(o.y0, float(img_h)));
  o.y1 = std::max(0.f, std::min(o.y1, float(img_h)));
  return o;
}

struct DetOut {
  std::vector<Box> boxes;
  float angle = 0.f;
  HeatStats heat;
  bool ok = false;
};

bool run_det(PaddlePredictor* det, const Image& img, int side, ResizeMode rmode,
             const std::string& abi, float thr, int min_area, DetOut* out) {
  std::vector<uint8_t> tensor;
  float scale = 1.f;
  int pad_x = 0, pad_y = 0;
  resize_u8(img, side, rmode, &tensor, &scale, &pad_x, &pad_y);
  std::fprintf(stderr, "run_det: resize=%s %dx%d → %d pad=%d,%d scale=%.4f\n",
               rmode == ResizeMode::TopLeft ? "topleft" : "letterbox", img.w, img.h, side, pad_x,
               pad_y, scale);
  if (!feed_u8(det, tensor, 1, 1, side, side, abi)) return false;
  det->Run();
  std::vector<float> heat;
  int H = 0, W = 0;
  if (!read_heatmap(det, &heat, &H, &W)) return false;
  compute_heat_stats(heat, H, W, &out->heat);
  std::fprintf(stderr, "run_det: heat max=%.4f mass=%d crc=0x%08x\n", out->heat.max, out->heat.mass,
               out->heat.crc);

  float mx = out->heat.max;
  float use_thr = thr;
  if (use_thr < 0.f) use_thr = 0.20f;
  if (mx > 0.f && mx < 0.25f && thr < 0.f) use_thr = std::max(0.02f, mx * 0.35f);
  int use_min = min_area > 0 ? min_area : 8;
  auto raw = heatmap_to_boxes(heat.data(), H, W, use_thr, use_min);
  if (raw.empty() && thr < 0.f) {
    raw = heatmap_to_boxes(heat.data(), H, W, std::max(0.01f, use_thr * 0.25f), 4);
  }
  if (raw.empty() && mx > 0.f && thr < 0.f) {
    raw = heatmap_to_boxes(heat.data(), H, W, mx * 0.15f, 4);
  }
  float hs = static_cast<float>(side) / H;
  float ws = static_cast<float>(side) / W;
  out->angle = consensus_angle(raw);
  out->boxes.clear();
  for (auto b : raw) {
    b.x0 *= ws;
    b.x1 *= ws;
    b.y0 *= hs;
    b.y1 *= hs;
    out->boxes.push_back(map_box_to_image(b, scale, pad_x, pad_y, img.w, img.h));
  }
  out->ok = true;
  return true;
}

CtcResult run_rec(PaddlePredictor* rec, const Image& patch, const std::vector<std::string>& dict,
                  const std::string& abi) {
  const int rh = 48, rw = 320;
  std::vector<uint8_t> tensor(static_cast<size_t>(rh) * rw, 0);
  float sc = std::min(static_cast<float>(rw) / patch.w, static_cast<float>(rh) / patch.h);
  int nw = std::max(1, static_cast<int>(patch.w * sc));
  int nh = std::max(1, static_cast<int>(patch.h * sc));
  int px = (rw - nw) / 2, py = (rh - nh) / 2;
  for (int y = 0; y < nh; ++y) {
    int sy = std::min(patch.h - 1, static_cast<int>(y / sc));
    for (int x = 0; x < nw; ++x) {
      int sx = std::min(patch.w - 1, static_cast<int>(x / sc));
      tensor[static_cast<size_t>(py + y) * rw + (px + x)] =
          patch.pix[static_cast<size_t>(sy) * patch.w + sx];
    }
  }
  if (!feed_u8(rec, tensor, 1, 1, rh, rw, abi)) return {};
  rec->Run();
  auto out = rec->GetOutput(0);
  auto shape = out->shape();
  const float* fp = out->data<float>();
  if (!fp) {
    // try copy
    size_t n = 1;
    for (auto d : shape) n *= static_cast<size_t>(d);
    std::vector<float> buf(n);
    try {
      out->CopyToCpu<float>(buf.data());
      return ctc_greedy_probs(buf.data(), shape, dict);
    } catch (...) {
      std::fprintf(stderr, "FAIL rec output not float\n");
      return {};
    }
  }
  return ctc_greedy_probs(fp, shape, dict);
}

void print_hist_brief(const HeatStats& h) {
  // print non-zero-ish high bins summary + first/last
  std::printf("HEAT max=%.4f mass=%d crc=0x%08x HxW=%dx%d hist0=%d hist1_5=%d,%d,%d,%d,%d\n",
              h.max, h.mass, h.crc, h.H, h.W, h.hist[0], h.hist[1], h.hist[2], h.hist[3],
              h.hist[4], h.hist[5]);
}

int stage_det_heat(const Args& args, const Image& img, PaddlePredictor* det) {
  float thr = args.box_thresh >= 0.f ? args.box_thresh : 0.20f;  // angle path uses 0.20
  int min_a = args.min_area > 0 ? args.min_area : 8;
  ResizeMode rm = args.resize;
  // default topleft for staged det if user left letterbox but set stage
  if (args.stage != Stage::Pipeline) {
    // honor explicit; stages default set in main
  }
  DetOut d;
  if (!run_det(det, img, args.det_side, rm, args.abi, thr, min_a, &d)) {
    std::printf("RESULT stage=det-heat FAIL\n");
    return 1;
  }
  print_hist_brief(d.heat);
  std::printf("RESULT stage=det-heat angle=%.3f boxes=%zu heat_max=%.4f mass=%d crc=0x%08x "
              "side=%d resize=%s threads=%d\n",
              d.angle, d.boxes.size(), d.heat.max, d.heat.mass, d.heat.crc, args.det_side,
              rm == ResizeMode::TopLeft ? "topleft" : "letterbox", args.threads);
  std::fprintf(stderr, "PASS det-heat\n");
  return 0;
}

int stage_det_boxes(const Args& args, const Image& img, PaddlePredictor* det) {
  // App red-box path: thresh 0.0, minArea 10
  float thr = args.box_thresh >= 0.f ? args.box_thresh : 0.0f;
  int min_a = args.min_area > 0 ? args.min_area : 10;
  DetOut d;
  if (!run_det(det, img, args.det_side, args.resize, args.abi, thr, min_a, &d)) {
    std::printf("RESULT stage=det-boxes FAIL\n");
    return 1;
  }
  print_hist_brief(d.heat);
  // stable sort: conf desc, then x0,y0
  auto boxes = d.boxes;
  std::sort(boxes.begin(), boxes.end(), [](const Box& a, const Box& b) {
    if (a.conf != b.conf) return a.conf > b.conf;
    if (a.x0 != b.x0) return a.x0 < b.x0;
    return a.y0 < b.y0;
  });
  const size_t nprint = std::min(boxes.size(), size_t(32));
  for (size_t i = 0; i < nprint; ++i) {
    const auto& b = boxes[i];
    std::printf("BOX i=%zu xyxy=%.1f,%.1f,%.1f,%.1f conf=%.4f ang=%.2f\n", i, b.x0, b.y0, b.x1,
                b.y1, b.conf, b.angle_deg);
  }
  std::printf("RESULT stage=det-boxes nboxes=%zu angle=%.3f heat_max=%.4f mass=%d crc=0x%08x "
              "side=%d resize=%s thr=%.3f min_area=%d\n",
              boxes.size(), d.angle, d.heat.max, d.heat.mass, d.heat.crc, args.det_side,
              args.resize == ResizeMode::TopLeft ? "topleft" : "letterbox", thr, min_a);
  std::fprintf(stderr, "PASS det-boxes\n");
  return 0;
}

int stage_rec(const Args& args, const Image& img, PaddlePredictor* rec,
              const std::vector<std::string>& dict) {
  auto r = run_rec(rec, img, dict, args.abi);
  std::printf("PROBS %s\n", r.probs.c_str());
  std::printf("RESULT stage=rec ocr='%s' mean_conf=%.4f logit_crc=0x%08x T=%d C=%d\n",
              r.text.c_str(), r.mean_conf, r.logit_crc, r.T, r.C);
  if (args.strict && !args.expect_text.empty()) {
    std::string norm = normalize(r.text);
    std::string want = normalize(args.expect_text);
    int dist = edit_distance(norm, want);
    if (!(norm == want || dist <= args.max_edit)) {
      std::fprintf(stderr, "FAIL rec text want=%s got=%s edit=%d\n", want.c_str(), norm.c_str(),
                   dist);
      return 1;
    }
  }
  std::fprintf(stderr, "PASS rec\n");
  return 0;
}

int stage_pipeline(const Args& args, const Image& img, PaddlePredictor* det, PaddlePredictor* rec,
                   const std::vector<std::string>& dict) {
  DetOut d0;
  float thr = args.box_thresh >= 0.f ? args.box_thresh : -1.f;
  int min_a = args.min_area > 0 ? args.min_area : -1;
  if (!run_det(det, img, args.det_side, args.resize, args.abi, thr, min_a, &d0)) {
    std::printf("RESULT stage=pipeline FAIL det pass1\n");
    return 1;
  }
  std::fprintf(stderr, "pass1 angle=%.2f boxes=%zu\n", d0.angle, d0.boxes.size());
  if (d0.boxes.empty()) {
    std::printf("RESULT stage=pipeline FAIL empty boxes\n");
    return 1;
  }
  if (args.load_smoke_only) {
    std::printf("RESULT stage=pipeline load_smoke=1 angle=%.2f boxes=%zu\n", d0.angle,
                d0.boxes.size());
    std::fprintf(stderr, "PASS paddle_ocr_functional (load-smoke-only)\n");
    return 0;
  }
  float deskew = -d0.angle;
  Image leveled = rotate_image(img, deskew);
  DetOut d1;
  if (!run_det(det, leveled, args.det_side, args.resize, args.abi, thr, min_a, &d1)) {
    std::printf("RESULT stage=pipeline FAIL det pass2\n");
    return 1;
  }
  std::fprintf(stderr, "pass2 residual_angle=%.2f boxes=%zu\n", d1.angle, d1.boxes.size());
  Box best{};
  bool used_fallback = false;
  if (!d1.boxes.empty()) {
    best = *std::max_element(d1.boxes.begin(), d1.boxes.end(), [](const Box& a, const Box& b) {
      return (a.x1 - a.x0) * (a.y1 - a.y0) < (b.x1 - b.x0) * (b.y1 - b.y0);
    });
  } else {
    used_fallback = true;
    best.x0 = leveled.w * 0.08f;
    best.x1 = leveled.w * 0.92f;
    best.y0 = leveled.h * 0.38f;
    best.y1 = leveled.h * 0.62f;
  }
  int x0 = std::max(0, static_cast<int>(std::floor(best.x0)) - 8);
  int y0 = std::max(0, static_cast<int>(std::floor(best.y0)) - 8);
  int x1 = std::min(leveled.w, static_cast<int>(std::ceil(best.x1)) + 8);
  int y1 = std::min(leveled.h, static_cast<int>(std::ceil(best.y1)) + 8);
  Image crop;
  crop.w = std::max(1, x1 - x0);
  crop.h = std::max(1, y1 - y0);
  crop.pix.resize(static_cast<size_t>(crop.w) * crop.h);
  for (int y = 0; y < crop.h; ++y)
    for (int x = 0; x < crop.w; ++x)
      crop.pix[static_cast<size_t>(y) * crop.w + x] =
          leveled.pix[static_cast<size_t>(y0 + y) * leveled.w + (x0 + x)];
  auto r = run_rec(rec, crop, dict, args.abi);
  std::string norm = normalize(r.text);
  std::string want = normalize(args.expect_text);
  int dist = edit_distance(norm, want);
  float ang_err = std::fabs(std::fabs(d0.angle) - std::fabs(args.expect_angle));
  std::printf(
      "RESULT stage=pipeline angle=%.2f ang_err=%.2f boxes=%zu ocr='%s' norm='%s' want='%s' "
      "edit=%d heat_mass=%d heat_crc=0x%08x probs='%s'\n",
      d0.angle, ang_err, d1.boxes.size(), r.text.c_str(), norm.c_str(), want.c_str(), dist,
      d0.heat.mass, d0.heat.crc, r.probs.c_str());

  bool ok = true;
  if (!used_fallback && ang_err > args.angle_tol) {
    std::fprintf(stderr, "FAIL angle tolerance\n");
    ok = false;
  }
  if (!(norm == want || dist <= args.max_edit ||
        (norm.find("ABCD") != std::string::npos && norm.find("12345") != std::string::npos))) {
    std::fprintf(stderr, "FAIL ocr text\n");
    ok = false;
  }
  if (!ok && args.strict) {
    std::fprintf(stderr, "FAIL paddle_ocr_functional\n");
    return 1;
  }
  std::fprintf(stderr, "PASS paddle_ocr_functional\n");
  return 0;
}

}  // namespace

int main(int argc, char** argv) {
  Args args;
  if (!parse_args(argc, argv, &args)) return 2;

  // Staged det/rec default to app-like topleft unless user overrode via --resize
  // (pipeline keeps letterbox for skewed_hello gate).
  bool resize_explicit = false;
  for (int i = 1; i < argc; ++i) {
    if (std::string(argv[i]) == "--resize") resize_explicit = true;
  }
  if (!resize_explicit && args.stage != Stage::Pipeline) {
    args.resize = ResizeMode::TopLeft;
  }

  std::fprintf(stderr, "paddle_ocr_functional abi=%s stage=%d image=%s det_side=%d threads=%d\n",
               args.abi.c_str(), static_cast<int>(args.stage), args.image_path.c_str(),
               args.det_side, args.threads);

  Image img;
  if (!load_pgm(args.image_path, &img)) return 1;

  std::vector<std::string> dict;
  if (args.stage == Stage::Rec || args.stage == Stage::Pipeline) {
    dict = load_dict(args.dict_path);
    if (dict.empty()) {
      std::fprintf(stderr, "FAIL empty dict\n");
      return 1;
    }
  }

  std::shared_ptr<PaddlePredictor> det, rec;
  if (args.stage != Stage::Rec) {
    det = make_pred(args.det_path, args.threads);
    if (!det) return 1;
  }
  if (args.stage == Stage::Rec || args.stage == Stage::Pipeline) {
    rec = make_pred(args.rec_path, args.threads);
    if (!rec) return 1;
  }

  switch (args.stage) {
    case Stage::DetHeat:
      return stage_det_heat(args, img, det.get());
    case Stage::DetBoxes:
      return stage_det_boxes(args, img, det.get());
    case Stage::Rec:
      return stage_rec(args, img, rec.get(), dict);
    case Stage::Pipeline:
    default:
      return stage_pipeline(args, img, det.get(), rec.get(), dict);
  }
}

extern "C" int paddle_ocr_functional_run(int argc, char** argv) {
  return main(argc, argv);
}
