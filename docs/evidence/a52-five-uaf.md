# CVE-2026-20971 — Samsung FIVE/PROCA task_integrity UAF on A52

New route after the binder service-registration blocker.

## Reachability on the A52

The A52 exposes the same procfs surface:

```text
/proc/self/integrity/label
/proc/self/integrity/reset_cause
/proc/self/integrity/reset_file
/proc/self/integrity/value
```

All four are readable as `shell`. The A52 kernel source also contains
`security/samsung/proca` and `task_integrity` consumers.

## The bug

`TASK_INTEGRITY(task)` is a raw `task->integrity` pointer; the procfs
handlers do not take a reference. During `execve()`, `__five_bprm_check()`
allocates a new `task_integrity`, assigns it, then calls
`task_integrity_put(old_tint)`, freeing the old object. A parent reading
`/proc/<child>/integrity/*` can be preempted after fetching the raw pointer
and resume on the freed object.

Affected Android 13–16; fixed in the January 2026 SMR. The A52 SPL is
2025-09-01, before the fix.

## Public state

LucidBit Labs published primitives, not a full LPE:

- `value` read: DWORD leak of reclaimed `task_integrity->user_value`;
- `reset_file`/`d_path`: `d_dname` function-pointer call, but KCFI removes
  useful targets;
- `label` read: spinlock write at `task_integrity->value_lock` (offset
  0xc), plus a potential leak.

The public CVE-2026-20971-CYL1 harness is an unprivileged **DoS** on
SM-S9210/6.1; it explicitly does not implement reclaim, R/W, or root.
`task_integrity` also has a dedicated cache, so cross-cache reclaim is
required.

## Next on-device work

1. Port the victim/reader `execve` race to the A52 and confirm the UAF
   oracle (`reader` SIGSEGV / dmesg oops) without reboot loops.
2. Evaluate the DWORD leak and spinlock write against the A52 `task_struct`
   and cache geometry.
3. Only if a useful write/leak primitive is found, build the cred/SELinux
   path.

This is a reachable new bug, not another GhostLock carrier attempt. No root
yet.
