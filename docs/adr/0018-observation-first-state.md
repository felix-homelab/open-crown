# ADR 0018 — Observation-first companion state

**Status:** Accepted
**Date:** 2026-11-22

## Context

The current app renders a single success screen after the page-cache chain runs.
It cannot distinguish "root was reached" from "the app can use root", and the
previous UI had no way to represent a watch value that is unknown, stale, or
blocked. As soon as the app grows real screens, the default failure mode is a
plausible-looking value with no evidence behind it.

The iPhone Watch experience does not have this problem: the phone owns the
transport and receives confirmations. Open Crown may never own the transport on
a given phone, and even when it does, the agent, the link, and the watch can
each be independently unavailable.

## Decision

Every watch-scoped UI value is an `Observation<T>` carrying:

- **origin** — watch report, agent report, advertisement, Android platform,
  local store, user input, or derived;
- **verification** — confirmed, reported, inferred, stale, unknown;
- **observedAt** — the time of the observation, not the time of render;
- **pair identity and epoch** — so a reset watch cannot inherit old values;
- **availability reason** — a closed enum when the value is absent.

Two corollaries:

1. **No promotion.** A stored pair is not a link. An advertisement is not a
   connection. A socket connect is not an authenticated agent session. A sent
   command is not an applied setting. Only `OBSERVED` may be rendered in the
   past tense or as a new current value.
2. **No silent defaults.** A missing value is rendered with its reason and a
   recovery action when one exists. Blocked rows name the blocker id and link
   to the evidence screen.

The design rules are in [docs/design/state-model.md](../design/state-model.md).
The implementation entry points are `model/Observation.kt`,
`data/CrownRepository.kt`, and the feature registry asset.

## Consequences

**Positive:** the UI cannot fake success; diagnostics and evidence are part of
the product; the registry, the app, and the docs share one status vocabulary;
tests can assert that a stored record is never `CONFIRMED`.

**Negative:** more verbose call sites, and screens must design an unavailable
state as carefully as a success state. That is the cost of an honest companion.

**Rejected alternatives:**

- **Nullable values with a global "connected" boolean** — hides the distinction
  between unknown and absent, and encourages defaults.
- **Optimistic writes with rollback** — impossible without a real transport,
  and it lies in the intermediate state.
- **A single "available" flag per screen** — a screen can be partially known;
  pairing, link, and face inventory have independent provenance.
