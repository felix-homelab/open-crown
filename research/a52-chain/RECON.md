# Recon — exact A52 source facts

Source mirror: [razorback159/A528BXXSBGYI3](https://github.com/razorback159/A528BXXSBGYI3)
(Samsung OSRC kernel for `A528BXXSBGYI3`, September 2025).

## CVE-2026-20971 surface

- `security/samsung/five/task_integrity.c`:
  - `task_integrity_cache` is a dedicated `kmem_cache` with
    `SLAB_HWCACHE_ALIGN|SLAB_PANIC|SLAB_NOTRACK`, object size
    `sizeof(struct task_integrity)`.
  - `task_integrity_free()` scrubs `label`, `user_value`, `value`,
    `reset_cause` and `reset_file` before `kmem_cache_free()`.
  - `task_integrity_alloc()` initializes `usage_count` and the event list.
- The `/proc/<pid>/integrity/*` readers need to be traced in the A52 tree
  (`security/samsung/five/` and PROCA) to confirm the same
  no-reference-before-use pattern as the public report. The public report
  targets 6.1.128; the A52 is 5.4 and must be checked, not assumed.
- `/proc` surface is same-UID readable, so the trigger needs no root.
- The public report's exploitation section is explicit: no reclaim/grooming,
  no arbitrary read/write, no control-flow hijack, no root. Dedicated cache,
  free scrub and RKP are the barriers.

## Other candidates

- `kgsl_iommu.c` has the CVE-2023-33107 `end <= gpuaddr` check: patched.
- `adreno_hwsched.c` has no `get_active_cmdobj_lpac`: CVE-2026-57545 absent.
- CVE-2026-21385 source pattern exists but the public probe was negative on
  the device; see [evidence](../../docs/evidence/a52-kgsl-probe.md).
- CVE-2025-47375/47376/47379/47386 and CVE-2026-25291 need patch-diff work
  against the Qualcomm bulletins before any trigger can be designed.

## Why not just port a public chain

The public chains are exact-target code:

- DirtyFrag needs 6.5+ `MSG_SPLICE_PAGES`; 5.4 is structurally out.
- GhostLock/98260 profiles are 5.10/6.x; the 5.4 branches in the wider
  ecosystem are for other vendors, not A52.
- CVE-2023-33107's exploit is A90-specific and the bug is patched here.
- CVE-2026-21385's only public primitive is an OOB GPU VA probe, and it is
  negative on this device.

Porting means new offsets, new heap behaviour and new evidence — not a rename.
