# A52 GhostLock sigreturn carrier

Adapted the P50/A52 GhostLock binary to use `rt_sigreturn` fpsimd stamping
instead of the MCAST/process_vm carriers.

A52 geometry derived from the stock Image:

```text
__arm64_sys_rt_sigreturn frame  0x30
restore_sigframe frame          0x60
restore_fpsimd_context frame    0x220, copy at helper sp, size 0x200
futex_wait_requeue_pi frame     0x1a0, waiter at sp+0x98
syscall-entry waiter depth      0x198
sigreturn copy offset to waiter 0x118
```

The waiter installs a SIGUSR1 handler, the main child `tgkill`s it after the
FUTEX_WAIT_REQUEUE_PI timeout, and the handler writes the fake rt_mutex_waiter
(11 words) into the delivered fpsimd vregs at +0x118. `rt_sigreturn` copies
that region to the stale waiter.

## On-device result

The carrier runs and reaches the trigger walk:

```text
slide sigreturn tgkill tid=... ret=0 ... armed=1
slide child carrier_done=1
```

The subsequent `sched_setattr` walk hangs; the device watchdog panics after
~100 s (`kernel_panic,sobugarm_watchdog_timer_exhardd_100s`). No write was
observed. Offsets 0x100/0x108/0x110/0x118/0x120 and several fake-lock
locations (BSS, selinux_state page, empty_zero_page) all hung.

The carrier is therefore not yet validated; the fake waiter placement,
fake-lock state, or the walk's expected tree contents need correction.
