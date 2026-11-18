# 05 — Stack

## 1. Summary

| Layer | Choice |
| --- | --- |
| Android UI | Kotlin 2.x + Jetpack Compose (Material 3) |
| Protocol core | Rust 2024, host-testable, no OS I/O |
| Bindings | UniFFI |
| Privileged agent | Rust static binary, separate process, Unix-socket RPC |
| Payload runtime | Rust + a small C shim, loaded as a native lib |
| IPC | Unix socket + protobuf (versioned); Binder for Android services |
| Storage | SQLite + SQLCipher for metadata; Android Keystore for wrapped keys |
| Crypto | `ed25519-dalek`, `x25519-dalek`, `rustls`, `zeroize` |
| Apple ID | Role undecided — see ADR 0003 |
| Build | Gradle (version catalog) + `cargo-ndk`; JDK 21; NDK r27+ |
| CI | GitHub Actions: Rust tests, Kotlin tests, `cargo-deny`, `ktlint`, SBOM, reproducible APK |
| Min SDK | 31 (Android 12) |
| Licence | MIT |

## 2. Why this stack

### The rewrite is the goal

The protocol already works in Java + Flutter + Rust. Open Crown does not exist to
add a feature the prior art lacks; the one new capability is *stock packaging*,
and that is the payload project, not the rewrite.

The rewrite is a deliberate maintainability decision: a pure Rust core with
host-side tests, a small privileged binary, and an explicit IPC contract. It is
recorded in [ADR 0001](adr/0001-kotlin-rust-uniffi.md) and it costs three to six
months before the protocol work starts. The previous revision justified the
rewrite with "Flutter bridge control" and "JNI drift"; those are real but
secondary, and stating the real reason keeps the effort estimate honest.

### Android-native UI

Compose gives direct control of process lifetime, foreground services,
permissions and Binder. The portable part is the core, not the UI.

### Rust core

The core is a state machine over untrusted bytes. Rust gives memory safety,
deterministic errors, and a test surface that runs on the host.

### UniFFI

Generated bindings remove hand-written JNI and its ABI drift. The generated
Kotlin is committed and diffed in CI.

### Separate, resident agent

The whole APK never runs as root. The agent is a small binary with a typed socket
API, a capability policy, and a foreground notification. It is resident for the
life of the boot while a watch is paired — see
[ADR 0008](adr/0008-resident-agent.md). The threat model treats the app process
as hostile because it is the component with network, parsers, and UI.

### Payload-agnostic privilege

No hard dependency on Magisk, KernelSU, or one exploit family. The agent can use
a catalog payload, a user-supplied root, or the desktop harness. That keeps the
project alive when a root method dies.

## 3. Deliberate changes from the prior approach

| Prior approach | Open Crown | Why |
| --- | --- | --- |
| Flutter/Dart UI | Kotlin + Compose | Fewer runtimes, better service control — and a rewrite we chose |
| Java bridge + hand-written JNI | Rust + UniFFI | Safer ABI, testable core |
| App itself runs as root | Separate agent | Smaller attack surface |
| Magisk module as install path | Per-boot payload | The one product differentiator |
| Shared IPC with desktop | Socket + protobuf | Explicit contracts, easier mocks |
| Feature-first development | Spec + vectors first | Clean room and regressions |
| Credential handling unspecified | Undecided by design | ADR 0003 is Proposed until the experiment runs |

## 4. Repository layout

```text
app/            Kotlin application, Compose screens, DI, foreground service
core/           Rust crate: protocol, crypto, state machines
ffi/            UniFFI definitions + generated Kotlin (checked in)
agent/          Rust privileged helper (controller broker, policy, audit)
runtime/        Payload runtime, device matcher, payload loader
catalog/        Manifest schema, signing tool, transparency log client
protocol/       Specs and golden vectors
tools/          Capture analysis, profile builders, fixture generator
docs/           This documentation set
```

## 5. Toolchain and build

- **JDK 21**, **Gradle 9.x** with a version catalog, **AGP** current stable.
- **Rust** stable, edition 2024, `rust-toolchain.toml` pinned.
- **cargo-ndk** for `arm64-v8a` (primary) and `x86_64` (emulator).
- **UniFFI** pinned; generated Kotlin committed and diffed in CI.
- **NDK** pinned in `gradle.properties`.
- **Reproducible builds**: `SOURCE_DATE_EPOCH`, deterministic zip, pinned
  dependencies, `cargo vendor` for release.
- The agent is built `panic = "abort"`, stripped, with a syscall allowlist
  enforced by seccomp at startup.

## 6. Auth stack — proposed, not decided

The previous revision specified an in-app SRP-6a client and a token store. The
experiment has not been run, and the prior art suggests the Watch may hold the
session while the phone only relays the owner's input.

Two candidate shapes:

| Shape | What the app does | Cost |
| --- | --- | --- |
| **Relay** | Passes the owner's input into the Watch's setup flow; stores nothing | No SRP, no token store; the password still transits the app and must be zeroized |
| **Session** | SRP-6a + 2FA in Rust, token in Keystore, provisions the Watch | Device attestation is the unknown; tokens become the highest-value secret |

Rules that hold either way:

- No password on disk, in logs, in crash reports, or in backups.
- No 2FA code in logs; expire it from memory.
- Confirmation that a code is accepted only once.
- A sign-out path that works offline.
- No analytics or crash SDK on auth screens.
- The auth code is open source and reviewed.

ADR 0003 stays **Proposed** until the experiment says which shape the Watch
accepts.

## 7. CI gates

1. `cargo fmt --check`, `cargo clippy -D warnings`, `cargo test`.
2. `cargo deny` (licences, advisories, bans).
3. Kotlin: `ktlint`, `detekt`, unit tests.
4. Golden-vector suite: every protocol spec has a passing vector.
5. Generated-bindings diff.
6. APK build + signature verification + SBOM.
7. Similarity scan for the provenance policy (see [03](03-clean-room.md)).
8. Auth tests once a shape is chosen: SRP or relay vectors, expiry, zeroization.

## 8. Performance and power

- The core is allocation-light; hot paths reuse buffers.
- The agent is resident while paired. That has a battery cost, and the UI states
  it. Scanning is duty-cycled; the link is not.
- The foreground service is mandatory, visible, and stoppable.
- The payload runs at most three times per boot; the UI shows the budget.

## 9. Open stack questions

- **UniFFI vs a thin C ABI** — UniFFI by default; revisit if it becomes the
  bottleneck.
- **SQLCipher vs field-level encryption** — depends on the metadata threat model.
- **Protobuf vs CBOR** for IPC — protobuf for schema evolution.
- **SRP vs relay** — decided by the activation experiment, not by preference.
- **Kotlin Multiplatform** for a desktop UI — not now; the desktop harness is a
  test tool, not a product.
