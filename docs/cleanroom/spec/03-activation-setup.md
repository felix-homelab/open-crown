# 03 — Activation and setup completion

**Status:** draft. Phase vocabulary and message roles are established by the reference; most message bodies are not. No hardware verification by Open Crown.
**Spec area:** `protocol/setup/` (not started).

## 1. Purpose

Take a bonded Watch from "secure link open" to "usable and provisioned": read its registry properties, adopt its own language/locale, commit pairing, activate with Apple, trigger the initial sync, deliver Wi-Fi, wait for PairedSync completion, and only then declare the Watch provisioned.

Terminology note: the reference calls this "Setup"; we call it **provisioning** and keep "setup" only in quoted protocol names (PBBridge setup phase).

## 2. Preconditions

- Bonded link and authenticated IDS control/data lanes (see 02, 04).
- A saved pair record that is not yet operationally eligible.
- Watch language/locale observable through the registry properties — **not** the phone's locale.
- For the Wi-Fi step: the phone has a supported network (reference: WPA2-PSK/CCMP only) and the setup session is still live.
- Apple activation service reachability is UNKNOWN; the reference does not document the endpoint or its authentication.

## 3. Phases (projection vocabulary)

The reference publishes these phases and the observation that drives each. They are a projection, not a mandatory total order.

| Phase | Driving observation (reference) | Meaning |
|---|---|---|
| `STARTING` | New engine run resets progress and transient fields | Session begins |
| `DISCOVERING` | LE scan active | Looking for a Watch |
| `CONNECTING` | Target locked | Connecting |
| `PIN_REQUIRED` | Current PIN request | User must type the code |
| `SECURITY` | SMP/encrypted link active | Securing the pair |
| `IDS` | IDS control checkpoint | Watch services coming up |
| `REGISTRY` | Initial registry read starts | Reading configuration |
| `CONFIGURING` | Validated properties / pre-activation state | Applying settings |
| `ACTIVATING` | Activation attempt armed | Activating with Apple |
| `ACTIVATION_INPUT` | Activation challenge present | Owner action required |
| `ACTIVATED` | Activation confirmed | Activated; **does not claim a visible face** |
| `SYNCING` | Correlated initial-sync response, before completion publication | Initial sync in flight |
| `WAITING_FOR_WATCH` | Preparation and completion publication sent, or Clock barrier | Check the Watch |
| `VERIFYING_RECONNECT` | Clock/provisioning observations reached the health barrier | Verification pending |
| `VERIFIED` | Strict completion, or explicit owner confirmation for this activated pair | Provisioned |
| `STOPPING` / `STOPPED` | Stop request | Tearing down |
| `FAILED` | Protocol/host/cleanup/confirmation failure | Terminal until a new run |

## 4. Durable checkpoints

The reference persists an ordered checkpoint ordinal per pair. A high ordinal alone does not permit operational mode; flags, evidence and identity are checked too.

| Ordinal | Checkpoint | Ordinal | Checkpoint |
|---:|---|---:|---|
| 1 | Pairing material persisted | 10 | Initial properties received |
| 2 | SMP bond recorded (raw) | 11 | Ready to commit paired |
| 3 | Stock bond imported | 12 | Paired committed |
| 4 | Stock encrypted reconnect | 13 | Activation confirmed |
| 5 | Relay prelude negotiated | 14 | Provisioning confirmed |
| 6 | Class-D established | 15 | PairedSync complete |
| 7 | Class-C established | 16 | Provisioning complete |
| 8 | IDS control ready | 17 | Clock visibility confirmed |
| 9 | IDS data ready | 18 | Operational health confirmed |

The owner's confirmation lives in a different record from the Watch's own evidence. A legacy terminal record that was never observed must not become operational-eligible by itself.

## 5. Messages and frames

### 5.1 PBBridge roles

| ID | Direction | Role | Body | Status |
|---:|---|---|---|---|
| 3 | phone → Watch | Open the final provisioning controller; `controllerType=10`, body bytes `08 0a` | 2 bytes observed | Observed on one build |
| 4 | Watch → phone | "Finished activating" | UNKNOWN | Observed |
| 18 | Watch → phone | Response to 21 | Sent even when no delegate consumes it | Observed |
| 19 | phone → Watch | Diagnostic only; must not drive progress | UNKNOWN | Observed |
| 21 | phone → Watch | Prepare initial sync; **empty** protobuf body | Empty | Observed |
| 25 | phone → Watch | Carries the Watch's own language list and locale into the preferences archive | UNKNOWN | Claimed |
| 36 | phone → Watch | "Normal": notify the registry that activation completed | UNKNOWN | Observed; the native side may defer until a compatibility state is reached |

The reference's own correction is important: response 18 does **not** prove that 36 was applied; they are separate observations.

### 5.2 NPS preference envelope (type 0)

Protobuf, type 0, outer message:

| Field | Number | Type | Semantics |
|---|---:|---|---|
| timestamp | 1 | fixed64 | Publication time; protobuf fixed64 is little-endian |
| domain | 2 | string | Preference domain, e.g. `com.apple.pairedsync` |
| keys | 3 | repeated message | One entry per key |

Key message:

| Field | Number | Type | Semantics |
|---|---:|---|---|
| key | 1 | string | Preference key |
| value | 2 | bytes | Binary plist (not an archive) |
| twoWaySync | 3 | bool | Optional; whether the value is expected to sync back |
| timestamp | 4 | fixed64 | Optional per-key timestamp |

Other NPS types named by the reference: type 1 is managed configuration; type 2 is a backup path with a **different outer schema** (`container` string = 1, `domain` = 2, repeated `keys` = 3) and its own restore permissions. It is not a type-0 body with a changed number.

### 5.3 PairedSync state values

| Key | Values observed | Meaning |
|---|---|---|
| `PSYWatchSyncState` | `version=1`, `globalProgress=100`, `syncProgressState=3` | Terminal sync state drives the Watch's progress controller |
| `PSYWatchSyncClientState` | `version=1`, `syncProgressState=3`, `syncSessionType=0`, `migrationSync=false` | Client-session state; the plist key maps to a client-state API |
| Beginning state | `state=2`, `progress=50` | Sent before the initial-sync preparation; terminal state 3 alone was historically insufficient |
| Active labels | `activeActivityLabels` / `completedActivityLabels` | Read by the Watch's sync client; a start-waiting transaction checks session type 0 and active labels |
| Timeout | 600 s | Applies to one specific start-waiting transaction, not to every provisioning path |

## 6. Completion rules

1. An outgoing send, an app ACK, a PBBridge 4, a response 18, or a written progress value **never** establishes a visible face.
2. Durable activation requires the activation result (and owner input if challenged).
3. Provisioning completion requires either (a) the strict native barrier — clock visibility plus a controlled reconnect — or (b) an explicit owner confirmation recorded for this activated pair, after a clean stop (exit 0, Bluetooth/handoff cleanup, pair re-read and validated).
4. Late events must not regress the phase; a stopped/failed run is terminal until reset.
5. Repeated current challenges republish fields; stale challenges must not restore credentials after activation or stop.

## 7. State machine

```text
BONDED ─▶ REGISTRY ─▶ CONFIGURING ─▶ ACTIVATING ─┬─▶ ACTIVATED ─▶ SYNCING ─▶ WAITING_FOR_WATCH
                          ▲                      └─▶ ACTIVATION_INPUT ──▶ ACTIVATING
                          │
        (locale missing) ─┘  → FAILED(SETUP_LOCALE_MISSING), before commit
WAITING_FOR_WATCH ─ strict native evidence ─▶ VERIFYING_RECONNECT ─▶ VERIFIED
WAITING_FOR_WATCH ─ owner confirmation + clean stop ─▶ VERIFIED
Any ─ stop ─▶ STOPPING ─▶ STOPPED(terminal)
Any ─ fault ─▶ FAILED(terminal)
```

## 8. Error taxonomy

| Code | Cause | Required behaviour |
|---|---|---|
| `SETUP_LOCALE_MISSING` | Watch did not supply an authenticated language/locale | Stop before commit; never fall back to the phone locale |
| `SETUP_REGISTRY_INCOMPLETE` | Required properties absent | Stop; do not invent defaults |
| `SETUP_ACTIVATION_REJECTED` | Apple service refused | Report; keep pair; allow bounded retry |
| `SETUP_ACTIVATION_INPUT_REQUIRED` | Challenge needs owner input | Surface input; no spinner that implies progress |
| `SETUP_SYNC_TIMEOUT` | No terminal sync state within the applicable window | Report unverified; do not synthesise state 3 |
| `SETUP_SYNC_STALE` | Response does not correlate to this session | Ignore; keep waiting |
| `SETUP_WIFI_UNSUPPORTED` | Network not WPA2-PSK/CCMP | Skip Wi-Fi; provisioning may still complete |
| `SETUP_WIFI_EXPIRED` | Archive lifetime elapsed or session changed | Discard; no late send |
| `SETUP_COMPLETION_UNCONFIRMED` | Native barrier absent and owner has not confirmed | Stay WAITING/VERIFYING; never auto-complete |
| `SETUP_STOP_INCOMPLETE` | Cleanup/exit failed | Report FAILED; do not save operational mode |
| `SETUP_LATE_EVENT` | Event from an earlier session | Drop; log correlation ids only |

## 9. UNKNOWN

- **UNKNOWN:** the activation request/response payloads, their signature scheme, and the Apple service endpoints.
- **UNKNOWN:** what "paired" is committed locally versus remotely, and which side owns `isPaired`.
- **UNKNOWN:** PBBridge field numbers and bodies for 4, 18, 19, 25 and 36 (only roles and a two-byte body for 3 are stated).
- **UNKNOWN:** how the Watch decides its compatibility state for 36, and what states exist.
- **UNKNOWN:** the PairedSync capability negotiation and the per-service sync gate.
- **UNKNOWN:** the Wi-Fi V2 archive byte layout (only "V2 ADD" and its gating are established).
- **UNKNOWN:** whether activation challenges are account-bound, and whether an Apple ID session is required on the phone or the Watch.
- **UNKNOWN:** whether the strict native barrier is reproducible at all; the reference fell back to owner confirmation.
- **UNKNOWN:** how the 600 s transaction timeout relates to other provisioning paths.
- **UNKNOWN:** whether any of this works on a non-Ultra model or a second OS build.

## 10. Acceptance criteria

```text
Given a bonded, unactivated pair, when provisioning runs,
then phases advance without regression and activation is confirmed by an activation result,
     the Watch's own language/locale are used (a missing value stops the run),
     the initial-sync preparation receives a correlated response,
     PairedSync reaches its terminal state (or the run reports unverified),
     and the pair becomes operational-eligible only after the completion rule is satisfied.

Given a missing Watch locale, then the run fails before commit and no activation is attempted.

Given a stale or unrelated initial-sync response, then it is ignored and the phase does not advance.

Given an app ACK for the sync preparation, then the UI does not claim completion.

Given owner confirmation, then the setup transport is stopped cleanly, the pair is re-read and validated,
     and only then is operational mode saved.
```

Evidence class required: **observation** for every phase transition; **physical proof** for the visible face or a documented owner confirmation, clearly labelled as such.

## 11. Evidence

| Fact | Reference source |
|---|---|
| Phase vocabulary and driving observations | `docs/live-20261006-optical-pairing/SETUP-387-STATE-CHAIN.md` "Observed chain"; README §Public phases |
| Checkpoint ordinals 1–18 | README §Public phases and durable checkpoints |
| Completion routing and owner confirmation | `docs/SETUP_COMPLETION_388.md` "Cause and behavior", "Physical verification" |
| PBBridge 3/4/18/19/21/25/36 roles | README §Activation and Setup completion, "Native Watch path reconstructed from binaries"; `docs/live-20261005-sync/SETUP_CHAIN.md` "Commands and handlers" |
| NPS wire schema and PSY values | `docs/live-20261005-sync/SETUP_CHAIN.md` "NPS wire schema and values PSY" |
| Watch language handling | `docs/BRIDGE_ARCHITECTURE.md` setup-language paragraph; README line 376 |
| Wi-Fi gating and archive semantics | `docs/BRIDGE_ARCHITECTURE.md`; `docs/live-20261006-optical-pairing/RESULT.md` "Automatic Wi-Fi during fresh setup" |
| Live ordering example | `docs/live-20261006-optical-pairing/RESULT.md` "Observed protocol events" |

**Must be confirmed on hardware:** the full activation exchange (not just its result); that a fresh pair reaches the strict barrier on any build; that the language path uses the Watch's authenticated values; and the Wi-Fi archive layout. The reference itself never observed a strict native completion.
