# ffi

The UniFFI boundary between Kotlin and Rust.

- `src/lib.rs` exports the protocol revision, a typed error, and
  `prepare_payload` (verify, revocation, stage, token).
- `generated/` holds the committed Kotlin bindings. Regenerate with
  `tools/ffi-generate.sh`; CI fails if it is stale.
- `tools/build-android-lib.sh` cross-compiles `libopencrown.so` for arm64 and
  copies it to `app/src/main/jniLibs/arm64-v8a/` (gitignored). The APK packages
  it.
- The JVM test in `app/src/test` loads the host library and calls both the
  simple function and `prepare_payload` against the committed example bundle.

## Not wired yet

The app's main source set does not compile the generated bindings, so nothing in
the UI calls Rust at runtime. That is the next integration: add JNA for Android,
move `generated/` into the main source set, and show the protocol revision on
the skeleton screen.
