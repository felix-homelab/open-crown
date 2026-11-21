# A52 GhostLock SROP carrier

The signal-handler patch path did not reliably restore the patched fpsimd
vregs. A real-frame SROP path was implemented instead:

1. capture a real `rt_sigframe` from a SIGUSR1 handler (`ucontext - 0x80`);
2. copy it to a 0x2000-byte aligned buffer;
3. locate the fpsimd record and write the 11-word fake waiter at
   `vregs + 0x118`;
4. set the frame's saved `sp`/`pc` to a static stack and `srop_resume`;
5. call `rt_sigreturn` directly from a naked trampoline.

## On-device proof

The FP marker written into the frame at `vregs+0x118+0x50` is restored into
`v22.d[1]` after `rt_sigreturn`:

```text
slide srop fp marker=4141414142424242
slide child carrier_done=1 sig magic=46508001 vregs=16 off=280 sve=0
cur_lock=... task=0000000000000000
```

So the SROP frame, record patch, kernel copy, and register restore path all
work. The GhostLock walk still hangs afterward. Offsets 0x100, 0x108, 0x110,
0x118, 0x120 and zero/fake-lock variants have all hung.

This rules out the ucontext-copy problem; the remaining issue is the exact
stale-waiter stack address or the fake waiter/lock state consumed by
`rt_mutex_adjust_prio_chain`.

## uid_lock fake-lock test

A real static `DEFINE_RT_MUTEX(uid_lock)` exists on A52 at
`0xffffffc0131675b0`; `uid_sys_stats` itself is not exposed through the
accessory path. Using the real zero `uid_lock` as `waiter->lock` with SROP
still produced `carrier_done=1` followed by a hung `sched_setattr` walk.

This makes a merely-nonzero fake `wait_lock` an unlikely sole explanation; the
stale-waiter placement remains the leading suspect.
