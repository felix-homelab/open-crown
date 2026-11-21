# A52 no-unlock chain — documented dead end

**Device:** Samsung Galaxy A52s 5G `SM-A528B`, kernel
`5.4.254-qgki-28575149-abA528BXXSBGYI3`, bootloader binary B (11), locked,
Android 14, SPL `2025-09-01`.
**Constraint:** stock kernel, locked bootloader, no unlock, no persistent
change, no data wipe.

## What was proven

- The A52 kernel source is statically missing the CVE-2026-64468
  `binder_free_transaction()` fix, so the bug is present.
- An ordinary app can obtain a binder handle to another app process through
  `ActivityManager.bindService`, bypassing the denied
  `BINDER_SET_CONTEXT_MGR`/`addService` paths.
- `HiddenApiBypass` + `BinderProxy.mNativeData` exposes the native
  `IBinder*`; the `BpBinder` handle is at offset `0x10` (confirmed by
  disassembling the A52 `libbinder.so`).
- Raw native `BC_TRANSACTION` works through the app's existing
  `/dev/binder` fd (`700 -> 701`, `300 -> 301`).
- A two-service `:walker`/`:victim` harness reproduces the public PoC's
  e2/e1 nested transaction, `BINDER_THREAD_EXIT`, and victim death/fd close.

Evidence: [a52-binder-bootstrap.md](a52-binder-bootstrap.md),
[a52-binder-harness.md](a52-binder-harness.md).

## Why the chain stops here

1. **Victim process model.** The public PoC's P is a dedicated process that
   opens its own `/dev/binder`, sends e1, and closes that fd. The A52 app
   victim is shared with the framework, ActivityManager bindings and the
   binder thread pool. Closing one fd or killing the process does not free
   its `binder_proc` at the instant the PoC needs; coordinated 16-thread runs
   with CPU pinning still produced no UAF/panic.

2. **No clean bootstrap for a dedicated binder proc.** A native child with a
   fresh binder fd needs a handle in that new proc. The app-based
   `bindService` handle cannot be transferred into a new binder proc, and
   the denied `BINDER_SET_CONTEXT_MGR`/`addService` paths are the only normal
   bootstrap for one.

3. **No public arm64 privilege escalation.** The public
   [CVE-2026-64468 repository](https://github.com/aramosf/cve-2026-64468)
   provides an unprivileged arm64 PoC and an **x86_64** LPE. The LPE uses
   x86_64 control-flow/`commit_creds` gadgets and hard-coded kernel offsets;
   there is no public arm64/A52 port. The A52 adds KASLR, PAC/kCFI-era
   hardening, Samsung RKP and SELinux, with no lab kernel or symbol leak.

4. **Other candidates are not a better route.** CVE-2026-20971 is DoS-only
   (dedicated `task_integrity_cache`, free scrub, RKP); CVE-2026-21385 was
   negative on the A52; CVE-2023-33107 and CVE-2023-20938 are patched;
   CVE-2026-57545 is absent; DirtyFrag needs 6.5+; GhostLock/SamTimer target
   5.10+; CVE-2025-21479 is A7xx-only. The binary B anti-rollback blocks
   downgrade to a pre-fix build.

## Only known working A52 root

OEM unlock + a custom 5.4 kernel / KernelSU (or equivalent). That is
persistent, wipes data, trips KNOX and changes the device's security state.
The user explicitly forbade unlocking, so it is outside this goal.

## Verdict

Under the stated no-unlock constraint, there is **no working stock-kernel
temporary-root chain** for this A52. The app-based binder work is a real
bootstrap and topology, but it cannot reach the UAF with a clean victim proc,
and the only public LPE is x86_64. This is a documented dead end for the
constraint; it is not a claim that the kernel is unfixed.
