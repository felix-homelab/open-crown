# ADR 0016 — Consent-gated execution supervisor

**Status:** Accepted for private development
**Date:** 2026-11-21

## Context

M1 deliberately stopped at readiness and `prepare`; it staged a bundle and
minted a token but never ran it. The next layer needs to turn a prepared bundle
into a launched payload without pretending that the app itself has acquired
privilege.

The app process is unprivileged. On Android, executing a file from app-private
data is normally denied by SELinux/W^X, and a payload that grants `uid 0` is
started by its own bootstrap, not by the UI. An execution API therefore cannot
be a root escalation; it is a **consent gate and launch contract** around a
staged entry.

## Decision

- `runtime::execution::execute` takes a `Prepared` bundle, a `Consent` record,
  arguments and a timeout.
- It re-checks the consent payload id, version and capability set against the
  manifest, re-hashes the staged entry against the signed manifest, makes the
  entry executable, and launches it with `CROWN_TOKEN` and `CROWN_BOOT_ID`.
- It captures bounded stdout/stderr and kills the process group on timeout.
- `ExecutionBudget` enforces the documented three attempts per boot in memory.
  Persisting the budget across a service restart is future work.
- It does not acquire root, write SELinux policy, start the agent, or touch HCI.
  Those remain behind the payload bootstrap and the resident-agent design
  (ADR 0007, ADR 0008, ADR 0011).
- It is not wired to the UI or FFI yet. The A52 has no bundle, and the external
  SamTimer payload requires a Shizuku/shell runner that Open Crown does not have.

## Consequences

**Positive:** the launch contract is executable and tested on the host; token
and boot id are passed exactly once; a tampered staged entry is refused; a hung
entry cannot wedge the caller.

**Negative:** on Android the same call will usually fail with `EACCES` from the
app sandbox. That failure is the correct result until the payload bootstrap or a
privileged runner exists. The budget is not yet persisted, and the supervisor is
not yet reachable from Kotlin.

## Alternatives considered

- **Run the payload from the UI process with `Runtime.exec`** — rejected: the
  app is `untrusted_app`; it cannot execute staged payloads and must not pretend
  it can.
- **Add Shizuku now** — deferred: it is a new privileged-runner dependency and
  a separate decision, not part of the launch contract.
- **No execution layer until K2a** — rejected: the contract and its failure
  modes can be tested on the host now.
