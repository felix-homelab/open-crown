# A52 UniFFI seam: Rust core loaded in the app

Date: 2026-11-22 14:55 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34.
Build: Open Crown debug APK with the arm64 `libopencrown.so` and JNA AAR.
Serial: withheld. Boot count: 73.
Apple Watch: not present.

## What was done

1. Regenerated the committed Kotlin binding from `ffi/src/lib.rs`
   (`tools/ffi-generate.sh`).
2. Cross-compiled `libopencrown.so` for `aarch64-linux-android` and installed
   the APK.
3. Opened **Settings → Diagnostics** and read the Rust core section.

## Raw observation

```text
Rust core
UniFFI                 loaded
                       UniFFI loaded; protocol revision 0
Protocol revision      0
Discovery rules        10000 ms · 32 max
IDS rules              64 pending · backoff 15/30/60 s
Face rules             16 MiB · 180 s read-back
Setup checkpoints      1–18
```

Screenshot: [a52-uniffi-core.png](a52-uniffi-core.png).

The APK contains:

```text
lib/arm64-v8a/libjnidispatch.so
lib/arm64-v8a/libopencrown.so
lib/arm64-v8a/libpagecache_relay.so
```

## Conclusion

- The Kotlin↔Rust seam is real: the app loads the generated binding, calls
  `protocolVersion()` and `coreRules()`, and renders values that come from the
  core modules rather than duplicated Kotlin constants.
- Protocol revision 0 is correct: no wire spec has been verified on hardware.
- This does not claim any watch capability. It verifies the architecture seam
  and the fact that a missing native library is reported as unavailable, not
  replaced with a default.

## Limits

- Tested on one ABI (arm64-v8a) and one Android build.
- The discovery/IDS/setup/faces rules are host-tested, not exercised against a
  Watch.
- Release minification has not been exercised with JNA/UniFFI; debug only.
