# ADR 0010 — Portfolio order: research, companion, catalog

**Status:** Accepted
**Date:** 2026-11-18

## Context

The project wants three things at once: a research record, a stock-phone
companion, and a signed payload catalog. One maintainer cannot ship all three on
the same date, and the catalog depends on a supply chain and a review process
that do not exist yet.

## Decision

Ship in this order:

1. **Research record** — continuously. Specs, vectors, K2a/K2b results, hardware
   notes. No gate.
2. **Companion for prepared devices** — v1. Works with bring-your-own-root and
   with a catalog payload when one exists. This is a complete product on its own.
3. **Catalog for unpatched devices** — when the supply, review and legal gates
   pass. If they pass early, the catalog returns to v1; otherwise v1.1.

Phase 0 (K2a) runs before all of it and can end the stock-phone track honestly.

## Consequences

**Positive:** something useful ships even if K2a fails; the catalog is not
blocked on the protocol work; the README's support statements match reality.

**Negative:** the "no root required" promise is deferred, and the announcement
has to say that plainly. The rooted-device companion competes with the prior art
on polish rather than capability.

## Alternatives considered

- **Catalog first** — rejected: it gates the whole product on the least
  plannable subsystem.
- **Research only** — kept as the fallback if K2a fails and nobody wants a
  rooted-device app.
