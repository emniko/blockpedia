cmake_minimum_required(VERSION 3.22.1)
project(seedmap C)

set(CMAKE_C_STANDARD 11)

file(GLOB CUBIOMES_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/cubiomes/*.c
    ${CMAKE_CURRENT_SOURCE_DIR}/cubiomes/features/*.c
)

add_library(seedmap SHARED
    seedmap_core.c
    seedmap_jni.c
    ${CUBIOMES_SOURCES}
)

target_include_directories(seedmap PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/cubiomes
)

# cubiomes is third-party code: keep its warnings out of the build log.
target_compile_options(seedmap PRIVATE -O3 -w -fvisibility=hidden)
target_compile_definitions(seedmap PRIVATE NDEBUG)
target_link_libraries(seedmap m)
