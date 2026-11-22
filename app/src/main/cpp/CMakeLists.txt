cmake_minimum_required(VERSION 3.22.1)
project(pagecache_relay C)

add_library(pagecache_relay SHARED pagecache_relay/pagecache_relay.c)
target_compile_options(pagecache_relay PRIVATE -O2 -march=armv8-a+crypto)
target_link_libraries(pagecache_relay log)
