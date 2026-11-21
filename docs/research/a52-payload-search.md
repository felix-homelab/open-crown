# A52 payload search — 2026-11-21

**Target:** Samsung Galaxy A52s 5G `SM-A528B` (`a52sxq`), Snapdragon 778G
(`SM7325`), Adreno 642L, kernel
`5.4.254-qgki-28575149-abA528BXXSBGYI3`, Android 14 / SDK 34, build
`A528BXXSBGYI3`, SPL `2025-09-01`.

**Question:** is there a public, per-boot, locked-bootloader payload for this
exact build that Open Crown can integrate as an external bundle?

**Answer:** no public payload was found. Every viable family stops on the
kernel version, the GPU family, or the public chain's maturity.

## Candidates checked

| Candidate | Claimed target | Verdict for the A52 |
| --- | --- | --- |
| [DirtyFrag](https://github.com/mitschud/DirtyFrag) / CVE-2026-43284 | ESP-in-UDP page-cache write; Samsung 5.10–6.18 KMIs | **Dead on 5.4.** The bug exists in 5.4, but the exploit needs `MSG_SPLICE_PAGES` UDP support (kernel 6.5+). The 5.4-qgki research records this as not feasible: [dirtyfrag-research](https://github.com/HirokiAkihiko/dirtyfrag-research). |
| GhostLock / CVE-2026-43499 | futex-priority-inheritance UAF; Root My Galaxy 5.15–6.6, RootMyS24 6.1 | **No 5.4 target.** Root My Galaxy publishes 5.10.237, 5.15.189, 6.1.145, 6.1.157, 6.6.x and 6.6.102 payloads; the public [supported-payload table](https://github.com/BuSung-dev/Root-My-Galaxy-Payloads#supported-payloads) has no 5.4 build. |
| SamTimer / CVE-2026-98260 | POSIX CPU timer UAF; RootMyS24 6.1.145–6.1.162 | **No 5.4 target.** The public profiles and the integrated `futex-timer` bundle are 6.1.145+; the CVE's fixed stable lines are 6.1.191 and later. |
| CVE-2026-20971 | Samsung KNOX `task_integrity` UAF, A-series tested (A54) | **DoS, not LPE.** The public package is an unprivileged DoS trigger and report, explicitly without a privilege-escalation chain: [CVE-2026-20971-CYL1](https://github.com/zhanghangjie/CVE-2026-20971-CYL1). No A52 target and no public exploitation. The A52 source predates the Jan-2026 fix, so the UAF code is likely present, but there is no public root chain. |
| CVE-2023-33107 | Qualcomm KGSL SVM integer wrap; A52/778G is an affected platform | **Patched.** The exact A52 kernel source (`razorback159/A528BXXSBGYI3`) has the fix in `kgsl_iommu.c`: `iommu_addr_in_svm_ranges()` computes `end = gpuaddr + size` and rejects `end <= gpuaddr`, the same check as Qualcomm's [fix commit](https://git.codelinaro.org/clo/la/kernel/msm-4.19/-/commit/d66b799c804083ea5226cfffac6d6c4e7ad4968b). A full public exploit exists ([keto0422](https://github.com/keto0422/CVE-2023-33107)) but is dead on this build. |
| CVE-2026-21385 | Qualcomm KGSL alignment sign extension; Snapdragon 778G listed; CISA KEV | **Public probe negative on this A52.** The exact source contains the uncapped `max_t(uint64_t, 1 << kgsl_memdesc_get_align(memdesc), PAGE_SIZE)` line, but the public probe run as `uid 2000` produced `gpuaddr=0x4080000000` for align 31, not the wrapped `0x80000000`; no `kgsl` fault or panic. See [../evidence/a52-kgsl-probe.md](../evidence/a52-kgsl-probe.md). The bug is actively exploited elsewhere, but this primitive does not work on the A52. |
| CVE-2026-57545 | KGSL draw-object type confusion in `adreno_hwsched.c` | **Not applicable / absent.** `get_active_cmdobj_lpac` and the `is_cmdobj` path are not present in the A52 5.4 source tree; the bug is in a newer hardware-scheduler path. |
| CVE-2026-25291 | Qualcomm shared-memory page-list race; 778G listed | **No public exploit.** The CVE describes concurrent shared-memory page-list corruption; no public PoC or root chain was found, and the A52 is EOL. |
| CVE-2025-21479 | Adreno KGSL command-packet bug; Some Samsung Snapdragon ports | **Not this GPU.** The public PoC states it affects Adreno A7xx (Snapdragon 8 Gen 1 / XR2 Gen 2 and newer): [CVE-2025-21479 PoC](https://github.com/Koalamana9/CVE-2025-21479). The A52s reports `Adreno642Lv1` (A6xx). The FX5P port is a different SoC/kernel and is not a drop-in. |
| Other temporary-root loaders | Curated in [awesome-temporary-root](https://github.com/fynks/awesome-temporary-root) | The [Samsung device table](https://github.com/fynks/awesome-temporary-root/blob/main/docs/devices/samsung.md) lists S23/S24/S25/S26, A15/A16/A17/A36/A53/A56 and tablets. **No A52/A52s row.** |
| Bootloader unlock + Magisk/KernelSU | A52s OEM unlock is region-dependent; custom recoveries and kernels exist | A root path, but **not** a per-boot payload: it requires an unlocked bootloader, a data wipe, a modified boot image and a persistent root. It violates ADR 0007/0011 and the "nothing survives reboot" goal, and trips KNOX. It is not planned as an Open Crown payload. |

## Static source checks

The exact A52 kernel source is mirrored at
[razorback159/A528BXXSBGYI3](https://github.com/razorback159/A528BXXSBGYI3)
(Samsung OSRC, build `A528BXXSBGYI3`, September 2025). Checks performed:

- `kgsl_iommu.c` has the CVE-2023-33107 wrap check `end <= gpuaddr`; that
  exploit is patched.
- `kgsl_iommu.c` still has the CVE-2026-21385 `1 << align` line, but the public
  probe does not wrap on the A52 (below).
- `adreno_hwsched.c` has no `get_active_cmdobj_lpac`, so CVE-2026-57545 does
  not apply to this build.
- `security/samsung/five/task_integrity.c` and PROCA are present; the
  source predates the January 2026 CVE-2026-20971 fix. Only a DoS trigger is
  public.

## Device probe

The public CVE-2026-21385 probe was built for arm64 API 34 and run on the A52
as shell (`uid 2000`, Enforcing). It opened `/dev/kgsl-3d0` and swept
`MAP_USER_MEM`, `GPUOBJ_ALLOC` and `GPUOBJ_IMPORT`. Align 31 returned
`gpuaddr=0x4080000000` (a normal 64-bit pagetable address), not the wrapped
`0x80000000`; there was no `kgsl` fault, `BUG`, panic or `FATAL`. The probe is
negative for this build. Full output:
[../evidence/a52-kgsl-probe.md](../evidence/a52-kgsl-probe.md).

## What this means for the project

The A52 remains the M1 **probe/readiness/UI** device, not a payload target.
`catalog/supported-devices.json` keeps it as `example`; no A52 target is added
to `catalog/payload-index.json`.

The practical options are:

1. **Port a 5.4-compatible chain.** The closest public raw bugs are
   CVE-2026-20971 (KNOX UAF, DoS-only public) and the KGSL races
   (CVE-2026-25291 / CVE-2025-4737x, no public PoC). Turning any of them into
   `uid 0` + a domain is original exploit development with its own target
   offsets and evidence, not an integration task. The one public KGSL probe
   that was tested (CVE-2026-21385) is negative on the A52.
2. **Port GhostLock to 5.4.** The public GhostLock project has 5.4 branches
   for other vendors, but no Samsung A52 profile. This needs A52 symbols,
   offsets and a kernel build; no source or device evidence exists here.
3. **Relax the no-unlock constraint.** Use the A52's OEM unlock, a custom
   kernel with KernelSU and a data wipe. That is a different product decision
   and is explicitly outside the current ADRs.
4. **Use a supported device for payload work.** The external `futex-timer`
   bundle already targets `SM-S9280` DZJ3; a DZJ3 device is the shortest path
   to an actual payload run.

## Evidence kept

- The A52 probe/readiness evidence in [../evidence/a52-m1.md](../evidence/a52-m1.md).
- The CVE-2026-21385 device probe and log in
  [../evidence/a52-kgsl-probe.md](../evidence/a52-kgsl-probe.md).
- The external payload and provenance in
  [../../payloads/external/org.opencrown.payload.futex-timer/PROVENANCE.md](../../payloads/external/org.opencrown.payload.futex-timer/PROVENANCE.md).
- This note is a negative result, not a claim that the A52 is secure. The
  device is out of security support (EOL 2025-10-10); a future public chain
  would still need an exact A52 profile.
