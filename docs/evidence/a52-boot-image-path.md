# A52s boot-image path research

Target: SM-A528B / Snapdragon 778G, locked bootloader, Android 14, no wipe.

## Result

No public unprivileged boot-image path was found for this device.

## Candidates checked

- **Odin authentication bypass CVE-2024-20865 / LK JPEG CVE-2024-20832**
  (Quarkslab Galaxy A* boot-chain): the published chain targets MediaTek
  Galaxy A devices and Little Kernel. A52s SM-A528B is Qualcomm and uses the
  Qualcomm boot chain; the Quarkslab chain does not apply as published.
- **CVE-2026-21155 / SVE-2026-3546 Samsung S-Boot logo decoder OOB write**:
  affects bootloaders before SMR Nov-2026. Public PoC target is SM-S731U1 and
  the reported impact is a bootloader-side OOB/DoS, not a verified A52s root.
  A52s-specific applicability and a root-capable primitive are not public.
- **DirtyFrag / CVE-2026-43284**: 5.4-qgki research says this is not feasible
  because the exploit needs MSG_SPLICE_PAGES (6.5+).
- **Boot image signature**: the stock boot image is verified by the bootloader;
  `up_param`/logo paths for Snapdragon A52s are not a writable root path.
- **OEM unlock**: the ordinary Samsung unlock requires the OEM-unlock toggle,
  download mode, and a data wipe; it is the only known boot-image route and is
  excluded by the user's no-wipe constraint.

## Consequence

A boot-image root on this A52s requires either OEM unlock (wipe/Knox) or a new
device-specific bootloader exploit with a public/derived root primitive. None
was found in public sources.
