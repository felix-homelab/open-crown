# 07 — Protocol research

## 1. Objective

Produce a written, testable specification of everything the phone must say and
understand to pair with, activate, and operate an Apple Watch — **from a known
map, not from a blank sheet.**

The protocol has been reconstructed in public. Our job is to verify each step
against our own hardware, write it down, and reimplement it cleanly. Discovery is
not the work; correctness and provenance are.

## 2. What is already mapped

The prior art's public documentation names the layers we need. We use these
terms because they are observable protocol names, not copied identifiers.

| Layer | What it covers | Source of the map |
| --- | --- | --- |
| Discovery | Advertising, setup metadata, model and watchOS hints | Prior art README, own captures |
| Pairing bootstrap | Control channel bring-up, capability probes | Prior art README |
| Security handshake | uIKE control, SPAKE2+/PPK, PIN and optical paths | Prior art README |
| Link setup | ERTM, NetworkRelay, Class-C/Class-D, authenticated ESP | Prior art README |
| Control channel | IDS streams (control and application), NWSC | Prior art README |
| Setup | PBBridge messages, initial properties, language, PairedSync/NPS, Albert/Buddy observers | Prior art README |
| Faces | Native collection, archives, previews, readback | Prior art README |
| Notifications | Bulletin path, per-app filtering | Prior art README |
| Health | Ingress format, anchors, completeness | Partially mapped |
| Wi-Fi | `alloy.wifi.networksync` archive, V2 ADD | Prior art README |

The value of this table is that it tells a contributor *where to look*. It is
not a specification; every row still needs a spec and a vector.

## 3. Research areas

Each area gets a directory under `protocol/`, a spec, and vectors.

| Area | Questions to answer | Notes |
| --- | --- | --- |
| Discovery | Setup vs normal advertising; model and watchOS encoding; setup state | Unprivileged; first milestone |
| Pairing bootstrap | First channel; probe order; local vs server-assisted | Desktop harness |
| Security handshake | uIKE, SPAKE2+/PPK, PIN, optical code, bond derivation | The crux |
| Link setup | ERTM, NetworkRelay, Class-C/D, encryption parameters, reconnect | Must survive reboots |
| Control channel | IDS framing, NWSC, request/response correlation, versioning | Foundation for services |
| Setup and activation | PBBridge ordering, activation relay, language, observers | Order-sensitive |
| Faces | Collection format, selection, mutation, resources, previews | Readback mandatory |
| Notifications | Bulletin format, delivery, actions, filtering | Android listener side is ours |
| Health | Ingress format, encryption, anchors, completeness | Local-first |
| Wi-Fi | Credential archive, supported security modes | Narrow scope |
| Telephony | What a paired phone must provide | T3, best effort |

## 4. Method

### 4.1 Capture

- **Android HCI snoop log** for the phone side (developer options).
- **Host-side HCI** on Linux with a USB dongle for controlled experiments.
- **A BLE sniffer** for advertising only.
- **Screen recordings and timestamps** to correlate actions with traffic.

A caveat the previous revision missed: **encrypted link traffic cannot be read by
sniffing.** To see the interesting messages you must be a pairing peer, or you
must already hold the link key. The plan assumes the host *is* the peer — which
is exactly what the desktop harness provides.

Every capture is stored with date, phone build, watch model, watchOS version,
what the user did, and a hash. Captures are never edited; derived files are
regenerated.

### 4.2 Classify

For each message: direction, channel, length, encryption, server dependence,
idempotence. Unknown bytes are marked, not guessed.

### 4.3 Differencing

Change one variable at a time: language, Wi-Fi, notification source, face,
watchOS version. The diff is the specification.

### 4.4 Specify

```text
Purpose
Actors and preconditions
Sequence (with a diagram)
Message table: name, direction, fields, types, endianness, optionality
State machine and error cases
Version/feature detection
Open questions
Vectors
```

### 4.5 Verify

The implementation must reproduce the captured bytes for the same inputs, and
the Watch must accept them. A vector-only pass is not acceptance.

## 5. What we will not do

- No decompilation of watchOS firmware or Apple apps as a source of code.
- No use of Apple private keys, entitlements, or internal services.
- No capture of another person's device or account.
- No publication of captures containing identifiers, tokens, or health data
  without redaction.

Reverse engineering for interoperability is lawful in some jurisdictions and
restricted in others; see [09](09-legal-ethics.md). Default posture: observe our
own devices, publish prose and vectors, not binaries.

## 6. Versioning

Every spec carries `supported_watchos` and `last_verified`. A new watchOS
release triggers: re-run the capture suite, diff against the previous vectors,
mark breaking changes, publish a delta note.

The app refuses to claim support for a version it has not verified. A
`protocol_mismatch` is expected behaviour, not a crash.

## 7. Tooling to build (Phase 1)

- `tools/capture-import` — normalize HCI snoop and pcap into fixtures.
- `tools/fixture-diff` — compare captures and highlight changed messages.
- `tools/spec-lint` — required sections and at least one vector.
- `tools/vector-gen` — capture to golden vector with redaction.
- `tools/hci-echo` — the Phase 0 harness; open a channel and echo bytes.

## 8. Open research questions

1. Which messages require a live Apple server, and which are local?
2. Can the Watch hold the Apple ID session, with the phone only relaying input?
3. How does the Watch decide a phone is trusted after a reboot?
4. What changes between watchOS major versions, and how early is it detectable?
5. Does the Watch accept a fresh optical/PIN pairing on every model, or only the
   ones tested so far?
