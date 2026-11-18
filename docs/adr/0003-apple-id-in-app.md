# ADR 0003 — Apple ID handling

**Status:** **Proposed** — blocked on the Phase 2 experiment
**Date:** 2026-11-18 (revised)

## Context

Activation is coupled to an Apple ID, but the previous version of this ADR
assumed the app had to perform SRP-6a and store tokens. The prior art reaches a
visible face with no Apple ID string in its documentation, using an
`activationResponse` command and an `ACTIVATION_INPUT` phase: the owner's
input travels separately, through the setup flow.

Two shapes are plausible:

1. **Relay** — the Watch holds the session; the phone forwards the owner's input
   and stores nothing.
2. **Session** — the phone authenticates against Apple (SRP + 2FA) and
   provisions the Watch; tokens become the most valuable secret in the system.

We do not know which the Watch accepts, so this ADR does not decide.

## Decision

**Deferred.** Phase 2 traces the prior art's activation path and tests which
shape is accepted. The ADR is updated to Accepted or Superseded with evidence.

Until then, no SRP client and no token store are written.

## Consequences

**Positive:** the highest-risk subsystem is not built on a guess; the relay
shape, if it works, removes the token store and much of the credential liability.

**Negative:** activation stays unresolved longer; the UI cannot be finalised;
both shapes must be kept in the data model.

Either way: the password is transient and zeroized, never logged or persisted; a
2FA code is used once and never stored; the auth code is open source and reviewed.

## Alternatives considered

- **Decide app-side now** — rejected: it is exactly the assumption that failed
  the review.
- **iPhone-once activation** — kept as a fallback, but note that unpairing an
  Apple Watch erases it, so "activate on the iPhone, then take over" may not
  survive the handover. It needs the same experiment, not a sentence.
