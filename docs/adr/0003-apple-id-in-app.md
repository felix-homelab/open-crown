# ADR 0003 — In-app Apple ID sign-in

**Status:** Accepted (with a security gate)
**Date:** 2026-11-18

## Context

Activation is an Apple service bound to an Apple ID. Two viable models exist:
the user activates once on an iPhone and the app takes over, or the user signs
in inside the app. The owner chose in-app sign-in.

## Decision

The app implements Apple ID sign-in with SRP-6a and a 2FA relay. The password
is transient and zeroized; only tokens are stored, in the Android Keystore, with
expiry and revocation.

This decision has a hard gate: it ships only if the auth subsystem passes an
independent security review and the SRP implementation matches published test
vectors.

## Consequences

**Positive:** the product is genuinely iPhone-free; the user experience is
self-contained; no dependency on a second device.

**Negative:** the project becomes a credential-handling product. That raises
the security bar, the liability, and the trust requirements. Apple may
rate-limit or lock accounts. The auth code must be open and reviewed. The app
must explain exactly what is sent to Apple and why.

## Alternatives considered

- **iPhone-once activation:** simpler and safer, but contradicts the product
  promise. Kept as a fallback if the auth gate fails.
- **App-specific password:** only if Apple requires it; worse UX and still a
  credential.
- **OAuth / Sign in with Apple:** not applicable to the activation service.

## Gate

Kill gate K4 in the roadmap: if safe sign-in cannot be demonstrated, activation
falls back to iPhone-once and the positioning changes.
