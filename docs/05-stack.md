# 05 — Stack

## 1. Summary

| Layer | Choice |
| --- | --- |
| Android UI | Kotlin 2.x + Jetpack Compose (Material 3) |
| Protocol core | Rust 2024, `no_std`-friendly where practical |
| Bindings | UniFFI (replaces hand-written JNI/C ABI) |
| Privileged agent | Rust static binary, separate process, Unix-socket RPC |
| Exploit runtime | Rust + a small C shim, loaded as a native lib |
| IPC | Unix socket + protobuf (versioned); Binder for Android services |
| Storage | SQLite + SQLCipher for metadata; Android Keystore for key material |
| Crypto | `ed25519-dalek` (signatures), `x25519-dalek` (key agreement), `rustls` (TLS), `zeroize` |
| Apple ID auth | SRP-6a (in-house, reviewed) + 2FA relay; tokens only |
| Build | Gradle (version catalog) + `cargo-ndk`; JDK 21; NDK r27+ |
| CI | GitHub Actions: Rust tests, Kotlin tests, `cargo-deny`, `ktlint`, SBOM, reproducible APK |
| Min SDK | 31 (Android 12) |
| Target SDK | current stable at kickoff |

## 2. Why this stack

### Android-native UI, not Flutter

The previous generation of this idea used a shared Flutter UI. That buys
cross-platform screens but costs a runtime bridge at exactly the place we need
control: process lifetime, foreground services, Bluetooth permissions, and
Binder. Open Crown is Android-first; the portable part is the protocol core,
not the UI. Compose gives the same velocity with none of the bridge.

### Rust core, not Kotlin core

The core is a state machine over untrusted bytes. Rust gives memory safety,
deterministic error handling, and a test surface that runs on the host. The same
core can run in a desktop bridge or a capture analyzer without an emulator.

### UniFFI, not hand-written JNI

Hand-written JNI is where previous projects accumulated ABI drift and unsafe
pointers. UniFFI generates the Kotlin bindings from the Rust API, preserving
types and errors. The cost is a code-generation step; that is cheaper than a
runtime signature mismatch.

### Separate agent, not "the app runs as root"

Running the whole APK as root turns every UI bug into a root bug and makes the
audit surface enormous. The agent is a small static binary with a typed socket
API and a capability policy. The app can be compromised without handing over the
radio.

### Exploit runtime in-app, agent in root

The exploit must run in the app because it needs the app's network and file
descriptors. The agent is the thing that needs root. Splitting them keeps the
privileged surface to a few hundred lines of Rust.

### Payload-agnostic privilege

No hard dependency on Magisk, KernelSU, or a specific exploit. The agent can use
a catalog payload, a user-supplied root, or a desktop bridge. That keeps the
project alive when a root method dies.

## 3. Deliberate changes from the prior approach

| Prior approach | Open Crown | Why |
| --- | --- | --- |
| Flutter/Dart UI | Kotlin + Compose | Fewer runtimes, better service/permission control |
| Java bridge + hand-written JNI | Rust + UniFFI | Safer ABI, testable core |
| App itself runs as root | Separate agent | Smaller attack surface |
| Magisk module as install path | Per-boot exploit catalog | Stock phone, no permanent install |
| Shared IPC with desktop | Socket + protobuf, versioned | Explicit contracts, easier mocks |
| Feature-first development | Spec + vectors first | Clean room and regressions |
| Credential handling unspecified | SRP + 2FA, tokens only | Explicit, auditable auth |

## 4. Repository layout

```text
app/            Kotlin application, Compose screens, DI, services
core/           Rust crate: protocol, crypto, state machines
ffi/            UniFFI definitions + generated Kotlin (checked in)
agent/          Rust privileged helper (HCI broker, policy, audit)
runtime/        Exploit runtime, device matcher, payload loader
catalog/        Manifest schema, signing tool, transparency log client
protocol/       Specs and golden vectors (the clean-room record)
tools/          Capture analysis, profile builder, fixture generator
docs/           This documentation set
```

## 5. Toolchain and build

- **JDK 21**, **Gradle 9.x** with a version catalog, **AGP** current stable.
- **Rust** stable, `edition = "2024"`, `rust-toolchain.toml` pinned.
- **cargo-ndk** for `arm64-v8a` (primary) and `x86_64` (emulator).
- **UniFFI** pinned; generated Kotlin committed and diffed in CI.
- **NDK** pinned in `gradle.properties`.
- **Reproducible builds**: `SOURCE_DATE_EPOCH`, deterministic zip, pinned
  dependencies, `cargo vendor` for release.
- The agent is built `panic = "abort"`, stripped, with a fixed syscall
  allowlist enforced by seccomp at startup.

## 6. Auth stack

Apple ID sign-in is the highest-risk subsystem, so it gets its own rules:

- SRP-6a client implemented in Rust, reviewed against published test vectors.
- TLS via `rustls` with certificate pinning to Apple's endpoints.
- The password exists only in memory for the duration of the SRP exchange and
  is zeroized immediately after.
- 2FA is relayed: the app shows the prompt target and accepts the code; no code
  is logged.
- Only tokens are stored, in the Android Keystore, with an expiry and a
  revocation path.
- No analytics, no crash reporter payloads containing identifiers, no network
  calls except Apple endpoints and the catalog.

## 7. CI gates

1. `cargo fmt --check`, `cargo clippy -D warnings`, `cargo test`.
2. `cargo deny` (licenses, advisories, bans).
3. Kotlin: `ktlint`, `detekt`, unit tests.
4. Golden-vector suite: every protocol spec has a passing vector.
5. Generated-bindings diff.
6. APK build + signature verification + SBOM.
7. Similarity scan for the clean-room policy (see 03).
8. Auth tests: SRP vectors, token expiry, zeroization assertions.

## 8. Performance and power

- The core is allocation-light; hot paths reuse buffers.
- Scanning is duty-cycled; the agent exposes a scan window, not a free-running
  radio.
- No background work without a foreground service and a visible notification.
- The exploit runs at most three times per boot; the UI shows the budget.

## 9. Open stack questions

- **UniFFI vs. a thin C ABI** — UniFFI default; revisit if it becomes the
  bottleneck.
- **SQLCipher vs. field-level encryption** — depends on the metadata threat
  model.
- **Protobuf vs. CBOR** for IPC — protobuf for schema evolution.
- **SRP implementation** — in-house reviewed vs. a vetted crate; prefer a
  vetted crate if one passes review.
- **Kotlin Multiplatform** for a future desktop UI — not now.

These are tracked as ADRs, not decided in prose.
