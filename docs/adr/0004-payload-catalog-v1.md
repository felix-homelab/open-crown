# ADR 0004 — Payload catalog ships in v1

**Status:** Accepted
**Date:** 2026-11-18

## Context

The catalog is the riskiest subsystem: it signs and distributes code that
borrows root. It could have been deferred to v2, with v1 requiring an
already-rooted phone. The owner chose to ship it in v1.

## Decision

v1 includes the payload catalog, the exploit runtime, and the agent. The rest of
the product (protocol, pairing, features) depends on the catalog for privilege,
so the catalog and the protocol work proceed in parallel.

## Consequences

**Positive:** the app is self-contained on a stock phone; no user prerequisite;
the core product promise holds from the first release.

**Negative:** signing infrastructure, review process, revocation, transparency
log, supply-chain hardening, brick liability, and a mandatory second reviewer
all land in v1. Any delay in the catalog delays pairing.

## Alternatives considered

- **v2 catalog, v1 requires root:** safer and faster, but splits the product
  into two audiences and delays the stock-phone promise.
- **No catalog, bring-your-own-root:** simplest legally, but reduces the app to
  a tool for an already-technical audience.

## Guardrail

If the catalog is not review-ready by the start of Phase 3, the release target
drops back to "requires existing root" for v1 and the catalog ships in v1.1.
The protocol work is not blocked by catalog governance.
