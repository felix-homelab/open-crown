# 07 — Protocol research

## 1. Objective

Produce a written, testable specification of everything the phone must say and
understand to pair with, activate, and operate an Apple Watch — derived from
black-box captures of devices we own.

This is the largest and longest part of the project. The exploit layer is a
means to an end; this is the end.

## 2. Research areas

Each area gets its own directory under `protocol/`, its own spec, and its own
vectors.

| Area | Questions to answer | Notes |
| --- | --- | --- |
| Discovery | What is advertised in setup vs. normal mode? How is the model and watchOS version encoded? How is setup state signalled? | Unprivileged; first milestone. |
| Pairing bootstrap | Which channel is opened first? What is the handshake? What is local vs. server-assisted? | Requires the radio. |
| Security handshake | Key exchange, authentication, PIN/optical proof, bond derivation. | The crux. |
| Link setup | Encryption, link policy, channel parameters, reconnect rules. | Must survive reboots. |
| Control channel | Framing, request/response correlation, error model, versioning. | Foundation for every service. |
| Setup and activation | Ordering, activation relay, initial properties, language, completion observers. | Many steps are order-sensitive. |
| Faces | Inventory format, selection, mutation, resources, previews. | Readback mandatory. |
| Notifications | Bulletin format, delivery, actions, per-app filtering. | Android listener side is ours. |
| Health | Ingress format, encryption, anchors, completeness. | Local-first. |
| Wi-Fi | Credential relay format, supported security modes. | Narrow scope. |
| Telephony | What a paired phone must provide for calls/SMS. | T3, best effort. |

## 3. Method

### 3.1 Capture

- **Android HCI snoop log** for the phone side (requires developer options).
- **BLE sniffer** (e.g. nRF52-based) for the air side, when the link is not
  encrypted or when we need advertising.
- **Host-side HCI** (Linux `btmon`, or a USB dongle) for a controlled reference
  stack.
- **Screen recordings and timestamps** to correlate user actions with traffic.

Every capture is stored with: date, phone build, watch model, watchOS version,
what the user did, and a hash. Captures are never edited; derived files are
regenerated.

### 3.2 Classify

For each message: direction, channel, length, whether it is encrypted, whether
it depends on a server round-trip, and whether it is idempotent. Unknown bytes
are marked, not guessed.

### 3.3 Differencing

Change one variable at a time: language, Wi-Fi on/off, notification source,
watch face, watchOS version. The diff is the specification.

### 3.4 Specify

Each area gets a spec with:

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

### 3.5 Verify

The implementation must reproduce the captured bytes for the same inputs
(golden vectors), and the Watch must accept them (hardware test). Both are
required; a vector-only pass is not acceptance.

## 4. What we will not do

- No decompilation of watchOS firmware or Apple apps as a source of code.
- No use of Apple private keys, entitlements, or internal services.
- No capture of another person's device or account.
- No publication of captures that contain identifiers, tokens, or health data
  without redaction.

Reverse engineering for interoperability may be lawful in some jurisdictions
and restricted in others; see [09 — Legal and ethics](09-legal-ethics.md). The
default posture is conservative: observe our own devices, publish prose and
vectors, not binaries.

## 5. Versioning

The protocol drifts. Every spec carries a `supported_watchos` range and a
`last_verified` field. A new watchOS release triggers:

1. Re-run the capture suite.
2. Diff against the previous vectors.
3. Mark breaking changes.
4. Publish a delta note.

The app refuses to claim support for a version it has not verified. A
`protocol_mismatch` error is expected behaviour, not a bug.

## 6. Tooling to build (Phase 0–1)

- `tools/capture-import` — normalize HCI snoop and pcap into fixtures.
- `tools/fixture-diff` — compare two captures and highlight changed messages.
- `tools/spec-lint` — check that a spec has the required sections and at least
  one vector.
- `tools/vector-gen` — turn a capture into a golden vector with redaction.
- `tools/hci-echo` — Phase 2 smoke test: open a channel and echo bytes.

## 7. Open research questions

1. Which pairing messages require a live Apple server, and which are local?
2. Is the bond derivable from data the phone can observe, or does it require a
   secret held only by the Watch/iPhone?
3. How does the Watch decide a phone is "trusted" after a reboot?
4. What changes between watchOS major versions, and how early can it be
   detected?
5. Can activation be completed with only the Watch's own network path, or does
   it always need the phone as a relay?

The answers decide whether Open Crown is a companion or a research artifact.
They are answered in Phase 3, not by assumption.
