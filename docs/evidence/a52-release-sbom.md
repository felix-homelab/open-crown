# A52 release SBOM

Date: 2026-11-22 15:52 Europe/Berlin
Artifact: app/build/outputs/apk/release/app-release.apk (CI-key bench build)
APK SHA-256: 1472e881cc55e3262b7ec9bb48f02dff66669d5a62df0baf819f758510900f42
Format: CycloneDX 1.5 JSON

## Result

[tools/release/sbom.py](../../tools/release/sbom.py) produced
[a52-release-sbom.cdx.json](a52-release-sbom.cdx.json) with **144 components**:

- 134 Rust packages from cargo metadata (workspace, build, test and runtime);
- 7 Android runtime libraries with pinned versions and licences;
- 3 native files from the APK, each with a SHA-256.

The CycloneDX metadata component carries the APK hash. CI now generates and
uploads this SBOM with the signed release artifact.

## Method

~~~sh
python3 tools/release/sbom.py \
  --apk app/build/outputs/apk/release/app-release.apk \
  --out release-sbom.cdx.json
~~~

The Android list is maintained in the generator from the version catalog; the
Rust list is read live from cargo metadata. Native hashes are read from the APK
zip, not from the build directory.

## Limits

- This is an inventory, not a signed attestation or a licence-text bundle.
- The Rust list includes build/test-only crates because cargo metadata does not
  mark the exact native-library runtime closure. A release SBOM should narrow
  that to the built libopencrown.so graph.
- Android versions are a static list; a dependency bump must update it (CI
  cannot infer the resolved versions from the APK).
- CycloneDX schema validation was not run with an external validator; the
  document is parsed and component hashes are unit-tested.
- The SBOM is unsigned and not published with a hash manifest.
