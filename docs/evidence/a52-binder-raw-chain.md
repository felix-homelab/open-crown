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
