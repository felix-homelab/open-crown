# 08 — Telephony, media, camera, walkie-talkie, HomeKit, updates, cellular, wallet/secure element

**Status:** draft — and largely negative. The reference establishes route names and a few UI method names for these areas, and explicitly states that almost nothing is implemented or verified. Recording that honestly is the deliverable here.
**Spec areas:** `protocol/telephony/` (T3, not started); media/others have no area yet.

## 1. Purpose

Prevent the project from inheriting unverified claims about high-risk feature families: calls and SMS, media remote, camera remote, Walkie-Talkie, HomeKit, software updates, cellular, and Wallet/secure element. This spec exists to say what must *not* be built from the reference, and what evidence would be required first.

## 2. Preconditions (if any of this is ever attempted)

- A captured, replayable session for the specific feature on our own hardware.
- A threat model for the domain: calls reveal numbers/audio, Wallet involves payment networks and the secure element, cellular involves an operator.
- Explicit product and legal approval per row; several rows are destructive or regulated.
- A kill switch, and no automatic retry for any non-idempotent operation.

None of these preconditions are met today.

## 3. What the reference actually establishes

| Area | Established | Not established |
|---|---|---|
| Telephony | A route `alloy.telephony`, Class-C, urgent priority; "research codecs" exist; Flutter methods `triggerCall` and `callAction` return `UNIMPLEMENTED` from the adapter and are rejected by the native whitelist | Any call, SMS, contact, or audio behaviour |
| Notifications/SMS overlap | Notification mirroring can display a message card (see 06) | Sending an SMS, reading history, or replying on behalf of the Messages app |
| Media remote | Nothing beyond registry rows graded research | Player selection, metadata, transport control, volume |
| Camera remote | Nothing beyond registry rows graded research | Preview frames, shutter, saved photo |
| Walkie-Talkie | Nothing beyond registry rows graded research | Invitation, availability, audio |
| HomeKit | Nothing beyond registry rows graded research | Accessory discovery, read, write, authorisation |
| Software update | Nothing beyond registry rows graded research | Manifest, download, signature verification, install, rollback |
| Cellular | Nothing beyond registry rows graded research | eSIM provisioning, operator entitlement, calls/data without the phone |
| Wallet / secure element | Nothing beyond registry rows graded research | Passes, transit, keys, payment, attestation, provisioning |

**Route table (all that is known):**

| Topic | Class | Claimed purpose | Our status |
|---|---|---|---|
| `alloy.telephony` | C | Telephony | Route name only |
| `alloy.findmylocaldevice` | D | Find devices (not telephony, listed for contrast) | See 07 |

No other route is named for media, camera, walkie-talkie, HomeKit, updates, cellular, or Wallet in the reference's route table. If those features exist, their channels are not documented.

## 4. Message and frame tables

| Message | Direction | Fields | Endianness | Status |
|---|---|---|---|---|
| (telephony research codec) | both | UNKNOWN | UNKNOWN | Codec exists; no captured exchange in the prose |
| `triggerCall` | Crown → Agent | UNKNOWN | n/a | Adapter returns `UNIMPLEMENTED` |
| `callAction` | Crown → Agent | UNKNOWN | n/a | Adapter returns `UNIMPLEMENTED` |

There is nothing honest to put in a field-level table for the remaining families. Writing one would be invention.

## 5. State machine

**None established.** The only state-like fact is negative: the adapter/whitelist rejects the two telephony methods before any command is sent.

## 6. Error taxonomy

| Code | Cause | Required behaviour |
|---|---|---|
| `FEATURE_UNIMPLEMENTED` | Method not implemented by the backend | Report; no fallback that pretends to work |
| `FEATURE_REJECTED_BY_POLICY` | Native whitelist refuses the method | Report; do not bypass |
| `FEATURE_NO_EVIDENCE` | Feature requested but no capture exists | Refuse to implement from the reference |
| `FEATURE_REQUIRES_APPROVAL` | Regulated/destructive domain | Block until product/legal sign-off |

## 7. UNKNOWN

- **UNKNOWN:** every wire detail for calls, SMS, contacts, media, camera, Walkie-Talkie, HomeKit, updates, cellular, and Wallet.
- **UNKNOWN:** whether telephony uses the same IDS framing as other topics or a different service family.
- **UNKNOWN:** whether audio uses a separate transport entirely (the reference's registry grades call audio research-only).
- **UNKNOWN:** whether Wallet/secure-element operations are reachable at all from a non-Apple host, and what authorisation they require.
- **UNKNOWN:** whether software update flows are gated by Apple signing in a way that makes third-party initiation impossible.
- **UNKNOWN:** which of these families are legally restricted (payments, radio, emergency services) in our target jurisdictions.

## 8. Acceptance criteria

These are gates, not feature tests:

```text
Given no capture for a family in this spec, then no implementation is merged for it.

Given a request for a telephony method, then the app reports FEATURE_UNIMPLEMENTED
     and does not send a command.

Given any Wallet, cellular, or update work proposal, then it carries a written
     product/legal approval and a threat model before code review.

Given a future capture, then the spec is rewritten from that capture and this
     document's "not established" table is updated — never the reverse.
```

## 9. Evidence

| Fact | Reference source |
|---|---|
| Telephony route and "full native acceptance unproven" | README §IDS routes and handlers |
| `triggerCall`/`callAction` unimplemented and whitelisted off | README §Flutter methods without a current Android implementation; §Operational commands |
| Calls/SMS/contacts are research; calls/audio unverified | README §Capabilities ("Calls / SMS / contacts" row) |
| All media/camera/Wallet/cellular/update rows graded research | `docs/WATCH_FEATURE_REGISTRY.md` groups G06, G07, G16–G19, G22–G26 |
| Owner-confirmed limitations | README line 59 |

**Must be confirmed on hardware before any of this is called supported:** a captured end-to-end exchange per feature, on our own pair, plus the approvals in §2. Until then this spec's correct state is "not started", and its main value is that it stops a future contributor from mistaking the reference's registry for a capability list.
