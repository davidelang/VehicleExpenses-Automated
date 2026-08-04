#!/usr/bin/env python3
"""Make paddle_lite_jni a thin wrapper over paddle_light_api_shared on x86.

Upstream fat jni embeds PADDLELITE_OBJS but forgets target_link_libraries for
cblas/xxhash, so the link fails. Deployed apps already ship thin jni + light.
"""
from pathlib import Path
import sys

p = Path(sys.argv[1] if len(sys.argv) > 1 else "lite/api/CMakeLists.txt")
t = p.read_text()
if "THIN_JNI_X86_PATCH" in t:
    print("already patched thin jni")
    raise SystemExit(0)

old = """          add_library(paddle_lite_jni SHARED $<TARGET_OBJECTS:PADDLELITE_OBJS> android/jni/native/paddle_lite_jni.cc android/jni/native/tensor_jni.cc)
            if(LITE_WITH_X86)
                add_dependencies(paddle_lite_jni eigen3)
                add_dependencies(paddle_lite_jni xxhash)
                add_dependencies(paddle_lite_jni cblas)
            endif()
          set_target_properties(paddle_lite_jni PROPERTIES COMPILE_FLAGS "${TARGET_COMIPILE_FLAGS}")
          set_target_properties(paddle_lite_jni PROPERTIES LIBRARY_OUTPUT_DIRECTORY ${PADDLE_BINARY_DIR}/lite/api/android/jni/native)
"""

new = """          # THIN_JNI_X86_PATCH: x86 thin jni links light_api (has cblas/xxhash);
          # fat jni with OBJECTS fails to link openblas/xxhash on android-x86.
          if(LITE_WITH_X86)
            add_library(paddle_lite_jni SHARED android/jni/native/paddle_lite_jni.cc android/jni/native/tensor_jni.cc)
            add_dependencies(paddle_lite_jni paddle_light_api_shared)
            target_link_libraries(paddle_lite_jni paddle_light_api_shared m log)
          else()
            add_library(paddle_lite_jni SHARED $<TARGET_OBJECTS:PADDLELITE_OBJS> android/jni/native/paddle_lite_jni.cc android/jni/native/tensor_jni.cc)
          endif()
          set_target_properties(paddle_lite_jni PROPERTIES COMPILE_FLAGS "${TARGET_COMIPILE_FLAGS}")
          set_target_properties(paddle_lite_jni PROPERTIES LIBRARY_OUTPUT_DIRECTORY ${PADDLE_BINARY_DIR}/lite/api/android/jni/native)
"""

if old not in t:
    print("ERROR: expected jni block not found", file=sys.stderr)
    # show nearby
    idx = t.find("paddle_lite_jni SHARED")
    print(repr(t[idx:idx+400]) if idx>=0 else "no paddle_lite_jni")
    raise SystemExit(1)
p.write_text(t.replace(old, new, 1))
print("patched thin jni x86:", p)
