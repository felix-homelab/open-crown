# Third-party software

Status: inventory for the private research build. This is not a legal opinion,
and it is not yet a distributable notice bundle.

## Product code

Open Crown is MIT-licensed. The reference project is not a dependency, is not
linked into the app, and is not distributed.

No Apple code, fonts, SF Symbols, face artwork or other Apple-owned assets are
included.

## Shipped runtime components

| Component | Version | Licence |
| --- | --- | --- |
| Kotlin standard library | 2.2.20 | Apache-2.0 |
| AndroidX Core KTX | 1.17.0 | Apache-2.0 |
| AndroidX Activity Compose | 1.11.0 | Apache-2.0 |
| Jetpack Compose BOM (Foundation 1.9.1, Material 3 1.3.2, UI 1.9.1) | 2025.09.00 | Apache-2.0 |
| JNA | 5.17.0 | LGPL-2.1-or-later or Apache-2.0 (dual) |
| UniFFI runtime and generated binding | 0.32.2 | MPL-2.0 |

The Rust core links third-party crates resolved in Cargo.lock. The workspace
currently resolves 134 packages across build, test and runtime; the runtime
subset is what ships in libopencrown.so. Licence families present include:

| Licence family | Count / notable packages |
| --- | --- |
| MIT or Apache-2.0 (or both) | Most crates |
| MPL-2.0 | UniFFI crates (uniffi, uniffi_core, uniffi_bindgen, uniffi_macros, uniffi_meta, uniffi_pipeline, uniffi_udl, uniffi_internal_macros) |
| BSD-3-Clause | curve25519-dalek, ed25519-dalek, subtle |
| Zlib | foldhash |
| Unicode-3.0 | unicode-ident (combined with MIT/Apache-2.0) |
| LGPL-2.1-or-later (alternative) | r-efi offers an MIT/Apache alternative |

MPL-2.0 is file-level copyleft. The generated binding under ffi/generated is
generated output; modifications to MPL-covered files must remain available under
MPL-2.0. JNA's LGPL alternative means the Apache-2.0 option may be used, but a
release sign-off must record which option applies.

## How this was produced

- Rust: cargo metadata license fields, 2026-11-22.
- Android: Gradle debugRuntimeClasspath on 2026-11-22; Compose BOM components
  are Apache-2.0.
- Test-only dependencies (JUnit, org.json, Truth) are not listed as shipped.

## Release gates still open

- Runtime-only SBOM exists for the exact release artifact; signing and publication remain open.
- Bundle the required licence texts and notices in the APK or an adjacent file.
- Record the JNA licence option and the MPL-2.0 file list.
- Re-run the inventory after any dependency update.
