cmake_minimum_required(VERSION 3.22.1)
project(binderprobe)

add_library(binderprobe SHARED native-lib.cpp)
target_link_libraries(binderprobe android log binder_ndk)
