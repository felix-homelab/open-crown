# A52 GhostLock SROP carrier — documented dead end

## What was built

A real-frame SROP carrier for CVE-2026-43499:

1. capture a real `rt_sigframe` from a SIGUSR1 handler;
2. copy it to an aligned 0x2000 buffer;
3. locate the fpsimd record and write the fake rt_mutex_waiter at
   `vregs + 0x118`;
4. set saved `sp`/`pc`;
5. invoke `rt_sigreturn` directly.

The FP marker at `vregs+0x118+0x50` is restored into `v22.d[1]`:

```text
slide srop fp marker=4141414142424242
slide child carrier_done=1 sig magic=46508001 vregs=16 off=280 sve=0
```

So frame capture, patch, kernel copy, and register restore work.

## Why it still does not root

The subsequent `sched_setattr` chain walk always hangs. Tested:

- fake locks: `empty_zero_page`, `init_stack`, `__bss_start`, `init_task`
  PI fields, `init_task.pi_lock`, and the real static `DEFINE_RT_MUTEX(uid_lock)`;
- stamp offsets `0x100` through `0x120`;
- pointer and leaf tree shapes, including a safe in-kernel target;
- slot file disabled so the fake lock is slot 0.

All reached `carrier_done=1` and then hung. The watchdog dumps available in
`/data/log` do not include the spinning task’s kernel stack; they only show a
global exit-path deadlock.

The missing information is the actual stale `pi_blocked_on` address (or the
exact `rt_sigreturn` copy depth). The only unprivileged ways to measure it were
tested and denied:

```text
shell: /proc/self/stack  permission denied
app:   stack_fd=96, stack_read=-1 errno=13
shell/app: perf_event_open errno=13
shell/app: /proc/kallsyms errno=13
```

Without that address, every fake-waiter placement is unverified, and the walk
cannot be distinguished from reading the original waiter.

## Conclusion

This is a documented dead end for the GhostLock chain on this locked A52 under
the current constraints. A new unprivileged measurement/primitive, OEM unlock,
or a custom kernel is required.
