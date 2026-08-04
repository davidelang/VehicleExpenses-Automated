#!/usr/bin/env python3
"""Make LITE_BUILD_TAILOR conv helper copies skip missing files (x86 has no winograd etc)."""
from pathlib import Path
import sys
p = Path(sys.argv[1] if len(sys.argv) > 1 else "lite/kernels/CMakeLists.txt")
t = p.read_text()
if "SAFE_CONV_COPY_PATCH" in t:
    print("already safe-copy patched")
    raise SystemExit(0)
# Replace the whole execute_process copy block for conv_compute
old = '''            if (${filename} STREQUAL "conv_compute")
                execute_process(
                    COMMAND ${CMAKE_COMMAND} -E copy "${PADDLE_SOURCE_DIR}/lite/kernels/${target}/conv_depthwise.cc" "${kernel_tailor_src_dir}/conv_depthwise_${target}_${suffix}.cc"
                    COMMAND ${CMAKE_COMMAND} -E copy "${PADDLE_SOURCE_DIR}/lite/kernels/${target}/conv_winograd.cc" "${kernel_tailor_src_dir}/conv_winograd_${target}_${suffix}.cc"
                    COMMAND ${CMAKE_COMMAND} -E copy "${PADDLE_SOURCE_DIR}/lite/kernels/${target}/conv_direct.cc" "${kernel_tailor_src_dir}/conv_direct_${target}_${suffix}.cc"
                    COMMAND ${CMAKE_COMMAND} -E copy "${PADDLE_SOURCE_DIR}/lite/kernels/${target}/conv_gemmlike.cc" "${kernel_tailor_src_dir}/conv_gemmlike_${target}_${suffix}.cc"
                )
            endif()'''
new = '''            if (${filename} STREQUAL "conv_compute")
                # SAFE_CONV_COPY_PATCH: only copy helpers that exist (x86 lacks winograd/gemmlike/depthwise_common)
                foreach(_helper conv_depthwise conv_winograd conv_direct conv_gemmlike conv_depthwise_common)
                  if(EXISTS "${PADDLE_SOURCE_DIR}/lite/kernels/${target}/${_helper}.cc")
                    execute_process(
                      COMMAND ${CMAKE_COMMAND} -E copy
                        "${PADDLE_SOURCE_DIR}/lite/kernels/${target}/${_helper}.cc"
                        "${kernel_tailor_src_dir}/${_helper}_${target}_${suffix}.cc")
                  endif()
                endforeach()
            endif()'''
if old not in t:
    # try with depthwise_common already inserted
    old2 = old.replace(
        'conv_gemmlike_${target}_${suffix}.cc"\n                )',
        'conv_gemmlike_${target}_${suffix}.cc"\n'
        '                    COMMAND ${CMAKE_COMMAND} -E copy "${PADDLE_SOURCE_DIR}/lite/kernels/${target}/conv_depthwise_common.cc" "${kernel_tailor_src_dir}/conv_depthwise_common_${target}_${suffix}.cc"\n'
        '                )'
    )
    if old2 in t:
        old = old2
    else:
        print("ERROR block not found", file=sys.stderr)
        idx = t.find('conv_compute')
        print(repr(t[idx:idx+600]))
        raise SystemExit(1)
p.write_text(t.replace(old, new, 1))
print("safe conv copy patched:", p)
