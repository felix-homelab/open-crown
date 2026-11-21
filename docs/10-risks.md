# 10 — Risks

Scored L (likelihood) × I (impact), each 1–5. Anything with a score ≥ 15 has a
named owner and a written mitigation before the phase that exposes it.

## 1. Register

| # | Risk | L | I | Score | Mitigation |
| --- | --- | --- | --- | --- | --- |
| R1 | Activation needs an Apple-device-held session | 3 | 5 | 15 | Phase 2 decision; relay vs session; iPhone-once as a written fallback |
| R2 | No payload path to the controller (K2a fails) | 3 | 5 | 15 | Phase 0 tests both shapes before any scaffolding; rooted-device fallback |
| R3 | Payload panics or bricks a phone | 3 | 4 | 12 | Per-family persistence analysis, preflight, watchdog, brick drill |
| R4 | Apple ID lockout or attestation wall | 3 | 4 | 12 | Relay-first bias, no auto-retry, gate G1 |
| R5 | Apple legal action or takedown | 2 | 5 | 10 | No Apple binaries or keys; MIT; accepted-risk posture |
| R6 | watchOS update breaks the protocol | 4 | 3 | 12 | Versioned specs, capture suite, per-release delta, support statements |
| R7 | Android update breaks the payload | 4 | 3 | 12 | Exact-target catalog, retirement, supported list |
| R8 | One test device hides portability bugs | 4 | 3 | 12 | Expand the matrix before claiming support |
| R9 | Maintainer burnout / abandonment | 3 | 5 | 15 | Scope order (ADR 0010), small releases, published decision log |
| R10 | Malicious or broken payload in the catalog | 2 | 5 | 10 | Offline signing, transparency log, review, revocation |
| R11 | Credential handling leaks a password | 2 | 5 | 10 | Zeroization, no logging, open auth code, security review |
| R12 | Health data leak | 2 | 4 | 8 | Local-first, encrypted at rest, opt-in, export/delete |
| R13 | Scope creep (calls/SMS, watch apps) | 4 | 2 | 8 | T3 best effort; watch apps permanently out |
| R14 | Legal ambiguity in a target jurisdiction | 3 | 4 | 12 | Source-first distribution; published risk statement |
| R15 | Supply-chain compromise of the build | 2 | 4 | 8 | Pinned deps, SBOM, reproducible builds, signed releases |
| R16 | Product pushes users to skip security updates | 4 | 4 | 16 | One-time warning, supported list, no update-shaming, retirement on patch |
| R17 | Resident root agent compromised | 3 | 5 | 15 | Small binary, seccomp, no network, foreground visibility, watchdog |
| R18 | Public n-day used against users before it is patched | 4 | 4 | 16 | It is already public; we do not add capability, we document the exposure |
| R19 | User updates and the app silently stops working | 5 | 3 | 15 | Update check, explicit "this build is no longer supported" state |
| R20 | Prior art abandoned, map goes stale | 3 | 3 | 9 | Keep own captures; do not depend on it as a runtime component |
| R21 | Provenance contamination from contributors | 2 | 4 | 8 | Signed attestation per PR; similarity scan; named prior art |
| R22 | Single reviewer ships a bad payload | 3 | 4 | 12 | @@unreviewed@@ labelling; 7-day public comment on the catalog PR |
| R23 | Phase 0 conflates exploit failure with policy failure | 4 | 3 | 12 | Stage-level reporting; separate K2a and K2b verdicts in the evidence |

## 2. The four that actually matter

### R2 — no path to the controller

If a payload cannot hold the controller under enforcing SELinux without Magisk's
configuration, the stock-phone product does not exist. This is why Phase 0 runs
before Phases 1–8, and why ADR 0010 already defines the fallback.

**Early warning:** every shape ends in `selinux_denied`; the only working
configuration is the one we set out to replace.

### R16 — the product makes users less safe

The supported devices are the unpatched ones. That is a genuine harm, not a
marketing problem. Mitigation is honesty and a short list, not a cleverer
message.

**Early warning:** a support request that says "I turned off updates to keep it
working" and we have no answer.

### R17 — a resident root daemon

The agent is a long-lived privileged process on a networked device. Its attack
surface is the app's socket protocol and the controller path, and it must be
treated as exposed for the whole boot.

**Early warning:** any parser in the agent; any network call from the agent; any
capability broader than the operation requested.

### R19 — updates kill the app

A vendor patch silently retires a payload, and the user experiences a broken
app. The app must detect the build, say plainly that support ended, and point at
the rooted-device path.

**Early warning:** a user on a newer build than the catalog's newest entry.

## 3. Assumptions that could invalidate the plan

1. A payload can take the controller without Magisk-class configuration.
2. Activation can be completed with the owner's Apple ID from the phone, or the
   Watch can hold the session with a relay.
3. A public n-day can be maintained for at least one Android release cycle.
4. The protocol is observable as a pairing peer, not only on the wire.
5. Users will accept a resident root daemon and an unpatched device.
6. One maintainer can run research, product, and distribution at once.

Each has a test or a written fallback. If one fails, the project re-scopes; it
does not push through.

## 4. What we will not do to de-risk

- We will not ship a stolen-device feature to grow the audience.
- We will not hide the agent from the user to make the app feel smoother.
- We will not claim support for a device we have not tested.
- We will not use Apple binaries or keys to shortcut the protocol work.
- We will not buy or hoard 0-days to make the catalog look stronger.
