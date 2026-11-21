# Candidate — CVE-2026-64468 binder UAF

## Why this candidate

- A52 `drivers/android/binder.c` has the unfixed
  `binder_free_transaction()` (reads `t->to_proc` without pinning the target
  thread/process).
- CVE-2026-64468 affects Linux 5.2–7.1; A52 is 5.4.
- There is a public arm64 PoC and an x86_64 LPE:
  [aramosf/CVE-2026-64468](https://github.com/aramosf/CVE-2026-64468).
- Binder is the Android IPC path, so no unlock or custom kernel is needed in
  principle.

## Device result

The PoC runs on the A52 but aborts at `BINDER_SET_CONTEXT_MGR` (`status=M`):
Android's `servicemanager` already owns the context manager. A direct
`IServiceManager.addService` probe to handle 0 returned `EX_SECURITY`
(`status=-2147483647`) and no service was registered. `/dev/vndbinder` is not
openable by shell; `/dev/hwbinder` also has a manager; binderfs is root-only;
unprivileged user namespaces are disabled.

See [../../docs/evidence/a52-binder-64468-probe.md](../../docs/evidence/a52-binder-64468-probe.md).

## What would make it a chain

1. **Bootstrap without replacing the context manager.** Shell cannot
   `addService` (`EX_SECURITY`). An app-based bootstrap would need a different
   route (e.g. a bound service handle via `bindService`) and is unproven.
2. **Arm64 offset/port work.** The public LPE is x86_64; the A52 needs binder
   object offsets, kernel base/KASLR handling, and a `cred`/SELinux bypass.
3. **Stability.** A lost race corrupts `binder_proc`; each failed attempt can
   panic. A lab kernel or a sacrificial boot is needed.

## Bootstrap progress

The `BINDER_SET_CONTEXT_MGR` and `addService` paths are denied, but a
separate research APK proved that `ActivityManager.bindService` gives an
ordinary app a binder handle to a node in another app process. See
[../binder-bootstrap/README.md](binder-bootstrap/README.md) and
[../../docs/evidence/a52-binder-bootstrap.md](../../docs/evidence/a52-binder-bootstrap.md).

## Native handle gap

`HiddenApiBypass` makes `BinderProxy.mNativeData` readable; the native
`BpBinder` handle is at offset 0x10, and raw native transactions now reach the
Java service (`700 -> 701`, `300 -> 301`). The native raw-binder bridge is
proven. See [binder-bootstrap/README.md](binder-bootstrap/README.md).

## Topology harness

The two-service B/P topology now runs: e2 is delivered to victim, victim sends
nested e1 back to the walker thread, walker reads e1, exits the thread, and
victim dies. Coordinated 16-thread runs with CPU pinning and victim fd close
still show no UAF/panic. The app victim's binder proc is shared with the
framework and does not free like the PoC's dedicated P process; this is the
current obstacle. See
[../../docs/evidence/a52-binder-harness.md](../../docs/evidence/a52-binder-harness.md).

## Status

**Statically vulnerable; public PoC present; a viable app-based bootstrap
handle exists.** The PoC topology and arm64 LPE are not ported yet. No root
and no chain. This is the most promising stock/no-unlock candidate found so
far, ahead of the KNOX UAF because a public LPE exists.
