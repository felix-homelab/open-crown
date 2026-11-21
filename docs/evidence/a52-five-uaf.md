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

## On-device result

The public victim/reader harness was compiled aarch64-static and run as
shell on the A52:

```text
/data/local/tmp/five_uaf_probe 5 reset_file
```

The device immediately rebooted:

```text
ro.boot.bootreason=kernel_panic,null
boot_id=c8eede6d-1971-41e4-80e8-fd003373c54b
```

That confirms the FIVE/PROCA `task_integrity` UAF is reachable on this A52
kernel, not just on the public SM-S9210 target.

A `value`-field run did not panic, but hung until the timeout; the boot id
stayed `c8eede6d-...`.

## Remaining exploitation problem

A52 has `CONFIG_CFI_CLANG=y` (non-permissive) and the `task_integrity`
dedicated cache. The public primitives are a DWORD leak, a CFI-constrained
`d_dname` call, and the `value_lock` spinlock write at offset 0xc. None is a
direct arbitrary write; a cross-cache reclaim + CFI-compatible target is
needed before a cred/SELinux path can be built.

No root.

## A52 structure/free-path analysis

A52 `struct task_integrity` layout:

```text
user_value   0x00
value        0x04
usage_count  0x08
value_lock   0x0c
list_lock    0x10
label        0x18
events       0x20...
reset_cause  ...
reset_file   ...
```

`task_integrity_free()` scrubs the object before `kmem_cache_free()`:

```c
kfree(intg->label); intg->label = NULL;
intg->user_value = INTEGRITY_NONE;
intg->value = INTEGRITY_NONE;
atomic_set(&intg->usage_count, 0);
intg->reset_cause = CAUSE_UNSET;
if (intg->reset_file) { fput(intg->reset_file); intg->reset_file = NULL; }
kmem_cache_free(task_integrity_cache, intg);
```

So a plain reader sees scrubbed zeros unless the slot is reclaimed. The
cache is dedicated (`task_integrity_cache`), so a cross-cache reclaim is
required for the value/leak or spinlock primitives.

The `reset_file` path is more interesting: a failed `execve` of a regular
executable non-ELF file such as `/system/bin/monkey` leaves a fresh tint
whose `reset_file` has refcount 1. That makes the file UAF reachable without
cross-cache reclaim, but `d_path` then hits the A52's non-permissive KCFI
`d_dname` call and the file/dentry objects still need controlled reclaim.

No root yet.

## Reclaim spray availability

The usual Android/Linux cross-cache sprays were checked on the A52:

```text
CONFIG_SYSVIPC       not set
CONFIG_POSIX_MQUEUE  not set
add_key(user)        EACCES (Permission denied)
```

So the public msg_msg pump and key-payload spray are unavailable. The
remaining controllable allocations are sk_buff (sendmsg), pipe buffers, and
possibly user xattrs; none is a drop-in replacement for the dedicated
`task_integrity_cache` reclaim in the published research.

This makes the FIVE/PROCA UAF a confirmed DoS/reachability result on the
A52, but a full LPE requires developing a new cross-cache reclaim from the
remaining allocation primitives.
