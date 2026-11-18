# ADR 0009 — Public n-days and a supported-device list

**Status:** Accepted
**Date:** 2026-11-18

## Context

A payload must match model and kernel build exactly, and a vendor patch retires
it. The project needs a supply model; the previous documents scored this risk
but never answered it.

## Decision

- Payloads are built on **publicly disclosed vulnerabilities**, calibrated per
  build. No 0-day acquisition and no bespoke chain per device.
- Supported devices are those with a passing hardware test on the current
  release. The list names model and build, not an Android version range.
- The app states once, at setup, that updating the phone may end support. It
  never pressures a user to skip updates.
- A payload is retired when its bug is patched; the app reports
  `unsupported_build` and points at the rooted-device path.
- A port is a new entry with its own evidence; there are no wildcards.

## Consequences

**Positive:** a maintainable supply chain for one person; the exploit is not a
secret, so the security story is about the agent, not about secrecy.

**Negative:** the product works best on phones that are not receiving security
updates, which is a real harm to the user (R16). The support list ages, and the
project must say so loudly. The payload artifact is extractable and is a rooting
tool for that build, which the legal chapter accepts.

## Alternatives considered

- **Buy or develop 0-days** — rejected: cost, ethics, and legal exposure.
- **Bring-your-own-root only** — kept as the fallback and the v1 path in
  ADR 0010.
