# A52 two-service binder topology harness — 2026-11-21

- **Device:** Galaxy A52s 5G `SM-A528B`, kernel
  `5.4.254-qgki-28575149-abA528BXXSBGYI3`, Enforcing
- **Boot id before/after:** `27d1bcba-dea6-45b0-9cd6-1679851bbb67` (unchanged)
- **Result:** the CVE-2026-64468 transaction shape was reproduced through two
  app services; no kernel panic and no UAF trigger in these runs.

## Shape

- `WalkerService` in `:walker` (B) and `VictimService` in `:victim` (P) bind
  to each other through `ActivityManager`.
- Walker extracts P's raw `BpBinder` handle through
  `BinderProxy.mNativeData` + offset `0x10`.
- 8 native walker threads send synchronous e2 (`code=1`) to P.
- P's `onTransact` sends nested e1 (`code=2`) back to the walker handle.
- Walker threads read e1 on the same thread, free the buffer, wait, then
  issue `BINDER_THREAD_EXIT`.
- P schedules process death after e1, freeing its `binder_proc`.

## Observed

```text
BinderHarness: walker pid=24544
BinderHarness: victim pid=24545
BinderHarness: walkerStart fd=77 victim_handle=38 threads=8
BinderProbeNative: worker index=0 send=0 child=0
BinderHarness: victim sent e1; scheduling death
BinderProbeNative: worker index=0 exited
BinderHarness: victim pid=24629
```

The e2/e1 nested transaction is delivered; the walker thread exits with the
stack; the victim process dies. No `kernel_panic` and no UAF was observed in
two timings (walkers exit 20–34 ms after e1 with victim death at 20 ms, and
walkers exit 100–135 ms after e1 with victim death at 10 ms).

## Verdict

**Topology: reproduced. UAF: not triggered. Root: none.**

The remaining problem is the precise `binder_thread_release()` versus
`binder_deferred_release()` race, not the bootstrap. The public PoC uses more
threads, CPU pinning, preemption/IRQ helpers and round control; this harness
does not yet reproduce that timing.
