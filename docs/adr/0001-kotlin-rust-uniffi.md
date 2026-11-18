# ADR 0001 — Kotlin UI + Rust core + UniFFI

**Status:** Accepted
**Date:** 2026-11-18 (revised)

## Context

The protocol already works in Java + Flutter + Rust
([apple_watch_connector](https://github.com/LordixDemon/apple_watch_connector)).
Open Crown does not add a capability the prior art lacks; its one new claim is
stock-phone privilege, which belongs to the payload work.

The rewrite is therefore a **maintainability decision**, not a product
requirement. The previous revision justified it with "Flutter bridge control"
and "JNI drift". Those are real costs, but they are secondary; the honest reason
is that the maintainer does not want to build the next phase on that tree.

## Decision

- UI: Kotlin 2.x + Jetpack Compose.
- Core: Rust 2024, no OS I/O, pure state machines and codecs.
- Bindings: UniFFI-generated Kotlin.
- Agent: separate Rust binary, own process, socket RPC, resident for the boot.

## Consequences

**Positive:** memory-safe core; host-side tests; a small privileged surface;
explicit contracts; no Dart runtime.

**Negative:** two toolchains; a UniFFI codegen step; three to six months before
the protocol work starts; the rewrite delivers no capability the prior art lacks.
That cost is in the effort table, not hidden in a percentage.

## Alternatives considered

- **Fork the prior art and port it incrementally** — rejected as the default:
  the Java/Flutter tree is the thing the maintainer wants to leave behind. It
  remains the fallback if the rewrite stalls.
- **Flutter + Dart** — rejected: a runtime bridge at the layer that needs
  process and service control.
- **Kotlin-only core** — rejected: protocol codecs over untrusted bytes benefit
  from Rust's safety and host testing.
- **C++ core** — rejected: same safety concerns as JNI, worse ergonomics.
