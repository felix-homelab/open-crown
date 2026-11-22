# ADR 0020 — K2a blocked on the A52: choose the next product path

**Status:** Proposed — requires a product-owner decision
**Date:** 2026-11-22
**Supersedes:** the K2a assumption in [ADR 0007](0007-uid0-plus-domain.md) for
this device and chain; it does not change the long-term goal.

## Context

K2a asked whether a per-boot payload can yield a process that holds the
Bluetooth controller under enforcing SELinux, without the priv-app/hidden-API
configuration the project avoids.

The A52 chain answers a narrower question and fails K2a:

- The page-cache chain yields uid 0 in u:r:init:s0 and patches the intended
  function (576/576 bytes verified).
- The app's real domain, u:r:untrusted_app:s0, is **denied** a connection to the
  relay socket (EACCES), while the shell domain connects.
- The relay is an unauthenticated raw-syscall RPC. Anyone allowed to connect can
  execute arbitrary syscalls as init.
- The relay never returns to its init caller. It permanently consumes the init
  thread that logged, kills the property/log path, and the phone watchdog-resets
  within minutes.
- There is no unprivileged trigger; the app cannot make init log, and it cannot
  connect afterwards.

Evidence: [broker-probe-a52.md](../evidence/broker-probe-a52.md),
[a52-broker-untrusted-app.md](../evidence/a52-broker-untrusted-app.md),
[BLOCKERS.md](../cleanroom/BLOCKERS.md#b-app-agent-broker).

The current chain therefore cannot be repaired into a shipped broker by adding
an app-side client. Both the SELinux boundary and the relay's own design are
blockers.

## Decision to make

Pick one product path. This ADR records the options and a recommendation; it is
not accepted until the owner chooses.

### Option A — Rooted-device companion (recommended for the product path)

Require an existing root solution the user controls (Magisk/KernelSU or a
user-supplied root) to start the agent. The app never ships a privilege chain.
Keep the per-boot, visible, watchdogged agent design. This is already ordered by
[ADR 0010](0010-ship-order.md) and is the only path that can reach the transport
on this phone without a policy change.

- Pros: executable now, honest installation requirement, no new exploit work.
- Cons: not a stock-phone product; narrower audience; the agent still needs a
  controller domain that the root solution provides.
- Work: verify the root solution's SELinux context can bind HCI_CHANNEL_USER;
  build the app-to-agent socket in a directory both sides can access; keep the
  agent authenticated and visible.

### Option B — Time-boxed alternative privilege research

Search for a mechanism that yields an app-connectable, authenticated agent and
does not consume init. Examples include a different payload family, a
system-server path, or a policy-compatible broker.

- Pros: preserves the stock-phone thesis.
- Cons: unbounded research; the last chain took weeks and still failed the
  product requirement. Must have a hard time box and a kill criterion.

### Option C — Unprivileged product now

Ship discovery, diagnostics, the registry, and device explanation without
pairing, and defer the companion to a later release.

- Pros: useful, honest, shippable now.
- Cons: does not meet the product's reason to exist.

## Recommendation

Take **A** as the product path and **C** as the immediate release shape, with a
small, explicitly time-boxed **B** spike only if the owner wants to keep the
stock-phone thesis alive. Do not take the current relay into any release: it is
unauthenticated root and destabilises the phone.

## Consequences

- The feature registry's transport-gated rows stay blocked or unimplemented;
  they are not re-labelled "in progress".
- The README and install flow must state the root requirement if A is chosen.
- The in-tree chain remains a research artifact behind the Diagnostics consent
  sheet; it is not a product feature.
- If the owner chooses B, no UI claim changes until it produces a measured,
  authenticated agent channel.

## Not decided here

- Whether the Watch itself can activate without an iPhone (the activation gate).
- Whether a physical Watch is available for verification at all.
