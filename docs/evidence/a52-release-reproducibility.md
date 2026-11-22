# A52 release reproducibility

Date: 2026-11-22 15:47–15:49 Europe/Berlin
Source: commit b0c62b0, clean worktree
Device for prior install checks: Samsung SM-A528B (a52sxq), Android 14 / SDK 34
Toolchain: JDK 21, Gradle 8.14.3, AGP 8.13.2, compileSdk 36, NDK 30.0.16248370
Signing: local Android debug key, bench only

## Method

Two independent clean release builds from the same commit and worktree state:

~~~sh
./gradlew clean :app:assembleRelease --offline --no-build-cache
cp app/build/outputs/apk/release/app-release-unsigned.apk /tmp/repro-2-unsigned.apk

./gradlew clean :app:assembleRelease --offline --no-build-cache
cp app/build/outputs/apk/release/app-release-unsigned.apk /tmp/repro-3-unsigned.apk

cmp /tmp/repro-2-unsigned.apk /tmp/repro-3-unsigned.apk
~~~

Each artifact was zipaligned and signed with the same debug keystore, then
compared again.

An earlier comparison was discarded as invalid: the first APK predated registry
changes, so the feature-registry asset legitimately differed. The valid
comparison used two builds from the same source state.

## Result

| Artifact | SHA-256 |
| --- | --- |
| Unsigned APK | ff97a1766d84ed95bfb750d8c067d372368d89f34f9eefde45f931f08fe8c352 |
| Signed bench APK | 5b5f29dba0edaa6e0115d90b3ccb21ed5679dea0bf86113ff7eccc7d1fde87b4 |

Both unsigned and signed files were byte-identical across the two clean builds.
The signed APK verifies with apksigner. It contains no libpagecache_relay.so and
no payload asset (verified separately in a52-release-no-relay.md).

## Limits

- Reproducibility is proven for this commit, worktree state, absolute checkout
  path and toolchain. A different checkout path, JDK/NDK revision or dirty
  worktree (AGP embeds VCS information) can change the bytes and was not tested.
- The production signing key was not available; this is a debug-key bench test.
  A production key must reproduce the same result before distribution.
- CI does not yet enforce a double-build comparison.
- APK v4 signing and a published hash manifest are not implemented.
