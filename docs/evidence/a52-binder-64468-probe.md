# A52 binder UAF reachability probe — 2026-11-21

- **Device:** Galaxy A52s 5G `SM-A528B`, kernel
  `5.4.254-qgki-28575149-abA528BXXSBGYI3`, SPL `2025-09-01`, binary B
- **Candidate:** CVE-2026-64468, Linux Binder `binder_free_transaction()`
  process-lifetime UAF
- **Public code:** [aramosf/CVE-2026-64468](https://github.com/aramosf/CVE-2026-64468)
  (`binder_chain_64468.c`, arm64 QEMU PoC; x86_64 LPE)
- **Run as:** `uid=2000(shell)`, SELinux `Enforcing`
- **Result:** PoC builds and runs, but aborts at the Android context-manager
  boundary. No race, no root, no panic; boot id unchanged.

## Static A52 source check

The A52 `drivers/android/binder.c` has the old
`binder_free_transaction()`:

```c
static void binder_free_transaction(struct binder_transaction *t)
{
        struct binder_proc *target_proc = t->to_proc;
        if (target_proc) {
                binder_inner_proc_lock(target_proc);   /* can be freed */
                ...
```

The upstream fix `f223d27a546c` adds `spin_lock(&t->lock)` and pins
`t->to_thread`. The fix is from June 2026; the A52 source is September 2025,
so the fix is absent. CVE-2026-64468 lists Linux 5.2–7.1 as affected.

## Device probe

Built the public PoC for arm64 API 34. The first run aborted because the PoC
pins the manager to CPU 7 and that `sched_setaffinity` failed; a local build
with the pin ignored reached the binder setup:

```text
CVE-2026-64468 CHAIN-START duration=1s threads=2 ... cpus=8
CVE-2026-64468 CHAIN-ABORT stage=manager status=M
```

`status=M` is the manager's `BINDER_SET_CONTEXT_MGR` failure. Android already
has `servicemanager` as the context manager, so an unprivileged process cannot
become it.

Additional checks:

- `/dev/binder` is a 0666 symlink to `/dev/binderfs/binder`; the manager can
  open it, but `BINDER_SET_CONTEXT_MGR` is refused (`M`).
- `/dev/vndbinder` open is denied to shell (`O`); `/dev/hwbinder` opens but its
  context manager is already set (`M`).
- `unshare -U` returns `EINVAL`; `unshare -m` is not permitted. A private
  binderfs mount is not available.
- `/dev/binderfs/binder-control` is `0600 root:root`, and the binderfs
  directory is not traversable by shell, so a new binder device cannot be
  created.

No root, no race, no panic, no persistent change. The public PoC's Linux
context-manager bootstrap is the blocker on Android; the vulnerability itself
is present.

Full output: [a52-binder-64468-probe.log](a52-binder-64468-probe.log).

## Next research step

A real Android chain needs a bootstrap that does not require becoming the
context manager: register a node with the existing `servicemanager`
(`addService`) if shell/app permission allows, or obtain a private binder
device another way. That is an Android adaptation, then arm64 offsets for the
public x86_64 LPE. Neither is done, and no root is claimed.
