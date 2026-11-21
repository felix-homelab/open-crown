# Plan — A52 original chain research

## Goal

Produce a **temporary, reboot-reversible** root path for
`SM-A528B / 5.4.254-qgki-28575149-abA528BXXSBGYI3`, or conclude with evidence
that no practical path exists under the project constraints.

## Non-goals

- No permanent boot-image modification.
- No bootloader unlock on the daily A52.
- No payload from another device presented as an A52 payload.
- No claim of root before an on-device `uid=0` + SELinux context observation.

## Candidate triage

| # | Candidate | Why it is on the list | Main obstacle |
| --- | --- | --- | --- |
| 1 | CVE-2026-20971 PROCA/FIVE `task_integrity` UAF | A52 source predates the Jan-2026 fix; reachable from `/proc/<pid>/integrity/*`; and the public report shows the UAF trigger | Dedicated `task_integrity_cache`, free-time scrub, RKP_CFP/KDP; public work stops at DoS |
| 2 | CVE-2025-47375/47376/47379/47386 Qualcomm IOCTL UAFs | A52 is EOL and pre-March-2026; generic-looking IOCTL/shared-buffer bugs | No public PoC; patch diff and object lifetime must be derived |
| 3 | CVE-2026-25291 shared-memory page-list race | A52 is pre-Oct-2026; local race | No public PoC; concurrency and cache control unknown |
| — | CVE-2026-21385 KGSL alignment | Public probe exists | Probe was negative on this A52 |
| — | CVE-2023-33107 KGSL SVM | Full public exploit exists | Patched in A52 source; older builds blocked by binary B |
| — | GhostLock / 98260 / DirtyFrag | Public chains exist | Wrong kernel branch (5.4 vs 5.10/6.x) |

Start with #1 because the trigger is public and the A52 source is confirmed to
have the vulnerable lifetime pattern. If the UAF cannot be turned into a
controlled primitive after a bounded reclaim study, move to #2/#3 rather than
force it.

## Phases

### P0 — Lab

- Sacrificial A52s (same model; ideally same binary/build), full backup,
  bootloader unlock, custom 5.4 kernel with KASAN/KCSAN and
  `panic_on_oops=0`, EDL/firehose recovery tested.
- No chain work on the daily A52.
- Exit: lab boot survives a deliberate oops and can be restored.

### P1 — Bounded UAF confirmation

- Build a two-process trigger on the lab kernel; CPU pinning; one run per boot.
- Confirm the exact `d_path`/`reset_file` UAF with KASAN; capture dmesg.
- Exit: reproducible UAF, no uncontrolled panic; trigger and logs committed
  under `research/a52-chain/` with a lab-only guard.

### P2 — Primitive study

- Map `struct task_integrity`, cache flags, scrub sequence and every read path.
- Try reclaim with new `task_integrity` objects (same cache), not generic
  sprays; determine whether any field is user-influenced enough to leak or
  redirect a read.
- If dedicated cache/scrub/RKP makes a controlled primitive impossible,
  record the negative result and stop. Do not invent a primitive.
- Exit: either a repeatable info leak/read/write primitive, or a documented
  dead end.

### P3 — Root

- Data-only path preferred; RKP_CFP/KDP must not be assumed bypassable.
- Target: `uid 0` + a domain that can hold the controller, then launch the
  Crown agent with token/boot id.
- KernelSU late-load is acceptable only as a test vehicle; it is not the
  product requirement.
- Exit: on the lab A52, `su -c id` shows `uid=0`, SELinux context recorded,
  reboot removes everything.

### P4 — Exact target and productization

- Reproduce on `A528BXXSBGYI3` specifically; record hashes, offsets and
  evidence.
- Only then consider a catalog entry. The A52 stays `example` until the run
  and evidence exist.

## Abort criteria

- No controlled primitive after a bounded reclaim study of candidate #1.
- Trigger requires disabling KASLR/RKP in a way that cannot exist on stock.
- Lab device cannot be recovered from a panic.
- Any step requires modifying the daily A52's boot chain.
- No sacrificial device is available.

## Safety

- One bounded run per boot; no loops that can boot-loop.
- `panic_on_oops=0` only on the lab kernel; stock A52 stays untouched.
- No partition writes; no bootloader changes on the target phone.
- Every run is logged with boot id and dmesg.
