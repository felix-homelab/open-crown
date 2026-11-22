# 05 — Watch faces: inventory, delta transactions, import/export, native resources

**Status:** draft. The delta transaction's stage machine and limits are established; the change encoding and archive internals are not. No hardware verification by Open Crown.
**Spec area:** `protocol/faces/` (not started).

## 1. Purpose

Read the Watch's real face collection, mutate it only through a correlated transaction confirmed by read-back, move face packages on and off the phone, and never confuse a local design or a preview with an installed face.

## 2. Preconditions

- Operational Session on an activated pair.
- Known current inventory with a generation/sequence; mutations require it.
- For import: a validated package (structure, configuration, resources) and an explicit user Add; for resource replacement, a known baseline package hash.
- The Watch is not in provisioning; face commands are operational-only.

## 3. Three independent data sets

| Set | Contents | Proves |
|---|---|---|
| Native collection | UUIDs, order, selection, configurations, packages read from the Watch | What is installed |
| Local design library | Our own designs, catalogue/cache, local files | Nothing about the Watch |
| Native preview assets | Renderer output and parameters | Nothing about installation |

A rendered preview, a saved design, or a queued command must never be presented as an installed face.

## 4. Reading inventory

```text
request collection
  → header (sequence/generation, request kind)
  → multipart response parts
  → validate session/parts/archive/configuration
  → persist native collection + package metadata
  → publish observation (only when complete)
```

- A partially received multipart transfer is never published as if the library were complete.
- Header sequence/generation is persisted separately; a known unfinished session can be recovered.
- This controls native synchronisation, not deletion of user faces.

## 5. Delta transaction

### 5.1 Stages

```text
START_READY → START_WAIT → BATCH_READY → BATCH_WAIT → END_READY → END_WAIT → READBACK_WAIT
                                                                              ├─ APPLIED
                                                                              ├─ REJECTED
                                                                              └─ UNKNOWN
```

### 5.2 Messages

| Message | Direction | Meaning | Correlation |
|---|---|---|---|
| START | phone → Watch | Open a delta session for a target face/config | session id |
| START response | Watch → phone | Session accepted/refused | same session id |
| BATCH(index, changes) | phone → Watch | Ordered change batch | session id + batch index |
| BATCH response | Watch → phone | Batch accepted/refused | same session + index |
| END | phone → Watch | Commit the transaction | session id |
| END response | Watch → phone | Accepted, rolled back, or error | session id |
| Read-back request | phone → Watch | Fetch the complete collection | request id |
| Complete collection | Watch → phone | Read-back payload | request id |

Byte-level field layout of START/BATCH/END is **UNKNOWN**. Known properties: changes are carried in batches; replies must match topic, message id, session, peer and batch index; a read-back timeout of **180 s** applies; a batch or END error may follow a partial write, and the transaction is never auto-replayed.

### 5.3 Operations and their confirmation

| Operation | Input | Confirmation required |
|---|---|---|
| Select | target face UUID | Newly observed selected UUID |
| Duplicate | source face UUID; new UUID derived from the request | New face/archive present in read-back |
| Remove | face UUID | UUID absent from read-back; the final face cannot be removed |
| Reorder | exact permutation | Full matching order in read-back |
| Update configuration | face UUID + binary configuration | Matching configuration in read-back |
| Add package | `.watchface` archive | New target UUID and archive in read-back |
| Replace resources | baseline package hash + resources | Matching resources in read-back |

## 6. Package transfer

| Limit | Value |
|---|---|
| Archive size | ≤ 16 MiB |
| Inline add | ≤ 128 KiB in the command |
| Upload chunk | ≤ 64 KiB, sequential, offset-validated |
| Transfer lifetime | 30 minutes |
| Export chunk | ≤ 64 KiB with stable total and SHA-256; final hash verified by the Crown |
| Pair/epoch change | Stops the transfer; unfinished uploads are cancelled or expire |

```text
validate package → explicit Add/resource replacement
  → inline (≤128 KiB) OR begin(total) → append(offset, chunk ≤64 KiB)* → finish(seal)
  → native ADD/RESOURCES command → delta transaction → read-back
```

Photos editing uses real resources and a baseline package hash. Local drafts and previews do not submit mutations; gallery creation requires an explicit Add.

## 7. Alternative path: StateReplicator over QUIC

The reference implements a separate, pinned-QUIC path for modern snapshot exchange. It does **not** replace the delta transaction, and its file persistence is unimplemented.

| Property | Value |
|---|---|
| QUIC application protocol (ALPN) | `application-service` |
| Largest datagram / batch | 1200 bytes; at most 4 per batch |
| Envelope protocol / zone protocol | 8 / 6 |
| Message type | `StateReplicator` |
| Client identity | `com.apple.nanotimekit.replicator.library` |
| Zones | `library_snapshots`, `gallery_snapshots` |
| Transport | caller-supplied datagrams over the existing relay tunnel; the QUIC engine opens no OS socket |
| Trust | public-key pins from authenticated discovery plus proof of possession; not Web PKI |

NetworkMessenger header (verified by the reference against native code):

| Byte offset | Length | Meaning | Order |
|---:|---:|---|---|
| 0 | 16 | prefix (canonical UUID tuple) | — |
| 16 | 4 | header length 72 | little-endian |
| 20 | 16 | message id | — |
| 36 | 16 | sender id | — |
| 52 | 4 | payload length | little-endian |
| 56 | 4 | type: data / file / extended attributes / failure / compressed file (0–4) | little-endian |
| 60 | 4 | sequence count | little-endian |
| 64 | 4 | sequence index | little-endian |
| 68 | 4 | priority: high / medium / low (0–2) | little-endian |

The reference's application limits (4 MiB payload, 1024 fragments) are its own policy, not Apple's.

## 8. State machine (mutation)

```text
IDLE ─ validate inventory/target/payload/baseline ─▶ PLANNED
PLANNED ─ send START ─▶ START_WAIT
START_WAIT ├─ accepted ─▶ BATCH_READY ─ (all batches) ─▶ END_READY
           ├─ refused ─▶ REJECTED
           └─ timeout ─▶ UNKNOWN
END_READY ─ send END ─▶ END_WAIT ├─ accepted ─▶ READBACK_WAIT
                                ├─ rollback/error ─▶ REJECTED
                                └─ timeout ─▶ UNKNOWN
READBACK_WAIT ├─ expected result observed ─▶ APPLIED
              ├─ 180 s elapsed/disconnect ─▶ UNKNOWN
              └─ contradictory observation ─▶ REJECTED
Terminal states never auto-retry. A fresh observation is required before any new mutation.
```

## 9. Error taxonomy

| Code | Cause | Required behaviour |
|---|---|---|
| `FACE_NO_INVENTORY` | Collection not known for this Session | Refuse mutation |
| `FACE_TARGET_UNKNOWN` | UUID not in current collection | Refuse |
| `FACE_BASELINE_STALE` | Generation/config baseline changed | Refuse; re-read |
| `FACE_PACKAGE_INVALID` | Structure/config/resources fail validation | Refuse before transfer |
| `FACE_PACKAGE_TOO_LARGE` | > 16 MiB | Refuse |
| `FACE_TRANSFER_EXPIRED` | > 30 min or pair/epoch changed | Cancel; do not resume silently |
| `FACE_TRANSFER_OFFSET` | Offset/chunk mismatch or hash mismatch | Abort |
| `FACE_DELTA_REFUSED` | START/BATCH/END refused | Report; no replay |
| `FACE_DELTA_PARTIAL` | Error after some batches | UNKNOWN; require fresh read-back |
| `FACE_READBACK_TIMEOUT` | 180 s without complete collection | UNKNOWN; never claim applied |
| `FACE_READBACK_MISMATCH` | Read-back contradicts the intent | REJECTED; keep observation |
| `FACE_LAST_FACE` | Removing the only face | Refuse |

## 10. UNKNOWN

- **UNKNOWN:** START/BATCH/END payload encoding, field numbers, and the change object format.
- **UNKNOWN:** what a "configuration" blob is (binary plist, OPACK, JSON) and how it is versioned.
- **UNKNOWN:** maximum batch count/size, and whether order within a batch is significant.
- **UNKNOWN:** `.watchface` internal layout, signature/validation rules, and resource addressing.
- **UNKNOWN:** how read-back completeness is determined beyond "a complete collection arrived".
- **UNKNOWN:** whether the 209-face/135-face counts in the reference are product limits, per-model limits, or parser bounds.
- **UNKNOWN:** the OPACK encoding beyond the header; the reference repaired a decoder against native samples but the format is not published here.
- **UNKNOWN:** QUIC handshake details, zone record schemas, and file-transfer framing; the reference's own file persistence is unimplemented.
- **UNKNOWN:** how Photos resources and complication descriptors map into a configuration.
- **UNKNOWN:** whether a mutation is atomic from the Watch's point of view, or whether a partial write is observable.

## 11. Acceptance criteria

```text
Given a known collection, when a face is selected,
then the new selection is observed in a complete read-back and the UI shows it as applied.

Given a duplicate, when the transaction completes,
then a new UUID appears with the expected configuration/order and the source is unchanged.

Given a removal of the final face, then the command is refused before any message is sent.

Given an update whose read-back differs, then the result is REJECTED, not APPLIED.

Given a 16 MiB package, when imported, then chunks are sequential, the final hash matches,
     and the Watch read-back shows the new UUID and archive.

Given a disconnect during READBACK_WAIT, then the result is UNKNOWN and no automatic retry occurs.

Given an incomplete multipart collection, then it is not published as the library.
```

## 12. Evidence

| Fact | Reference source |
|---|---|
| Three data sets, read flow, mutation transaction, stages, 180 s | README §Native faces: read, mutate, import, resources |
| Archive limits, 128 KiB inline, 64 KiB chunks, 30 min TTL, SHA-256 export | README §`.watchface`, Photos, and resources |
| Operation list and confirmation rules | README §Complete Companion → Bridge API registry, "Operational commands" |
| Preview/asset claims and 530-swatch validation | README §Native UI and previews; `docs/WATCH_APP_NATIVE_OPTION_SWATCHES_90.md` |
| Physical read-back example (4-face collection, selection restored) | `docs/WATCH_FACES_NATIVE_COVERAGE_51.md` "Actual Watch verification" |
| Replicator constants and header layout | README §Modern StateReplicator and QUIC; `docs/WATCH_FACES_REPLICATOR_39.md` "Actual binary header, separately verified" |
| Unfinished file persistence | README line 782 |

**Must be confirmed on hardware:** the delta payload encoding; whether read-back is a full collection or a diff; the archive format; and resource replacement. The reference's own registry records an open regression in collection reads (a partial collection without an END marker), so even the inventory path is not settled.
