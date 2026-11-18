# 06 — Roadmap

Gate-based. A phase ends when its exit criteria are met, not when its tasks are
"mostly done". Gates exist to kill the project early if the foundation is wrong.

## Phase 0 — Foundation (2–3 weeks)

- [ ] Gradle + Rust workspace, CI green, debug APK.
- [ ] UniFFI round-trip: Kotlin calls Rust and gets an error type.
- [ ] Capture tooling: HCI snoop import, pcap decode, fixture format.
- [ ] `protocol/` skeleton and spec template.
- [ ] Threat model v0, clean-room policy, provenance log.
- [ ] Threat model for Apple ID credential handling.

**Exit:** CI builds on a clean machine; a recorded capture replays through the
fixture format.

## Phase 1 — See the Watch (2–4 weeks)

**Goal:** discovery and capability reporting, no privilege.

- [ ] BLE scan and advertisement decode (model, watchOS hints, setup state).
- [ ] Device fingerprint (phone) and catalog target matching.
- [ ] UI: scan, select, capability report, "why root is needed" explainer.
- [ ] Golden vectors for advertisement parsing.

**Exit:** on the test phone, the app identifies the Watch in setup mode and
shows a correct capability report with no root.

**Kill gate K1:** if the advertisement cannot be decoded reliably across the
supported watchOS versions, redesign discovery before anything else.

## Phase 2 — Borrow the Radio (4–6 weeks)

**Goal:** a stock phone with no root can run a session that owns HCI.

- [ ] Exploit runtime: device matcher, manifest verification, staging.
- [ ] Agent: HCI broker, policy, audit, one-time-token handshake.
- [ ] First payload for the test device (family chosen by what is available).
- [ ] Bootstrap via usermode-helper; agent exits at session end.
- [ ] Reboot test: after reboot there is no `su`, no agent, no residual module.
- [ ] Failure UX: no payload, exploit panic, handshake failure, HCI failure.
- [ ] Retry budget and cooldown; no boot loops.

**Exit:** on the stock test phone, the app borrows root, opens an HCI channel to
the Watch, echoes bytes, and returns to stock on reboot.

**Kill gate K2:** if no payload can be produced for the test device family, the
product cannot exist as specified. Options: bridge-first, or a supported-device
list only.

## Phase 3 — Pair (6–10 weeks)

**Goal:** pair a factory-reset Watch from the app.

- [ ] Spec: pairing bootstrap, security handshake, bond storage.
- [ ] Spec: PIN and optical paths.
- [ ] Core state machine with checkpoints and resume.
- [ ] Bond persistence in Keystore; reconnect after reboot.
- [ ] Hardware test: factory reset → pair → encrypted link, three times.

**Exit:** three consecutive clean pairings on the same hardware without a
desktop host.

**Kill gate K3:** if pairing needs an iPhone-derived secret we cannot obtain
lawfully, stop and publish the finding.

## Phase 4 — Activate and finish setup (4–8 weeks)

**Goal:** the Watch reaches a normal face.

- [ ] Apple ID SRP sign-in, 2FA relay, token storage, revocation.
- [ ] Spec: activation relay, initial properties, language/locale.
- [ ] Spec: setup completion and observer branches.
- [ ] Activation UI with explicit consent and clear scope.
- [ ] Checkpoint model: activated ≠ setup complete ≠ ready.
- [ ] Recovery from a failed activation; account-lockout handling.

**Exit:** factory reset → pair → activate → visible face → reboot → reconnect.

**Kill gate K4:** if Apple ID sign-in cannot be done without unacceptable
lockout or attestation risk, activation moves to "iPhone-once" mode and the
positioning changes.

## Phase 5 — Operate (T1) (4–8 weeks)

- [ ] Notifications: listener, bulletin path, actions, per-app toggles.
- [ ] Faces: inventory, selection, safe mutation with readback.
- [ ] Settings: wrist, 24-hour, invert; readback.
- [ ] UI polish, onboarding, diagnostics.

**Exit:** T1 features pass acceptance on the hardware matrix.

## Phase 6 — Health and Wi-Fi (T2) (3–6 weeks)

- [ ] Health ingress, local store, export, deletion.
- [ ] Wi-Fi relay for supported networks; explicit failure for unsupported.
- [ ] Privacy review and data-flow documentation.

**Exit:** T2 features pass acceptance; health data never leaves the device by
default.

## Phase 7 — Calls and SMS (T3) (best effort)

- [ ] Feasibility spike: what the Watch expects from a phone for telephony.
- [ ] If viable: call state, audio route, SMS relay.
- [ ] If not: publish the finding and close the track.

**Exit:** either working on the matrix, or a written "not feasible" report.

**Kill gate K5:** if telephony requires an Apple service or entitlement we
cannot reproduce, close T3 permanently.

## Phase 8 — Harden and distribute (2–3 weeks)

- [ ] Second reviewer for root payloads; transparency log live.
- [ ] Reproducible release APK, SBOM, signature.
- [ ] Clean-room audit sign-off.
- [ ] Recovery docs and a "brick drill" on a sacrificial device.
- [ ] Incident response for a bad payload.

## Effort and sequencing

| Phase | Calendar (part-time) | Confidence |
| --- | --- | --- |
| 0 | 2–3 weeks | High |
| 1 | 2–4 weeks | High |
| 2 | 4–6 weeks | Medium |
| 3 | 6–10 weeks | Low–medium |
| 4 | 4–8 weeks | Medium |
| 5 | 4–8 weeks | Medium |
| 6 | 3–6 weeks | Medium |
| 7 | best effort | Low |
| 8 | 2–3 weeks | High |

The low confidence in Phase 3 is honest: pairing is the technical crux and the
place where a hidden dependency on Apple infrastructure would surface.

## What "v0.1" means

A v0.1 release is **Phase 1 only**: scan, identify, report capabilities, and
explain why pairing is not available yet. It is useful, safe, and shippable
without root.
