#!/usr/bin/env sh
# Cross-compile the UniFFI library for the target ABI and place it where Gradle
# packages native libraries from. The .so is a build artifact and is gitignored.
set -eu

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

if [ -x "$root/.toolchain/cargo/bin/cargo" ]; then
  export CARGO_HOME="$root/.toolchain/cargo"
  export RUSTUP_HOME="$root/.toolchain/rustup"
  export PATH="$root/.toolchain/cargo/bin:$PATH"
fi

ndk="${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME}"
api="${ANDROID_API:-31}"

case "$(uname -s)" in
  Darwin) host="darwin-x86_64" ;;
  Linux) host="linux-x86_64" ;;
  *) echo "unsupported host: $(uname -s)" >&2; exit 1 ;;
esac

toolchain="$ndk/toolchains/llvm/prebuilt/$host"
cc="$toolchain/bin/aarch64-linux-android${api}-clang"
if [ ! -x "$cc" ]; then
  echo "compiler not found: $cc" >&2
  exit 1
fi

rustup target add aarch64-linux-android
export CC_aarch64_linux_android="$cc"
export AR_aarch64_linux_android="$toolchain/bin/llvm-ar"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$cc"

cargo build -p opencrown-ffi --release --target aarch64-linux-android

mkdir -p app/src/main/jniLibs/arm64-v8a
cp target/aarch64-linux-android/release/libopencrown.so app/src/main/jniLibs/arm64-v8a/
file app/src/main/jniLibs/arm64-v8a/libopencrown.so
