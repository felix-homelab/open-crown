# 00 — Project charter

## 1. Problem

An Apple Watch is functionally tied to an iPhone: setup, activation, watch
faces, notifications, health sync, and updates all flow through Apple's
proprietary pairing stack. People who move to Android and keep their Watch are
left with a device that shows the time and nothing else.

There is no public API for this and no supported path. Any solution has to
re-implement the protocol from observed behaviour and gain enough control of
the phone's Bluetooth controller to speak it. Stock Android does not grant that
control, so the app must also be able to borrow root for a session — without
turning the phone into a permanently rooted device.

## 2. Mission

Build a maintained, auditable Android application that can pair with, activate,
and operate an owner's Apple Watch, using a clean-room protocol implementation
and a signed, per-boot exploit catalog that leaves the phone stock between
sessions.

## 3. Users

| User | Need | What they get |
| --- | --- | --- |
| Android user with a spare Watch | Use the watch they already own | Pairing, notifications, faces, settings |
| Privacy-minded user | No permanent root, no always-on daemon | Per-boot privilege, no su after reboot |
| Researcher | Understand the protocol stack | Published specs, captures, golden vectors |
| Contributor | Add a device or a payload | Catalog schema, signing rules, CI gates |
| Security reviewer | Audit a root-capable app | Clean-room provenance, reproducible builds, small agent |

## 4. Goals

**G1 — Own the transport, on a stock phone.** Acquire root for one session
through a signed, device-matched exploit payload, then drive the Bluetooth
controller directly.

**G2 — Re-implement pairing.** A clean-room spec and implementation of the
pairing bootstrap, security handshake, and bond persistence.

**G3 — Complete setup.** Activation and the initial sync sequence, driven by
the owner's Apple ID entered in-app, ending in a visible watch face.

**G4 — Operate.** Reconnect without re-pairing; deliver notifications; read and
write supported settings and faces; carry health data and Wi-Fi credentials.

**G5 — Stay honest.** No watch-side exploitation, no activation-lock bypass, no
silent privilege escalation. Every payload is signed, reviewable, reversible,
and gone after reboot.

**G6 — Keep the phone stock.** No bootloader unlock, no persistent root, no
boot-image modification. A reboot is always a clean recovery path.

## 5. Non-goals

- Bypassing Activation Lock, iCloud Lock, or another person's pairing.
- Running code on the Watch, jailbreaking watchOS, or modifying firmware.
- Installing arbitrary third-party watch apps (requires Apple's signing).
- Replacing an iPhone for App Store, cellular provisioning, or Apple Pay.
- Permanent root or a general-purpose rooting tool.
- Supporting every Watch model on day one.
- Shipping a universal, silent, one-click root for arbitrary phones.

## 6. Success criteria for v1.0

On the project's hardware matrix (at least one phone and one Watch, ideally two
of each):

1. A factory-reset Watch can be discovered, paired, and activated by the app
   using the owner's Apple ID entered in-app.
2. The Watch shows its normal face and survives a reboot of both devices.
3. The app reconnects without re-pairing after a Bluetooth toggle, with the
   exploit re-run automatically and no permanent root left behind.
4. Notifications from selected Android apps appear on the Watch.
5. The face inventory and at least three settings are read/write with readback.
6. Health ingress and Wi-Fi relay work on the supported matrix.
7. Calls/SMS relay works, or is publicly deferred with a written status.
8. Every privileged action is attributable to a signed payload and logged.
9. A clean-room audit finds no copied code and no unexplained binary.
10. After a reboot, `su` and the agent are absent; the phone is stock.

## 7. Principles

1. **The Watch is a victim, not a target.** We never exploit it.
2. **Ownership first.** No feature may work on a device the user cannot prove
   they own.
3. **Borrowed privilege.** Root is a session capability, not an installation.
4. **Spec before code.** No implementation lands without a written behaviour
   spec and a golden vector.
5. **Reversibility.** Every state change has a documented undo; a reboot is the
   ultimate undo.
6. **Boring where possible.** Standard Android and Rust tooling, no exotic
   runtimes.
7. **Evidence over claims.** A feature is "done" when there is a capture, a
   test, and a hardware note.
8. **Credential minimalism.** The app never stores an Apple ID password.

## 8. Constraints

- Android 12+ (planned); a 64-bit arm64 device with a supported kernel build.
- The exploit payload must match the device and kernel exactly.
- Apple can change the protocol at any watchOS release.
- The project must remain distributable: no Apple binaries, no Apple keys, no
  leaked internal entitlements.
- Health data is sensitive; processing is local-first.
- The app handles Apple ID authentication; that raises the security bar above a
  normal companion app.

## 9. Stakeholders and decision rights

Single-maintainer project at kickoff. Decisions are recorded as ADRs; the
maintainer approves payloads and protocol specs. A second reviewer is required
for any payload that grants root — this is a hard requirement, and if no
reviewer exists the payload does not ship.
