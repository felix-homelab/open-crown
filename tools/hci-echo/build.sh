#!/usr/bin/env sh
# Cross-compile the K2a probe for the target phone.
#
# Requires ANDROID_NDK_HOME (NDK r27+). The probe is pushed by the app's payload
# runtime, not by adb, in the real flow; this build step is for packaging it.
set -eu

here="$(cd "$(dirname "$0")" && pwd)"
ndk="${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to an NDK install}"
api="${ANDROID_API:-31}"

case "$(uname -s)" in
  Darwin) host="darwin-x86_64" ;;
  Linux) host="linux-x86_64" ;;
  *) echo "unsupported host: $(uname -s)" >&2; exit 1 ;;
esac

cc="$ndk/toolchains/llvm/prebuilt/$host/bin/aarch64-linux-android${api}-clang"
if [ ! -x "$cc" ]; then
  echo "compiler not found: $cc" >&2
  exit 1
fi

"$cc" -O2 -Wall -Wextra -Werror -o "$here/hci-echo" "$here/hci_echo.c"
echo "built $here/hci-echo"
