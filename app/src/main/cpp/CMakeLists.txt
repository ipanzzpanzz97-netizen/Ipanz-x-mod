cmake_minimum_required(VERSION 3.22.1)
project("ipanz_audio" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

if(NOT DEFINED ORT_NATIVE_DIR OR NOT EXISTS "${ORT_NATIVE_DIR}/headers/onnxruntime_cxx_api.h")
    message(FATAL_ERROR "ORT_NATIVE_DIR tidak sah atau headers onnxruntime tidak ditemui: ${ORT_NATIVE_DIR}")
endif()

add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES
    IMPORTED_LOCATION "${ORT_NATIVE_DIR}/jni/${ANDROID_ABI}/libonnxruntime.so"
    INTERFACE_INCLUDE_DIRECTORIES "${ORT_NATIVE_DIR}/headers"
)

add_library(
    ipanz_audio
    SHARED
    ipanz_audio_engine.cpp
)

target_compile_options(
    ipanz_audio
    PRIVATE
    -O3
    -ffast-math
    -Wall
)

target_link_libraries(
    ipanz_audio
    PRIVATE
    oboe::oboe
    onnxruntime
    android
    log
)
