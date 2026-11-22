# Reference asset inventory — Apple-origin and third-party material

**Status:** audit of the quarantined copy, read-only. No asset was copied, moved, or executed.
**Reference tree:** `reference/apple_watch_connector/` (file mtimes 2026-10-13; 21,547 files, 228 MiB on disk).
**Method:** path/size census, extension census, header/filename sampling, SHA-256 computed in place. Asset contents were not transcribed. Citations point to the reference's prose documents only (README sections, `docs/**`); no source path, identifier, or asset content is reproduced here.
**Verdict vocabulary:** `vendor` = may ship in Open Crown; `do-not-vendor` = must not enter any Open Crown artifact; `original-equivalent-needed` = a functional replacement must be produced by Open Crown itself.

## 1. Summary

| ID | Category | Path (under reference tree) | Files | Bytes | Verdict |
|---|---|---|---:|---:|---|
| A1 | Native face render programs | `apple-watch-companion/assets/native_faces/configured_previews/*.nfp` | 16,793 | 87,371,955 | do-not-vendor |
| A2 | Native face shared pixel maps | `.../configured_previews/*.nfpm` | 99 | 3,250,312 | do-not-vendor |
| A3 | Native face lossless previews | `.../configured_previews/*.webp` | 1,865 | 79,041,072 | do-not-vendor |
| A4 | Preview index shards | `.../configured_preview_indexes/*.json` | 256 | 3,744,651 | do-not-vendor |
| A5 | Pigment swatch images | `.../pigment_swatches/*.png` | 1,054 | 2,586,603 | do-not-vendor |
| A6 | Face family previews | `.../previews/*.png` | 49 | 4,595,907 | do-not-vendor |
| A7 | Component preview programs | `.../component_previews/*` | 3 | 147,716 | do-not-vendor |
| A8 | Face catalog / collection metadata | `.../native_faces/*.json` | 9 | 1,693,963 | do-not-vendor |
| B1 | `.watchface` package fixtures | `apple-watch-bridge/core/src/test/resources/*.watchface`, `.../clockface-390-normalization/` | 4 | 138,652 | do-not-vendor |
| B2 | Captured/serializer binary fixtures | `.../core/src/test/resources/**` (`.bin`, `.bplist`) | 252 | 805,562 | do-not-vendor |
| B3 | Optical decoder oracle fixtures | `.../core/src/test/resources/optical/*.json` | 3 | 6,828 | do-not-vendor |
| C1 | Optical decoder firmware input | `firmware/`, `research-tools/` (ignored, absent) | 0 | 0 | do-not-vendor (absent) |
| D1 | AOSP-derived Bluetooth HAL AIDL | `apple-watch-bridge/hal/src/main/hal_aidl/**` | 3 | ~ | do-not-vendor-from-reference (re-derive from AOSP) |
| D2 | Gradle wrapper JARs | `*/gradle/wrapper/gradle-wrapper.jar` | 2 | ~ | do-not-vendor-from-reference |
| D3 | Flutter template icons / platform art | `android/app/src/main/res/mipmap-*`, `ios/Runner/Assets.xcassets/**`, `web/icons/*` | ~40 | ~ | original-equivalent-needed |
| D4 | Crypto-network brand SVGs | `docs/assets/networks/*.svg` | 7 | ~ | do-not-vendor |
| D5 | Project documentation artwork | `docs/assets/*.svg` | 7 | ~ | do-not-vendor |
| E1 | Third-party runtime dependencies | `Cargo.lock`, `pubspec.lock`, Gradle manifests | not vendored | n/a | vendor (own SBOM) |
| E2 | Apple binaries / firmware / disassembly | not present in tree | 0 | 0 | do-not-vendor |

Byte totals for A1–A8 sum to **182,432,179 bytes across 20,128 files** (the 213 MiB `du` figure includes filesystem block overhead and APK-packaging copies).

## 2. A — Native face asset payload (the dominant category)

### A1–A3 Configured preview payloads

- **Path:** `apple-watch-companion/assets/native_faces/configured_previews/`
- **What it is:** the app's offline preview library for watch face configurations. Three file types: `.nfp` (indexed preview program, 16,793), `.nfpm` (shared pixel map, 99), `.webp` (lossless image, 1,865). Filenames are content addresses: three sampled files hash exactly to their names (A1 `0002cf14…d2ac`, A2 not sampled, A3 `002414cf…6082`), so the tree is self-describing and an inventory hash is derivable without reading image content.
- **Provenance claim:** rendered from Apple's NanoTimeKit face implementations inside an iOS/watchOS 26.2 simulator research harness, then split into shared pixel planes; catalog keys are "exact configuration" identities. Evidence: `docs/WATCH_FACES_NATIVE_COVERAGE_51.md` ("Native rendering and compression": 8,321 requests, 20,894 exact configuration keys, 16,793 indexed frames, 99 shared maps, final managed data 165,817,523 bytes / 18,684 files); README §Native faces.
- **Licence evidence:** none in tree. No Apple licence, no attribution, no source image licence. The reference's own release notes state that permission to ship firmware-derived assets is unresolved (`docs/BUILD_AND_RELEASE.md` "Remaining release gates"; `docs/REFACTOR_RELEASE_92.md`).
- **Verdict:** **do-not-vendor**. These are renderer outputs of Apple's proprietary face compositors. Open Crown must not ship, cache, or mirror them.
- **Hash:** manifest digest over sorted `size path` pairs = `02e47d84d725237e` (SHA-256, first 16 hex). Representative file hash: `0002cf1438e0753d4b26706e5ede9d3950a274c5813248b55cbc712b22e8d2ac` (.nfp, filename = SHA-256).

### A4 Preview index shards

- **Path:** `.../configured_preview_indexes/*.json` — 256 shards, 3,744,651 bytes.
- **What it is:** lazy lookup shards (the reference describes a 256-shard catalog with bounded workers/cache) mapping configuration identity to preview asset.
- **Provenance claim:** generated alongside A1–A3 from native configuration identities; no independent source stated.
- **Licence evidence:** none.
- **Verdict:** **do-not-vendor**. The mapping is derived from Apple's face catalogue; a replacement must be built from Open Crown's own catalogue or from the user's own device.
- **Hash:** manifest digest `3bc5191e91736d5d`; representative shard `00b397f992675ba7e2f24bde734aff3d8b5f3ad2b0e9b38af5e28517204b6642` (filename = SHA-256).

### A5 Pigment swatches

- **Path:** `.../pigment_swatches/*.png` — 1,054 files, 2,586,603 bytes.
- **What it is:** colour swatch images used by the colour picker, one per palette entry.
- **Provenance claim:** exported from the native option compositor (original PNG bytes and SHA-256 identities preserved). Evidence: `docs/WATCH_APP_NATIVE_OPTION_SWATCHES_90.md` ("Native source and capture": actual compositor hook, 530 provider images, 185 native colours, 19 sliders); `docs/WATCH_APP_NATIVE_OPTION_SECTIONS_89.md` (transient simulator probes).
- **Licence evidence:** none. Apple artwork/rendering, no permission statement.
- **Verdict:** **do-not-vendor**. Original-equivalent needed if the product requires swatches; generate from Open Crown's own rendering or omit.
- **Hash:** manifest digest `1cb1f2ed9a3eac66`; representative `0017c4eaf61b3e062d007d26a29545da386ce921a1d33ef0e58f8b387f47836c` (filename = SHA-256).

### A6 Face family previews

- **Path:** `.../previews/*.png` — 49 files, 4,595,907 bytes.
- **What it is:** one gallery thumbnail per face family. Filenames embed Apple bundle identifiers of the form `bundle_com_apple_NTK…FaceBundle.png` (49 distinct family names; the string `NTK` is the Apple framework prefix).
- **Provenance claim:** simulator-rendered family samples, per the same coverage document; the file naming itself shows the assets are keyed to Apple bundle identities.
- **Licence evidence:** none. Apple artwork and Apple bundle identifiers.
- **Verdict:** **do-not-vendor**. Note the *names* are also Apple-origin data: even a file-name list is evidence of derivation and should not be imported.
- **Hash:** manifest digest `8266a4ba989776cc`.

### A7 Component preview programs

- **Path:** `.../component_previews/` — 2 `.nfc` files plus `catalog.json` (147,716 bytes total).
- **What it is:** compiled preview components ("NFC" program containers) with a small catalogue.
- **Provenance claim:** the reference describes this swatch work as research output that has not reached its production option UI (README §Capabilities); the builder is described in `docs/WATCH_APP_NATIVE_OPTION_SWATCHES_90.md` (four programs, 127,670 bytes).
- **Licence evidence:** none.
- **Verdict:** **do-not-vendor**; the byte format itself is an Apple-adjacent container of unknown specification.
- **Hash:** manifest digest `3c29abddca08a9be`.

### A8 Face catalogue metadata

- **Path:** `.../native_faces/*.json` — 9 files, 1,693,963 bytes.

| File | Bytes | SHA-256 |
|---|---:|---|
| `complication_labels.json` | 1,213 | `f0853dc6153102c05d40185e0c2d1f702190853715ab34d1d1aa4b710e5f00a5` |
| `complication_layouts_26_2.json` | 135,350 | `cc7fc1292dc727fb565c484eb07c374094b96313dc30abb25b5ddc9f787b8f94` |
| `configured_previews.json` | 40,815 | `67b6c7067a7f43eeb53cc8af3660c63526e991b2e6fe896ef3dbcb03048bc7cf` |
| `edit_sections_26_2.json` | 24,101 | `5c114ee0609ebf1490c17d908b4f9b6418fc28308a7780707dfd6c678f8459ca` |
| `gallery_collections_26_2.json` | 28,860 | `556d914b2c08099f864c32c11a55ec2af1b45c28dbaf7e870a833e6c7a28186b` |
| `photos_26_2.json` | 162,102 | `73b87ac8fe98b60e4007ff40e85c35017300619c5d4f413b35c071e3c25ea540` |
| `pigment_collections_26_2.json` | 261,438 | `062ab84423e0e526166aabeb8eb7adbc7904f5f42723b030935c7f0317a2a837` |
| `pigments_26_2.json` | 349,195 | `bbe430e1d89cfdb09c867159d56a970f5631ec15e23211b124940e798debca44` |
| `watchos_26_2.json` | 690,889 | `e567a49ef10455f030965a9a9e37170e0a595391aa4e622b3596518e1b4dd1cc` |

- **What it is:** face family catalogue, option/edit sections, galleries, palette collections, complication labels/layouts, Photos configuration metadata, and a whole-catalogue dump tagged to watchOS 26.2.
- **Provenance claim:** extracted/normalised from watchOS 26.2 simulator face bundles; "original images and Companion parameters" (README §Native faces, "Three independent data sets"). The `_26_2` suffix ties the data to a specific Apple OS build.
- **Licence evidence:** none. Database/compilation rights in the catalogue are unaddressed.
- **Verdict:** **do-not-vendor**. Treat catalogue *facts* (e.g. that a family exists) as observable protocol data usable in a spec, but do not copy the JSON structure, labels, or fullname tables.
- **Hash:** per-file SHA-256 above.

## 3. B — Fixtures and protocol vectors

### B1 `.watchface` packages

| Path | Bytes | SHA-256 |
|---|---:|---|
| `core/src/test/resources/clockface-390-normalization/copy.watchface` | 3,684 | `8c38209cf504499794d8b2eac7978f6e4d63e1ea49961f0aca12e386939555e7` |
| `.../clockface-390-normalization/original.watchface` | 3,714 | `2708134e02ba5f9101850d95e04805826e0c6dbad0b11ef63cb9de5d881db83a` |
| `.../clockface-402-greenfield.watchface` | 130,885 | `5e5bd10875c90c0b670013eb4d451bf135831094a874af26e3aacb1e2bf7bec7` |
| `.../clockface-402-greenfield-wire.watchface` | 369 | `4d1a065082a1356303bf232311d940ec6d5124dad4713e3d9d0e2d88ffbaa1a2` |

- **What it is:** Apple's installed-face archive format (`.watchface`), including one large "greenfield" package and a wire-sized variant, plus a before/after normalisation pair.
- **Provenance claim:** none stated. The naming and sizes indicate a real face package and a captured wire form; the reference does not say whether they came from a device, a simulator, or were authored.
- **Licence evidence:** none. Contains Apple-format data and possibly Apple resources.
- **Verdict:** **do-not-vendor**. Also a personal-data risk: installed face packages can embed user configuration (names, photos).
- **Hash:** above (full SHA-256).

### B2 Captured / serializer binary fixtures

- **Path:** `apple-watch-bridge/core/src/test/resources/**` — 242 `.bin` (786,111 B) and 10 `.bplist` (19,451 B), concentrated in `clockface-355-session/` (112), `clockface-355-descriptors/` (99), `ids/` (11), `activation/`.
- **What it is:** wire-level golden payloads: serializer envelopes, socket-pair frames, credential/SPAKE-style bplists, face-session dictionaries and descriptors, activation fixtures, sysdiagnose samples.
- **Provenance claim:** the reference requires serializer fixtures to stay synthetic, forbids device activation material, personal Bluetooth addresses, pairing keys and account credentials in the repository (README §Start from a clean machine / release gates), and says activation captures were replaced with deterministic synthetic fixtures (CHANGELOG). Some filenames nevertheless say `-native` (e.g. three socket-pair fixtures), which reads as captured-native rather than synthesised.
- **Licence evidence:** none. If they are captures, they are device/Apple-derived; if synthetic, they are still authored against Apple encodings.
- **Verdict:** **do-not-vendor**; if useful, re-derive as Open Crown's own vectors from our own captures (protocol/ areas expect exactly that).
- **Hash:** aggregate digest not computed per file (252 files); the largest single fixture family is the 112-file session set.

### B3 Optical decoder oracle fixtures

| Path | Bytes | SHA-256 |
|---|---:|---|
| `core/src/test/resources/optical/chroma-oracle.json` | 1,640 | `2b2c746dfff3a0d6a8ea45eab28bfe1737dcbb303b3ae2d06a34584862b8326b` |
| `core/src/test/resources/optical/convolution-oracle.json` | 2,184 | `406f189922ee9571b446e850e38f4e7f66c44db5253c64b0ce4c4d4146d12214` |
| `core/src/test/resources/optical/packet-oracle.json` | 3,004 | `7aa151346aca978f7436e78fe2a839ac90a86c46e8fde347bc0eeeea0dde8a68` |

- **What it is:** expected outputs of the optical decode pipeline (chroma signal, convolution stage, packet/erasure stage) used to test the portable port against the original firmware reader.
- **Provenance claim:** "Committed fixtures contain synthetic data only" (`docs/live-20261006-optical-pairing/RESULT.md`), but the oracles are derived from traces of Apple's reader.
- **Licence evidence:** none; derivation from Apple's algorithm is the claim itself.
- **Verdict:** **do-not-vendor**. Our vectors must come from our own captures.

## 4. C — Apple binary inputs that are absent

- **C1:** The camera decoder's build depends on a pinned Apple `VisualPairing` firmware image and a private asset-generation script. Both are absent: `firmware/` and `research-tools/` are ignored by the reference's `.gitignore` and do not exist in the copy. Evidence: README §Build (the asset-preparation step needs four inputs the repository does not ship) and `docs/BUILD_AND_RELEASE.md` (the inputs are missing from the published source, which the guide calls an open release gate).
- The build therefore cannot be reproduced from the public tree, and the resulting asset (reported as a 493,936-byte native reader) is not distributable.
- **Verdict:** **do-not-vendor**. Also a Gate for us: Open Crown must not depend on Apple firmware or on an extracted decoder.

## 5. D — Tooling, platform art, and third-party visual assets

| ID | Path | What it is | Provenance / licence | Verdict |
|---|---|---|---|---|
| D1 | `apple-watch-bridge/hal/src/main/hal_aidl/android/hardware/bluetooth/*.aidl` (3) | Bluetooth HAL interface declarations | Headers say "Copyright 2022 The Android Open Source Project", Apache-2.0; the reference's `apple-watch-bridge/NOTICE` attributes them to AOSP `hardware/interfaces` Bluetooth HAL. NOTICE names a different path (`app/src/main/hal_aidl`) than the files (they live under `hal/`). | do-not-vendor-from-reference; if needed, take from AOSP upstream with NOTICE |
| D2 | `apple-watch-bridge/gradle/wrapper/gradle-wrapper.jar`, `apple-watch-companion/android/gradle/wrapper/gradle-wrapper.jar` | Gradle wrapper bootstrap | Gradle, Apache-2.0; no licence text in tree | do-not-vendor-from-reference (obtain from Gradle) |
| D3 | `apple-watch-companion/android/app/src/main/res/mipmap-*/ic_launcher.png`, `ios/Runner/Assets.xcassets/AppIcon.appiconset/*` (15), `web/icons/*` (4), `web/favicon.png` | App icons / launch images | Presumed Flutter template art (BSD-3-Clause, Flutter Authors); not verified, no licence file in tree | original-equivalent-needed (we ship our own icon) |
| D4 | `docs/assets/networks/{bitcoin,ethereum,bnb,polygon,ton,solana,tron}.svg` (7) | Cryptocurrency brand marks for the donation page | Third-party trademarks; no attribution, no licence, no trademark notice | do-not-vendor |
| D5 | `docs/assets/{watch-companion-header,language-en,language-ru,support,video,donations-header,donations-divider}.svg` (7) | Project banners/buttons | No licence statement; assumed project-owned but unproven | do-not-vendor |

No fonts are vendored. No `.ttf`/`.otf`/`.woff` files exist in the tree; the Flutter UI's icon font comes from the `cupertino_icons` package at build time, and system fonts are used otherwise.

## 6. E — Third-party software dependencies

- **E1:** dependencies are resolved, not vendored: `Cargo.lock` (46 KB lockfile; direct crates are only `rusb` plus local workspace crates), `apple-watch-companion/pubspec.yaml`/`pubspec.lock` (Flutter SDK, `provider`, `intl`, `shared_preferences`, `archive`, `http`, `path_provider`, `ffi`, `camera` 0.12.1, `file_selector`, `crypto`, `image` 4.3.0, `xml` 6.6.1, `cupertino_icons`), Gradle/AndroidX and Java runtime jars assembled by their build scripts.
- **Licence evidence:** **none in tree.** There is no SBOM, no `THIRD-PARTY-NOTICES`, and no dependency licence texts. Only the AOSP AIDL attribution in `apple-watch-bridge/NOTICE` exists. The reference's README disclaims a blanket licence for third-party material without supplying those licences.
- **Verdict for Open Crown:** we may use the same upstream dependencies normally, but we must generate our own SBOM (ADR-adjacent requirement in `docs/03-clean-room.md` §8) and must not treat the reference as a licence source.

## 7. Not present (checked, so the record is complete)

- No Apple frameworks, dylibs, `.ipa`, `.app`, IPSW, dyld shared caches, or Ghidra databases. No `.apk`/`.aab` (ignored by the reference's `.gitignore`).
- No firmware images, device backups, keystores, or pairing material.
- No Apple SDK content beyond simulator-derived render/metadata outputs.
- No Apple logos; the Apple word marks appear only as descriptive product names in prose.

## 8. Findings that affect Open Crown

1. **The asset payload is the largest legal exposure, not the code.** 182 MB / 20,128 files of Apple-derived renderer output are the single biggest redistributable-adjacent blob in the reference.
2. **The reference's own licence position is unresolved.** Its release guide says permission to ship firmware-derived assets must be resolved; the assets are nevertheless in the tree. We cannot inherit an assumption that they are distributable.
3. **Content addressing makes provenance checks cheap.** Because filenames are SHA-256 of content, an audit can compare against our tree by hash set, and any accidental import is detectable.
4. **Derived data is still derived.** Catalogue JSON, family names, palette fullnames, and `.watchface` packages are Apple-origin data even though they are not executable code.
5. **Tooling exceptions are narrow.** Only D1/D2 have a clear upstream licence; both should be fetched from upstream, not copied.

## 9. UNKNOWN

- **UNKNOWN:** whether the reference holds written permission for any Apple-derived asset. No evidence either way.
- **UNKNOWN:** whether `.watchface` fixtures contain third-party or personal content (e.g. a user's Photos face).
- **UNKNOWN:** whether generated previews are copyrightable separately from the underlying Apple artwork in the relevant jurisdictions.
- **UNKNOWN:** the licensing status of the doctrine SVGs (D4) and of the bundled licence text for Gradle/Flutter assets.
- **UNKNOWN:** the exact provenance chain for the 252 binary fixtures (synthetic vs captured) — the tree contains contradictory signals.
