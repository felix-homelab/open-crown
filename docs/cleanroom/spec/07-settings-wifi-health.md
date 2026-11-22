# 07 — Settings, pigments, monogram, Wi-Fi, Health, find devices, time

**Status:** draft. Preference keys, domains and limits are established; most value encodings and the Wi-Fi archive are not. No hardware verification by Open Crown.
**Spec areas:** `protocol/wifi/`, `protocol/health/`, plus preference notes for `protocol/setup/`.

## 1. Purpose

Change Watch state through the preference channel (settings, palettes, monogram), deliver network credentials, ingest encrypted Health data, locate devices, and keep time in sync — each with an explicit read-back rule.

## 2. Preconditions

- Operational Session, except Wi-Fi during first provisioning (Class-C setup lane).
- For any preference write: a current observed baseline (pair, Session epoch, revision/timestamp) and a full value set where the key is a set.
- For Health ingress: current peer identity and registry binding; encryption material available.
- For Find: the phone has the required effect permission (sound/flash) for the phone-side effect.

## 3. Settings (NPS writes)

| API enum (reference) | Domain | Native key | Direction |
|---|---|---|---|
| `RIGHT_WRIST` | `com.apple.nano` | `wornOnRightArm` | Two-way |
| `INVERT_SCREEN` | `com.apple.nano` | `invertUI` | Two-way |
| `TIME_24_HOUR` | `.GlobalPreferences` | `AppleICUForce24HourTime` | Not marked two-way |

Rules:
- Missing or malformed values remain **unknown**, never `false`.
- A write is confirmed only by a new observation of the exact key.
- Apple reference-date values count seconds from 2001-01-01; converting to or from Unix time means adding or subtracting 978307200. Do not mix the two epochs.

## 4. Pigments (Add Colors)

| Property | Value |
|---|---|
| Domain | `com.apple.NanoTimeKit` |
| Keys | `SelectedPigmentList`, `AutoSelectedPigmentList` |
| Semantics | Pair-scoped sets of palette fullnames, not per-face fields |
| Limits | 1023 names; 64 KiB value bytes |
| Write baseline | pair + epoch + both timestamps + complete sets |
| Confirmation | A new observed mirror with the expected values |

The automatic set and the manual set do not share a lifecycle. A write checks both baseline collections and keeps the Watch's manual opt-out intact. A scoped preference is never replayed out of a global cache that belongs to something else.

## 5. Monogram

| Property | Value |
|---|---|
| Domain / key | `com.apple.NanoTimeKit` / `customMonogram` |
| Scope | Global per pair, distinct from a face's own monogram dictionary |
| Text rules | Empty preserves; non-empty takes the first five UTF-16 units, expands to a composed range, uppercases using the host locale, then validates |
| Bound | 4096 UTF-16 units on the native channel |
| Confirmation | Matching text, pair, epoch, newer source timestamp and a remote mirror; a local mirror never confirms |

Editing text stages no face delta and changes no selected face.

## 6. Wi-Fi

| Property | Value |
|---|---|
| Route | `alloy.wifi.networksync`, Class-C |
| Supported network | WPA2-PSK/CCMP only (reference scope) |
| Trigger | After activation, language completion, Normal, and the correlated initial-sync response; or an operational READY |
| Archive | "V2 ADD" (byte layout UNKNOWN) |
| Retry | At most 3 reads, 5 s apart |
| Archive lifetime | 10 s after creation; refused after pair/generation/readiness change |
| Dispatch | No more than one archive is successfully dispatched for a given setup generation |
| Meaning of ACK | Watch received the archive; **not** that it joined the network |
| Secret handling | Passphrase never enters the UI, logs, arguments or diagnostics |

## 7. Health

Ingress path:

```text
Watch encrypted Health message
  → IDS Class-C lane
  → forward encrypted data to the app inbox (replay-safe)
  → verify peer identity + registry binding
  → decode native objects
  → durable Health session / defaults mirror
  → limited typed observations to the Crown
```

Health defaults mirror (from the reference's native research):

| Rule | Detail |
|---|---|
| Dictionary validation | Category required; each key and timestamp required |
| Domain | A missing domain becomes the empty string before insertion; presence is preserved |
| Value kinds | double → int64 → string → raw data → nil; missing value becomes a tombstone, not zero |
| Timestamps | NSDate reference seconds (not Unix, not milliseconds) |
| Merge | Newer replaces; equal/older and tombstones keep the prior date; a matching stored date disables replacement |
| Storage | Local mirror, versioned, bounded 4096 rows / 16 MiB, separate encryption keys, full transactions |
| Failure | Read/SQL/key failures abort; they never become an empty result |
| Native errors noted | One family resets received anchors; one means journal-full; some ignorable errors can still report success |

The mirror is explicitly **not** native acceptance: no receive anchors, no native reply, no clinical import.

## 8. Find devices and time

| Flow | Messages | Confirmation |
|---|---|---|
| Phone → Watch sound | `PING_WATCH` → `alloy.findmylocaldevice` → correlated response | Native `didPlay=true` is a claim; audible sound is physical proof |
| Watch → Phone find | Native request → phone effects (sound/flash) → durable duplicate claim → correlated result | The effect happens on the phone; camera/flash availability is separate from permission "opened" |
| Stop phone signal | Local stop command | Local `STOPPED` receipt |
| Time / time zone | `alloy.timesync`, `alloy.timezonesync` | Exchange exists; a real clock change is unverified |

## 9. About / battery (system settings)

| Element | Value |
|---|---|
| Request / response | Type 5 request; type 6 response with `response=true`; observed 22 bytes |
| Fields | 1..7 each at most once; capacity is field 5 (unsigned 64-bit), charging is field 6 (bool) |
| Unknown values | Sent as explicit `-1` in at least one field; presence logging does not prove a reading |
| Confirmation | Only a validated response replaces a prior observation |

## 10. State machine (preference write)

```text
OBSERVED(baseline) ─ user change ─▶ VALIDATING(pair/epoch/revision/sets)
   ├─ invalid/stale ─▶ REJECTED
   └─ valid ─▶ SENDING(NPS) ─▶ SENT ─▶ AWAIT_OBSERVATION
                                          ├─ matching observation ─▶ APPLIED
                                          ├─ timeout ─▶ UNKNOWN
                                          └─ contradictory ─▶ REJECTED
```

Wi-Fi worker: `IDLE → READING_NETWORK → ENCODING → DISPATCHING → ACKED → (join unknown)`, with expiry and generation guards.

Health ingress: `RECEIVED → REPLAY_CHECK → BINDING_CHECK → DECODE → MIRROR_COMMIT → OBSERVATION`; any failure aborts the batch.

## 11. Error taxonomy

| Code | Cause | Required behaviour |
|---|---|---|
| `PREF_UNKNOWN_VALUE` | Field absent/malformed | Treat as unknown; never default to false/zero |
| `PREF_STALE_BASELINE` | Pair/epoch/revision changed | Refuse the write |
| `PREF_WRITE_TIMEOUT` | No observation | UNKNOWN; do not claim applied |
| `PREF_SET_TOO_LARGE` | > 1023 names or > 64 KiB | Refuse |
| `MONOGRAM_INVALID` | Malformed UTF-16/emoji or failed validation | Refuse; do not truncate silently |
| `WIFI_UNSUPPORTED` | Not WPA2-PSK/CCMP | Skip |
| `WIFI_EXPIRED` | Archive older than 10 s or generation changed | Discard |
| `WIFI_NOT_JOINED` | ACK received, no join evidence | Report delivery only |
| `HEALTH_UNAUTHENTIC` | Peer/registry binding fails | Drop; no decode |
| `HEALTH_REPLAY` | Duplicate message | Acknowledge without re-applying |
| `HEALTH_DECODE` | Native decode failure | Abort batch; keep prior mirror |
| `HEALTH_STORE` | DB/key failure | Abort; never return empty |
| `FIND_UNAVAILABLE` | Phone effect permission/state | Report; do not imply sound played |

## 12. UNKNOWN

- **UNKNOWN:** the NPS value encodings for most keys beyond "binary plist", and whether two-way sync is enforced by the Watch.
- **UNKNOWN:** the exact pigment fullname space and how visibility vs availability differ on the wire.
- **UNKNOWN:** the Foundation normalisation rules for monogram text beyond the reference's behavioural description.
- **UNKNOWN:** the Wi-Fi V2 archive layout, supported cipher suites beyond WPA2/CCMP, and multi-network behaviour.
- **UNKNOWN:** the Health encryption scheme, key agreement, message types, and anchor semantics.
- **UNKNOWN:** the Health defaults dictionary's full key space and the clinical object model.
- **UNKNOWN:** the find-device payload for the Watch→phone direction and its error cases.
- **UNKNOWN:** whether time sync can shift the Watch clock, and what the Watch does with an implausible value.
- **UNKNOWN:** whether About fields 1–4 and 7 have stable meanings; the reference names only capacity and charging.

## 13. Acceptance criteria

```text
Given an observed baseline, when a supported setting is written,
then a new observation of the exact key shows the new value; a timeout is UNKNOWN, not applied.

Given a pigment change, then both complete sets are sent and a new mirror matches;
     a queue/ACK alone does not mark it applied.

Given a monogram longer than five UTF-16 units after normalisation, then it is refused, not truncated.

Given a supported Wi-Fi network, when provisioning reaches the trigger,
then one archive is dispatched, and a join is reported only with independent evidence.

Given a Health message from an unbound peer, then it is dropped before decode.

Given a Health storage failure, then the mirror keeps its prior state and the batch fails.

Given a Find Watch request, then a native response is reported as a claim and sound as physical proof.
```

## 14. Evidence

| Fact | Reference source |
|---|---|
| Settings keys/domains and unknown-vs-false rule | README §Watch settings |
| Pigment domain/keys/limits/baseline | README §Add Colors / pigment visibility |
| Monogram rules and confirmation | README §Monograms; `docs/WATCH_APP_MONOGRAM_EDITOR_73.md` |
| Wi-Fi trigger/limits/ACK meaning | README §Wi-Fi; `docs/BRIDGE_ARCHITECTURE.md`; `docs/live-20261006-optical-pairing/RESULT.md` "Automatic Wi-Fi during fresh setup" |
| Health ingress and limits | README §Health; `docs/live-20261005-sync/HEALTH-375-DEFAULTS-MIRROR-EVIDENCE.md` "Native evidence" and "Implementation boundaries" |
| Defaults dictionary rules | Same Health document, "Native evidence" table |
| Find devices | README §Find Watch / Find Phone; `docs/live-20261005-sync/PHONE-PING-350-COMPANION-EVIDENCE.md` |
| About request/response sizes and fields | `docs/live-20261005-sync/BATTERY-346-HARDWARE-EVIDENCE.md`; `docs/live-20261005-sync/NOTIFICATION-345-ACTION-CLAIMS-EVIDENCE.md` "Exact firmware battery research" |

**Must be confirmed on hardware:** every preference write's read-back on a second OS build; a real Wi-Fi join; Health native acceptance (anchors, grants) rather than mirror-only; and a second model for the About fields.
