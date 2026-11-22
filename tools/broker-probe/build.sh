#!/bin/sh
# Build the broker probe for arm64-v8a.
#
# Links DYNAMICALLY against bionic on purpose: a "-static" NDK link produces
# a PT_TLS segment with alignment 8, which bionic refuses to exec on arm64
# ("executable's TLS segment is underaligned ... needs to be at least 64").
# The dynamic binary runs unmodified from /data/local/tmp and from app data.
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)

if [ -n "${ANDROID_NDK_HOME:-}" ]; then
    NDK="$ANDROID_NDK_HOME"
elif [ -d "$ROOT/.android-sdk/ndk/27.0.12077973" ]; then
    NDK="$ROOT/.android-sdk/ndk/27.0.12077973"
else
    echo "set ANDROID_NDK_HOME to an NDK (27.0.12077973 verified)" >&2
    exit 1
fi

case "$(uname -s)" in
    Darwin) HOST=darwin-x86_64 ;;
    Linux)  HOST=linux-x86_64 ;;
    *) echo "unsupported host: $(uname -s)" >&2; exit 1 ;;
esac

CC="$NDK/toolchains/llvm/prebuilt/$HOST/bin/aarch64-linux-android21-clang"
[ -x "$CC" ] || { echo "missing compiler: $CC" >&2; exit 1; }

"$CC" -O2 -Wall -Wextra -o "$HERE/probe" "$HERE/probe.c"

echo "built: $HERE/probe"
