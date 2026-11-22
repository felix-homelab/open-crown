# 01 — Discovery

**Status:** draft. Behavioural; no field offsets are established.
**Spec area:** `protocol/discovery/` (not started).

## 1. Purpose

Find a Watch that is willing to be paired, identify it well enough to select it deliberately, and hand a fresh binding to pairing. Discovery is unprivileged: it needs the BLE scanner, not the controller.

## 2. Preconditions

- Bluetooth is enabled and the scan permission is granted.
- No exclusive owner is mid-pairing (discovery may run alongside, but a selection is only valid for the current scanning session).
- The Watch is in pairing/setup mode. A Watch that is already paired is not expected to advertise setup metadata.
- On a desktop host, the platform BLE stack (CoreBluetooth, BlueZ, WinRT) is available; the reference's macOS path additionally requires an Apple-private entitlement to go further, which we do not have.

## 3. Messages and frames

The reference establishes *that* a setup-mode advertisement carries an FE25 payload with model and OS, and *that* a 12-byte setup advertisement is bound into the optical code. It does not publish the byte layout.

| Item | Where it appears | Known semantics | Encoding / endianness | Status |
|---|---|---|---|---|
| FE25 setup metadata | BLE advertisement from a Watch in setup mode | Identifies a pairable candidate and yields product type and watchOS version | UNKNOWN; named by payload type, not by offset | Observed on hardware (one model, one OS) |
| Apple manufacturer data | BLE advertisement | Presence is common; **not** sufficient to make a device pairable or selectable | UNKNOWN | Observed |
| 5-digit advertising identifier | Bound into the optical code's name field | Ties a scanned optical code to one advertisement | Decimal text, UNKNOWN width/checksum | Claimed; parser described |
| 12-byte setup advertisement | Advertisement data | Secret-binding material for the optical branch: the derived secret is `key ‖ advertisementData` | 12 raw bytes | Claimed |
| Product type | Decoded from FE25 metadata | e.g. `Watch7,5` | UNKNOWN | Observed |
| watchOS version | Decoded from FE25 metadata | e.g. `26.2.0` | UNKNOWN | Observed |
| NetworkRelay advertisement format | Advertisement | Reference release notes add discovery "using the supported NetworkRelay advertisement format", independent of the display catalogue and setup compatibility range | UNKNOWN | Claimed in CHANGELOG only |
| Device address / UUID | Advertisement | Android: LE address; desktop: platform UUID. Never a configured/known address | 6-byte address or 128-bit UUID | Observed |
| Local name | Advertisement / scan response | Display only; not identity | UTF-8, truncated by controller | UNKNOWN |

**Selection rules established by the reference:**

1. Bounded inventory; stale candidates expire.
2. A bounded temporary token represents a candidate; the UI must return that token, not an address.
3. Freshness is rechecked at selection; a changed advertisement or a different candidate rejects the token.
4. Advertising Apple manufacturer data is not enough to make a device selectable.
5. The selected device's metadata is retained until disconnection.
6. While it is being observed, the desktop adapter re-reads discovery about four times a second, publishes only state that actually changed, and refreshes discovery once more before accepting a selection.

## 4. State machine

```text
IDLE
 └─ start scan ──▶ SCANNING
                     ├─ valid FE25 candidate ──▶ CANDIDATE(token, firstSeen, lastSeen, metadata)
                     │                               ├─ refresh ──▶ CANDIDATE (updated)
                     │                               └─ expiry ──▶ SCANNING
                     ├─ scan failure ──▶ FAILED(scan)
                     └─ stop ──▶ IDLE
CANDIDATE ─ select(token) ─▶ VALIDATING
                               ├─ metadata unchanged & fresh ──▶ SELECTED
                               └─ stale / changed / unknown token ──▶ REJECTED(stale)
SELECTED ─ connect ─▶ CONNECTING
                       ├─ L2CAP/ATT up ──▶ CONNECTED (hand to 02)
                       ├─ timeout ──▶ FAILED(connect-timeout)
                       └─ cancel ──▶ IDLE
```

Transitions must not resurrect a candidate after the scan session ends. A new scan starts a new token namespace.

## 5. Error taxonomy

| Code (our vocabulary) | Cause | Required behaviour |
|---|---|---|
| `DISCOVERY_PERMISSION_DENIED` | Scan permission missing | Report; do not pretend to scan |
| `DISCOVERY_SCAN_FAILED` | Adapter off, platform error | Report; offer retry |
| `DISCOVERY_NO_CANDIDATE` | No valid setup metadata seen | Explain that a paired Watch may not advertise; offer retry |
| `DISCOVERY_STALE_TOKEN` | Advertisement changed or expired between publish and select | Reject; rescan; never fall back to an address |
| `DISCOVERY_METADATA_INVALID` | Truncated/unknown FE25 payload | Keep the device non-selectable; log lengths only |
| `DISCOVERY_CONNECT_TIMEOUT` | No link within the platform budget (reference: 20 s on desktop) | Release the attempt; never retry silently in a loop |
| `DISCOVERY_ADAPTER_LOST` | Controller removed/disabled | Invalidate inventory and selection |

## 6. UNKNOWN

- **UNKNOWN:** FE25 byte layout, field offsets, and whether it is service data or manufacturer data.
- **UNKNOWN:** Apple company identifier value and any length/version prefix.
- **UNKNOWN:** whether the 12-byte setup advertisement is the raw manufacturer payload, a sub-field, or a hash of it.
- **UNKNOWN:** advertisement interval, TX power, RSSI thresholds, and whether the reference filters on any of them.
- **UNKNOWN:** whether LE privacy addresses rotate during setup, and how a token survives rotation (or not).
- **UNKNOWN:** the model→display-name mapping and the full set of product types.
- **UNKNOWN:** which watchOS versions change the FE25 contents.
- **UNKNOWN:** whether a paired Watch advertises a different payload that could be used for reconnect discovery.
- **UNKNOWN:** what the NetworkRelay advertisement format adds beyond FE25.

## 7. Acceptance criteria

```text
Given a factory-reset Watch on watchOS 26.2, when a scan runs for 30 s,
then exactly one candidate appears with decoded product type and OS version,
     the decoded values match the Watch's own About screen,
     and no other BLE device in range is selectable.

Given a published candidate, when the advertisement changes or expires before selection,
then the selection is rejected with DISCOVERY_STALE_TOKEN and no connection is attempted.

Given a device that advertises only Apple manufacturer data without valid setup metadata,
when a scan runs, then it is visible at most as an unknown device and is never selectable.

Given a paired Watch, when a scan runs, then it is not offered as a pairing candidate.

Given a truncated or unknown FE25 payload, when it is decoded,
then the candidate is rejected without a crash and without invented field values.
```

Required evidence class: **observation** (raw advertisement bytes + decoded fields) for each row; **physical proof** for the model/OS match against the Watch UI.

## 8. Evidence

| Fact | Reference source |
|---|---|
| FE25 setup metadata carries model and OS; paired Watch may not advertise | `docs/MACOS_PAIRING_TRANSPORT_FINDINGS.md` opening; `docs/MACOS_RUST_CORE.md` discovery section |
| Selection tokens, freshness, bounded inventory, 250 ms polling, 20 s connect timeout | `docs/MACOS_RUST_CORE.md` discovery section; README §3 "Discover and select the actual Watch" |
| Manufacturer data alone is insufficient | README §3; `docs/MACOS_RUST_CORE.md` |
| 12-byte setup advertisement and 5-digit advertising ID | `docs/live-20261006-optical-pairing/RESULT.md` "Source chain and protocol facts" |
| NetworkRelay advertisement format | `CHANGELOG.md` "Unreleased" |
| No configured address used for selection | `docs/MACOS_PAIRING_TRANSPORT_FINDINGS.md` "Physical experiment" |

**Must be confirmed on hardware:** the full advertisement payload bytes for at least two models and two OS builds; whether FE25 survives a paired device; rotation behaviour of the LE address; and that our decoder rejects every malformed payload the reference's parser would.

**Not established by the reference:** anything about advertisement scheduling, scan-window tuning, or multi-Watch environments. Multi-pair selection is graded research-only in its own registry.
