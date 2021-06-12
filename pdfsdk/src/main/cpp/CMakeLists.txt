cmake_minimum_required(VERSION 3.4.1)

# Specify where JPEGTurbo libraries are located
set(Pdfium_DIR ${CMAKE_SOURCE_DIR}/pdfium)
set(Utils_DIR ${CMAKE_SOURCE_DIR}/utils)

# shared lib will also be tucked into APK and sent to target
# refer to app/build.gradle, jniLibs section for that purpose.
# ${ANDROID_ABI} is handy for our purpose here. Probably this ${ANDROID_ABI} is
# the most valuable thing of this sample, the rest are pretty much normal cmake
add_library(pdfsdk SHARED IMPORTED)
set_target_properties(pdfsdk PROPERTIES IMPORTED_LOCATION
    ${Pdfium_DIR}/lib/${ANDROID_ABI}/libpdfsdk.so)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--no-warn-mismatch")

include_directories(${Pdfium_DIR}/include)

add_subdirectory(${Utils_DIR})

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             pdfsdk_jni

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             pdfsdk_jni.cpp )

target_include_directories(pdfsdk_jni PRIVATE
                            ${CMAKE_SOURCE_DIR}/utils
                            ${Pdfium_DIR}/include
                            ${Pdfium_DIR}/include/cpp )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       pdfsdk_jni
                       pdfsdk
                       hk_utils
                       jnigraphics
                       android
                       GLESv2
                       log )