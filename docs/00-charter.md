# 00 — Project charter

## 1. Problem

An Apple Watch is functionally tied to an iPhone: setup, activation, watch
faces, notifications, health sync, and updates all flow through Apple's
proprietary pairing stack. People who move to Android and keep their Watch are
left with a device that shows the time and nothing else.

There is no public API for this and no supported path. A solution has to
re-implement the protocol from observed behaviour and take control of the
phone's Bluetooth controller. Stock Android does not grant that control to an
ordinary app, and on Android 12+ it does not grant it to `uid 0` alone either:
the controller sits behind an SELinux domain and a platform configuration.

## 2. Mission

Build a maintained, auditable Android application that can pair with, activate,
and operate an owner's Apple Watch, using a clean-room protocol implementation
and a signed, per-boot payload that leaves nothing behind after a reboot.

## 3. Users

| User | Need | What they get | What it costs them |
| --- | --- | --- | --- |
| Android user with a rooted phone | Use the watch they own | Pairing, notifications, faces, settings | Root already; Open Crown adds an agent |
| Android user on an unpatched build | Same, without rooting | Same, via a signed n-day | Updates can retire support |
| Researcher | Understand the protocol | Specs, captures, vectors | Nothing |
| Contributor | Add a device or a payload | Schema, signing rules, CI gates | Review obligations |
| Security reviewer | Audit a root-capable app | Provenance, reproducible builds, small agent | The agent is a daemon while a watch is paired |

We are explicit about the second row: the product works best on phones that are
not receiving security updates. That is a real cost to the user, it is stated at
install time, and it is recorded in [09 — Legal and ethics](09-legal-ethics.md)
rather than hidden in a support page.

## 4. Goals

**G1 — Own the transport.** Acquire privilege for the boot through a signed,
device-matched payload, then hold the Bluetooth controller. *Gated by K2a; see
[06 — Roadmap](06-roadmap.md).*

**G2 — Re-implement pairing.** A clean-room spec and implementation of the
pairing bootstrap, security handshake, and bond persistence. The protocol is
mapped by prior art; we verify and reimplement, we do not rediscover.

**G3 — Complete setup.** Device activation and the initial sync sequence,
ending in a visible watch face. Whether the Apple ID session lives on the phone
or on the Watch is an open experiment, not a design assumption.

**G4 — Operate.** Reconnect without re-pairing, deliver notifications, read and
write supported settings and faces, carry health data and Wi-Fi credentials —
with a resident agent for as long as a watch is paired.

**G5 — Stay honest.** No watch-side exploitation, no activation-lock bypass, no
silent privilege escalation, no claim we cannot evidence. Every payload is
signed, reviewable, reversible at reboot, and attributable in the audit log.

**G6 — Leave nothing behind.** No bootloader unlock, no persistent root, no
boot-image modification, and no agent after a reboot. "Stock" means *nothing
survives the reboot*, not *nothing is running*.

## 5. Non-goals

- Bypassing Activation Lock, iCloud Lock, or another person's pairing.
- Running code on the Watch, jailbreaking watchOS, or modifying firmware.
- Installing arbitrary third-party watch apps (requires Apple's signing).
- Replacing an iPhone for App Store, cellular provisioning, or Apple Pay.
- Delivering watchOS updates. The Watch will need an iPhone for those.
- Permanent root or a bootloader unlock.
- Supporting every Watch model on day one.
- A payload for arbitrary devices. Payloads are exact-target only.

## 6. Success criteria for v1.0

On the hardware matrix (at least one phone and one Watch, with both named):

1. A factory-reset Watch is discovered, paired, and activated by the app on a
   **supported prepared phone**, without an iPhone present.
2. The Watch shows its normal face and survives a reboot of both devices.
3. The app reconnects without re-pairing after a Bluetooth toggle, with the
   agent restored and no permanent root left behind.
4. Notifications from selected Android apps appear on the Watch.
5. The face inventory and at least three settings are read/write with readback.
6. Health ingress and Wi-Fi relay work on the supported matrix.
7. Calls/SMS relay works, or is publicly deferred with a written status.
8. Every privileged action is attributable to a signed payload and logged.
9. A provenance audit finds no copied code and no unexplained binary.
10. After a reboot, the agent and any borrowed privilege are absent, and the
    phone is exactly as before. While the agent runs, it is visible.

## 7. Principles

1. **The Watch is a victim, not a target.** We never exploit it.
2. **Ownership first.** No feature works on a device the user cannot activate
   with their own Apple ID. Lock bypass is refused, and we do not pretend that
   an affirmation proves ownership.
3. **Borrowed privilege, stated accurately.** `uid 0 + an SELinux domain`, for
   the life of the boot, behind a visible service. See
   [ADR 0007](adr/0007-uid0-plus-domain.md) and
   [ADR 0008](adr/0008-resident-agent.md).
4. **Spec before code.** No implementation lands without a written behaviour
   spec and a golden vector.
5. **Reversibility.** Every state change has a documented undo; a reboot is the
   ultimate undo *for memory state*. Page-cache and policy changes must each
   argue their own persistence case.
6. **Boring where possible.** Standard Android and Rust tooling, no exotic
   runtimes.
7. **Evidence over claims.** A feature is "done" when there is a capture, a
   test, and a hardware note. A claim with no citation is a hypothesis.
8. **Name the prior art.** A design document that benchmarks against an unnamed
   project cannot be audited.
9. **One experiment at a time.** The claim that can kill the project is tested
   before the scaffolding, not after.
10. **Credential minimalism.** No password is stored. Which component
    authenticates is decided by experiment, not preference.

## 8. Constraints

- Android 13+ (planned); a 64-bit arm64 device with a supported kernel build.
- The payload must match the device and kernel exactly, and a vendor patch can
  retire it.
- The supported device list is evidence, not intention. It ages.
- Apple can change the protocol at any watchOS release.
- The project must remain distributable: no Apple binaries, no Apple keys, no
  leaked internal entitlements. Licence: MIT.
- Health data is sensitive; processing is local-first.
- The app may handle Apple ID input. If it does, the security bar rises above a
  normal companion app, and the decision to do so is recorded in ADR 0003.

## 9. Stakeholders and decision rights

Single-maintainer project at kickoff. Decisions are recorded as ADRs; the
maintainer approves payloads and protocol specs.

A second reviewer is required for any payload that grants root **where one is
available**. A release that ships a root payload without a second reviewer is
labelled `unreviewed` in its release notes and in the catalog entry. We do not
claim a review that did not happen, and we do not write a gate that a
single-maintainer project can never pass.
