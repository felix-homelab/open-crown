# 04 — IDS framing, routes, sessions and reconnect

**Status:** draft. Route names and protection classes are established; message envelopes are described behaviourally only. No hardware verification by Open Crown.
**Spec area:** `protocol/ids/`, `protocol/link/` (not started).

## 1. Purpose

Define the authenticated application layer above the normal link: how a Channel is selected, how messages are correlated, which protection class a route uses, and when a Session may be re-established without replaying provisioning.

## 2. Preconditions

- Normal link registered; NetworkRelay prelude negotiated; Class-D then Class-C protection established (see 02/03).
- IDS control stream up (Hello exchanged, service map installed) before any application Channel is used.
- A single Session epoch for the connection; the Crown has been told the epoch and readiness state.

## 3. Frames

The reference does not publish the IDS envelope byte layout. What it does establish:

| Element | Meaning | Encoding |
|---|---|---|
| Topic | Channel name; the routing key | Apple reverse-DNS string; `alloy.X` abbreviates `com.apple.private.alloy.X` |
| Protection class | Class-C or Class-D route | UNKNOWN wire encoding; class is per route, not per app |
| Message identifier | Correlation of ACK/response to request | UUID (canonical form) |
| Response identifier | Points a response at its request | UUID |
| Priority | Reference states all listed application routes are "urgent" | UNKNOWN numeric encoding |
| Payload | Protobuf, binary plist, resource bytes, or face payload | Per Channel |
| ACK | Transport-level acknowledgement of receipt | Separate from a semantic response |

```text
inner IPv6
  └─ TCP
      └─ NWSC service connector
          ├─ IDS control stream  (Hello, service map)
          └─ IDS application stream (topic + message id + payload)
```

**A topic is not a TCP connection.** Services with the same priority and protection class ride one shared connector. The class letter describes how a route is protected; it does not name an app or a radio channel.

## 4. Routes

All of the following are named as application routes, all with urgent priority. Being on the list says nothing about whether the feature works.

| Topic suffix | Class | Claimed purpose | Our note |
|---|---|---|---|
| `alloy.bluetoothregistry` | D | Registry properties/lifecycle | Provisioning prerequisite |
| `alloy.bluetoothregistryclassc` | C | Class-C registry exchange | Provisioning prerequisite |
| `alloy.pbbridge` | C | Provisioning/activation/Buddy | See 03 |
| `alloy.preferencessync.pairedsync` | C | Paired preferences and sync state | **Must not inherit Class-D** |
| `alloy.preferencessync` | D | Ordinary preferences (NPS) | See 07 |
| `alloy.bulletindistributor` | C | Notifications and actions | See 06 |
| `alloy.bulletindistributor.settings` | D | Notification policy | See 06 |
| `alloy.findmylocaldevice` | D | Find Watch / Find Phone | See 07 |
| `alloy.health.sync.classc` | C | Health synchronisation | Encrypted ingress; see 07 |
| `alloy.telephony` | C | Telephony | Research codecs only; see 08 |
| `alloy.timesync` | D | Time exchange | See 07 |
| `alloy.timezonesync` | D | Time zone exchange | See 07 |
| `alloy.clockface.sync` | D | Face collection and delta | See 05 |
| `alloy.systemsettings` | D | About/diagnostics | See 07 |
| `alloy.wifi.networksync` | C | Wi-Fi archive | See 07 |
| `alloy.sysdiagnose` | C | Diagnostic archives | Not a product feature |
| `alloy.idscredentials` | D | Mutual device credentials | Commands 11/12; separate from login/activation |

Separate path: StateReplicator over NetworkRelay/QUIC, registered as its own service (`com.apple.private.alloy.replicator`), is not an IDS topic route. See 05.

## 5. Correlation and session rules

1. Replies must match topic, message identifier, Session epoch and peer; face deltas additionally match session and batch index.
2. An ACK proves receipt at the transport layer only. A semantic response is separate.
3. A reply that belongs to an older Session must not be allowed to change current state.
4. Unknown topics are rejected; the reference does not define a generic forwarding fallback.
5. One serial transport owner; no second owner during teardown.
6. Credentials from `alloy.idscredentials` are device credentials, not an account or activation state.

## 6. Reconnect

| Rule | Detail |
|---|---|
| Eligibility | Same saved pair that was activated; setup transport safely stopped; incomplete paired commit may forbid reconnect |
| Policy gate | IDS policy rejection prohibits reconnect; a timer cannot override it |
| Backoff | 15 s → 30 s → 60 s, only when retries are allowed |
| Replay | No provisioning, activation, Buddy or re-pair replay on an operational reconnect |
| Epoch | Every reconnect is a new epoch; earlier commands are invalidated |
| Teardown | A second flow cannot acquire the controller while the first is stopping |

## 7. State machine

```text
NORMAL_REGISTERED ─▶ RELAY_PRELUDE ─▶ CLASS_D ─▶ CLASS_C
CLASS_C ─▶ IDS_CONTROL_HELLO ─▶ SERVICE_MAP ─▶ IDS_READY
IDS_READY ─▶ OPERATIONAL (commands/responses)
OPERATIONAL ─ link loss ─▶ CLOSED(epoch++)
CLOSED ─ eligible pair + policy ─▶ BACKOFF(15/30/60) ─▶ RECONNECTING ─▶ NORMAL_REGISTERED
CLOSED ─ policy refusal / incomplete commit ─▶ INELIGIBLE(terminal until user action)
```

## 8. Error taxonomy

| Code | Cause | Required behaviour |
|---|---|---|
| `IDS_CONTROL_TIMEOUT` | No control Hello within budget | Close; report |
| `IDS_UNKNOWN_TOPIC` | Route not in the service map | Reject; do not guess a class |
| `IDS_CLASS_MISMATCH` | Message offered on the wrong protection class | Reject |
| `IDS_CORRELATION_MISS` | Reply does not match topic/message/epoch/peer | Drop; log ids only |
| `IDS_LATE_EPOCH` | Reply from an earlier Session | Drop; never restore state |
| `IDS_POLICY_REFUSED` | Device refuses the session | Stop retrying |
| `IDS_RECONNECT_BACKOFF` | Transient failure | 15/30/60 s, bounded |
| `IDS_TEARDOWN_TIMEOUT` | Cleanup did not finish | Report; block a new owner until clean |
| `IDS_QUEUE_FULL` | Bounded command queue saturated | Reject new commands; keep existing |

## 9. UNKNOWN

- **UNKNOWN:** IDS envelope encoding (protobuf vs binary plist), its field numbers, and whether it is length-prefixed.
- **UNKNOWN:** IDS control Hello contents, version negotiation, and the service-map encoding.
- **UNKNOWN:** NWSC connector parameters, TCP ports, and inner IPv6 addressing.
- **UNKNOWN:** whether Class-C and Class-D are distinct SAs, distinct ESP selectors, or both.
- **UNKNOWN:** keepalive, idle timeout, and maximum message size.
- **UNKNOWN:** how many application streams exist per class, and whether topics are multiplexed or per-stream.
- **UNKNOWN:** whether reconnect can reuse the bond without any application-level handshake.
- **UNKNOWN:** the meaning of "IDS policy rejection" and what triggers it.
- **UNKNOWN:** how the reference's 15/30/60 backoff was chosen; it is a product policy, not a protocol fact.

## 10. Acceptance criteria

```text
Given a bonded, activated pair, when an operational connection starts,
then NetworkRelay, Class-D, Class-C, control Hello and the service map complete in order,
     and only then is a Channel used.

Given a message on an unknown topic, then it is rejected and no other route is attempted.

Given an ACK from the current epoch, then the Crown records a receipt, not an effect.

Given a reply arriving after a reconnect, then it is dropped and the UI does not change.

Given a lost link, when retries are allowed, then delays are 15/30/60 s and every attempt gets a new epoch.

Given an ineligible pair (incomplete commit or policy refusal), then no automatic reconnect occurs.
```

## 11. Evidence

| Fact | Reference source |
|---|---|
| Layering, topic vs connection, class semantics | README §Protocol stack ("IDS application traffic over the normal link") |
| Route table, `alloy.` expansion, urgent priority | README §IDS routes and handlers |
| Pairedsync is Class-C | README §IDS routes, closing note |
| Credentials commands 11/12 | README §IDS routes |
| Reconnect rules and backoff | README §Operational reconnection |
| Epoch/ownership invariants | README §Keys, storage, and ownership; `docs/BRIDGE_ARCHITECTURE.md` "Invariants" |
| Replicator is a separate path | README §Modern StateReplicator and QUIC |

**Must be confirmed on hardware:** the IDS control Hello and service-map bytes; correct class selection per route on a second OS build; reconnect stability over 20 cycles and 24 h (the reference only claims short cycles); and behaviour under phone Wi-Fi/cellular change, which the reference leaves unverified.
