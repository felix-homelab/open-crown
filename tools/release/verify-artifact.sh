#!/usr/bin/env bash
# Release artifact gate: signature, SDK, native payload and relay exclusion.
#
# Usage: tools/release/verify-artifact.sh path/to/app.apk
# Requires ANDROID_SDK_ROOT (or .android-sdk in this repo) with build-tools 36.0.0.
set -euo pipefail

APK=${1:?usage: verify-artifact.sh path/to/app.apk}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
SDK=${ANDROID_SDK_ROOT:-$ROOT/.android-sdk}
BT=${ANDROID_BUILD_TOOLS:-$SDK/build-tools/36.0.0}

for tool in "$BT/apksigner" "$BT/aapt2"; do
    if [ ! -x "$tool" ]; then
        echo "missing tool: $tool" >&2
        exit 2
    fi
done

echo "== sha256 =="
shasum -a 256 "$APK"

echo "== signature =="
"$BT/apksigner" verify --print-certs "$APK"

echo "== sdk =="
"$BT/aapt2" dump badging "$APK" | grep -E "^(package|minSdkVersion|targetSdkVersion):"
MIN_SDK=$("$BT/aapt2" dump badging "$APK" | sed -n "s/^minSdkVersion:'\([0-9]*\)'/\1/p")
if [ "$MIN_SDK" != "33" ]; then
    echo "FAIL: expected minSdkVersion 33, found $MIN_SDK" >&2
    exit 1
fi

echo "== forbidden privileged payload =="
if unzip -l "$APK" | grep -E "libpagecache_relay|payloads/org.opencrown"; then
    echo "FAIL: quarantined relay or payload is packaged" >&2
    exit 1
fi
echo "ok: no relay library and no payload asset"

echo "== native core =="
unzip -l "$APK" | grep "lib/arm64-v8a/libopencrown.so"

echo "== dependency native libs =="
unzip -l "$APK" | grep "lib/arm64-v8a/" | awk '{print $4}'