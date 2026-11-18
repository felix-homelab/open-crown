# ADR 0004 — Payload catalog ships with the product

**Status:** Accepted, ordered by [ADR 0010](0010-ship-order.md)
**Date:** 2026-11-18 (revised)

## Context

The catalog is the riskiest subsystem: it signs and distributes code that yields
root. It could have been deferred, with v1 requiring an already-rooted phone.
The project wants all three of its tracks — research, companion, catalog — in
scope.

The previous guardrail contradicted the decision: it said the catalog ships in
v1, and then said it ships in v1.1 if it is not ready. That is not a guardrail,
it is a coin flip.

## Decision

The catalog is **in scope** and ships when its gates pass, in the order set by
[ADR 0010](0010-ship-order.md):

- **v1** — companion for prepared devices (bring-your-own-root, or a catalog
  payload if one exists). A complete product on its own.
- **v1.1** — catalog for unpatched devices, once supply, review and legal gates
  pass.
- If K2a and K2b pass early, the catalog returns to v1. The order decides, not
  the version number.

## Consequences

**Positive:** something useful ships even if the privilege work fails; the
protocol work is never blocked on catalog governance; the README's support
statements stay true.

**Negative:** the "no root required" promise is deferred, and the announcement
must say so plainly. Signing infrastructure, review, revocation, transparency
log, brick liability and the reviewer requirement all still land.

## Alternatives considered

- **Catalog in v1 unconditionally** — rejected: it makes the whole release
  hostage to the least plannable subsystem.
- **No catalog, bring-your-own-root only** — kept as the v1 fallback.
