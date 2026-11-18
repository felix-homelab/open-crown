# ADR 0001 — Kotlin UI + Rust core + UniFFI

**Status:** Accepted
**Date:** 2026-11-18

## Context

The companion needs a native Android UI, a testable protocol engine, and a
privileged helper. Prior approaches used a shared Flutter UI and a hand-written
JNI bridge, which made process lifetime, Bluetooth permissions, and ABI drift
hard to control.

## Decision

- UI: Kotlin 2.x + Jetpack Compose.
- Core: Rust 2024, no OS I/O, pure state machines and codecs.
- Bindings: UniFFI-generated Kotlin.
- Agent: separate Rust binary, own process, socket RPC.

## Consequences

**Positive:** memory-safe core; host-side tests; small privileged surface;
explicit contracts; no Dart runtime.

**Negative:** two toolchains; UniFFI codegen step; Kotlin/Rust interop has a
learning curve; the team must be productive in both.

## Alternatives considered

- **Flutter + Dart:** rejected — a runtime bridge at the exact layer that needs
  control.
- **Kotlin-only core:** rejected — protocol codecs over untrusted bytes benefit
  from Rust's safety and host testing.
- **C++ core:** rejected — same safety concerns as JNI, worse ergonomics.
