# A52 minified release build with UniFFI

Date: 2026-11-22 15:00 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34.
Build: :app:assembleRelease from this tree, R8 minified, debug-signed for the
bench only.
Serial: withheld.

## Build

~~~sh
JAVA_HOME=.toolchain/jdk/Contents/Home ./gradlew :app:assembleRelease
apksigner sign --ks ~/.android/debug.keystore --out /tmp/crown-release.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
~~~

Result:

~~~text
size   3,770,042 bytes
sha256 a8d41d78f43318a83de61c23709031140b977d75e474520e389c36c26cf10554
~~~

R8 keep rules for the generated UniFFI binding and JNA are in
[app/proguard-rules.pro](../../app/proguard-rules.pro).

## Device observation

The debug-signed release APK installed over the debug build and opened
Diagnostics. The Rust core section still reported:

~~~text
UniFFI                 loaded
                       UniFFI loaded; protocol revision 0
~~~

Screenshot: [a52-release-build.png](a52-release-build.png).

## Conclusion

- R8 with the committed keep rules does not strip the UniFFI/JNA surface.
- The release artifact packages the arm64 core and starts.
- This is **not** a production-signed release. The APK was signed with the local
  debug key solely to install it on the test phone. No distribution claim is
  made, and the signing ceremony remains a release gate.
- Lint-vital ran as part of the release build; no fatal issue.
