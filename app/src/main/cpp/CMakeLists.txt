cmake_minimum_required(VERSION 3.22.1)
project("ipanz_audio" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)
find_package(onnxruntime REQUIRED CONFIG)

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
    onnxruntime::onnxruntime
    android
    log
)
