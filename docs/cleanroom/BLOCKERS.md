# Blocker catalog

A blocker is a first-class product state, not an apology. Every blocked row in
[feature-registry.json](feature-registry.json) names an id from this file, and
every id here names the exact dependency, the evidence, and what would have to
change for the row to become reachable.

Status vocabulary:

- **permanent** — depends on an Apple-controlled service, credential, or
  secure element this project will not obtain. Will not become reachable.
- **conditional** — depends on a platform capability, policy change, or
  hardware state that could change. Re-evaluate at each phase gate.
- **project** — not a product limitation; the current build/verification
  environment lacks something (for example a physical Watch). Never used to
  excuse a missing implementation.

No row may cite a blocker that is not defined here. No blocker may be added to
hide an unimplemented feature; a missing implementation is `unimplemented`.

---

## Apple-service blockers

### B-APPLE-ACCOUNT

**Dependency:** Apple ID / iCloud account authentication and the account-bound
services behind it (iCloud sync, account-scoped device records, Find My network,
iMessage, FaceTime, Siri account features, Walkie-Talkie, Health iCloud sync,
continuity unlock).

**Why it is blocked:** these services require a session issued by Apple's
servers to an Apple-issued client, with device attestation and account
credentials. Apple publishes no API for third-party companion clients to obtain
or use that session. Open Crown will not harvest Apple ID credentials, replay
an iPhone session, or impersonate an Apple client. Even reading account state
would require the user to hand over credentials the project must not hold
([ADR 0003](../adr/0003-apple-id-in-app.md)).

**Evidence:**
- Apple's public developer documentation exposes Apple ID account services
  only through first-party OS frameworks (AuthenticationServices / iCloud
  APIs), not a general companion protocol. https://developer.apple.com/documentation/authenticationservices
- Apple Media Services terms prohibit accessing services by any means other
  than the applicable Apple software/interface:
  https://www.apple.com/legal/internet-services/itunes/us/terms.html
- The reference project's own feature inventory lists these as research
  boundaries rather than verified parity; see
  [inventory/reference-features.md](inventory/reference-features.md).

**Would become reachable if:** Apple published a companion-service API with
delegated authorization that does not require an Apple client identity. Not
expected.

**Affected capabilities:** `secure.apple-id`, `secure.icloud-sync`,
`secure.findmy-network`, `secure.imessage`, `secure.facetime`,
`apps.siri`, `comm.walkie-talkie`, `secure.health-icloud`,
`secure.continuity-unlock`, `family.setup`, `family.schooltime`,
`family.reports`, `family.limits`, `family.services`.

---

### B-ACTIVATION-LOCK

**Dependency:** Activation Lock / Find My Lock validation.

**Why it is blocked:** verification is performed by Apple against the device's
activation state and the owner's account. There is no public offline check.
Open Crown does not attempt bypass, and it does not present a local heuristic
as activation-lock status. Unpair/reset recovery for an owner's watch is in
scope; defeating a lock is not.

**Evidence:**
- Apple Activation Lock description (server-side account binding):
  https://support.apple.com/en-us/108904
- Project non-goal: [charter](../00-charter.md) §5; [ADR 0005](../adr/0005-no-watch-exploitation.md).

**Affected capabilities:** `secure.activation-lock`,
`pair.lock-recovery` (only the owner-authorized erase path is in scope).

---

### B-APPLE-PAY

**Dependency:** Apple Pay, Wallet cards, transit cards, and secure-element
applet provisioning.

**Why it is blocked:** card provisioning, tokenization, and payment applets are
provisioned by Apple Pay servers into an Apple-controlled secure element using
Apple-issued certificates. Android's secure element is a different
provisioning domain; there is no interoperable path. Displaying wallet UI
without provisioning would be a fake feature.

**Evidence:**
- Apple Pay security and provisioning overview:
  https://support.apple.com/en-us/102140
- Apple Pay JS / merchant APIs cover accepting payments, not provisioning a
  non-Apple companion. https://developer.apple.com/apple-pay/

**Affected capabilities:** `secure.apple-pay`, `secure.wallet`,
`secure.transit`, `secure.secure-element`.

---

### B-CELLULAR-PROVISIONING

**Dependency:** eSIM/cellular plan provisioning for a Watch.

**Why it is blocked:** carrier plan activation is an operator process mediated
by Apple and the iPhone Watch app. Watch cellular status can be *shown* if the
watch reports it, but provisioning is not available to this client.

**Evidence:** Apple Watch cellular setup requires an iPhone and a carrier plan;
there is no public companion provisioning API.
https://support.apple.com/en-us/109024 (cellular setup) ,
https://support.apple.com/en-us/108918 (carrier support).
The exact page is not load-bearing: the provisioning boundary is Apple/carrier
contractual and cannot be bypassed by a protocol implementation.

**Affected capabilities:** `secure.cellular-provisioning`,
`daily.cellular-status` (status read may be possible; provisioning is not).

---

### B-APP-STORE-DRM

**Dependency:** App Store purchases, FairPlay DRM, and paid content.

**Why it is blocked:** purchases and DRM playback require Apple account
entitlements and FairPlay keys. Open Crown will not implement a purchase or
DRM workaround, and it will not display a purchase UI it cannot complete.

**Evidence:** Apple Media Services terms (purchases and DRM):
https://www.apple.com/legal/internet-services/itunes/us/terms.html

**Affected capabilities:** `apps.store-purchase`, `secure.drm-playback`,
`secure.appstore`.

---

### B-WATCH-APP-SIGNING

**Dependency:** installing or updating third-party watch apps and
complications.

**Why it is blocked:** watch apps must be signed and provisioned by Apple for
the specific watch. Open Crown can inventory the apps already installed and
remove them if the protocol allows it; it cannot sign a new one.

**Evidence:** Apple Developer Program code-signing requirements:
https://developer.apple.com/support/code-signing/

**Affected capabilities:** `apps.install`, `apps.update`,
`apps.third-party-complications`, `faces.import-third-party`.

---

### B-WATCHOS-UPDATE

**Dependency:** watchOS software update download, verification, and install.

**Why it is blocked:** updates are Apple-signed and authorized through Apple's
infrastructure. The companion can surface availability/version state if the
watch reports it, but it cannot deliver an update. The charter already declares
watchOS updates a non-goal.

**Evidence:** [charter](../00-charter.md) §5; Apple watchOS update requirements:
https://support.apple.com/en-us/108926

**Affected capabilities:** `apps.watchos-update`,
`apps.watchos-update-install`.

---

### B-CONTINUITY-UNLOCK

**Dependency:** Apple Watch unlock of a Mac / iPhone continuity features.

**Why it is blocked:** continuity authentication binds an Apple account,
Secure Enclave keys, and Apple's proximity protocol between first-party
devices. A non-Apple companion cannot join that trust set.

**Evidence:** Apple Platform Security, "Continuity" and Auto Unlock:
https://support.apple.com/guide/security/welcome/web

**Affected capabilities:** `secure.continuity-unlock`.

---

## Platform / architecture blockers

### B-HCI-OWNERSHIP

**Dependency:** an SELinux domain and HAL ownership that can hold the Bluetooth
controller while the Android framework is detached.

**Why it is conditional:** the current A52 chain yields `uid 0` in
`u:r:init:s0` for one boot. It does not yet prove the agent can bind
`HCI_CHANNEL_USER` under enforcing SELinux or take the HAL without the
priv-app/hidden-API configuration the project avoids. This is K2a. Until it is
proven, every transport-dependent feature is unimplemented, not "working".

**Evidence:** [docs/13-status.md](../13-status.md),
[docs/06-roadmap.md](../06-roadmap.md) G0,
[docs/adr/0007-uid0-plus-domain.md](../adr/0007-uid0-plus-domain.md).

**Would become reachable if:** a hardware probe binds the user channel under
enforcing SELinux, or the project accepts a rooted-device prerequisite
([ADR 0010](../adr/0010-ship-order.md)).

**Affected capabilities:** all `link.*` rows, all transport-gated feature rows.

---

### B-APP-AGENT-BROKER

**Dependency:** a channel from `untrusted_app` to the resident privileged
agent that does not require a manual ADB step.

**Why it is conditional:** the current relay listens in the init domain and
`untrusted_app` is excluded from its Unix socket. Whether a permissible
broker can be built (init proxy, app-owned socket, file queue, binder) is under
test in Phase 2. The project will not use a manual ADB command as the shipped
trigger, and it will not pretend an unavailable channel exists.

**Evidence:** [docs/evidence/a52-app-chain.md](../evidence/a52-app-chain.md),
[`docs/evidence/broker-probe-a52.md`](../evidence/broker-probe-a52.md), and the
real-domain measurement in
[`docs/evidence/a52-broker-untrusted-app.md`](../evidence/a52-broker-untrusted-app.md).
The app runs as `u:r:untrusted_app:s0`, the relay socket is live, and the
connect is denied with EACCES. A file-queue alternative was also tested: the
init-domain relay's openat on a 0644 file owned by the app returned EACCES, so
app-data files are not a channel either. The relay also never returns to init.

**Would become reachable if:** a probe demonstrates an allowed direction under
enforcing SELinux, or the product explicitly requires a rooted device and the
agent is launched by the root solution rather than by ADB.

**Affected capabilities:** every privileged action, `foundation.broker`,
`foundation.agent-session`, and the pairing/setup rows.

---

### B-DEVICE-PROFILE

**Dependency:** an exact device/kernel payload profile for a device other than
the verified A52.

**Why it is conditional:** payloads are exact-target only. The app must report
"unsupported" or "compatible, untested" rather than attempt a chain. This is a
normal state, documented in [04-payload-system](../04-payload-system.md).

**Affected capabilities:** `foundation.privilege` on other devices.

---

## Project constraints

### B-NO-PHYSICAL-WATCH

**Dependency:** an Apple Watch available on the bench for pairing and
observation.

**Why it is a project constraint:** no Watch is currently attached to the test
environment. Nothing in the registry may be marked `verified` without it.
This blocker does not excuse missing implementation: it blocks *verification*,
not *building*.

**Affected capabilities:** every row that requires physical proof.

---

## Asset and evidence blockers

### B-ASSET-APPLERENDER

**Dependency:** distribution of Apple-rendered watch-face previews, swatches,
catalogue data or other Apple-owned visual assets.

**Why it is blocked:** the reference tree contains 182,432,179 bytes of
Apple-rendered face assets (about 20,128 files) and its own release guide admits
the permission to ship firmware-derived assets is unresolved. Nothing of the
kind may enter this repository. An original preview renderer can be built from
device-reported face data, but it cannot use Apple's artwork.

**Evidence:** [inventory/reference-assets.md](inventory/reference-assets.md),
[inventory/reference-license-report.md](inventory/reference-license-report.md).

**Would become reachable if:** an Apple asset is proved redistributable under a
written licence. Not expected. Prefer an original renderer.

**Affected capabilities:** `faces.preview`, `faces.native-resources`,
`faces.pigments`, `faces.photo` where a preview is Apple-rendered.

---

### B-OPTICAL-FIRMWARE

**Dependency:** a lawful, independent decoder for the watch face's optical
pairing frame.

**Why it is blocked:** the reference's optical path depends on an Apple
`VisualPairing` firmware image and private tooling that are excluded from its
published tree; its portable decoder was never completed. Open Crown cannot
vendor Apple firmware, and it will not guess a decoder from screenshots.

**Evidence:** [inventory/reference-features.md](inventory/reference-features.md)
and the lane A spec [spec/02-pairing-security.md](spec/02-pairing-security.md).

**Would become reachable if:** an owner-supplied capture produces an
independently derived decoder with test vectors, or the PIN path is sufficient
for the supported models.

**Affected capabilities:** `pair.optical`.

---

### B-EVIDENCE-GAP

**Dependency:** verifiable evidence for a reference claim.

**Why it is a project constraint:** of 253 reference capability rows, 194 are
research-only and many "hardware" rows cite reports that are excluded from the
published tree (36 linked reports are absent). A reference README or a dangling
report is not proof. Every parity claim needs our own capture or physical run.

**Evidence:** [inventory/reference-features.md](inventory/reference-features.md).

**Effect:** does not block building a feature, but blocks citing the reference
as verification. Rows stay `implemented_unverified` or `unimplemented` until
our own evidence exists.

---

### B-COMPLETION-POLICY

**Dependency:** an agreed definition of "setup complete" that can be observed
from the watch, not inferred from an iPhone-side celebration.

**Why it is conditional:** the reference's own report relied on owner-confirmed
completion and did not establish strict clock/reconnect evidence. Open Crown
will not mark setup complete because a progress bar finished. The product needs
to decide which observation is the completion signal (visible face report,
PairedSync terminal state, or an owner confirmation explicitly labelled as
such).

**Evidence:** [inventory/reference-features.md](inventory/reference-features.md);
[03-activation-setup spec](spec/03-activation-setup.md).

**Affected capabilities:** `pair.setup-completion`, `pair.face-visible`.

---

### B-QUARANTINE-CI

**Dependency:** a CI and repository guard that keeps the reference and
Apple-origin assets out of tracked/build inputs.

**Why it is a project constraint:** `reference/` is gitignored, but a glob, a
packaging rule or a careless commit could still pull it in. The guard is
implemented by `tools/cleanroom/check-boundary.py` and runs in CI.

**Affected capabilities:** `ops.cleanroom-audit`.

---

## Blocker hygiene

- A blocker must name a dependency, not a feeling. "Apple's protocol is hard"
  is not a blocker.
- A blocker must link evidence that a reviewer can open.
- A blocker must list affected registry ids and be reflected in the generated
  registry counts.
- When a conditional blocker is cleared, the evidence is recorded and the
  affected rows move to `implemented_unverified` (not directly to `verified`).
- Permanent blockers remain visible in the app's Evidence screen. They are not
  hidden behind a generic "unsupported" message.