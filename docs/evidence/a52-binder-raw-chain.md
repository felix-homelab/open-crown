# A52 raw binder CVE-2026-64468 chain — working construction

The app harness now builds the public B/P nested synchronous transaction
chain with raw binder threads.

## Changes

- B's synchronous e2 transaction carries a `BINDER_TYPE_BINDER` object at
  offset 4, so P receives a handle to B's raw binder node.
- P reads that object from the e2 payload and sends e1 to it, not to the
  WalkerService Java binder.
- B's raw thread handles the incoming `BR_TRANSACTION` code 2, sends
  `BC_REPLY`, frees the buffer, and then issues `BINDER_THREAD_EXIT`.
- P sends e1 from the same binder thread that received e2, then execs a
  clean shell (closing its binder fd) after the expected count.

## Observed

On the A52:

```text
walker pid=16776
victim pid=16777
walkerStart result=0
victim sent e1 count=1/16
...
victim sent e1 count=16/16
victim execing clean shell
```

The app process remains alive; the victim service restarts and the chain
repeats. No panic occurred in this run.

This is the first correct B/P nested raw-binder chain on the A52. The next
steps are to increase B-thread/round concurrency, add an observable UAF
oracle, and only then attempt reclaim/creds.

No root; no `uid 0`.

## Thread scaling

The binder process is limited to **16 simultaneous binder threads**:

- with `walkerStart(..., 64)`, the victim stopped at `count=16/64` and never
  completed the chain;
- with 16, it completes all 16 nested e1 calls and execs.

A 60-second run completed 4 victim cycles (`victim execing clean shell`),
with no panic. Each victim restart takes roughly 15 seconds, so the current
Java-service loop is too slow for a probabilistic race.

The next implementation should use a raw P loop that reopens `/dev/binder`
and re-registers its node without waiting for an Android service restart.

## Manager broker scaffold

Added a Java ManagerService handle broker (codes 10/12 register, 11 get)
and a native raw registration probe. The native probe reports

```text
broker manager=63 slot=1 rc=0 errno=11
brokerProbe=0
```

but the manager never logs the registration, so the binder object is not
being delivered/parsed yet. The next fix is the binder object alignment and
the raw transaction offsets/reply reading.

The chain itself was stabilized by fixing the victim expected count at 16.
A 60-second run still produced only 4 complete victim cycles; the service
restart/reconnect delay dominates. A raw P endpoint loop is still needed for
a useful race rate.

No root; no panic in this run.

## Per-process binder handle blocker

A new raw `/dev/binder` open creates a **separate binder_proc** from the
Android framework's binder fd. Handles obtained from `BinderProxy` are
per-proc, so a Java ManagerService handle is invalid on the new fd. A raw
code-1 transaction to manager 63 returned `rc=0` but was never delivered.

Using the existing framework fd still did not deliver the broker
registration/reply; the raw write path needs the same read/reply handling as
`nativeRawTransact`.

For a looping raw P endpoint, the right architecture is either:

- use the process's existing framework binder proc (but P cannot be freed
  repeatedly), or
- obtain a fresh context-manager handle for each new raw proc.

`BINDER_SET_CONTEXT_MGR` is already held by servicemanager on this Android
device, so the app cannot become a second context manager without a binderfs
mount/CAP_SYS_ADMIN.

The raw B/P chain still works on the framework procs; the raw-proc loop is
blocked on handle exchange.

## servicemanager registration is denied

App UID:

```text
SecurityException: App UIDs cannot add services.
```

Shell UID via `app_process` (a dedicated dex calling
`ServiceManager.addService`):

```text
uid=2000
fail java.lang.SecurityException: SELinux denied.
```

`BINDER_SET_CONTEXT_MGR` is already held by servicemanager, and unprivileged
service registration is denied for both app and shell. Therefore a new raw
binder_proc cannot obtain a valid handle to the Java ManagerService or to any
broker it registers.

The only remaining binder chain uses the **existing framework binder procs**,
which completes 16/16 nested transactions but cannot be freed/reopened in a
fast loop for the narrow race.

No root.
