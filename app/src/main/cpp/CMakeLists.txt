cmake_minimum_required(VERSION 3.22.1)

project("native-lib")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/core
)

add_library(
    native-lib
    SHARED
    native-lib.cpp
    core/animation/CubicBezier.cpp
    core/animation/AnimatedProperty.cpp
    core/model/Transform.cpp
    core/model/Layer.cpp
    core/model/Project.cpp
    core/io/ProjectSerializer.cpp
    core/render/SoftwarePreviewRenderer.cpp
    core/render/GpuCompositor.cpp
    core/media/MediaDecoder.cpp
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
find_library(android-lib android)
find_library(gles3-lib GLESv3)
find_library(egl-lib EGL)
find_library(mediandk-lib mediandk)

target_link_libraries(
    native-lib
    ${log-lib}
    ${jnigraphics-lib}
    ${android-lib}
    ${gles3-lib}
    ${egl-lib}
    ${mediandk-lib}
)