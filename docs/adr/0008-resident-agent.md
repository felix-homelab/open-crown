# ADR 0008 — Resident agent for the life of the boot

**Status:** Accepted
**Date:** 2026-11-18

## Context

The charter sold "no always-on daemon". Notifications require a live link at the
moment a notification fires, and the prior art runs its root owner behind a
separate foreground service. A session-scoped agent that exits with the UI
cannot deliver the product.

## Decision

The agent is raised once per boot, after a user gesture, and stays resident
behind a **mandatory foreground service with a visible notification and a stop
action** for as long as a watch is paired. It exits on unpair, on app stop, or at
reboot.

The payload is **not** re-run to reconnect. Reconnect uses the resident agent.
Re-raising the agent after it has exited requires a new gesture.

## Consequences

**Positive:** notifications work; reconnect is cheap; the consent model becomes
honest (one gesture, one visible daemon) instead of pretending every operation
is a fresh escalation.

**Negative:** a long-lived root process is a long-lived attack surface; battery
cost; "session" now means one boot, and the charter had to be rewritten. The
agent's parser and policy are now core security code.

## Alternatives considered

- **Foreground-only notifications** — rejected: the Watch's main value is not
  delivered.
- **Re-run the payload per notification batch** — rejected: exploit reliability,
  crash exposure and audit volume would scale with notification rate, and three
  attempts per boot cannot support it.
