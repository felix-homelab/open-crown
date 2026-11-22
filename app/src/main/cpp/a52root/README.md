# A52 page-cache chain

`a52_pagecache.c` is the app-side native exploit. It patched
`/system/lib64/libbase.so` in the page cache and verified 576/576 bytes on the
SM-A528B. `payload_576_a52.bin` is the A52 relay payload; the patch script
records the four byte-level changes that avoid the A52 init panic paths.

Build the shared library with the Android NDK:

```sh
$NDK/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android31-clang \
  -shared -fPIC -O2 -march=armv8-a+crypto \
  -o ../../jniLibs/arm64-v8a/liba52root.so a52_pagecache.c -llog
```

The app packages the prebuilt library and the payload asset. The chain is
research-only: it yields an init-domain relay for the current boot, and Android
does not let `untrusted_app` connect to that relay. Trigger and use it over ADB.
