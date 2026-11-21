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
| CVE-2026-20971 | Samsung KNOX `task_integrity` UAF, A-series tested (A54) | **DoS, not LPE.** The public package is an unprivileged DoS trigger and report, explicitly without a privilege-escalation chain: [CVE-2026-20971-CYL1](https://github.com/zhanghangjie/CVE-2026-20971-CYL1). No A52 target and no public exploitation. |
| CVE-2025-21479 | Adreno KGSL command-packet bug; Some Samsung Snapdragon ports | **Not this GPU.** The public PoC states it affects Adreno A7xx (Snapdragon 8 Gen 1 / XR2 Gen 2 and newer): [CVE-2025-21479 PoC](https://github.com/Koalamana9/CVE-2025-21479). The A52s reports `Adreno642Lv1` (A6xx). The FX5P port is a different SoC/kernel and is not a drop-in. |
| Other temporary-root loaders | Curated in [awesome-temporary-root](https://github.com/fynks/awesome-temporary-root) | The [Samsung device table](https://github.com/fynks/awesome-temporary-root/blob/main/docs/devices/samsung.md) lists S23/S24/S25/S26, A15/A16/A17/A36/A53/A56 and tablets. **No A52/A52s row.** |
| Bootloader unlock + Magisk/KernelSU | A52s OEM unlock is region-dependent; custom recoveries and kernels exist | A root path, but **not** a per-boot payload: it requires an unlocked bootloader, a data wipe, a modified boot image and a persistent root. It violates ADR 0007/0011 and the "nothing survives reboot" goal, and trips KNOX. It is not planned as an Open Crown payload. |

## What this means for the project

The A52 remains the M1 **probe/readiness/UI** device, not a payload target.
`catalog/supported-devices.json` keeps it as `example`; no A52 target is added
to `catalog/payload-index.json`.

The practical options are:

1. **Port a 5.4-compatible chain.** The closest public raw bug is
   CVE-2026-20971 (if the A52 is affected), but only a DoS PoC exists. Turning
   it into `uid 0` + a domain is original exploit development with its own
   target offsets and evidence, not an integration task.
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
- The external payload and provenance in
  [../../payloads/external/org.opencrown.payload.futex-timer/PROVENANCE.md](../../payloads/external/org.opencrown.payload.futex-timer/PROVENANCE.md).
- This note is a negative result, not a claim that the A52 is secure. The
  device is out of security support (EOL 2025-10-10); a future public chain
  would still need an exact A52 profile.
