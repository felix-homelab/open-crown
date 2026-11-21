# 06 — Roadmap

Gate-based and ordered by risk. The claim that can kill the project is tested
first, not after six weeks of scaffolding. A phase ends when its exit criteria
are met.

## Phase 0 — First payload (6–10 weeks)

**Goal:** run the first payload on a stock phone and find out whether the
process it produces can hold the controller.

There is **no harness root**. The only path to privilege is the payload itself,
so K2b and K2a are exercised in the same session. Each stage reports separately,
because a failure has to say whether the exploit failed or the policy did.

- [ ] Payload runtime: matcher, manifest verification, staging (`runtime/`).
- [ ] Probe agent: the first `agent/` binary; prints uid, SELinux context, and
      attempts the HCI user channel bind (`tools/hci-echo`).
- [ ] First payload: an exact-target public n-day for the test build. Supplied
      by the maintainer; the repository carries the plumbing, not the chain.
- [ ] **Stage A (K2b):** the payload yields `uid 0`; the probe's uid line proves it.
- [ ] **Stage B (K2a):** the probe binds `HCI_CHANNEL_USER` under enforcing SELinux.
- [ ] Record denials, the context, and whether Android's Bluetooth stack had to
      be stopped before the bind succeeded.
- [ ] Reboot recovery: nothing persists, the probe is gone, SELinux is enforcing.

**Exit:** a written result with stage-level attribution, the denial text, and a
reproducible procedure.

**Gate G0 (privilege):** two findings, two fallbacks.
Stage A failing means no usable payload for this build: the catalog is not
viable and the project is bring-your-own-root.
Stage B failing while stage A passes means the stock-phone product does not
exist without a policy change; if the change is larger than a payload can carry,
fall back to [ADR 0010](adr/0010-ship-order.md)'s rooted-device companion.

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
| 0 — First payload | 6–10 weeks | Low |
| 1 — Foundation | 2–3 weeks | High |
| 2 — Activation decision | 1–2 weeks | Medium |
| 3 — Protocol and pairing | 6–10 weeks | Low–medium |
| 4 — Payload system | 4–8 weeks | Low |
| 5 — Setup and features | 8–12 weeks | Medium |
| 6 — Health and Wi-Fi | 3–6 weeks | Medium |
| 7 — Calls and SMS | best effort | Low |
| 8 — Catalog and distribution | 4–6 weeks | Medium |

Total: forty to sixty part-time weeks of work, or roughly two calendar years
once hardware waits, review cycles and payload research are included.
Phase 0 is two to three weeks and can end the project honestly; that is the point
of putting it first.

## What "v0.1" means

A v0.1 release is **Phase 0 + Phase 1**: the K2a result published, the payload
plumbing and probe working, discovery and capability reporting on a stock
device, and a clear explanation of what is not available yet. It is useful, safe, and shippable
without the catalog.
