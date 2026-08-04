#!/usr/bin/env python3
"""Patch Paddle-Lite lite/kernels/CMakeLists.txt for FP16 tailor builds.

Upstream LITE_BUILD_TAILOR copies conv_depthwise/winograd/direct/gemmlike when
stripping conv_compute, but not conv_depthwise_common.cc. FP16 depthwise uses
DepthwiseConvCommon from that file, so the link fails without this copy.
"""
from pathlib import Path
import sys

def main() -> int:
    p = Path(sys.argv[1] if len(sys.argv) > 1 else "lite/kernels/CMakeLists.txt")
    t = p.read_text()
    if "conv_depthwise_common.cc" in t and "conv_depthwise_common_${target}_${suffix}.cc" in t:
        print("already patched:", p)
        return 0
    old = (
        'COMMAND ${CMAKE_COMMAND} -E copy '
        '"${PADDLE_SOURCE_DIR}/lite/kernels/${target}/conv_gemmlike.cc" '
        '"${kernel_tailor_src_dir}/conv_gemmlike_${target}_${suffix}.cc"\n'
        "                )"
    )
    new = (
        'COMMAND ${CMAKE_COMMAND} -E copy '
        '"${PADDLE_SOURCE_DIR}/lite/kernels/${target}/conv_gemmlike.cc" '
        '"${kernel_tailor_src_dir}/conv_gemmlike_${target}_${suffix}.cc"\n'
        "                    COMMAND ${CMAKE_COMMAND} -E copy "
        '"${PADDLE_SOURCE_DIR}/lite/kernels/${target}/conv_depthwise_common.cc" '
        '"${kernel_tailor_src_dir}/conv_depthwise_common_${target}_${suffix}.cc"\n'
        "                )"
    )
    if old not in t:
        print("ERROR: tailor conv_compute copy block not found in", p, file=sys.stderr)
        return 1
    p.write_text(t.replace(old, new, 1))
    print("patched:", p)
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
