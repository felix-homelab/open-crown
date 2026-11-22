# A52 release artifact: no privileged relay

Date: 2026-11-22 15:41–15:45 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34.
Build: release variant, R8-minified, bench-signed with the local debug key.
Serial: withheld.
Apple Watch: not present.

## Problem found

The first release APK inspected in this round contained
lib/arm64-v8a/libpagecache_relay.so and the payload asset. The relay is
quarantined research code and must not ship in a release.

## Change

- CMake target is behind OPEN_CROWN_BUILD_RELAY, default OFF.
- The debug variant passes -DOPEN_CROWN_BUILD_RELAY=ON; release does not.
- The payload package is copied to src/debug/assets, not main assets.
- PageCacheRelayChain loads the native library lazily; a release build without
  it reports the omission instead of crashing.
- BuildConfig.PRIVILEGED_CHAIN is false in release, true in debug. The payload
  probe and Diagnostics report "Not included" / "Privileged chain not included".

## Verification

Unsigned release APK: 3,825,805 bytes. Native libraries:

~~~text
lib/arm64-v8a/libandroidx.graphics.path.so
lib/arm64-v8a/libjnidispatch.so
lib/arm64-v8a/libopencrown.so
~~~

No libpagecache_relay.so and no assets/payloads entry. The debug APK still
contains both for research.

Bench-signed release SHA-256:
41ec04ad49c8df5068195a23e3448e21e1ff7dea4f922b7d9243f84511fc4b23

Installed on the A52. The app starts, Diagnostics reports "UniFFI loaded;
protocol revision 0", and Home shows the payload row as **Not included** with
the explanation that the quarantined payload is absent.

Screenshot: [a52-release-no-relay.png](a52-release-no-relay.png).

## Limits

- The APK is bench-signed with the Android debug key. This is not a production
  signing or distribution artifact.
- The relay Kotlin class and consent UI remain compiled into the release; only
  the native library and payload asset are absent. BuildConfig gates execution.
- R8 mapping is generated (27 MB) but not published.
