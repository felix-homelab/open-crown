# 06 — Roadmap

Gate-based and ordered by risk. The claim that can kill the project is tested
first, not after six weeks of scaffolding. A phase ends when its exit criteria
are met.

## Phase 0 — Prove the claim (2–3 weeks)

**Goal:** find out whether a payload can hold the controller under enforcing
SELinux, without Magisk's configuration.

- [ ] Build a throwaway harness on the target phone with **temporary root**
      (Magisk is the harness, not the product).
- [ ] Shape A: take the Bluetooth HAL and echo bytes.
- [ ] Shape B: detach the platform stack, open HCI, echo bytes.
- [ ] Record the exact SELinux denials, the context that works, and the minimum
      configuration diff versus Magisk's module.
- [ ] Measure: success rate, boot impact, recovery after a forced panic.

**Exit:** a written result for both shapes — pass or fail — with the policy
diff, the log, and a reproducible procedure.

**Gate G0 (privilege):** if shape B fails and shape A needs the same Magisk-class
configuration we set out to replace, the stock-phone product does not exist. Fall back to
[ADR 0010](adr/0010-ship-order.md)'s rooted-device companion, and say so in the
README.

## Phase 1 — Foundation (2–3 weeks)

- [ ] Gradle + Rust workspace, CI green, debug APK.
- [ ] UniFFI round-trip: Kotlin calls Rust and gets an error type.
- [ ] Capture tooling: HCI snoop import, pcap decode, fixture format.
- [ ] `protocol/` skeleton and spec template.
- [ ] Threat model v0; provenance log opened with the prior art named.
- [ ] Licence and `SECURITY.md`.

**Exit:** CI builds on a clean machine; a recorded capture replays through the
fixture format.

## Phase 2 — Activation decision (1–2 weeks)

**Goal:** settle ADR 0003 before any credential code is written.

- [ ] Read the prior art's `activationResponse` / `ACTIVATION_INPUT` path.
- [ ] Determine whether the Watch can hold the session and the phone only relays.
- [ ] If the phone must authenticate: prototype the attestation requirement on a
      test account; measure lockout risk.
- [ ] Update ADR 0003 to Accepted or Superseded with the evidence.

**Exit:** ADR 0003 has a status backed by a test, not a preference.

**Gate G1 (activation):** if neither relay nor session is possible, device activation is
not reachable and T0 is dead.

## Phase 3 — Protocol verification and pairing (6–10 weeks)

**Goal:** a clean-room implementation of discovery → pairing → encrypted link.

- [ ] Specs written from the prior art's published map plus own captures.
- [ ] Desktop harness first: pair on Linux with a USB HCI dongle.
- [ ] Core state machine with checkpoints and resume.
- [ ] Pairing bootstrap, security handshake (SPAKE2+/PPK and PIN), bond storage.
- [ ] Hardware test: factory reset → pair → encrypted link, three times.

**Exit:** three consecutive clean pairings on the same hardware, on the desktop
harness and on the phone.

**Gate G2 (pairing):** if pairing needs an iPhone-derived secret we cannot obtain
lawfully, stop and publish the finding.

## Phase 4 — Payload system (4–8 weeks)

- [ ] Payload runtime: matcher, manifest verification, staging.
- [ ] Agent: resident service, controller broker, policy, audit, handshake.
- [ ] K2b: acquire `uid 0` on the target build with a public n-day.
- [ ] Signing, revocation list, transparency log client.
- [ ] Failure UX: no payload, SELinux denial, panic, handshake failure.
- [ ] Retry budget and cooldown; no boot loops.

**Exit:** on the target phone, the app takes the controller with a signed
payload, stays resident, and returns to nothing at reboot.

**Gate G3 (supply):** if no n-day can be maintained for one release cycle on the
target family, the catalog is not viable; the project becomes
bring-your-own-root only.

## Phase 5 — Setup and features (T0/T1) (8–12 weeks)

- [ ] Activation and setup completion; `READY` as separate checks.
- [ ] Reconnect without re-pairing, with the resident agent restored.
- [ ] Notifications, faces, settings with readback.
- [ ] UI: consent, supported list, diagnostics, unpatched-device warning.

**Exit:** T0–T1 pass acceptance on the hardware matrix.

## Phase 6 — Health and Wi-Fi (T2) (3–6 weeks)

- [ ] Health ingress, local store, export, deletion.
- [ ] Wi-Fi relay for supported networks; explicit failure elsewhere.
- [ ] Privacy review and data-flow documentation.

**Exit:** T2 passes acceptance; health data never leaves the device by default.

## Phase 7 — Calls and SMS (T3) (best effort)

- [ ] Feasibility spike: what the Watch expects from a phone for telephony.
- [ ] If viable: call state, audio route, SMS relay.
- [ ] If not: publish the finding and close the track.

## Phase 8 — Catalog and distribution (4–6 weeks)

- [ ] Second reviewer process (or `unreviewed` labelling).
- [ ] Transparency log live; revocation tested.
- [ ] Reproducible release APK, SBOM, signature, signing certificate published.
- [ ] Clean-room audit sign-off.
- [ ] Recovery docs and a brick drill on a sacrificial device.
- [ ] Incident response for a bad payload.

## Effort

| Phase | Calendar (part-time) | Confidence |
| --- | --- | --- |
| 0 — Prove the claim | 2–3 weeks | Medium |
| 1 — Foundation | 2–3 weeks | High |
| 2 — Activation decision | 1–2 weeks | Medium |
| 3 — Protocol and pairing | 6–10 weeks | Low–medium |
| 4 — Payload system | 4–8 weeks | Low |
| 5 — Setup and features | 8–12 weeks | Medium |
| 6 — Health and Wi-Fi | 3–6 weeks | Medium |
| 7 — Calls and SMS | best effort | Low |
| 8 — Catalog and distribution | 4–6 weeks | Medium |

Total: thirty to fifty part-time weeks of work, or roughly one to two calendar
years once hardware waits, review cycles and payload research are included.
Phase 0 is two to three weeks and can end the project honestly; that is the point
of putting it first.

## What "v0.1" means

A v0.1 release is **Phase 0 + Phase 1**: the K2a result published, the harness
working, discovery and capability reporting on a rooted device, and a clear
explanation of what is not available yet. It is useful, safe, and shippable
without the catalog.
