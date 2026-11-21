# A52 temporary-root research — documented dead end under no-unlock

Target: SM-A528B / `a52sxq`, kernel
`5.4.254-qgki-28575149-abA528BXXSBGYI3`, Android 14, locked bootloader,
SELinux Enforcing. Constraint: no OEM unlock, no wipe, no persistent custom
kernel.

## Routes evaluated on the real device

### 1. GhostLock / CVE-2026-43499

- UAF confirmed in 64-bit and 32-bit; no-carrier `sched_setattr` panics.
- Per-boot KASLR leak works via raw ftrace caller.
- Stock symbols and task/waiter offsets recovered.
- Every unprivileged stack-copy carrier fails to deliver the full waiter:
  process_vm is 8 bytes short; compat select is clobbered by `do_select`;
  pselect is too deep; MCAST is heap; `PR_SET_MM_MAP` is capability-gated.

### 2. Binder / CVE-2026-64468

- Public B/P nested topology reproduced on framework binder procs.
- Raw 32-bit and 64-bit binder objects and `BINDER_THREAD_EXIT` replies work.
- A raw proc cannot exchange handles: `ServiceManager.addService` is denied
  for app UIDs and SELinux-denied for shell; `BINDER_SET_CONTEXT_MGR` is
  already owned by servicemanager.
- Framework-proc chain cannot be freed/reopened fast enough for the narrow
  race.

### 3. Samsung FIVE/PROCA / CVE-2026-20971

- UAF confirmed by panic from both `/proc/self/exe` and
  `/system/bin/monkey` reset_file triggers.
- `task_integrity_free()` scrubs before free; object uses a dedicated cache.
- A52 has non-permissive KCFI.
- `SYSVIPC`/`POSIX_MQUEUE` off; `add_key` EACCES; public msg_msg/key sprays
  unavailable.
- A cred/suid overlap hypothesis exists (`value_lock` at 0xc vs `cred->suid`),
  but no cross-cache reclaim or controlled write has been achieved.

## Conclusion

All three reachable bug classes are blocked by the A52's platform/cache/CFI
constraints, not by a lack of UAF triggers or KASLR. No run produced `uid 0`.

To continue productively, one of the constraints must change:

1. allow OEM unlock / custom kernel or KernelSU image, or
2. authorize a different device/sacrificial bootloader state, or
3. provide a new unprivileged primitive/bug that does not need the missing
   stack-copy/reclaim/service-registration step.

Without one of those, the objective is a documented dead end on this exact
locked A52.

## FIVE/PROCA follow-up

Cred layout on A52 was recovered from setresuid/setresgid disassembly:

```text
uid 0x04, gid 0x08, suid 0x0c, egid 0x10, euid 0x14, sgid 0x18
```

The FIVE `value_lock` at 0xc really overlaps `suid`. However, the same label
handler then dereferences `task_integrity->label` at 0x18, which on a
reclaimed cred is `sgid`. An ordinary app/shell cred has a nonzero gid, so
the dereference panics before the zeroed suid can be used. A viable target
must have gid 0; unprivileged A52 processes cannot obtain that
(`CONFIG_USER_NS` off, no setgid-root binary, CapEff zero).

No alternative handler performs the spinlock write, and `value` has no
write. Thus the FIVE cred/suid idea is also blocked.
