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
Android's `servicemanager` already owns the context manager. `/dev/vndbinder`
is not openable by shell; `/dev/hwbinder` also has a manager; binderfs is
root-only; unprivileged user namespaces are disabled.

See [../../docs/evidence/a52-binder-64468-probe.md](../../docs/evidence/a52-binder-64468-probe.md).

## What would make it a chain

1. **Bootstrap without replacing the context manager.** Register a binder node
   with the existing `servicemanager` (`addService`) if shell/app permission
   allows, then use `getService` handles for the walker/victim. This is the
   open question.
2. **Arm64 offset/port work.** The public LPE is x86_64; the A52 needs binder
   object offsets, kernel base/KASLR handling, and a `cred`/SELinux bypass.
3. **Stability.** A lost race corrupts `binder_proc`; each failed attempt can
   panic. A lab kernel or a sacrificial boot is needed.

## Status

**Statically vulnerable; public PoC present; Android bootstrap unsolved.**
No root and no chain. This is the most promising stock/no-unlock candidate
found so far, ahead of the KNOX UAF because a public LPE exists.
