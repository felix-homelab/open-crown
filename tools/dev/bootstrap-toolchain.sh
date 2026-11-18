#!/usr/bin/env bash
# Install the build toolchain inside the repository.
#
# Everything lands under .toolchain/ and .android-sdk/, both gitignored. This
# exists because the build environment can only write inside the workspace, and
# because pinning the toolchain here keeps the build reproducible.
#
# Requires: curl, unzip, tar.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"

case "$(uname -m)" in
  arm64|aarch64)
    rust_host="aarch64-apple-darwin"
    jdk_arch="aarch64"
    ;;
  x86_64)
    rust_host="x86_64-apple-darwin"
    jdk_arch="x64"
    ;;
  *)
    echo "unsupported architecture: $(uname -m)" >&2
    exit 1
    ;;
esac

mkdir -p .toolchain .android-sdk/cmdline-tools

# --- Rust -------------------------------------------------------------------
if [ ! -x .toolchain/cargo/bin/cargo ]; then
  echo "installing rust..."
  curl -sSfL "https://static.rust-lang.org/rustup/dist/${rust_host}/rustup-init" -o .toolchain/rustup-init
  chmod +x .toolchain/rustup-init
  HOME="$root/.toolchain/home" \
    RUSTUP_HOME="$root/.toolchain/rustup" \
    CARGO_HOME="$root/.toolchain/cargo" \
    ./.toolchain/rustup-init -y --no-modify-path --profile minimal --default-toolchain stable
  HOME="$root/.toolchain/home" \
    RUSTUP_HOME="$root/.toolchain/rustup" \
    CARGO_HOME="$root/.toolchain/cargo" \
    "$root/.toolchain/cargo/bin/rustup" component add rustfmt clippy
fi

# --- JDK 21 -----------------------------------------------------------------
if [ ! -x .toolchain/jdk/Contents/Home/bin/java ]; then
  echo "installing jdk 21..."
  curl -sSfL -o .toolchain/jdk.tar.gz \
    "https://api.adoptium.net/v3/binary/latest/21/ga/mac/${jdk_arch}/jdk/hotspot/normal/eclipse"
  mkdir -p .toolchain/jdk
  tar -xzf .toolchain/jdk.tar.gz -C .toolchain/jdk --strip-components=1
  rm -f .toolchain/jdk.tar.gz
fi

# --- Gradle -----------------------------------------------------------------
if [ ! -x .toolchain/gradle-8.14.3/bin/gradle ]; then
  echo "installing gradle..."
  curl -sSfL -o .toolchain/gradle.zip \
    "https://services.gradle.org/distributions/gradle-8.14.3-bin.zip"
  unzip -q -o .toolchain/gradle.zip -d .toolchain
  rm -f .toolchain/gradle.zip
fi

# --- Android command line tools --------------------------------------------
if [ ! -x .android-sdk/cmdline-tools/latest/bin/sdkmanager ]; then
  echo "installing android command line tools..."
  curl -sSfL -o .toolchain/cmdline-tools.zip \
    "https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"
  unzip -q -o .toolchain/cmdline-tools.zip -d .android-sdk/cmdline-tools
  mv .android-sdk/cmdline-tools/cmdline-tools .android-sdk/cmdline-tools/latest
  rm -f .toolchain/cmdline-tools.zip
fi

export JAVA_HOME="$root/.toolchain/jdk/Contents/Home"
export ANDROID_HOME="$root/.android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export HOME="$root/.toolchain/home"
# sdkmanager derives its cache from user.home, not HOME. Without this it tries
# to write to the real home directory, which the sandbox refuses.
export JAVA_OPTS="-Duser.home=$HOME -Djava.net.preferIPv4Stack=true"
mkdir -p "$HOME"

echo "installing android packages..."
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" \
  "platform-tools" "platforms;android-36" "build-tools;36.0.0"

printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
echo "toolchain ready"
