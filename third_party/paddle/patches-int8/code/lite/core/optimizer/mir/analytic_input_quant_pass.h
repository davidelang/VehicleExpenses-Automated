// Analytic feed quant: bake host integer feed + in-graph calib dequant.
// Default: kInt8 XOR-centered (q = (int8_t)(b ^ 128)) + int8_to_fp*.
// Optional: kUInt8 raw greyscale + uint8_to_fp* (out = (u - 128) * scale),
// same math as int8 xor without host XOR buffer rewrite.
#pragma once
#include <memory>
#include <string>
#include "lite/api/paddle_place.h"
#include "lite/core/optimizer/mir/pass.h"

namespace paddle {
namespace lite {
namespace mir {

class AnalyticInputQuantPass : public ProgramPass {
 public:
  // "int8" (default) or "uint8"
  void SetInputDtype(const std::string& dtype) { input_dtype_ = dtype; }
  const std::string& input_dtype() const { return input_dtype_; }
  void Apply(const std::unique_ptr<SSAGraph>& graph) override;

 private:
  std::string input_dtype_{"int8"};
};

}  // namespace mir
}  // namespace lite
}  // namespace paddle