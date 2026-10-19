cmake_minimum_required(VERSION 3.22.1)
project(seedmap C)

set(CMAKE_C_STANDARD 11)

file(GLOB CUBIOMES_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/cubiomes/*.c
    ${CMAKE_CURRENT_SOURCE_DIR}/cubiomes/features/*.c
)
# Chest loot (MIT, see cubiomes/loot/LICENSE_loot_library.h.txt), including per-version tables.
file(GLOB_RECURSE LOOT_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/cubiomes/loot/*.c)

add_library(seedmap SHARED
    seedmap_core.c
    seedmap_layers.c
    seedmap_world.c
    seedmap_features.c
    seedmap_jni.c
    ${CUBIOMES_SOURCES}
    ${LOOT_SOURCES}
)

target_include_directories(seedmap PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/cubiomes
)

# cubiomes is third-party code: keep its warnings out of the build log.
target_compile_options(seedmap PRIVATE -O3 -w -fvisibility=hidden)
target_compile_definitions(seedmap PRIVATE NDEBUG)
target_link_libraries(seedmap m)
