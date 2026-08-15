// heatmap_stage_host — Linux amd64 det-only stage (feed + heatmap gates).
// Feed recipe approximates Android HeatmapStageDump / prepareScale + populateMonoUInt8:
//   long-edge scale to S, INTER_AREA-like box filter, pad content to 32-align, pad into tier square.
//
// Usage:
//   heatmap_stage_host --det det.nb --image mono.pgm --product-path uint8_fp32_u8
//   [--out-jsonl results.jsonl] [--threads 1]
//   [--scales 2048]          comma-separated long-edge scales (default 224,608,1024)
//   [--feed-mode product|exact]  product=tier ladder 224/608/1024… (default);
//                                exact=scale×scale like multi-scale det outer
//   [--dump-heat path.f32]   write last scale heatmap as float32 LE + path.json meta
//
// Build: scripts/build-heatmap-stage-host.sh
// Mem:   /usr/bin/time -v …  (Maximum resident set size)

#include <cstdint>
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

#include <paddle_api.h>

using namespace paddle::lite_api;

struct Image {
  int w = 0, h = 0;
  std::vector<uint8_t> y;
};

static bool load_pgm(const std::string& path, Image* im) {
  std::ifstream f(path, std::ios::binary);
  if (!f) return false;
  std::string magic;
  f >> magic;
  if (magic != "P5") return false;
  int maxv = 0;
  f >> im->w >> im->h >> maxv;
  f.get();
  if (im->w <= 0 || im->h <= 0) return false;
  im->y.resize(static_cast<size_t>(im->w) * im->h);
  f.read(reinterpret_cast<char*>(im->y.data()), static_cast<std::streamsize>(im->y.size()));
  return true;
}

static uint32_t crc32_bytes(const uint8_t* p, size_t n) {
  uint32_t c = 0xffffffffu;
  for (size_t i = 0; i < n; ++i) {
    c ^= p[i];
    for (int k = 0; k < 8; ++k) c = (c >> 1) ^ (0xedb88320u & (0u - (c & 1u)));
  }
  return ~c;
}

static uint32_t crc32_f32(const float* p, size_t n) {
  uint32_t c = 0xffffffffu;
  for (size_t i = 0; i < n; ++i) {
    uint32_t bits;
    std::memcpy(&bits, &p[i], 4);
    for (int b = 0; b < 4; ++b) {
      uint8_t by = static_cast<uint8_t>((bits >> (8 * b)) & 0xff);
      c ^= by;
      for (int k = 0; k < 8; ++k) c = (c >> 1) ^ (0xedb88320u & (0u - (c & 1u)));
    }
  }
  return ~c;
}

// Box-filter resize (approx OpenCV INTER_AREA for downscale).
static void resize_area(const Image& src, int tw, int th, std::vector<uint8_t>* dst) {
  dst->assign(static_cast<size_t>(tw) * th, 0);
  for (int y = 0; y < th; ++y) {
    double y0 = (double)y * src.h / th;
    double y1 = (double)(y + 1) * src.h / th;
    int iy0 = (int)std::floor(y0);
    int iy1 = std::min(src.h, (int)std::ceil(y1));
    for (int x = 0; x < tw; ++x) {
      double x0 = (double)x * src.w / tw;
      double x1 = (double)(x + 1) * src.w / tw;
      int ix0 = (int)std::floor(x0);
      int ix1 = std::min(src.w, (int)std::ceil(x1));
      double sum = 0, area = 0;
      for (int iy = iy0; iy < iy1; ++iy) {
        double yb0 = std::max(y0, (double)iy);
        double yb1 = std::min(y1, (double)(iy + 1));
        for (int ix = ix0; ix < ix1; ++ix) {
          double xb0 = std::max(x0, (double)ix);
          double xb1 = std::min(x1, (double)(ix + 1));
          double a = (xb1 - xb0) * (yb1 - yb0);
          sum += a * src.y[static_cast<size_t>(iy) * src.w + ix];
          area += a;
        }
      }
      (*dst)[static_cast<size_t>(y) * tw + x] =
          area > 0 ? static_cast<uint8_t>(std::min(255.0, sum / area + 0.5)) : 0;
    }
  }
}

// Product path: scale long edge to S, 32-align, letterbox into discrete product tiers.
static const int kProductTiers[] = {224, 608, 1024, 2048, 2560};

// Multi-scale det path: host canvas / Lite feed is exact scale×scale (outer=scale),
// content long-edge scaled into that square (matches MultiScaleDetRunner feedByTier[scale]).
enum class FeedMode { ProductTier, ExactSquare };

static void make_feed(const Image& im, int scale, FeedMode mode, std::vector<uint8_t>* feed,
                      int* tier_out, int* outer_w, int* outer_h, int* content_w, int* content_h) {
  int long_edge = std::max(im.w, im.h);
  float sc = long_edge <= scale ? 1.f : float(scale) / float(long_edge);
  int tw = std::max(1, int(im.w * sc));
  int th = std::max(1, int(im.h * sc));
  *content_w = tw;
  *content_h = th;

  int tier;
  if (mode == FeedMode::ExactSquare) {
    // Exact multi-scale outer: requested scale (clamped to at least content side).
    tier = std::max(scale, std::max(tw, th));
    *outer_w = tier;
    *outer_h = tier;
  } else {
    int aw = ((tw + 31) / 32) * 32;
    int ah = ((th + 31) / 32) * 32;
    *outer_w = aw;
    *outer_h = ah;
    tier = 2560;
    for (int t : kProductTiers) {
      if (t >= std::max(aw, ah)) {
        tier = t;
        break;
      }
    }
  }
  *tier_out = tier;

  std::vector<uint8_t> resized;
  resize_area(im, tw, th, &resized);
  feed->assign(static_cast<size_t>(tier) * tier, 0);
  int copy_h = std::min(th, tier);
  int copy_w = std::min(tw, tier);
  for (int y = 0; y < copy_h; ++y)
    for (int x = 0; x < copy_w; ++x)
      (*feed)[static_cast<size_t>(y) * tier + x] = resized[static_cast<size_t>(y) * tw + x];
}

static bool read_heatmap(const Tensor& out, std::vector<float>* heat, int* H, int* W) {
  auto shape = out.shape();
  if (shape.size() < 2) return false;
  *H = static_cast<int>(shape[shape.size() - 2]);
  *W = static_cast<int>(shape[shape.size() - 1]);
  size_t n = static_cast<size_t>(*H) * *W;
  heat->assign(n, 0.f);
  auto prec = out.precision();
  if (prec == PRECISION(kUInt8) || static_cast<int>(prec) == 3 /*kUInt8 often*/) {
    std::vector<uint8_t> u8(n, 0);
    try {
      out.CopyToCpu(u8.data());
    } catch (...) {
      const uint8_t* p = out.data<uint8_t>();
      if (!p) return false;
      std::memcpy(u8.data(), p, n);
    }
    for (size_t i = 0; i < n; ++i) (*heat)[i] = u8[i] / 255.f;
    return true;
  }
  try {
    out.CopyToCpu(heat->data());
    return true;
  } catch (...) {
    const float* p = out.data<float>();
    if (!p) return false;
    std::memcpy(heat->data(), p, n * sizeof(float));
    return true;
  }
}

static void hist100(const std::vector<float>& heat, int hist[100]) {
  std::memset(hist, 0, 100 * sizeof(int));
  float mx = 0.f;
  for (float v : heat) mx = std::max(mx, v);
  for (float v : heat) {
    int b;
    if (mx > 1.5f) {
      b = int(v);
      if (b > 99) b = 99;
      if (b < 0) b = 0;
    } else {
      b = int(v * 99.f + 0.5f);
      if (b > 99) b = 99;
      if (b < 0) b = 0;
    }
    hist[b]++;
  }
}

static std::vector<int> parse_scales(const char* s) {
  std::vector<int> out;
  std::string cur;
  for (const char* p = s;; ++p) {
    if (*p == ',' || *p == '\0') {
      if (!cur.empty()) out.push_back(std::atoi(cur.c_str()));
      cur.clear();
      if (*p == '\0') break;
    } else {
      cur.push_back(*p);
    }
  }
  return out;
}

int main(int argc, char** argv) {
  std::string det_nb, photo, out_jsonl, product_path = "uint8_fp32_u8", label = "linux-x86_64";
  std::string dump_heat;
  int threads = 1;
  FeedMode feed_mode = FeedMode::ProductTier;
  std::vector<int> scales = {224, 608, 1024};

  for (int i = 1; i < argc; ++i) {
    std::string a = argv[i];
    auto need = [&](const char*) -> const char* {
      if (i + 1 >= argc) std::exit(2);
      return argv[++i];
    };
    if (a == "--det") det_nb = need("--det");
    else if (a == "--image") photo = need("--image");
    else if (a == "--out-jsonl") out_jsonl = need("--out-jsonl");
    else if (a == "--product-path") product_path = need("--product-path");
    else if (a == "--label") label = need("--label");
    else if (a == "--threads") threads = std::atoi(need("--threads"));
    else if (a == "--scales") scales = parse_scales(need("--scales"));
    else if (a == "--feed-mode") {
      std::string m = need("--feed-mode");
      if (m == "product" || m == "tier") feed_mode = FeedMode::ProductTier;
      else if (m == "exact" || m == "square") feed_mode = FeedMode::ExactSquare;
      else {
        std::fprintf(stderr, "unknown --feed-mode %s (use product|exact)\n", m.c_str());
        return 2;
      }
    } else if (a == "--dump-heat") dump_heat = need("--dump-heat");
  }
  if (det_nb.empty() || photo.empty()) {
    std::fprintf(stderr,
                 "usage: heatmap_stage_host --det det.nb --image mono.pgm "
                 "[--scales 256,512,768,1024] [--feed-mode product|exact] "
                 "[--dump-heat out.f32] [--out-jsonl f]\n");
    return 2;
  }
  if (scales.empty()) scales = {2048};

  Image im;
  if (!load_pgm(photo, &im)) {
    std::fprintf(stderr, "FAIL load %s\n", photo.c_str());
    return 1;
  }

  MobileConfig config;
  config.set_model_from_file(det_nb);
  config.set_threads(threads);
  auto pred = CreatePaddlePredictor<MobileConfig>(config);
  if (!pred) {
    std::fprintf(stderr, "FAIL CreatePaddlePredictor\n");
    return 1;
  }

  std::ostringstream js;
  const std::string base = photo.substr(photo.find_last_of("/\\") + 1);
  const char* mode_s = feed_mode == FeedMode::ExactSquare ? "exact" : "product";
  js << "{\"file\":\"" << base << "\",\"product_path\":\"" << product_path << "\",\"label\":\""
     << label << "\",\"feed_mode\":\"" << mode_s << "\",";
  js << "\"source_w\":" << im.w << ",\"source_h\":" << im.h << ",";
  js << "\"source_crc32\":" << crc32_bytes(im.y.data(), im.y.size()) << ",";
  long ssum = 0;
  for (auto v : im.y) ssum += v;
  js << "\"source_sum\":" << ssum << ",\"scales\":[";

  for (size_t si = 0; si < scales.size(); ++si) {
    int scale = scales[si];
    std::vector<uint8_t> feed;
    int tier = 0, ow = 0, oh = 0, cw = 0, ch = 0;
    make_feed(im, scale, feed_mode, &feed, &tier, &ow, &oh, &cw, &ch);

    auto input = pred->GetInput(0);
    input->Resize({1, 1, tier, tier});
    try {
      input->SetPrecision(PRECISION(kUInt8));
    } catch (...) {
    }
    uint8_t* dst = nullptr;
    try {
      dst = input->mutable_data<uint8_t>();
    } catch (...) {
      dst = nullptr;
    }
    if (!dst) {
      std::fprintf(stderr, "FAIL mutable_data u8 tier=%d\n", tier);
      return 1;
    }
    std::memcpy(dst, feed.data(), feed.size());
    pred->Run();
    auto out = pred->GetOutput(0);
    std::vector<float> heat;
    int H = 0, W = 0;
    if (!read_heatmap(*out, &heat, &H, &W)) {
      std::fprintf(stderr, "FAIL heatmap scale=%d\n", scale);
      return 1;
    }
    int hist[100];
    hist100(heat, hist);
    int mass = 0;
    for (int b = 1; b < 100; ++b) mass += hist[b];
    long fsum = 0;
    for (auto v : feed) fsum += v;
    double hsum = 0;
    float hmax = 0.f;
    for (float v : heat) {
      hsum += v;
      hmax = std::max(hmax, v);
    }
    std::fprintf(stderr,
                 "scale=%d feed_mode=%s feed=%dx%d heat=%dx%d prec=%d mass=%d sum=%g max=%g\n",
                 scale, mode_s, tier, tier, W, H, static_cast<int>(out->precision()), mass, hsum,
                 hmax);

    if (si) js << ",";
    js << "{\"scale\":" << scale << ",\"feed_mode\":\"" << mode_s << "\",\"tier\":" << tier
       << ",\"outer_w\":" << ow << ",\"outer_h\":" << oh << ",\"content_w\":" << cw
       << ",\"content_h\":" << ch
       << ",\"feed_crc32\":" << crc32_bytes(feed.data(), feed.size()) << ",\"feed_sum\":" << fsum
       << ",\"heat_w\":" << W << ",\"heat_h\":" << H << ",\"heatmap_hist0\":" << hist[0]
       << ",\"heatmap_mass_bins1_99\":" << mass
       << ",\"heat_crc32_f32_bits\":" << crc32_f32(heat.data(), heat.size()) << ",\"heat_sum\":" << hsum
       << ",\"heatmap_hist\":[";
    for (int b = 0; b < 100; ++b) {
      if (b) js << ",";
      js << hist[b];
    }
    js << "]}";

    // dump last scale (or any) when requested
    if (!dump_heat.empty() && si + 1 == scales.size()) {
      std::ofstream hf(dump_heat, std::ios::binary);
      hf.write(reinterpret_cast<const char*>(heat.data()),
               static_cast<std::streamsize>(heat.size() * sizeof(float)));
      std::ofstream mf(dump_heat + ".json");
      mf << "{\"heat_w\":" << W << ",\"heat_h\":" << H << ",\"scale\":" << scale
         << ",\"tier\":" << tier << ",\"heat_sum\":" << hsum << ",\"heat_max\":" << hmax
         << ",\"mass\":" << mass << "}\n";
      std::fprintf(stderr, "wrote %s (%zuxf32)\n", dump_heat.c_str(), heat.size());
    }
  }
  js << "]}";

  std::string line = js.str();
  if (!out_jsonl.empty()) {
    std::ofstream o(out_jsonl, std::ios::app);
    o << line << "\n";
  }
  std::puts(line.c_str());
  return 0;
}
