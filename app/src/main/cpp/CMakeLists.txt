cmake_minimum_required(VERSION 3.22.1)

project("native_components")

# 16KB ELF max-page-size for Android 15+ (all ABIs; critical for armeabi-v7a on NDK r28).
add_link_options(-Wl,-z,max-page-size=16384)

# LibRaw Configuration
set(LIBRAW_PATH ${CMAKE_SOURCE_DIR}/libraw)
add_definitions(-DLIBRAW_NOTHREADS -DLIBRAW_NODISPLAY)

# Collect LibRaw sources
set(LIBRAW_SOURCES
    ${LIBRAW_PATH}/src/decoders/canon_600.cpp
    ${LIBRAW_PATH}/src/decoders/crx.cpp
    ${LIBRAW_PATH}/src/decoders/decoders_dcraw.cpp
    ${LIBRAW_PATH}/src/decoders/decoders_libraw.cpp
    ${LIBRAW_PATH}/src/decoders/decoders_libraw_dcrdefs.cpp
    ${LIBRAW_PATH}/src/decoders/dng.cpp
    ${LIBRAW_PATH}/src/decoders/fp_dng.cpp
    ${LIBRAW_PATH}/src/decoders/fuji_compressed.cpp
    ${LIBRAW_PATH}/src/decoders/generic.cpp
    ${LIBRAW_PATH}/src/decoders/kodak_decoders.cpp
    ${LIBRAW_PATH}/src/decoders/load_mfbacks.cpp
    ${LIBRAW_PATH}/src/decoders/smal.cpp
    ${LIBRAW_PATH}/src/decoders/unpack.cpp
    ${LIBRAW_PATH}/src/decoders/unpack_thumb.cpp
    ${LIBRAW_PATH}/src/demosaic/aahd_demosaic.cpp
    ${LIBRAW_PATH}/src/demosaic/ahd_demosaic.cpp
    ${LIBRAW_PATH}/src/demosaic/dcb_demosaic.cpp
    ${LIBRAW_PATH}/src/demosaic/dht_demosaic.cpp
    ${LIBRAW_PATH}/src/demosaic/misc_demosaic.cpp
    ${LIBRAW_PATH}/src/demosaic/xtrans_demosaic.cpp
    ${LIBRAW_PATH}/src/integration/dngsdk_glue.cpp
    ${LIBRAW_PATH}/src/integration/rawspeed_glue.cpp
    ${LIBRAW_PATH}/src/libraw_c_api.cpp
    ${LIBRAW_PATH}/src/libraw_datastream.cpp
    ${LIBRAW_PATH}/src/metadata/adobepano.cpp
    ${LIBRAW_PATH}/src/metadata/canon.cpp
    ${LIBRAW_PATH}/src/metadata/ciff.cpp
    ${LIBRAW_PATH}/src/metadata/cr3_parser.cpp
    ${LIBRAW_PATH}/src/metadata/epson.cpp
    ${LIBRAW_PATH}/src/metadata/exif_gps.cpp
    ${LIBRAW_PATH}/src/metadata/fuji.cpp
    ${LIBRAW_PATH}/src/metadata/hasselblad_model.cpp
    ${LIBRAW_PATH}/src/metadata/identify.cpp
    ${LIBRAW_PATH}/src/metadata/identify_tools.cpp
    ${LIBRAW_PATH}/src/metadata/kodak.cpp
    ${LIBRAW_PATH}/src/metadata/leica.cpp
    ${LIBRAW_PATH}/src/metadata/makernotes.cpp
    ${LIBRAW_PATH}/src/metadata/mediumformat.cpp
    ${LIBRAW_PATH}/src/metadata/minolta.cpp
    ${LIBRAW_PATH}/src/metadata/misc_parsers.cpp
    ${LIBRAW_PATH}/src/metadata/nikon.cpp
    ${LIBRAW_PATH}/src/metadata/normalize_model.cpp
    ${LIBRAW_PATH}/src/metadata/olympus.cpp
    ${LIBRAW_PATH}/src/metadata/p1.cpp
    ${LIBRAW_PATH}/src/metadata/pentax.cpp
    ${LIBRAW_PATH}/src/metadata/samsung.cpp
    ${LIBRAW_PATH}/src/metadata/sony.cpp
    ${LIBRAW_PATH}/src/metadata/tiff.cpp
    ${LIBRAW_PATH}/src/postprocessing/aspect_ratio.cpp
    ${LIBRAW_PATH}/src/postprocessing/dcraw_process.cpp
    ${LIBRAW_PATH}/src/postprocessing/mem_image.cpp
    ${LIBRAW_PATH}/src/postprocessing/postprocessing_aux.cpp
    ${LIBRAW_PATH}/src/postprocessing/postprocessing_ph.cpp
    ${LIBRAW_PATH}/src/postprocessing/postprocessing_utils.cpp
    ${LIBRAW_PATH}/src/postprocessing/postprocessing_utils_dcrdefs.cpp
    ${LIBRAW_PATH}/src/preprocessing/ext_preprocess.cpp
    ${LIBRAW_PATH}/src/preprocessing/preprocessing_ph.cpp
    ${LIBRAW_PATH}/src/preprocessing/raw2image.cpp
    ${LIBRAW_PATH}/src/preprocessing/subtract_black.cpp
    ${LIBRAW_PATH}/src/tables/cameralist.cpp
    ${LIBRAW_PATH}/src/tables/colorconst.cpp
    ${LIBRAW_PATH}/src/tables/colordata.cpp
    ${LIBRAW_PATH}/src/tables/wblists.cpp
    ${LIBRAW_PATH}/src/utils/curves.cpp
    ${LIBRAW_PATH}/src/utils/decoder_info.cpp
    ${LIBRAW_PATH}/src/utils/init_close_utils.cpp
    ${LIBRAW_PATH}/src/utils/open.cpp
    ${LIBRAW_PATH}/src/utils/phaseone_processing.cpp
    ${LIBRAW_PATH}/src/utils/read_utils.cpp
    ${LIBRAW_PATH}/src/utils/thumb_utils.cpp
    ${LIBRAW_PATH}/src/utils/utils_dcraw.cpp
    ${LIBRAW_PATH}/src/utils/utils_libraw.cpp
    ${LIBRAW_PATH}/src/write/apply_profile.cpp
    ${LIBRAW_PATH}/src/write/file_write.cpp
    ${LIBRAW_PATH}/src/write/tiff_writer.cpp
    ${LIBRAW_PATH}/src/write/write_ph.cpp
    ${LIBRAW_PATH}/src/x3f/x3f_parse_process.cpp
    ${LIBRAW_PATH}/src/x3f/x3f_utils_patched.cpp
)

# Build LibRaw as a static library
add_library(libraw_static STATIC ${LIBRAW_SOURCES})
target_include_directories(libraw_static PUBLIC ${LIBRAW_PATH})

# Prebuilts: opencv under jniLibs/, Paddle under third_party/paddle/artifact/jni/
# (the pin artifact dir; Gradle packages it from there too). Always set IMPORTED_SONAME so consumers get a
# basename DT_NEEDED. Thin x86 libpaddle_lite_jni.so historically had *no*
# ELF SONAME; without this, lld embeds the host absolute IMPORTED_LOCATION
# and the APK fails dlopen on device:
#   UnsatisfiedLinkError: library "/home/.../libpaddle_lite_jni.so" not found
# → NativeImageUtils class load fails → every multi-scale cell is ingest_fail
# in ~1 minute (looks like an impossibly fast "done" run).
set(JNI_LIBS_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})
set(PADDLE_JNI_DIR ${CMAKE_SOURCE_DIR}/../../../../third_party/paddle/artifact/jni/${ANDROID_ABI})
foreach(_pso libpaddle_lite_jni.so libpaddle_light_api_shared.so)
    if(NOT EXISTS ${PADDLE_JNI_DIR}/${_pso})
        message(FATAL_ERROR "missing ${PADDLE_JNI_DIR}/${_pso} (run fetch-deps build paddle)")
    endif()
endforeach()

# IMPORTED_NO_SONAME + -L: link with -lname so DT_NEEDED is the basename even
# when the prebuilt ELF has no SONAME (x86 thin jni). IMPORTED_SONAME alone is
# not enough for lld when the input .so lacks DT_SONAME.
add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES
    IMPORTED_LOCATION ${JNI_LIBS_DIR}/libopencv_java4.so
    IMPORTED_SONAME "libopencv_java4.so"
    IMPORTED_NO_SONAME TRUE
)

add_library(paddle_lite_jni SHARED IMPORTED)
set_target_properties(paddle_lite_jni PROPERTIES
    IMPORTED_LOCATION ${PADDLE_JNI_DIR}/libpaddle_lite_jni.so
    IMPORTED_SONAME "libpaddle_lite_jni.so"
    IMPORTED_NO_SONAME TRUE
)

# x86 split: thin jni + fat light_api_shared. Every ABI ships light_api_shared.
add_library(paddle_light_api_shared SHARED IMPORTED)
set_target_properties(paddle_light_api_shared PROPERTIES
    IMPORTED_LOCATION ${PADDLE_JNI_DIR}/libpaddle_light_api_shared.so
    IMPORTED_SONAME "libpaddle_light_api_shared.so"
    IMPORTED_NO_SONAME TRUE
)
set(PADDLE_EXTRA_LIBS paddle_light_api_shared)

# 1. buffer_set library (Isolated memory management)
add_library(buffer_set SHARED BufferSet.cpp)
target_link_directories(buffer_set PRIVATE ${JNI_LIBS_DIR} ${PADDLE_JNI_DIR})
target_link_libraries(buffer_set jnigraphics log opencv_java4)

# 2. native_ocr library (OpenCV dependent, uses c++_shared)
add_library(native_ocr SHARED NativeImageUtils.cpp ExpRotGmm.cpp QfRotGmm.cpp VeJni.cpp DngDevelop.cpp)
target_link_directories(native_ocr PRIVATE ${JNI_LIBS_DIR} ${PADDLE_JNI_DIR})
target_link_libraries(native_ocr
    jnigraphics
    log
    z
    opencv_java4
    paddle_lite_jni
    ${PADDLE_EXTRA_LIBS}
    libraw_static
)

# Include directories
include_directories(include)
include_directories(${LIBRAW_PATH})

# Per-ABI OCR models (.nb via .incbin). Not packaged as assets.
# VE_EMBED_EXP_DET is passed only by the qa build type.
if(NOT DEFINED VE_EMBED_EXP_DET)
    set(VE_EMBED_EXP_DET OFF)
endif()
if(VE_EMBED_EXP_DET)
    target_compile_definitions(native_ocr PRIVATE VE_EMBED_EXP_DET=1)
    add_library(native_ocr_qa SHARED ContentExpandNative.cpp NativeImageUtilsQa.cpp DngDevelop.cpp)
    target_link_directories(native_ocr_qa PRIVATE ${JNI_LIBS_DIR} ${PADDLE_JNI_DIR})
    target_link_options(native_ocr_qa PRIVATE
        -Wl,--wrap=malloc
        -Wl,--wrap=calloc
        -Wl,--wrap=realloc
    )
    target_link_libraries(native_ocr_qa
        jnigraphics
        log
        z
        opencv_java4
        paddle_lite_jni
        ${PADDLE_EXTRA_LIBS}
        libraw_static
    )
endif()
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(_pm_flavor arm64)
    set(_pm_arch armv8)
    set(_pm_prod prod_u8fp16)
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(_pm_flavor armv7)
    set(_pm_arch armv7)
    set(_pm_prod prod_u8fp32_u8)
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(_pm_flavor x86_64)
    set(_pm_arch x86_64)
    set(_pm_prod prod_u8fp32_u8)
else()
    message(FATAL_ERROR "libpaddle_models: unsupported ABI ${ANDROID_ABI}")
endif()
# Product models come straight from the paddle-models pin artifact dir
# (fetch-deps build paddle-models). Experiment dets (exp_det_ab) are not pin
# products and stay under app/src/<flavor>/paddle-models/.
set(_pm_root ${CMAKE_SOURCE_DIR}/../../${_pm_flavor}/paddle-models)
set(_pm_app ${CMAKE_SOURCE_DIR}/../../../../third_party/paddle-models/artifact/app/${_pm_arch})
set(_pm_entries
    "paddle/${_pm_prod}/det_${_pm_arch}.nb|${_pm_app}/det_${_pm_arch}.nb"
    "paddle/${_pm_prod}/rec_v3_${_pm_arch}.nb|${_pm_app}/rec_v3_${_pm_arch}.nb"
    "paddle/${_pm_prod}/rec_numeric_${_pm_arch}.nb|${_pm_app}/rec_numeric_${_pm_arch}.nb"
)
if(VE_EMBED_EXP_DET)
    set(_pm_exp "${_pm_root}/exp_det_ab/product_det_${_pm_arch}.nb")
    if(EXISTS "${_pm_exp}")
        list(APPEND _pm_entries "paddle/exp_det_ab/product_det_${_pm_arch}.nb|${_pm_exp}")
    endif()
endif()
set(_pm_cpp ${CMAKE_CURRENT_BINARY_DIR}/paddle_models_incbin.cpp)
set(_pm_syms "")
set(_pm_deps "")
set(_pm_table "")
set(_pm_i 0)
foreach(_ent IN LISTS _pm_entries)
    string(REPLACE "|" ";" _parts "${_ent}")
    list(GET _parts 0 _key)
    list(GET _parts 1 _path)
    if(NOT EXISTS "${_path}")
        message(FATAL_ERROR "libpaddle_models missing ${_path}")
    endif()
    list(APPEND _pm_deps "${_path}")
    string(APPEND _pm_syms
        "__asm__(\".section .rodata\\n\"\n"
        "        \".global pm${_pm_i}_start\\n\"\n"
        "        \"pm${_pm_i}_start:\\n\"\n"
        "        \".incbin \\\"${_path}\\\"\\n\"\n"
        "        \".global pm${_pm_i}_end\\n\"\n"
        "        \"pm${_pm_i}_end:\\n\"\n"
        "        \".section .text\\n\");\n"
        "extern \"C\" const char pm${_pm_i}_start[];\n"
        "extern \"C\" const char pm${_pm_i}_end[];\n")
    string(APPEND _pm_table "    {\"${_key}\", pm${_pm_i}_start, pm${_pm_i}_end},\n")
    math(EXPR _pm_i "${_pm_i} + 1")
endforeach()
file(WRITE "${_pm_cpp}"
"#include <jni.h>\n"
"#include <string.h>\n"
"${_pm_syms}\n"
"struct PmEntry { const char* key; const char* start; const char* end; };\n"
"static const PmEntry PM_ENTRIES[] = {\n"
"${_pm_table}"
"};\n"
"extern \"C\" JNIEXPORT jbyteArray JNICALL\n"
"Java_com_davidlang_vehicleexpensesautomated_ui_util_NativePaddleEngine_modelBlob(\n"
"    JNIEnv* env, jclass, jstring jkey) {\n"
"  const char* key = env->GetStringUTFChars(jkey, nullptr);\n"
"  const PmEntry* hit = nullptr;\n"
"  for (unsigned i = 0; i < sizeof(PM_ENTRIES) / sizeof(PM_ENTRIES[0]); ++i) {\n"
"    if (strcmp(PM_ENTRIES[i].key, key) == 0) { hit = &PM_ENTRIES[i]; break; }\n"
"  }\n"
"  env->ReleaseStringUTFChars(jkey, key);\n"
"  if (!hit) return nullptr;\n"
"  const jsize n = static_cast<jsize>(hit->end - hit->start);\n"
"  jbyteArray arr = env->NewByteArray(n);\n"
"  if (!arr) return nullptr;\n"
"  env->SetByteArrayRegion(arr, 0, n, reinterpret_cast<const jbyte*>(hit->start));\n"
"  return arr;\n"
"}\n")
# .incbin is invisible to the compiler's dependency scan: re-embed when a .nb changes.
set_source_files_properties("${_pm_cpp}" PROPERTIES OBJECT_DEPENDS "${_pm_deps}")
add_library(paddle_models SHARED "${_pm_cpp}")
target_link_libraries(paddle_models log)
