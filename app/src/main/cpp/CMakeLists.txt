cmake_minimum_required(VERSION 3.22.1)
project(pagecache_relay C)

# Quarantined research code. Off by default so a release artifact cannot package
# the relay; the debug variant opts in explicitly.
option(OPEN_CROWN_BUILD_RELAY "Build the quarantined page-cache relay" OFF)

if(OPEN_CROWN_BUILD_RELAY)
    add_library(pagecache_relay SHARED pagecache_relay/pagecache_relay.c)
    target_compile_options(pagecache_relay PRIVATE -O2 -march=armv8-a+crypto)
    target_link_libraries(pagecache_relay log)
endif()
