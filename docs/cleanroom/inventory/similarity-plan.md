# Similarity and provenance audit plan

**Purpose:** make "Open Crown did not copy the prior art" an auditable, repeatable claim rather than an assertion. This plan defines the checks, the thresholds, the banned names, the provenance record, and the release gates.

**Scope of the claim:** no one-to-one copy of the reference project's expression — source, comments, identifiers, file names, test names, string tables, assets, or generated code. It does **not** claim that independent implementations cannot resemble each other structurally, and it does not claim legal advice.

**Out of scope for this lane:** running the checks. This is the plan and the rules; execution belongs to CI and to the release reviewer.

## 1. Inputs and trust boundaries

| Input | Location | Handling |
|---|---|---|
| Our tree | repository root, excluding `reference/` | audited subject |
| Reference tree | `reference/apple_watch_connector/` | read-only evidence; never added to git, never in a build path |
| Reference prose | `README.md`, `CHANGELOG.md`, `docs/**` | citable; not copyable |
| Reference source | implementation files | **not inspected** by this lane; the audit may hash/file-name them but must not use them as a template |
| Our specs | `protocol/**`, `docs/cleanroom/spec/**` | written from facts and our own captures |

**Repository hazard, and its current state.** At the start of this audit the reference tree was untracked and **not ignored**: `git status` showed `?? reference/` and `git check-ignore` returned exit 1. During the audit window another lane added `reference/` to `.gitignore`; the rule is now verified at `.gitignore:59` (`git check-ignore -v reference/apple_watch_connector/README.md` → `.gitignore:59:reference/`). Remaining hand-off: a CI guard that fails if any **tracked** path starts with `reference/`, plus a check that no build/packaging glob reaches into it. The quarantine must never be committed even though it is now ignored.

## 2. Checks

### C1 — Banned identifier scan (exact and normalised)

- Case-insensitive substring scan over all tracked text files for the names in §4.
- Normalised scan: strip non-alphanumerics, lowercase, then compare. Catches `Hal_Transport_Session`, `halTransportSession`, `HALTRANSPORTSESSION`.
- Fuzzy scan: Levenshtein distance ≤ 1 on identifier tokens of length ≥ 8, to catch plural/suffix variants.
- **Threshold: zero hits in product code, build files, tests, resources, and packaging scripts.** The denylist document itself (`docs/cleanroom/inventory/similarity-plan.md`) and Lane C's generated `docs/cleanroom/audit/banned-identifiers.txt` are the only permitted locations; an audit report may quote a hit. Any product-code hit blocks the release until removed or justified in writing.

### C2 — Token-shingle comparison

- Tokenise source (Java, Kotlin, Rust, Dart, C/C++, Python, Gradle, YAML) with a language-aware lexer: comments and string literals removed, identifiers preserved.
- Build 5-token shingles per file.
- Compare our files against the reference's source corpus **offline**, in a job that is not part of the shipped repository and whose output is a report, not copied source.
- **Thresholds:** shared-shingle ratio per file < 0.5%; longest identical contiguous token run ≤ 12 tokens; no file may exceed both a 0.25 ratio and a 10-token run.
- Report per file: ratio, longest run, top-5 shared shingles with source path (paths only, no code).

### C3 — String-literal comparison

- Extract string literals ≥ 8 characters, normalise whitespace/case.
- Compare with the reference's literals.
- **Allow-list** (may legitimately match, but must be recorded in the provenance log): observable protocol constants — Apple topic names (`com.apple.private.alloy.*`), NPS domain/key names (`com.apple.nano`, `com.apple.NanoTimeKit`, `SelectedPigmentList`, `wornOnRightArm`, `invertUI`, `AppleICUForce24HourTime`, `customMonogram`), Apple face-family/pigment fullnames that appear on the wire, MIME/ALPN constants, RFC identifiers.
- **Threshold:** zero matches outside the allow-list.
- Rationale: these strings are observable protocol data, not the reference's expression. Any *other* shared literal ≥ 8 chars is treated as copying until proven otherwise.

### C4 — Structural comparison

- Per-file metrics: token count, comment ratio, branch/loop counts, max nesting depth, function count, average function length, import count.
- Directory-level: Jaccard similarity of module/file trees (basenames and relative paths), and of package/namespace segment sets.
- **Thresholds:** directory Jaccard < 0.25; per-file metric vector cosine < 0.80; no mirrored module boundary (i.e. our `core/`, `app/`, `agent/`, `runtime/` must not reproduce the reference's Gradle module split or its package tree).
- Note: our repository already has a different module split (Kotlin app + Rust core + FFI), which is the strongest structural argument we have. Record that fact rather than relying on it.

### C5 — Asset and hash audit

- Build a SHA-256 set of every file in the reference tree; build ours; **intersection must be empty**.
- For images, also compare perceptual hashes (dHash-64) with a Hamming threshold ≤ 6, to catch resized/re-encoded copies.
- Compare filenames: no file may share the reference's content-addressed names, and no asset may be "renamed" into our tree.
- **Threshold: zero.** One hit is a release blocker, not a finding.

### C6 — Build-without-reference

- CI runs in a clean clone with **no `reference/` directory** at all.
- Build and test commands must pass: `cargo test` (workspace), `./gradlew test` (bridge/app), `flutter test` (companion), plus the FFI smoke test.
- Grep the tracked tree for the path `reference/`, `apple_watch_connector`, and the reference's package namespace. Allowed matches: prose under `docs/cleanroom/**` and `docs/provenance.md`; **zero matches in build files, source, tests, or packaging scripts**.
- Verify the packaged artifact (APK/desktop bundle/source tarball) contains no path or hash from the reference.

### C7 — Documentation-derivation check

- Compare sentence-level word sequences between our specs/inventory and the reference's prose corpus. Excluded before comparison: fenced code, inline code spans, markdown links, file paths, table-cell boundaries (each cell is compared separately), and non-Latin text.
- **Threshold:** no shared contiguous sequence of **12 or more words** in prose. Sequences of 8–11 words are allowed only when they are (a) protocol constants or fixed field/command names, or (b) an attributed quotation of ≤ 25 words; every such sequence is listed in the audit report.
- **Self-audit at the time of writing** (13 files; corpus = `README.md`, `CHANGELOG.md`, `docs/**`): longest shared prose run = **8 words** (a list of prohibited fixture material), therefore below the 12-word threshold. Cryptographic constant sequences are kept in inline code so the prose comparison ignores them; that is formatting for testability, not evasion, and the constants are unchanged. Re-run after any prose edit; a regression above 12 words is a release blocker.

### C8 — Provenance-log completeness

- Every protocol feature has a row in `docs/provenance.md` (see §5). Missing row = gate failure.
- Every allow-listed protocol constant in C3 appears in the row for the feature that uses it.

## 3. Cadence and evidence retention

| When | What runs | Output |
|---|---|---|
| Every PR touching `protocol/`, `core/`, `app/`, `agent/`, `runtime/`, `ffi/` | C1, C3, C6 (fast path) | CI log, PR comment |
| Every release candidate | C1–C8 | `docs/cleanroom/audit/<date>-<commit>.md` + JSON reports |
| Quarterly, or after any contributor change | full C1–C7 on the default branch | archived report |

Reports record: commit SHA, tool versions, corpus hash (the reference tree's aggregate digest), command lines, thresholds, per-check counts, reviewer, date. Reports contain paths, hashes, and statistics only — never reference code or assets.

## 4. Banned identifiers

The machine-readable, authoritative list is maintained by Lane C at `docs/cleanroom/audit/banned-identifiers.txt` (per `REFERENCE_BOUNDARY.md` §5); it is generated from a symbol inventory and is not committed with source excerpts. The table below is the seed list and the rationale a reviewer can read without opening the reference.

**Rule:** none of these may appear in Open Crown code, tests, build files, resource names, or public API — not as types, packages, methods, fields, resource ids, or comments. This list is a denylist for scanning. It is **not** a naming source and must not be mined for alternatives.

| Class | Banned | Why |
|---|---|---|
| Namespace / package | `dev.applewatchandroid`, `dev.applewatchandroid.bridge`, `dev.applewatchandroid.companion` | Reference application identity |
| IPC identifiers | `companion/bridge`, `companion/events`, `permission.COMPANION_IPC` (as used by the reference) | Reference private contract |
| Wire prefixes | `BRIDGE_COMMAND_V1:`, `BRIDGE_COMMAND_STATUS_V1:`, `WATCH_SETUP_PHASE_V1`, `SET_ACTIVE_FACE:`, `NATIVE_FACE_SELECT:`, `NATIVE_FACE_ADD_FILE`, `NATIVE_FACE_RESOURCES_FILE`, `SET_WATCH_SETTING:` | Reference-private framing, not Apple protocol |
| Command enum names | `START_OPERATIONAL`, `STOP_SESSION`, `START_SETUP`, `PING_WATCH`, `SYNC_CURRENT_WIFI`, `REQUEST_FACE_COLLECTION`, `REQUEST_DEVICE_ABOUT` | Reference-private command vocabulary |
| Transport / setup types | `HalTransportSession`, `RootBluetoothHalHost`, `HalCommandReader`, `BridgeSetupService`, `BridgeSetupEngine`, `CompanionBridgeService`, `CompanionFlutterBridge`, `CompanionConnectionPolicy`, `CompanionSessionState`, `OperationalWatchService`, `OperationalSessionPolicy`, `WatchDiscoverySelection`, `SetupProgressState`, `PairingSessionRecord`, `PairingReplacementBackup`, `AppleWatchPostCommitCoordinator` | Module-specific identifiers |
| IDS types | `NormalLinkIdsSessionBridge`, `IdsModernSessionCoordinator`, `IdsApplicationRoute`, `IdsBootstrapState`, `IdsSocketPairCodec`, `IdsDeviceInfoExchange`, `AppleWatchInitialSetupIdsAdapter` | Module-specific identifiers |
| Face types | `ClockFaceSyncClient`, `ClockFaceSyncReceiver`, `ClockFaceSyncHeaderCodec`, `ClockFaceDeltaSession`, `ClockFaceDeltaPlan`, `ClockFaceDeltaCommand`, `NativeFaceTransferDispatcher` | Module-specific identifiers |
| Preference / health types | `NtkPreferenceEnvelope`, `PigmentPreferenceCodec`, `PigmentPreferenceMirror`, `PigmentPreferenceCommand`, `MonogramPreferenceCodec`, `OperationalHealthController`, `OperationalPhoneFinder`, `WatchLocaleSnapshot`, `InitialWifiSyncWorker` | Module-specific identifiers |
| Optical types | `OpticalPairingCode`, `OpticalPskSession`, `OpticalAuthMethodNegotiation`, `OpticalChromaSignal`, `OpticalWatermarkPackets`, `OpticalConvolution`, `OpticalAffinePatch`, `OpticalLocalContrast`, `OpticalDecoderService`, `OpticalDecoderClient`, `OpticalReader` | Module-specific identifiers |
| Codec types | `PairedSyncCodec`, `PairedSyncPreferenceStore`, `PbBridgeCodec`, `WifiNetworkSyncCodec`, `FindMyLocalDeviceCodec`, `NanoSystemSettingsDiagnostics`, `NanoRegistryPropertyCodec`, `PreferencesSyncCodec`, `BridgeCommandCodec` | Module-specific identifiers |
| Replicator types | `ReplicatorReadSession`, `ReplicatorNetworkHeader`, `OpackDecoder`, `IdsZoneAdvertiser` | Module-specific identifiers |
| Flutter/Dart types | `BridgeCommands`, `BridgeIpcClient`, `BridgeStateProjection`, `WatchBridgeService`, `BridgeTransport`, `PlatformBridgeTransport`, `RustBridgeTransport`, `CompanionScope`, `CompanionPlatform` | Reference app identifiers |
| File names | Any source file whose basename matches a reference source basename; the `*FaceBundle*` preview naming scheme; `clockface-<number>-*` fixture naming | File-system-level copying |
| Tests | Any test class/method name matching a reference test name; `*OracleTest`, `Optical*Test` naming families | Test-level copying |

**Allowed** (record in the provenance log, not banned): Apple protocol terms that appear on the wire or in public documentation — FE25, BT_CL, uIKE, IKE, SPAKE2+, PPK, OOB, SMP, ERTM, L2CAP, CID, NetworkRelay, NWSC, IDS, PBBridge, PairedSync, NPS, PSY, NanoRegistry, NanoTimeKit, NTK, OPACK, StateReplicator, ESP, IPsec; and Apple topic/domain/key constants listed in C3's allow-list.

## 5. Provenance log fields

Extend `docs/provenance.md` with one row per protocol feature. Required fields:

```text
feature:          <our name>                        # Open Crown vocabulary
spec:             protocol/<area>/<feature>.md @ <commit>
vectors:          protocol/<area>/vectors/*.json    # or "none yet — hypothesis"
captures:         <our capture id> sha256:<hash>    # own hardware captures only
reference docs:   <path#section>[, ...]             # prose only
reference facts:  <one-line list of facts taken>
reference assets: none                              # always "none"; anything else is a violation
code copied:      none                              # always "none"
allowed constants:<C3 allow-list entries used>
scan run:         <report id>, <date>, <commit>
reviewer:         <name>, <date>
attestation:      <the PR sign-off text from docs/provenance.md>
```

Rules:
- `reference assets` and `code copied` are invariants, not free text. A non-"none" value fails C8.
- A spec without a vector is labelled a hypothesis; a vector without a capture is labelled a guess (`protocol/README.md` rule 2).
- The row is written before the code is merged, not after.

## 6. Violation handling

1. Stop. Do not "rewrite until the scanner passes" — that produces evasion, not provenance.
2. Remove the material and re-derive the behaviour from our spec and captures.
3. Record the incident in the audit report and in `docs/provenance.md`.
4. If the material is an asset or a string from Apple rather than from the reference, treat it as a separate licensing defect and route it through the licence gate.

## 7. Acceptance criteria for "no one-to-one copy"

- [ ] C1: zero banned-identifier hits.
- [ ] C2: no file over the shingle thresholds; longest run ≤ 12 tokens.
- [ ] C3: zero non-allow-listed shared literals; allow-list documented.
- [ ] C4: directory Jaccard < 0.25; no mirrored module tree.
- [ ] C5: empty asset-hash intersection; zero perceptual-hash matches.
- [ ] C6: clean clone without `reference/` builds and tests; zero build-path references.
- [ ] C7: no 8-word prose reuse.
- [ ] C8: every feature has a provenance row.
- [ ] `reference/` is ignored by git and absent from every artifact.

## 8. UNKNOWN and limits

- **UNKNOWN:** whether the reference corpus we hold is complete (the copy is missing many of its own cited reports), so a similarity score is a lower bound on exposure, not an upper bound.
- **UNKNOWN:** the reference's own use of generated code (Flutter localisations, registrants) complicates token comparison; generated files are excluded from C2 but still scanned by C1/C3.
- **UNKNOWN:** no scanner exists in this repository yet; C1–C7 are specifications for tooling to be written under `tools/`, not running checks.
- **Limit:** these checks detect *expression* similarity. They cannot prove independent creation; the provenance log and the reviewer sign-off carry that part of the claim.
- **Limit:** Apple protocol constants are shared by necessity. Their presence is evidence of interoperability, not of copying — which is why the allow-list is explicit rather than silent.
