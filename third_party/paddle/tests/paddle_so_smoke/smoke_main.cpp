// paddle_so_smoke — ISA-faithful smoke for product paddle .so files.
// Built with NDK as a (preferably static) binary per ABI and run:
//   native or qemu-x86_64  → x86_64
//   qemu-aarch64[-static]  → arm64-v8a
//   qemu-arm[-static]      → armeabi-v7a
//
// Does not need full Android/Bionic dynamic linker for the paddle SO:
// mmaps the ELF and checks machine, dynsym exports, and stamp substrings.
//
// Usage:
//   paddle_so_smoke --abi arm64-v8a --so path/to/libpaddle_lite_jni.so \
//                   [--so path/to/libpaddle_light_api_shared.so] [--strict-stamps]
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <string>
#include <vector>

namespace {

constexpr uint16_t EM_ARM = 40;
constexpr uint16_t EM_AARCH64 = 183;
constexpr uint16_t EM_X86_64 = 62;
constexpr uint8_t ELFCLASS32 = 1;
constexpr uint8_t ELFCLASS64 = 2;
constexpr uint32_t SHT_DYNSYM = 11;
constexpr uint32_t SHT_STRTAB = 3;
constexpr uint32_t SHT_DYNAMIC = 6;
constexpr int32_t DT_NEEDED = 1;
constexpr int32_t DT_STRTAB = 5;

struct Args {
  std::string abi;
  std::vector<std::string> so_paths;
  bool strict_stamps = true;
};

void usage(const char* argv0) {
  std::fprintf(stderr,
               "Usage: %s --abi <arm64-v8a|armeabi-v7a|x86_64> "
               "--so <path> [--so <path> ...] [--no-strict-stamps]\n",
               argv0);
}

bool parse_args(int argc, char** argv, Args* out) {
  for (int i = 1; i < argc; ++i) {
    std::string a = argv[i];
    if (a == "--abi" && i + 1 < argc) {
      out->abi = argv[++i];
    } else if (a == "--so" && i + 1 < argc) {
      out->so_paths.emplace_back(argv[++i]);
    } else if (a == "--no-strict-stamps") {
      out->strict_stamps = false;
    } else if (a == "-h" || a == "--help") {
      usage(argv[0]);
      return false;
    } else {
      std::fprintf(stderr, "unknown arg: %s\n", a.c_str());
      usage(argv[0]);
      return false;
    }
  }
  if (out->abi.empty() || out->so_paths.empty()) {
    usage(argv[0]);
    return false;
  }
  return true;
}

uint16_t expected_machine(const std::string& abi) {
  if (abi == "arm64-v8a") return EM_AARCH64;
  if (abi == "armeabi-v7a") return EM_ARM;
  if (abi == "x86_64") return EM_X86_64;
  return 0;
}

bool read_file(const std::string& path, std::vector<uint8_t>* out) {
  std::ifstream f(path, std::ios::binary);
  if (!f) {
    std::fprintf(stderr, "FAIL open %s\n", path.c_str());
    return false;
  }
  f.seekg(0, std::ios::end);
  auto n = f.tellg();
  if (n <= 0) {
    std::fprintf(stderr, "FAIL empty %s\n", path.c_str());
    return false;
  }
  f.seekg(0, std::ios::beg);
  out->resize(static_cast<size_t>(n));
  f.read(reinterpret_cast<char*>(out->data()), n);
  return static_cast<bool>(f) || f.eof();
}

template <typename T>
T rd(const std::vector<uint8_t>& b, size_t off) {
  T v{};
  if (off + sizeof(T) > b.size()) return v;
  std::memcpy(&v, b.data() + off, sizeof(T));
  return v;
}

bool contains(const std::vector<uint8_t>& b, const char* needle) {
  const size_t n = std::strlen(needle);
  if (n == 0 || b.size() < n) return false;
  for (size_t i = 0; i + n <= b.size(); ++i) {
    if (std::memcmp(b.data() + i, needle, n) == 0) return true;
  }
  return false;
}

// Required JNI export on libpaddle_lite_jni.so (all ABIs we ship).
const char* kRequiredJniSym = "Java_com_baidu_paddle_lite_PaddlePredictor_getVersion";

std::vector<const char*> stamps_for_abi(const std::string& abi) {
  if (abi == "arm64-v8a") {
    return {"uint8_to_fp16", "fp32_to_uint8"};
  }
  if (abi == "armeabi-v7a") {
    return {"uint8_to_fp32", "int8_to_fp32", "fp32_to_uint8"};
  }
  // x86: stamps live primarily on light SO
  return {"fp32_to_uint8"};
}

struct DynSymCheck {
  bool saw_required_jni = false;
  int needed_count = 0;
};

bool scan_dynsym(const std::vector<uint8_t>& b, bool is64, DynSymCheck* chk) {
  // ELF header e_shoff, e_shentsize, e_shnum, e_shstrndx
  const size_t e_shoff = is64 ? rd<uint64_t>(b, 40) : rd<uint32_t>(b, 32);
  const uint16_t e_shentsize = rd<uint16_t>(b, is64 ? 58 : 46);
  const uint16_t e_shnum = rd<uint16_t>(b, is64 ? 60 : 48);
  if (e_shoff == 0 || e_shentsize == 0 || e_shnum == 0) {
    std::fprintf(stderr, "FAIL: no section headers\n");
    return false;
  }

  size_t dynsym_off = 0, dynsym_size = 0, dynstr_off = 0, dynstr_size = 0;
  size_t dynamic_off = 0, dynamic_size = 0;
  size_t entsize = is64 ? 24 : 16;  // default Elf_Sym

  // Elf64_Shdr: name@0 type@4 flags@8 addr@16 offset@24 size@32 link@40 entsize@56
  // Elf32_Shdr: name@0 type@4 flags@8 addr@12 offset@16 size@20 link@24 entsize@36
  for (uint16_t i = 0; i < e_shnum; ++i) {
    size_t sh = e_shoff + static_cast<size_t>(i) * e_shentsize;
    uint32_t sh_type = rd<uint32_t>(b, sh + 4);
    uint64_t sh_offset = is64 ? rd<uint64_t>(b, sh + 24) : rd<uint32_t>(b, sh + 16);
    uint64_t sh_size = is64 ? rd<uint64_t>(b, sh + 32) : rd<uint32_t>(b, sh + 20);
    uint64_t sh_entsize = is64 ? rd<uint64_t>(b, sh + 56) : rd<uint32_t>(b, sh + 36);
    uint32_t sh_link = is64 ? rd<uint32_t>(b, sh + 40) : rd<uint32_t>(b, sh + 24);
    if (sh_type == SHT_DYNSYM) {
      dynsym_off = static_cast<size_t>(sh_offset);
      dynsym_size = static_cast<size_t>(sh_size);
      if (sh_entsize) entsize = static_cast<size_t>(sh_entsize);
      // sh_link → dynstr section index
      if (sh_link < e_shnum) {
        size_t str_sh = e_shoff + static_cast<size_t>(sh_link) * e_shentsize;
        dynstr_off = static_cast<size_t>(is64 ? rd<uint64_t>(b, str_sh + 24)
                                              : rd<uint32_t>(b, str_sh + 16));
        dynstr_size = static_cast<size_t>(is64 ? rd<uint64_t>(b, str_sh + 32)
                                               : rd<uint32_t>(b, str_sh + 20));
      }
    } else if (sh_type == SHT_DYNAMIC) {
      dynamic_off = static_cast<size_t>(sh_offset);
      dynamic_size = static_cast<size_t>(sh_size);
    }
  }

  if (dynsym_off && dynstr_off && dynsym_size && dynstr_size) {
    for (size_t off = dynsym_off; off + entsize <= dynsym_off + dynsym_size;
         off += entsize) {
      uint32_t st_name = rd<uint32_t>(b, off);
      if (st_name == 0) continue;
      if (st_name >= dynstr_size) continue;
      size_t name_off = dynstr_off + st_name;
      if (name_off >= b.size()) continue;
      // C-string inside dynstr
      size_t end = name_off;
      while (end < b.size() && end < dynstr_off + dynstr_size && b[end] != 0) {
        ++end;
      }
      std::string name(reinterpret_cast<const char*>(b.data() + name_off),
                       end - name_off);
      if (name == kRequiredJniSym) {
        chk->saw_required_jni = true;
      }
    }
  }

  // NEEDED count from DYNAMIC if present
  if (dynamic_off && dynamic_size) {
    size_t dent = is64 ? 16 : 8;
    for (size_t off = dynamic_off; off + dent <= dynamic_off + dynamic_size;
         off += dent) {
      int64_t tag = is64 ? static_cast<int64_t>(rd<uint64_t>(b, off))
                         : static_cast<int64_t>(rd<int32_t>(b, off));
      if (tag == DT_NEEDED) chk->needed_count++;
      if (tag == 0) break;
    }
  }
  return true;
}

bool check_one_so(const std::string& abi, const std::string& path,
                  bool strict_stamps, bool* jni_ok_out) {
  std::fprintf(stdout, "== smoke %s %s ==\n", abi.c_str(), path.c_str());
  std::vector<uint8_t> b;
  if (!read_file(path, &b) || b.size() < 64) return false;
  if (!(b[0] == 0x7f && b[1] == 'E' && b[2] == 'L' && b[3] == 'F')) {
    std::fprintf(stderr, "FAIL: not ELF\n");
    return false;
  }
  const bool is64 = b[4] == ELFCLASS64;
  const uint16_t machine = rd<uint16_t>(b, 18);
  const uint16_t want = expected_machine(abi);
  if (machine != want) {
    std::fprintf(stderr, "FAIL: e_machine=%u want=%u\n", machine, want);
    return false;
  }
  std::fprintf(stdout, "  ELF class=%s machine=%u OK\n", is64 ? "64" : "32",
               machine);

  DynSymCheck chk;
  if (!scan_dynsym(b, is64, &chk)) return false;
  std::fprintf(stdout, "  DT_NEEDED count=%d\n", chk.needed_count);

  const bool is_jni = path.find("libpaddle_lite_jni.so") != std::string::npos;
  if (is_jni) {
    if (!chk.saw_required_jni) {
      // thin x86 jni still exports getVersion
      std::fprintf(stderr, "FAIL: missing dynsym %s\n", kRequiredJniSym);
      return false;
    }
    std::fprintf(stdout, "  dynsym %s OK\n", kRequiredJniSym);
    if (jni_ok_out) *jni_ok_out = true;
  }

  // Stamps: require on at least one SO of the ABI (checked by driver across set).
  // Per-file: report presence.
  auto stamps = stamps_for_abi(abi);
  int found = 0;
  for (const char* s : stamps) {
    bool hit = contains(b, s);
    std::fprintf(stdout, "  stamp %s: %s\n", s, hit ? "yes" : "no");
    if (hit) found++;
  }
  // x86 thin jni may lack stamps; light must have them — per-file only warn.
  if (strict_stamps && is_jni && abi != "x86_64" && found == 0) {
    std::fprintf(stderr, "FAIL: no expected stamps in jni SO\n");
    return false;
  }
  if (strict_stamps && path.find("libpaddle_light_api_shared") != std::string::npos &&
      abi == "x86_64" && found == 0) {
    std::fprintf(stderr, "FAIL: no expected stamps in x86 light SO\n");
    return false;
  }
  if (strict_stamps && abi == "armeabi-v7a" && is_jni && found < 2) {
    std::fprintf(stderr, "FAIL: armv7 jni missing fp32 calib stamps\n");
    return false;
  }
  if (strict_stamps && abi == "arm64-v8a" && is_jni &&
      !(contains(b, "uint8_to_fp16") && contains(b, "fp32_to_uint8"))) {
    std::fprintf(stderr, "FAIL: arm64 jni missing fp16 prod stamps\n");
    return false;
  }

  std::fprintf(stdout, "  PASS %s\n", path.c_str());
  return true;
}

}  // namespace

int main(int argc, char** argv) {
  Args args;
  if (!parse_args(argc, argv, &args)) return 2;
  if (!expected_machine(args.abi)) {
    std::fprintf(stderr, "FAIL: unknown abi %s\n", args.abi.c_str());
    return 2;
  }

  bool any_jni = false;
  bool all_ok = true;
  for (const auto& p : args.so_paths) {
    bool jni = false;
    if (!check_one_so(args.abi, p, args.strict_stamps, &jni)) all_ok = false;
    if (jni) any_jni = true;
  }
  // Prefer at least one jni path in the set
  if (all_ok && !any_jni) {
    std::fprintf(stderr,
                 "WARN: no libpaddle_lite_jni.so in set (export check skipped)\n");
  }
  if (all_ok) {
    std::fprintf(stdout, "PASS paddle_so_smoke abi=%s files=%zu\n",
                 args.abi.c_str(), args.so_paths.size());
    return 0;
  }
  std::fprintf(stderr, "FAIL paddle_so_smoke abi=%s\n", args.abi.c_str());
  return 1;
}
