# Install, update and recovery

Status: private research build. **It does not pair with a watch yet.** Do not
install it expecting a working companion.

## Requirements

- Android 13+ (SDK 33), arm64-v8a. The generated UniFFI binding uses java.lang.ref.Cleaner, which requires API 33.
- The current evidence device is a Samsung SM-A528B on Android 14 / SDK 34.
- No Apple Watch is required to run the app, but no watch feature can be
  verified without one.
- No root is required to install or use the current build. The privileged chain
  is an explicit Diagnostics experiment and is not part of normal use.

## Build from this tree

```sh
JAVA_HOME=.toolchain/jdk/Contents/Home ./gradlew :app:assembleDebug
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
```

The debug build is signed with the local debug key. The release build is
currently unsigned by default and must be signed for distribution; see the
release gate below.

## Release signing

Create or supply a production keystore outside the repository, then export:

~~~sh
export OPEN_CROWN_KEYSTORE=/secure/path/opencrown-release.jks
export OPEN_CROWN_STORE_PASSWORD=...
export OPEN_CROWN_KEY_ALIAS=...
export OPEN_CROWN_KEY_PASSWORD=...
./gradlew :app:assembleRelease
tools/release/verify-artifact.sh app/build/outputs/apk/release/app-release.apk
~~~

Without OPEN_CROWN_KEYSTORE the release is unsigned. The gate rejects a package
containing the quarantined relay or payload, checks the signature, and requires
minSdk 33. Never commit the keystore or passwords.

## First launch

1. Welcome screens explain that pairing is unavailable.
2. Get started opens Home, which shows live phone, Bluetooth, payload and agent
   observations.
3. Nearby devices permission is requested only when discovery needs it.
4. Notification access is granted through Android settings, not a runtime
   permission.

## Update

Install over the existing app with the same signing key. Pair records and
onboarding state are preserved. A signing-key change requires uninstall and
therefore deletes app data. The update path has been exercised on the A52 for
onboarding and per-app notification policy; sealed pair-state preservation with
a real watch is still untested.

## Recovery

| Situation | Action |
| --- | --- |
| App misbehaves | Force stop, then relaunch |
| Need a clean app state | Android Settings → Apps → Open Crown → Storage → Clear data |
| Need to remove everything | Uninstall the app; also revoke notification access if it remains listed |
| Privileged chain left the phone unstable | Reboot; the chain is memory-only and gone |
| Notification capture is unwanted | Revoke notification access and clear captured items |
| Discovery cannot see anything | Check Nearby devices permission and Bluetooth; a paired watch may not advertise |

## Release gate

Not yet met:

- production signing and published certificate;
- reproducible APK: two clean same-commit builds matched byte-for-byte with the debug key; CI enforcement and the production key are still open;
- SBOM: a 144-component CycloneDX draft exists; runtime-only narrowing, signing and publication are still open;
- release notes and SBOM;
- clean-room and licence sign-off;
- no in-tree payload dependency for normal use;
- physical recovery drills for a populated pair store and a Watch reset.

## Uninstall

Uninstalling removes the app, its keystore alias and local records. It does not
change the phone's boot image or any partition. If the research chain was run,
reboot to clear it.