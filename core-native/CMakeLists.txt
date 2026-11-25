cmake_minimum_required(VERSION 3.22)
project(meltype_core LANGUAGES C CXX)

# Keep inference kernels optimized in a debuggable APK.
set(CMAKE_BUILD_TYPE Release CACHE STRING "Native core build type" FORCE)
if(NOT ANDROID OR NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "Meltype native core targets Android arm64-v8a")
endif()

get_filename_component(MELTYPE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/.." ABSOLUTE)
# Keep source locations readable without embedding the builder's checkout path.
add_compile_options("-ffile-prefix-map=${MELTYPE_ROOT}=.")
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(GGML_CPU ON CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8-a" CACHE STRING "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)

# The native core contains only the portable CPU backend. No driver discovery,
# shader compiler, platform accelerator or downloadable backend is required.
foreach(backend IN ITEMS ACCELERATE BLAS LLAMAFILE OPENMP OPENMP_FETCH CUDA HIP
        MUSA VULKAN WEBGPU METAL RPC SYCL OPENVINO ET ET_SYSEMU OPENCL HEXAGON
        ZDNN ZENDNN VIRTGPU VIRTGPU_BACKEND CANN)
    set(GGML_${backend} OFF CACHE BOOL "" FORCE)
endforeach()
foreach(component IN ITEMS COMMON TESTS TOOLS EXAMPLES SERVER APP MTMD)
    set(LLAMA_BUILD_${component} OFF CACHE BOOL "" FORCE)
endforeach()
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMIT "51a77c4f247ce96fb932e29c9f81625ca9d3b0b7" CACHE STRING "" FORCE)
add_subdirectory("${MELTYPE_ROOT}/vendor/llama.cpp" "${CMAKE_CURRENT_BINARY_DIR}/llama")

add_library(meltype_core SHARED native_core.cpp)
target_compile_features(meltype_core PRIVATE cxx_std_17)
target_compile_options(meltype_core PRIVATE -O2 -Wall -Wextra)
target_link_libraries(meltype_core PRIVATE llama android log)
target_link_options(meltype_core PRIVATE "-Wl,-z,max-page-size=16384")

set(OFFICIAL_CORE "${MELTYPE_ROOT}/.tools/official-meltype-artifacts/libMeltypeNative.so")
if(NOT EXISTS "${OFFICIAL_CORE}")
    message(FATAL_ERROR "Build the official Meltype arm64 Core before assembling the APK: ${OFFICIAL_CORE}")
endif()
add_library(MeltypeNative SHARED IMPORTED)
set_target_properties(MeltypeNative PROPERTIES IMPORTED_LOCATION "${OFFICIAL_CORE}")
add_library(meltype_official_jni SHARED official_bridge.cpp)
target_compile_features(meltype_official_jni PRIVATE cxx_std_17)
target_link_options(meltype_official_jni PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(meltype_official_jni PRIVATE MeltypeNative)
