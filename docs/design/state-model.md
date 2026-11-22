# State model and observation rules

Status: normative for every screen.

The iPhone Watch experience can show a setting as changed the instant the user
taps because the phone owns the watch transport and receives a confirmation.
Open Crown does not own the transport yet, and may never own it on some devices.
The UI therefore cannot be written as if a write has happened. This document
defines the only sanctioned way to render watch state.

## 1. The rule

> No UI value is shown as live, applied, or connected without an observation
> that says so. Every other value is shown as unavailable with a reason.

Concretely:

- A Bluetooth advertisement is not a connection.
- A stored pairing record is not a live link.
- A socket connect is not an authenticated agent session.
- A command that was sent is not a command that was applied.
- A cached battery percentage is not current battery state.

## 2. Observation

Every value is an `Observation<T>`:

```kotlin
sealed interface Observation<out T> {
    data class Present<T>(val value: T, override val provenance: Provenance)
    data class Absent(val reason: AvailabilityReason, val detail: String, override val provenance: Provenance)
}
```

`Provenance` carries:

| Field | Meaning |
| --- | --- |
| `origin` | watch report, agent report, advertisement, Android platform, local store, user input, derived |
| `verification` | confirmed, reported, inferred, stale, unknown |
| `observedAtMillis` | when the value was actually observed |
| `pairId` / `epoch` | which watch identity the value belongs to |
| `note` | a short human explanation, safe to show in diagnostics |

### Verification levels

| Level | Use |
| --- | --- |
| `CONFIRMED` | the watch acknowledged the change and a later report shows the new value |
| `REPORTED` | one side reported it; no independent confirmation |
| `INFERRED` | computed from other observations; must say which |
| `STALE` | restored from disk or cache after a restart; needs a live refresh |
| `UNKNOWN` | no evidence |

## 3. Availability reasons

The reason is part of the product, not an error string to hide:

- `NOT_PAIRED` — no pairing record exists.
- `TRANSPORT_DOWN` — link lost; reconnect may restore it.
- `PRIVILEGE_UNAVAILABLE` — the per-boot privileged path is absent.
- `AGENT_UNAVAILABLE` — no listening agent.
- `BROKER_UNAVAILABLE` — the agent exists but the app cannot reach it.
- `PERMISSION_DENIED` — Android permission missing; offer the system prompt.
- `BLUETOOTH_OFF` — adapter disabled.
- `BLOCKED` — an entry in `docs/cleanroom/BLOCKERS.md` prevents reachability.
- `UNSUPPORTED` — this phone/watch combination is not supported.
- `NEVER_OBSERVED` — the channel exists but no report has arrived.
- `TIMEOUT` — an attempt elapsed.
- `ERROR` — a typed failure with a diagnostic detail.

The Evidence screen groups rows by these reasons. "Unavailable" without a reason
is a bug.

## 4. Command lifecycle

```text
QUEUED -> SENT -> ACKNOWLEDGED -> OBSERVED
                    \-> FAILED
                    \-> EXPIRED
```

- `QUEUED`: the app has accepted the request; nothing has left the process.
- `SENT`: bytes left the app toward the agent.
- `ACKNOWLEDGED`: the agent accepted the envelope. This is **not** a watch
  change.
- `OBSERVED`: a subsequent observation reports the target value. Only here may
  the UI use past tense ("Changed") or show the new value as current.
- `EXPIRED`: no observation arrived inside the deadline. Show the last known
  value and the command as unconfirmed. Do not retry silently.

## 5. Link states

```text
UNKNOWN
BLUETOOTH_OFF / NO_PERMISSION
IDLE <-> ADVERTISING -> CONNECTING -> CONNECTED_UNSECURED -> CONNECTED_SECURED
                                                \-> RECONNECTING -> FAILED
```

`transportReady` is true only for `CONNECTED_SECURED`. A paired record with no
live link is displayed as "Paired · Not connected", never as "Connected".

## 6. Pair/setup phases

`UNPAIRED -> DISCOVERED -> SELECTED -> PAIRING -> SECURING -> ACTIVATING ->
SYNCING -> READY`, with `RECONNECTING` and `ERROR` as recoverable side states.

Each phase transition must be persisted with the evidence that justified it. A
screen that draws a progress bar must derive it from these checkpoints, not from
elapsed time. If the transport is unavailable, the screen stops at the last
checkpoint and explains why.

## 7. Pair identity and epoch

`pairId` is local. `epoch` increments whenever the watch is reset or re-paired.
An observation whose `(pairId, epoch)` does not match the active record is
discarded and never rendered against the new watch. This is the guard against
showing a previous owner's face list or health sample on a re-paired watch.

## 8. UI consequences

1. **Home** shows the watch name only from a live report or a paired record;
   the record is labelled `Stale` until a live observation arrives.
2. **Discovery** lists advertisements as advertisements, with model and RSSI,
   and a "Pair" action that is enabled only when the transport can actually
   start pairing. Otherwise it is disabled with a reason.
3. **Setup** shows per-checkpoint rows, each with its observation. A checkpoint
   with no evidence is a paused row, not a spinner forever.
4. **Faces / Notifications / Health / Settings** show the real value or an
   unavailable state naming the reason. A blocked feature shows the blocker id
   and a link to the Evidence screen.
5. **Diagnostics** shows every probe result, including the ones that failed,
   with timestamps and the socket name. It never shows a redacted secret.
6. **Skeletons** are allowed only while a probe is running. A stable unavailable
   state must not look like a loading state.

## 9. What would count as verified

For any row to move to `verified`:

- the protocol exchange is implemented from a Lane A spec;
- tests cover the frame and the negative cases;
- the named watch and phone exercised it;
- the UI showed `OBSERVED`, not `ACKNOWLEDGED`;
- recovery after disconnect/reboot was exercised;
- the evidence file records command, result, date, and redaction.

Until then the registry says `implemented_unverified`, `blocked`, or
`unimplemented`. There is no fourth "basically works" state.
