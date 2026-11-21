#!/usr/bin/env sh
# Regenerate the Kotlin bindings from the Rust FFI surface.
#
# The generated file is committed (docs/05-stack.md) and diffed in CI.
set -eu

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

if [ -x "$root/.toolchain/cargo/bin/cargo" ]; then
  export CARGO_HOME="$root/.toolchain/cargo"
  export RUSTUP_HOME="$root/.toolchain/rustup"
  export PATH="$root/.toolchain/cargo/bin:$PATH"
fi

case "$(uname -s)" in
  Darwin) lib="libopencrown.dylib" ;;
  Linux) lib="libopencrown.so" ;;
  *) echo "unsupported host: $(uname -s)" >&2; exit 1 ;;
esac

cargo build -p opencrown-ffi
cargo run -q -p opencrown-ffi --bin uniffi-bindgen -- generate \
  --library "target/debug/$lib" \
  --language kotlin \
  --out-dir ffi/generated \
  --no-format
echo "regenerated ffi/generated"
