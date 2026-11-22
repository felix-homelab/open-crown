# 00 — Overview: stack layers and trust boundaries

**Status:** draft. Written from the prior art's prose, not from captures. No hardware verification is claimed anywhere in this spec set.
**Audience:** implementers of the Crown (companion), the Agent (privileged on-phone component), and desktop hosts.

## 1. Purpose

Fix the layer model, the ownership boundaries, and the evidence vocabulary that the other specs assume. Everything below is a claim about *layering*, not about byte layouts; byte layouts live in specs 01–08 and are marked UNKNOWN where the reference does not establish them.

## 2. Terms used in this spec set

| Term | Meaning here |
|---|---|
| **Crown** | Our companion layer: pair records, checkpoints, command queue, observations, UI |
| **Agent** | The privileged process that owns the Bluetooth controller for one boot (our "borrowed privilege") |
| **Link** | The BLE connection and its L2CAP channels |
| **Channel** | A logical IDS application topic carried over the Link; not a separate TCP connection |
| **Session** | One transport lifetime, identified by an epoch; a new connection is a new Session |
| **Checkpoint** | A durable, ordered marker of provisioning progress; never replayed if non-idempotent |
| **Pair record** | Our durable record of a Watch: identity, state, checkpoints, key references |
| **Observation** | A value read back from the Watch, with freshness |
| **Receipt** | An acknowledgement at some layer; never proof of effect |

Deliberate non-terms: we do not reuse the reference's class, module, or command names. Public, observable protocol terms (FE25, BT_CL, uIKE, SPAKE2+, PPK, OOB, SMP, ERTM, NetworkRelay, NWSC, IDS, PBBridge, PairedSync, NPS, PSY, NTK, OPACK) are kept because renaming observable protocol vocabulary would hide the relationship, which our provenance policy forbids.

## 3. Stack

| Layer | What it does | Owner | Spec |
|---|---|---|---|
| L0 Radio | BLE advertising, LE connection, pairing-mode advertisement | controller / OS | 01 |
| L1 Controller access | HCI commands/events/ACL. Two shapes: (A) hold the Android Bluetooth HAL from a privileged Agent; (B) own the HCI user channel / USB device from a desktop host. macOS uses CoreBluetooth and cannot reach the private control channel. | Agent (A) / desktop host (B) | 00, 02 |
| L2 L2CAP | The fixed signalling channel (CID `0x003a`, "BT_CL") carries a version/service negotiation that creates the pairing pipe; later pipes get dynamic CIDs; negotiated data channels use ERTM | Agent / desktop host | 02, 04 |
| L3 Pairing security | uIKE-style control SA; PIN branch (SPAKE2+/PPK) or optical branch (PSK/1); then OOB → LE Secure Connections SMP → durable bond | Agent | 02 |
| L4 Normal link | Registration, NetworkRelay endpoint negotiation, Class-D and Class-C protection, ESP/IPsec, inner IPv6, TCP, NWSC service connector | Agent | 04 |
| L5 IDS | Control Hello, service map, application topics, message correlation | Agent | 04 |
| L6 Feature protocols | PBBridge provisioning/activation, NPS/PairedSync, face collection/delta, notifications, settings, Wi-Fi, health, find-device, credentials, telephony, time; plus the separate StateReplicator/QUIC path | Agent | 03, 05–08 |
| L7 Crown | Pair record lifecycle, checkpoints, command admission, observations, UI | Crown | all |

**Two transport shapes.** Shape A is an Android Agent that holds the Bluetooth HAL under borrowed privilege. Shape B is a desktop host that owns the HCI user channel or USB controller. The wire protocol above L1 is intended to be identical; the reference only physically verified shape A (Android), one Linux host, and one Windows host, and explicitly did not complete macOS.

## 4. Message and frame layers (outermost to innermost)

| # | Layer | Notes |
|---|---|---|
| 1 | HCI ACL | Controller boundary |
| 2 | L2CAP ERTM | Negotiated dynamic channels, retransmission and windows |
| 3 | NetworkRelay | Authenticated relay endpoint negotiation |
| 4 | ESP / IPsec | Class-C or Class-D protection of inner packets |
| 5 | Inner IPv6 | Addresses inside the tunnel |
| 6 | TCP | One transport for IDS streams |
| 7 | NWSC | Service connector that selects/identifies the IDS service |
| 8 | IDS control or application message | Control Hello/service map, or a topic payload |

A topic is a routing key inside layer 8. It is not a socket, not a TCP connection, and not a separate protection class. Class-C and Class-D identify protected routes, not applications.

## 5. Trust boundaries

| ID | Boundary | What must hold | If it fails |
|---|---|---|---|
| B1 | Advertisement → selection | A candidate is selectable only after decoding valid setup metadata and a fresh selection; manufacturer data alone is not evidence of pairability | We try to pair with a non-pairable or spoofed device; user sees a failure, or worse, a mis-bound optical code |
| B2 | Controller ownership | Exactly one owner holds the controller for a boot; one serial packet state machine orders everything; a second flow cannot acquire it during teardown | Interleaved HCI, corrupted handshakes, unrecoverable link state |
| B3 | Authentication vs bond vs readiness | An authenticated control SA is not a durable bond; a bond is not IDS readiness; IDS readiness is not activation | We report success that the Watch has not accepted |
| B4 | Decrypted IDS payload | Only authenticated inner traffic is parsed; unknown topics are rejected; replies must match topic, message identifier, session, peer and (for delta) batch index | Cross-talk between features, stale replies applied to new state |
| B5 | Agent ↔ Crown IPC | Signature-protected caller, versioned envelope, canonical request id, epoch and deadline checks; admission distinguishes queued from applied | A UI can claim an effect that never happened, or a stale command executes after reconnect |
| B6 | Secrets | Keys, PINs, optical payloads, Wi-Fi passphrases never enter UI snapshots, logs, or preferences; optical material stays behind a single-use native handle | Credential disclosure via backups, logs, or diagnostics |
| B7 | Persistence | Pair/bond records are sealed before disk; checkpoints are monotonic; the owner's confirmation and the Watch's own evidence are different records; a new Session invalidates earlier commands | Replaying a non-idempotent provisioning step after a crash |
| B8 | Evidence claims | A receipt, an observation and a physical effect are three different things and must never be promoted into each other | False parity claims, and a product that "works" only in its own logs |

## 6. Identities and correlation

| Identity | Scope | Rule |
|---|---|---|
| Pair record id | A saved Watch | Stable across sessions; replacement requires an explicit, verified backup path |
| Session epoch | One transport lifetime | New connection ⇒ new epoch; late callbacks from old epochs are dropped |
| Request id | One Crown command | Canonical UUID; correlates queue→agent→result |
| Message id | One IDS application message | Correlates ACK/response to the request |
| Generation / sequence / revision | Feature baseline | Monotonic where the protocol defines it; a baseline mismatch must fail closed |

## 7. Claim taxonomy (mandatory vocabulary)

| Claim | Means | Does not mean |
|---|---|---|
| QUEUED | Admitted to a local queue | Anything left the phone |
| SENT | Bytes handed to the controller | The Watch received them |
| RECEIPT (ACK) | The peer's transport acknowledged | The feature took effect |
| OBSERVATION | A read-back from the Watch, with provenance and freshness | The user saw it |
| PHYSICAL PROOF | An independently observable effect on the device (sound, screen, network join) | Anything about a different feature |

Every spec's acceptance criteria must name which class of evidence it requires. No acceptance criterion may accept a receipt as proof of effect.

## 8. Scope and versioning

- The reference claims hardware testing on one Watch model (`Watch7,5`, Ultra 2) and one OS build (watchOS 26.2 / 23S303); it says so in a banner. Linux and Windows runs used the same model. Nothing establishes behaviour on other models or OS versions.
- Apple binary addresses and offsets in the reference are build-specific and must not be treated as portable. Our specs therefore describe ordering and semantics, not addresses.
- Protocol drift is expected. Each spec carries a "version and detection" note where the reference establishes one; otherwise UNKNOWN.

## 9. Preconditions for applying this model

- A Watch the operator owns, at a known model and OS build; unknown model or build means every behaviour below is a hypothesis.
- Exactly one controller owner (Agent or desktop host) for the duration of a Session.
- A pair record in a known state: `none`, `bonded`, `activating`, `activated`, `operational`, `failed`. The state determines which specs apply.
- For any write: an observed baseline from the current Session, not a cached value.
- For any acceptance claim: an evidence class from §7, named in advance.

## 10. Session life cycle (the model-level state machine)

```text
NO_PAIR ─ discovery/selection (01) ─▶ SELECTED
SELECTED ─ connect + control security (02) ─▶ BONDED
BONDED ─ normal link + IDS (04) ─▶ LINKED
LINKED ─ provisioning (03) ─▶ ACTIVATED
ACTIVATED ─ completion rule ─▶ OPERATIONAL
OPERATIONAL ─ link loss ─▶ RECONNECTING (04) ─▶ OPERATIONAL
Any ─ stop/teardown ─▶ RELEASED
Any ─ fault ─▶ FAILED (terminal until explicit reset)
```

Invariants: a Session has one epoch; a new connection is a new epoch; earlier commands and late callbacks from an earlier epoch are invalid; provisioning is never replayed on an operational reconnect; a second flow cannot own the controller while the first is releasing it.

## 11. Error taxonomy (stack level)

| Code | Layer | Meaning | Required behaviour |
|---|---|---|---|
| `STACK_NO_CONTROLLER` | L1 | No exclusive controller owner | Refuse to start; no fallback that pretends |
| `STACK_LINK_LOST` | L2 | Link dropped | Close Session, invalidate epoch |
| `STACK_SECURITY_REFUSED` | L3 | Authentication/bond failed | Close; discard transient material |
| `STACK_CLASS_UNAVAILABLE` | L4 | Required protection class absent | Do not send the feature's traffic |
| `STACK_IDS_NOT_READY` | L5 | Control/service map incomplete | Reject application commands |
| `STACK_UNSUPPORTED_TOPIC` | L5 | Unknown Channel | Reject; do not guess |
| `STACK_STALE_EPOCH` | L6/L7 | Message from an earlier Session | Drop |
| `STACK_POLICY_REFUSED` | L7 | Pair/session policy forbids the action | Stop; require user action |
| `STACK_TEARDOWN_INCOMPLETE` | L7 | Cleanup failed | Report; block new ownership |

## 12. UNKNOWN

- **UNKNOWN:** whether the layer stack above is stable across watchOS versions; only 26.2/23S303 is claimed.
- **UNKNOWN:** whether the control-channel negotiation is identical on non-Ultra models.
- **UNKNOWN:** the exact IDS control-Hello contents and the service-map encoding; the reference describes them behaviourally, not byte-by-byte.
- **UNKNOWN:** how much of the NetworkRelay/IPsec layer is reused between pairing and operational modes.
- **UNKNOWN:** whether shape B (desktop HCI) exposes every controller feature needed for the full path; the reference verified one Linux and one Windows host only.

## 13. Acceptance criteria

```text
Given a pair record in state X, when a spec is applied, then only the transitions legal from X are attempted.

Given any Session, then exactly one epoch is published to the Crown and no callback from an earlier epoch changes state.

Given a link loss during provisioning, then no provisioning step is replayed automatically.

Given a receipt (ACK) for any feature, then no acceptance criterion in specs 01–08 is satisfied by it alone.

Given an unknown model or OS build, then the spec's behaviour is labelled a hypothesis and the run is not called verified.
```

## 14. Evidence

| Fact | Reference source |
|---|---|
| Layer names and order | README §Protocol stack ("Establishing the link", "IDS application traffic over the normal link") |
| Topic ≠ connection; Class-C/D are protection | README §Protocol stack, closing paragraph |
| Two transport shapes, ownership, serial state machine | README §System architecture; `docs/BRIDGE_ARCHITECTURE.md` "Owners" and "Invariants" |
| Receipt vs effect vocabulary | README §What each confirmation proves |
| Build-specific addresses | `docs/live-20261005-sync/SETUP_CHAIN.md` "Evidence boundaries" |
| macOS cannot reach the control channel | `docs/MACOS_PAIRING_TRANSPORT_FINDINGS.md`; `docs/COMPANION_ARCHITECTURE.md` |

**Must be confirmed on hardware before any layer is called verified:** an end-to-end pairing on a second model; IDS Hello and topic routing on a second OS build; controller ownership under a second Android vendor's HAL; a desktop host reproducing the same pairing flow without Apple's private entitlement.
