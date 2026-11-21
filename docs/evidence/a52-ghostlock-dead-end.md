# A52 GhostLock data-only chain — documented dead end (2026-11-21)

**Target:** SM-A528B / `a52sxq`, kernel
`5.4.254-qgki-28575149-abA528BXXSBGYI3`, Android 14, locked bootloader,
SELinux Enforcing.

## What works

- **UAF is real and reachable.** The stock `remove_waiter()` clears
  `current->pi_blocked_on` instead of `waiter->task`. Both the 64-bit and
  32-bit futex-PI triggers return `EDEADLK` / `ETIMEDOUT`.
- **Trigger confirmation.** With the carrier skipped, `sched_setattr(waiter)`
  walks the dangling waiter and panics the kernel, proving the UAF.
- **KASLR leak.** A raw `sched_blocked_reason` record leaks a kernel text
  caller; matching it against
  `rpmh_write_batch+0x27c` gives a page-aligned slide each boot. The helper
  is in the research notes.
- **Stack geometry.** The WAIT_REQUEUE_PI waiter is at `top-0x198`;
  `rt_mutex_waiter` task/lock/prio/deadline are at +0x30/+0x38/+0x40/+0x48.

## Why the public data-only chain cannot complete

The fake waiter needs 0x50 bytes starting at `top-0x198`. Every available
unprivileged stack-copy primitive was measured against that address:

| Primitive | Copy address | Problem |
| --- | --- | --- |
| `process_vm_rw` iovstack | `top-0x190` | misses +0x0 by exactly 8 bytes |
| compat `_newselect` (142) | `top-0x1e0`, fd_sets 0x78 | parent lands in the **out** bitmap, rewritten by `do_select` |
| compat pselect6 | `top-0x270` | too deep |
| `MCAST_BLOCK_SOURCE` / `MCAST_MSFILTER` | heap | `memdup_user`, not stack |
| `PR_SET_MM_MAP` | `top-0x1e0` | requires `CAP_SYS_RESOURCE` |
| `PR_SET_VMA` | none | stores a user pointer |
| `sendmsg` iovstack | `top-0x158` | too high |
| `sendmmsg` / `poll` | ≤ `top-0x394` | too deep |

`process_vm_rw` covers waiter+0x8 through task/lock, and `_newselect` can
write the parent word, but `do_select` rewrites it. A whole-overlay attempt
still panics; a carrier-free run also panics, confirming the UAF rather than
a missing bug.

The LOCK_PI path has a fully covered waiter at `top-0x168`, but the tested
LOCK_PI/chain topology returns EDEADLK without creating the
WAIT_REQUEUE_PI-style dangling waiter, so it does not provide the write.

## Other routes

- **CVE-2026-64468 binder UAF:** investigated earlier; not triggered on the
  framework-shared app victim under the no-unlock constraint.
- **OEM unlock / custom kernel:** forbidden by the user; would be a
  persistent KNOX-tripping change.
- **New stack-copy primitive / different fake-waiter layout:** not found.

## Verification

All tests were on the real A52. Panics produced
`ro.boot.bootreason=kernel_panic,bug`; the phone recovered each time with no
data loss observed. No run produced `uid 0`.

**Conclusion:** with the public GhostLock data-only chain and the available
unprivileged syscalls on this exact A52 kernel, the chain cannot deliver the
full fake waiter to the kernel stack. This is a documented dead end for this
route, not a claim that no other kernel bug exists.
