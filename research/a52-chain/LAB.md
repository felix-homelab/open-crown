# Lab requirements

Do not start P1 on the daily A52.

## Hardware

- A **sacrificial** A52s `SM-A528B` (the same model; ideally binary B and
  build `A528BXXSBGYI3`, but any A52s for initial bring-up).
- USB cable, reliable power, and a tested recovery path:
  - download mode + Odin stock firmware for the exact model/CSC;
  - EDL/firehose loader if download mode is unavailable;
  - full backup of anything not expendable.

## Lab kernel

- Bootloader unlocked (destructive; wipes data, trips KNOX).
- Custom 5.4 kernel from the A52 source with:
  - `CONFIG_KASAN=y` (or KASAN + KCSAN where the race needs it);
  - `panic_on_oops=0` as a boot default for the lab;
  - `CONFIG_DEBUG_INFO` and symbols retained;
  - the stock Samsung drivers needed for PROCA/FIVE/KGSL.
- Keep the stock boot image and a known-good recovery image offline.

## Logging

- `pstore`/`ramoops` or serial if available.
- `dmesg` captured before each reboot; boot id recorded.
- One trigger run per boot; no automatic retry.

## Stop conditions

- Lab kernel cannot boot or cannot be recovered.
- Trigger causes an unrecoverable bootloop.
- No sacrificial device is available.
