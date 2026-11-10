cmake_minimum_required(VERSION 3.18)

project(fcitx5-android-plugin-rime VERSION ${VERSION_NAME})

# For reproducible build
add_link_options("LINKER:--hash-style=gnu,--build-id=none")

# prefab dependency
find_package(fcitx5 REQUIRED CONFIG)
get_target_property(FCITX5_CMAKE_MODULES fcitx5::cmake INTERFACE_INCLUDE_DIRECTORIES)
set(CMAKE_MODULE_PATH ${FCITX5_CMAKE_MODULES} ${CMAKE_MODULE_PATH})

find_package(ECM MODULE)
find_package(Fcitx5Core MODULE)
find_package(Fcitx5Module MODULE)

set(Rime_VERSION "1.12.0")
set(Rime_dependencies
        log # glog links to android log
        "${PREBUILT_DIR}/glog/${ANDROID_ABI}/lib/libglog.a"
        "${PREBUILT_DIR}/leveldb/${ANDROID_ABI}/lib/libleveldb.a"
        "${PREBUILT_DIR}/lua/${ANDROID_ABI}/lib/liblua_static.a"
        "${PREBUILT_DIR}/marisa/${ANDROID_ABI}/lib/libmarisa.a"
        "${PREBUILT_DIR}/opencc/${ANDROID_ABI}/lib/libopencc.a"
        "${PREBUILT_DIR}/yaml-cpp/${ANDROID_ABI}/lib/libyaml-cpp.a"
        )

add_library(Rime_static STATIC IMPORTED)
set_target_properties(Rime_static PROPERTIES
        IMPORTED_LOCATION "${PREBUILT_DIR}/librime/${ANDROID_ABI}/lib/librime.a"
        INTERFACE_INCLUDE_DIRECTORIES "${PREBUILT_DIR}/librime/${ANDROID_ABI}/include"
        INTERFACE_LINK_LIBRARIES "${Rime_dependencies}"
        )

# https://github.com/rime/librime/blob/1.9.0/src/rime_api.h#L663
# https://stackoverflow.com/questions/805555/ld-linker-question-the-whole-archive-option
# https://cmake.org/cmake/help/latest/manual/cmake-generator-expressions.7.html#link-features
if(CMAKE_VERSION VERSION_LESS 3.24)
    # Android/Clang equivalent for the supported SDK CMake 3.22.1 toolchain.
    set(RIME_TARGET "-Wl,--whole-archive" Rime_static "-Wl,--no-whole-archive")
else()
    set(RIME_TARGET $<LINK_LIBRARY:WHOLE_ARCHIVE,Rime_static>)
endif()
set(RIME_DATA_DIR "${CMAKE_INSTALL_DATADIR}/rime-data")

# Build the patch against the pinned upstream tree without modifying the
# submodule checkout. This also works when each Android ABI configures in parallel.
find_package(Git REQUIRED)
set(haoke_rime_patch "${CMAKE_CURRENT_SOURCE_DIR}/haoke-rime-nine-key.patch")
set(haoke_rime_source "${CMAKE_CURRENT_BINARY_DIR}/fcitx5-rime-source")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${haoke_rime_patch}")
file(COPY "${CMAKE_CURRENT_SOURCE_DIR}/fcitx5-rime/" DESTINATION "${haoke_rime_source}"
        PATTERN ".git" EXCLUDE)
execute_process(
        COMMAND "${CMAKE_COMMAND}" -E env "GIT_CEILING_DIRECTORIES=${CMAKE_CURRENT_BINARY_DIR}"
                "${GIT_EXECUTABLE}" apply "${haoke_rime_patch}"
        WORKING_DIRECTORY "${haoke_rime_source}"
        COMMAND_ERROR_IS_FATAL ANY
)
add_subdirectory("${haoke_rime_source}" fcitx5-rime)

install(FILES "haoke-pinyin-t9.conf"
        DESTINATION "${FCITX_INSTALL_PKGDATADIR}/inputmethod"
        COMPONENT config)

install(FILES
        "default.yaml"
        "haoke_pinyin_t9.schema.yaml"
        "rime-essay/essay.txt"
        "rime-prelude/key_bindings.yaml"
        "rime-prelude/punctuation.yaml"
        "rime-prelude/symbols.yaml"
        "rime-luna-pinyin/luna_pinyin_fluency.schema.yaml"
        "rime-luna-pinyin/luna_pinyin_simp.schema.yaml"
        "rime-luna-pinyin/luna_pinyin_tw.schema.yaml"
        "rime-luna-pinyin/luna_pinyin.dict.yaml"
        "rime-luna-pinyin/luna_pinyin.schema.yaml"
        "rime-luna-pinyin/luna_quanpin.schema.yaml"
        "rime-luna-pinyin/pinyin.yaml"
        "rime-stroke/stroke.dict.yaml"
        "rime-stroke/stroke.schema.yaml"
        DESTINATION "${FCITX_INSTALL_DATADIR}/rime-data"
        COMPONENT prebuilt-assets
        )
