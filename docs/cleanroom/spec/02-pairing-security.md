# 02 — Pairing and control security

**Status:** draft. The reference establishes the phase order, the optical code's payload model, and several exact cryptographic parameters. It does not publish the uIKE message layouts. No cryptographic construction here has been independently verified by Open Crown.
**Spec areas:** `protocol/pairing/`, `protocol/security/` (not started).

## 1. Purpose

Turn an unauthenticated Link to a pairing-mode Watch into (a) an authenticated control SA, (b) a durable Bluetooth bond, and (c) permission to open the normal link. Pairing is the only point where a user secret (PIN or camera code) enters.

## 2. Preconditions

- A Watch in setup mode with a fresh (already decoded) advertisement binding — see 01.
- Exclusive controller ownership (shape A: Agent holds the HAL; shape B: desktop owns the HCI channel).
- No saved pair for this Watch, or an explicit replacement flow with a verified encrypted backup of the existing pair (replacement is not a normal begin).
- Camera path only: a captured, decoded, single-use optical code bound to the advertisement; the native handle is alive and unused.
- PIN path only: the user can read and type the six-digit code shown by the Watch.

## 3. Phase A — Link and control/service negotiation

| Step | Direction | What happens | Known fields | Status |
|---|---|---|---|---|
| A1 | phone → Watch | LE connection after explicit selection | — | Observed |
| A2 | both | L2CAP fixed signalling channel CID `0x003a` ("BT_CL") | CID is fixed; dynamic CIDs are negotiated, never assumed | Observed on one host |
| A3 | both | BT_CL version / services / CREATE / ACCEPT; a dynamic pairing pipe is created | Service identifiers and version numbers: UNKNOWN | Observed |
| A4 | phone → Watch | Control SA initialisation ("probe") | uIKE payload: UNKNOWN | Observed |
| A5 | both | Additional key exchange, then control IKE authentication | Message IDs and payloads: UNKNOWN | Observed |

The reference warns explicitly that a public L2CAP PSM API does not give access to the fixed signalling channel; on macOS the platform daemon refuses the unencrypted bootstrap endpoint (see §8).

## 4. Phase B — Authentication branch

The two branches are **not** interchangeable. They use different authentication methods, different message counters, and different material. Sending camera material down the PIN path is a defect the reference calls out.

### B1 PIN branch

| Step | Direction | What happens | Notes |
|---|---|---|---|
| B1.1 | phone → Watch | Ask for / await a PIN method | The Watch shows a six-digit code |
| B1.2 | both | SPAKE2+ / PPK key exchange keyed by that code | Parameters, group, and password preprocessing: UNKNOWN |
| B1.3 | both | Pairing session messages; private notifications; counters at MID5 | Counter value is branch-specific |
| B1.4 | both | OOB → SMP → bond | See §6 |

### B2 Optical branch

| Step | Direction | What happens | Notes |
|---|---|---|---|
| B2.1 | camera → phone | Decode the Watch's animated code into a 110-byte payload | Image pipeline in §5 |
| B2.2 | phone | Parse and validate six fields; bind them to the fresh advertisement | Parser table below |
| B2.3 | both | Control SA negotiation selects **PSK/1**; a PIN ACK or PIN capability in the response is a downgrade attempt and must be rejected | The caller must acknowledge a Watch-initiated request |
| B2.4 | phone → Watch | IKE AUTH at **MID2**, authentication method **2**, key identity `com.apple.networkrelay.companionlink.pairing.auth.randomKey` | HMAC-SHA512 over the RFC 7296 §2.15 construction with the RFC 9242 §3.3 intermediate-suffix rule |
| B2.5 | Watch → phone | Responder MAC verified in constant time; no PIN PBKDF2, no SPAKE2+, no mandatory PIN PPK | Wrong key/identity/method/transcript/MID must all fail |
| B2.6 | both | Private notifications at **MID3** (PIN uses MID5); then OOB/SMP | The optical initial SA omits PIN/SPAKE2+/PPK proposals and rejects a PIN-capable response |

Optical payload model (established by the reference):

| Field | Meaning | Known value / encoding |
|---|---|---|
| Payload text form | Human/OCR-independent string carried by the code | `format--compatibility--advertisedName--hexKey--material--size&&OS` |
| `format` | Payload format discriminator | **4** in the observed sample |
| `compatibility` | Advertised compatibility version | **25** in the observed sample |
| `advertisedName` | Binds the five-digit advertising identifier, relay strategy, material and size | Validated against the live 12-byte advertisement |
| `hexKey` | 32-byte key material | 64 hex characters |
| `material` | Material selector | **14** in the observed sample |
| `size` | Payload size | **19** in the observed sample |
| `OS` | Advertised system version suffix | Checked against the advertisement before use |
| Derived secret | Mutable 44-byte value | `key ‖ advertisementData` (32 + 12 bytes) |

## 5. Optical image pipeline

The code is a watermark on an animated display. The reference's numbers are reproduced here as claims; they are not independently checked by us.

| Stage | Input | Output | Parameters claimed |
|---|---|---|---|
| Capture | Camera frames (YUV) | Packed UV frames | ≤ 90 frames, ≤ 960×540 UV, 128 MiB arena |
| Perspective/grid | UV frame | Sampling grid | Not finalised by the reference's portable port; a transitional firmware backend did the work |
| Chroma/contrast/filter | Grid samples | Soft signal | 1024 correlations per frame; fixed-point UV path matched native in all 230,400 pixels of one frame |
| Watermark decode | Correlations | Packet bits | Public 1024-bit mask; 100-bit header; 924 convolution symbols |
| Convolution decode | Bits | Data + CRC | `K=9; g=315/331/441/501/431/485; 149 data+CRC bits; 30 repeats` |
| Packet recovery | Packet symbols | 15 data bytes + ID | 5 packet-ID bits **LSB-first**; the following 15 bytes **MSB-first**; payload/CRC expansion **MSB-first** |
| Erasure/CRC | Packets | 110-byte payload | Packet CRC 24 bits; erasure-payload CRC 32 bits; GF(256) polynomial `0x11D`; LCG-generated packet equations; eight distinct packet IDs normally sufficient; duplicate frames do not increase rank |
| Parse | 110 bytes | Six fields | As §4, magic format 4 |

## 6. Phase C — OOB, SMP, bond

| Step | What happens | Notes |
|---|---|---|
| C1 | Private pairing notifications exchanged and validated | Both branches reach here; counters differ |
| C2 | Out-of-band material feeds LE Secure Connections SMP | OOB data format: UNKNOWN |
| C3 | SMP completes; controller accepts the long-term key and link encryption | Reference notes early SMP traffic must survive the control/pairing exchanges; bounded deferred queues |
| C4 | Pairing/security material sealed and persisted; bond stored | Persistence receipt ≠ durable bond; a storage failure cannot advance the commit |
| C5 | Normal registration opens the normal link | See 04 |

## 7. State machine

```text
SELECTED
  └─ connect ─▶ LINK_UP
                 └─ BT_CL negotiate ─▶ CONTROL_CHANNEL
                       └─ control SA init ─▶ CONTROL_SA
                             ├─ PIN requested ─▶ PIN_REQUIRED ─ submit code ─▶ PIN_AUTH
                             └─ optical handle ─▶ PSK_NEGOTIATE ─▶ PSK_AUTH(MID2)
PSK_AUTH ─ verify responder MAC ─▶ PRIVATE_NOTIFY(MID3)
PIN_AUTH ─ ppk/private notify(MID5) ─▶ PRIVATE_NOTIFY
PRIVATE_NOTIFY ─▶ SMP ─▶ BONDED ─▶ NORMAL_LINK (04)
Any state ─ fault/stop ─▶ CLOSED
```

Rules:
- A failed authentication never advances to SMP; it closes the link and discards transient material.
- A replacement flow must not delete the existing pair before the new one is verified; the reference requires a verified encrypted backup first.
- A new attempt is a new session; counters do not carry over.

## 8. Error taxonomy

| Code | Cause | Required behaviour |
|---|---|---|
| `PAIR_LINK_TIMEOUT` | No control channel within budget | Close, release ownership, report |
| `PAIR_BTCL_REFUSED` | Negotiation rejected or unsupported version | Report; do not retry with invented constants |
| `PAIR_SA_INIT_FAILED` | Control SA cannot be established | Close |
| `PAIR_PIN_REJECTED` | Wrong code | Allow bounded retry; the Watch may rate-limit |
| `PAIR_PIN_NOT_OFFERED` | Watch expects another branch | Do not force a branch |
| `PAIR_OPTICAL_STALE` | Code does not match the fresh advertisement | Discard handle; rescan |
| `PAIR_OPTICAL_MALFORMED` | Field parse/CRC failure | Discard; no partial use |
| `PAIR_OPTICAL_METHOD_MISMATCH` | PIN method chosen during optical negotiation | Abort; treat as downgrade |
| `PAIR_OPTICAL_MAC_INVALID` | Responder MAC fails constant-time check | Abort; wipe transcript |
| `PAIR_DUPLICATE_OR_REORDERED` | Duplicate/reordered fragments | Reject the exchange |
| `PAIR_SMP_TIMEOUT` | SMP does not complete (reported on Ultra 1 in the reference's issue tracker) | Close; preserve diagnostics without secrets |
| `PAIR_BOND_STORE_FAILED` | Sealing/persistence failure | Do not report bonded |
| `PAIR_BOOTSTRAP_RESTRICTED` | Host platform refuses the plaintext bootstrap (macOS daemon policy) | Disable pairing on that host; do not attempt raw ACL |
| `PAIR_UNSUPPORTED_ABI` | Decoder native ABI unsupported | Refuse to start the camera branch |

## 9. UNKNOWN

- **UNKNOWN:** uIKE message layouts, payload types, SA proposal contents, nonce sizes, key derivation, and the exact RFC 7296/9242 adaptation Apple uses.
- **UNKNOWN:** SPAKE2+ group, identity strings, password preprocessing, and PPK parameters.
- **UNKNOWN:** how the PIN is confirmed to the user and whether the Watch enforces attempt limits.
- **UNKNOWN:** OOB data layout and how it binds to SMP.
- **UNKNOWN:** SMP key distribution details and whether the bond is a standard LE SC bond or a vendor variant.
- **UNKNOWN:** whether the 110-byte optical payload is single-use, time-limited, or bound to one advertisement only.
- **UNKNOWN:** the perspective/grid localisation algorithm; the reference explicitly left its portable spatial detector unfinished.
- **UNKNOWN:** whether the optical branch works on non-arm64 Android devices, or on a host without a camera.
- **UNKNOWN:** the relationship between the pairing control SA and the later NetworkRelay SAs.

## 10. Acceptance criteria

```text
PIN
Given a reset Watch and exclusive controller ownership, when the user enters the correct six-digit code,
then the phone reaches an authenticated control exchange, completes SMP, stores a sealed bond,
     and the next connection reuses it without a PIN.

Given the wrong code, when submitted, then authentication fails, the link closes,
     no bond is stored, and no normal-link message is sent.

OPTICAL
Given a live animated code and a fresh advertisement, when the code decodes and binds,
then the phone negotiates PSK/1, verifies the responder MAC at MID2, and reaches private notifications at MID3.

Given a code captured from a different Watch or an older advertisement,
then binding fails with PAIR_OPTICAL_STALE and no authentication is attempted.

Given a responder that selects PIN during optical negotiation, then the phone aborts.

Given a camera image that decodes to a well-formed payload, authentication is still not claimed
     until the MAC verification succeeds.

RECOVERY
Given a storage failure after SMP, then the pair is not reported as bonded and the normal link is not opened.
```

Required evidence class: **observation** (full message trace with secrets redacted) plus **physical proof** (Watch shows paired; reconnect without a code).

## 11. Evidence

| Fact | Reference source |
|---|---|
| Phase order, BT_CL, dynamic CIDs | README §Protocol stack; README §4–§8 "Fresh pairing, step by step" |
| PIN vs optical branches, counters, "decoding does not authenticate" | README §6 and its flowchart |
| PSK/1, method 2, MID2, key identity, RFC references, SA_INIT exclusions | `docs/live-20261006-optical-pairing/RESULT.md` "Source chain and protocol facts" and following list |
| Payload fields, 44-byte secret, 12-byte advertisement binding | Same report: "Payload is `format--compatibility--…`" paragraph |
| Watermark/convolution/CRC parameters, bit order | Same report: "The five packet-ID bits are LSB-first…" and preceding paragraph |
| Optical bounds, handle lifetime, ABI restrictions | Same report: "Working recognition backend…" |
| macOS bootstrap restriction and entitlement finding | `docs/MACOS_PAIRING_TRANSPORT_FINDINGS.md` |
| Early SMP traffic and Ultra 1 timeout | `CHANGELOG.md` "Unreleased" |

**Must be confirmed on hardware:** the full control-SA trace on a second OS build; PIN and optical pairs on a second model; whether the bond is standard LE SC; and whether the optical path can be implemented without Apple firmware (the reference's own decoder is a transitional firmware backend, so this is an open design question for us, not a detail).
