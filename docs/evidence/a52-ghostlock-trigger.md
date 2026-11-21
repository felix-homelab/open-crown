# A52 GhostLock (CVE-2026-43499) trigger — 2026-11-21

- **Device:** Galaxy A52s 5G `SM-A528B`, kernel
  `5.4.254-qgki-28575149-abA528BXXSBGYI3`, Android 14, Enforcing
- **Config:** `CONFIG_FUTEX_PI=y`, `CONFIG_RANDOMIZE_BASE=y`,
  `CONFIG_ARM64_PAN=y`, `# CONFIG_UNMAP_KERNEL_AT_EL0 is not set`
- **Result:** the three-thread futex-PI cycle returns `EDEADLK`; the UAF
  trigger path is reachable. No panic, no root.

## Source check

`kernel/locking/rtmutex.c` in the A52 source has the vulnerable
`remove_waiter()`:

```c
raw_spin_lock(&current->pi_lock);
rt_mutex_dequeue(lock, waiter);
current->pi_blocked_on = NULL;
raw_spin_unlock(&current->pi_lock);
```

This uses `current`, not `waiter->task`, so the proxy-lock rollback leaves a
dangling `pi_blocked_on` pointer. The upstream fix is in the GhostLock
advisory.

## Trigger

`research/a52-chain/ghostlock/poc_deadlock_arm64.c`:

- waiter locks `f_pi_chain`, waits on `f_wait` with
  `FUTEX_WAIT_REQUEUE_PI` targeting `f_pi_target`;
- owner locks `f_pi_target`, then blocks on `f_pi_chain` (PI cycle);
- trigger calls `FUTEX_CMP_REQUEUE_PI` on `f_wait` with `uaddr2 =
  f_pi_target`, `val=1`, `val2=1`.

Observed:

```text
GhostLock three-thread deadlock trigger start
trigger ret=-1 errno=35 (Resource deadlock would occur)
waiter ret=-1 errno=110 (Connection timed out)
owner unexpectedly acquired chain
done waiter_ret=-1 trigger_ret=-1
```

Boot id before/after:
`27d1bcba-dea6-45b0-9cd6-1679851bbb67` (unchanged). No kernel panic.

## Verdict

**Vulnerable and reachable from `shell`; UAF trigger works. No root.**

Next: recover A52 symbol/struct offsets from a matching `vmlinux`, implement a
KASLR leak, and port the public arm64 GhostLock PI-write chain.
