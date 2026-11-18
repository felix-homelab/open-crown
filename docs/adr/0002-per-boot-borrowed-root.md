# ADR 0002 — Per-boot borrowed root, not persistent root

**Status:** Accepted
**Date:** 2026-11-18

## Context

Owning the Bluetooth controller needs uid 0 or HAL ownership on the phone. The
owner's requirement is that the phone stays unrooted. Three models were on the
table: require the user to root permanently (Magisk/KernelSU), borrow root per
boot through an exploit, or move the radio to a desktop bridge.

## Decision

Borrow root per boot through signed, device-matched exploit payloads. The phone
remains stock: no bootloader unlock, no boot-image modification, no persistent
su, no Magisk dependency. The agent exists only for the session and is gone
after reboot.

## Consequences

**Positive:** matches the owner's requirement; a reboot is a clean recovery;
no permanent attack surface; travels across devices without a custom kernel.

**Negative:** re-bootstrap every boot; exploit reliability becomes a product
feature; unsupported devices fall back to discovery-only; the catalog must be
maintained against vendor patches; a panic mid-session costs the session.

## Alternatives considered

- **Persistent root (Magisk/KernelSU):** rejected — violates the stock-phone
  requirement and widens the threat model.
- **Desktop bridge only:** rejected as the primary architecture — it is not an
  Android app, and it contradicts the product.
- **Bring-your-own-root only:** kept as an escape hatch for developers, not the
  default.
