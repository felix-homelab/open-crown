# Phase 0 runbook — first payload

**Goal:** one session, two attributions. Stage A: the payload yields uid 0.
Stage B: that process binds the HCI user channel. **No rooting step exists.**

If the payload fails, the session ends. That is the result, not a setup problem.

## What you need

- P1: the target phone, stock, bootloader locked. USB debugging only.
- The app installed (debug APK is fine).
- A payload bundle for the exact model and kernel build, signed.
- The probe built and packaged by the app.

## 0. Baseline (no root)

    adb shell getprop ro.product.model
    adb shell getprop ro.build.fingerprint
    adb shell getprop ro.build.version.release
    adb shell uname -r
    adb shell getenforce

Record all of it. None of these commands needs root.

## 1. Install and stage

    adb install -r app/build/outputs/apk/debug/app-debug.apk

Open the app once. The runtime extracts the probe into the app's private
directory. Do not push the probe to /data/local/tmp: the point is that only the
payload path executes it.

## 2. Run the probe

In the app: Phase 0 probe, then consent, then run. The runtime fingerprints the
device, verifies the manifest, runs the payload, and starts the probe with a
one-time token.

## 3. Collect

    adb logcat -b all -d > run.log

The probe also reads /dev/kmsg and prints avc lines as K2A avc=... If it cannot
read the log, the errno on that line is itself evidence.

## 4. Attribute the result

- Stage A passes when the probe prints K2A uid=0 euid=0.
- Stage B passes when it prints K2A stage=bind_user_channel status=ok.
- Stage A failing is a payload problem. Record the payload id and signature.
- Stage B failing with EACCES or EPERM is a policy problem. Keep the avc line.
- Stage B failing with EBUSY means Android's stack holds the controller. Record
  whether the probe could stop it, and what that broke.

## 5. Recover

    adb reboot
    adb shell getenforce

Confirm the probe binary is gone, no agent process remains, and SELinux is
enforcing. The app's data stays; that is expected.

## 6. Evidence

Copy [the template](../../docs/evidence/phase0-k2a.md), answer every field, and
decide G0 in [the roadmap](../../docs/06-roadmap.md).

## Rules

- Do not add a policy edit that makes the run pass and call it a pass. Record
  the edit and decide whether a payload can carry it.
- Do not run a payload you cannot reproduce or revoke.
- If the phone panics, stop, record, and do not retry in a loop.
