# ADR 0011 — No harness root

**Status:** Accepted
**Date:** 2026-11-21

## Context

Phase 0 was written with a temporary Magisk harness: root the test phone by
hand, run the probe, and isolate the controller question from the exploit
question.

The owner will not root a phone at any point. The product's claim is that
privilege arrives through the payload and nowhere else; a manually rooted test
phone would measure a different product.

## Decision

- No manual rooting step, in development or in testing. Not Magisk, not
  KernelSU, not adb root.
- The payload is the only path to privilege.
- Phase 0 runs the first payload and the probe in one session. K2a and K2b are
  attributed by stage, not by environment.

## Consequences

**Positive:** the experiment matches the product. There is no unlocked-bootloader
device in the loop, no "it works when rooted" illusion, and no harness
configuration that the product will never ship.

**Negative:** Phase 0 now costs a payload, so it is 6–10 weeks rather than 2–3.
Every iteration needs a working chain, which makes the loop expensive. A failure
is ambiguous unless the probe reports each stage. The permissive-mode comparison
is unavailable unless the payload itself can set it, and that must not be
assumed.

**Mitigation:** the probe prints uid, SELinux context, bind result, errno and the
first avc lines. The runtime records the payload id and signature with each run.
The evidence file carries separate K2a and K2b verdicts.

## Alternatives considered

- **Temporary root harness** — rejected by the owner, and it measures a
  configuration the product does not use.
- **Rooted emulator** — no Bluetooth controller, so it cannot answer the
  question.
- **Desktop/Linux harness** — proves the protocol, not the Android privilege
  path; still useful in Phase 3.
