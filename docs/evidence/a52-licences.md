# A52 open-source licence screen

Date: 2026-11-22 15:36 Europe/Berlin
Device: Samsung SM-A528B (a52sxq), Android 14 / SDK 34.
Build: Open Crown debug APK from this tree.
Serial: withheld.
Apple Watch: not present.

## What was done

Opened Settings -> Open-source licences and inspected the rendered inventory.

## Observation

~~~text
Licences
Open-source software

Open Crown                  MIT
Kotlin, AndroidX, Compose   Apache-2.0
JNA                         Dual LGPL-2.1-or-later or Apache-2.0
UniFFI                      MPL-2.0
Rust crates                 MIT/Apache-2.0, MPL-2.0, BSD-3-Clause,
                            Zlib, Unicode-3.0

Apple assets                none bundled
Notice bundle               licence texts and release SBOM still open
~~~

Screenshot: [a52-licences.png](a52-licences.png).

## Conclusion

- The app has a real licence surface matching [THIRD_PARTY.md](../THIRD_PARTY.md).
- The screen says plainly that licence texts and a release SBOM are not bundled
  yet; it is not presented as a completed notice file.
- No Apple asset claim is made.
