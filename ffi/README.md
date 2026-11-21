# ffi

The UniFFI boundary between Kotlin and Rust.

- `src/lib.rs` exports the protocol revision, a typed error, and
  `prepare_payload` (verify, revocation, stage, token).
- `generated/` holds the committed Kotlin bindings. Regenerate with
  `tools/ffi-generate.sh`; CI fails if it is stale.
- `tools/build-android-lib.sh` cross-compiles `libopencrown.so` for arm64 and
  copies it to `app/src/main/jniLibs/arm64-v8a/` (gitignored). The APK packages
  it together with JNA's `libjnidispatch.so`.
- The JVM test in `app/src/test` loads the host library and calls both the
  simple function and `prepare_payload` against the committed example bundle.

## Wired

The app compiles `generated/` into the main source set and calls
`RustBridge.protocolVersion()` at startup. The call is guarded, so a missing
library shows a message instead of crashing. The skeleton screen reports the
revision, which is `0` until a spec is verified.

`prepare_payload` is exposed and covered by the JVM test, but no UI calls it
yet: that needs the consent screen and a bundle picker, which land after
Phase 0.

Device behaviour is unverified until the bench run.
