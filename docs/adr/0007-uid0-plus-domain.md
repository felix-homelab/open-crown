# ADR 0007 — Borrowed privilege is uid 0 + SELinux domain

**Status:** Accepted, to be verified by K2a
**Date:** 2026-11-18

## Context

The previous contract was "uid 0 for one session". The only working Android
build of this stack ships a priv-app, a hidden-API whitelist and SELinux rules,
and states: *"Root access alone does not supply this configuration."*

On Android 12+, a process with uid 0 in an ordinary app domain still cannot stop
the platform Bluetooth stack, open an HCI device, or reach the vendor HAL. The
capability the design needs is a **domain**, not a uid.

## Decision

A payload must yield:

1. `uid 0` for the agent process, and
2. an SELinux context that permits holding the Bluetooth controller for the
   life of the boot.

The manifest's capability set is `root` + `domain` + `controller`. A
payload that yields only `root` is rejected by the agent.

## Consequences

**Positive:** the contract matches the capability the design needs, and K2a has
something concrete to test.

**Negative:** the payload may have to change policy, which is a system-level
change and weakens the "memory-only, reboot undoes it" story. Each payload must
therefore document its persistence behaviour. Transport shape A (HAL) may need
Magisk-class configuration; shape B (HCI) may need less. Phase 0 decides.

## Alternatives considered

- **Assume uid 0 is enough** — rejected: contradicted by the prior art's own
  installation notes.
- **Require Magisk** — that is the prior art, and it removes the only reason
  for this project to exist.
- **Bring-your-own-root only** — kept as the fallback in ADR 0010.
