# ADR 0002 — Per-boot borrowed root, not persistent root

**Status:** Accepted, **under test** (K2a/K2b)
**Date:** 2026-11-18 (revised)

## Context

Holding the Bluetooth controller needs privilege Android will not grant an app.
Three models were on the table: require the user to root permanently
(Magisk/KernelSU), borrow privilege per boot through a payload, or move the radio
to a desktop bridge.

The original version of this ADR assumed `uid 0` was sufficient. It is not.
The only working Android build of this stack ships a priv-app, a hidden-API
whitelist and SELinux rules, and says root alone does not supply them. See
[ADR 0007](0007-uid0-plus-domain.md).

## Decision

Borrow privilege per boot through signed, exact-target payloads. The phone keeps
its bootloader locked and its system partition untouched. The payload yields
`uid 0` **and an SELinux context that can hold the controller**. The agent is
resident for the boot ([ADR 0008](0008-resident-agent.md)). At reboot, nothing
remains.

## Consequences

**Positive:** a reboot is a clean recovery; no permanent su; no boot-image
change; the model travels across devices without a custom kernel.

**Negative:** re-raise every boot; exploit reliability becomes a product feature;
the SELinux step may require policy changes, which weakens the "memory-only"
claim; unsupported devices fall back to bring-your-own-root; the catalog must be
maintained against vendor patches; a panic mid-boot costs the session.

Whether this is achievable at all is K2a. This ADR stays "under test" until
Phase 0 reports.

## Alternatives considered

- **Persistent root (Magisk/KernelSU)** — rejected: it is the prior art and
  removes the reason for this project.
- **Desktop bridge only** — rejected as the product: it is not an Android app.
- **Bring-your-own-root only** — kept as the v1 path in
  [ADR 0010](0010-ship-order.md).
