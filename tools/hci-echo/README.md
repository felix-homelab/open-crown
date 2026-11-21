# hci-echo

**Status:** source exists and is syntax-checked on the host; not yet cross-compiled
or run on a phone.

## Purpose

The K2a probe from [06 — Roadmap](../../docs/06-roadmap.md), Phase 0. It runs as
the first agent, after the payload has produced uid 0, and answers one question:
can that process take the Bluetooth controller through the HCI user channel
under enforcing SELinux?

There is no harness root. The payload is the only path to privilege
([ADR 0011](../../docs/adr/0011-no-harness-root.md)).

## What it does

1. Prints uid, euid, /proc/self/attr/current and /sys/fs/selinux/enforce.
2. Opens an AF_BLUETOOTH raw HCI socket.
3. Brings the controller down with HCIDEVDOWN.
4. Binds to HCI_CHANNEL_USER.
5. Sends Read Local Version Information, then Reset, waiting for each Command
   Complete.
6. Reads /dev/kmsg for avc lines and prints them.

Every stage prints a machine-readable line:

    K2A stage=bind_user_channel status=ok errno=0 (ok)
    K2A uid=0 euid=0 gid=0 pid=1234
    K2A file=/proc/self/attr/current value=u:r:su:s0
    K2A avc=avc: denied { ... }
    K2A_RESULT status=pass

## Build

    ANDROID_NDK_HOME=/path/to/ndk tools/hci-echo/build.sh

The probe is packaged by the app and extracted by the runtime; pushing it with
adb is only for bring-up after the payload path exists.

## Run

See [runbook.md](runbook.md). Record the output in
[the evidence file](../../docs/evidence/phase0-k2a.md).

## Limits

- Shape A (owning the Bluetooth HAL) is not something a standalone binary can
  do. It needs a priv-app and policy, which is the prior art's path. This probe
  measures shape B, the thesis.
- It does not pair with a Watch. Phase 0 does not need one.
- It may wedge Bluetooth until reboot. That is an expected failure mode.
