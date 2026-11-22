# 06 — Notifications

**Status:** draft. Transport, identity and claim rules are established behaviourally; the bulletin payload encoding is not. No hardware verification by Open Crown.
**Spec area:** `protocol/notifications/` (not started).

## 1. Purpose

Mirror phone notifications to the Watch, keep them revision-bound, let Watch actions and replies affect exactly the Android notification they came from, and never apply a stale action to newer content.

## 2. Preconditions

- Notification-listener access granted on Android; per-app policy may further filter.
- Operational Session on an activated pair (the notification route is Class-C).
- The phone's notification has a stable identity (publisher + record + section) and a revision.
- For reply: the Android notification exposes a RemoteInput target for that revision.

## 3. Data model

| Element | Meaning | Notes |
|---|---|---|
| Publisher id | Android package/category identity | Stable per app |
| Record id | One posted notification | Reused Android keys get a new publisher after removal |
| Section id / display name | Conversation/section grouping | Carried in the command |
| Revision | Content version of one record | A new revision invalidates earlier actions |
| Action id | One action on one revision | Must resolve to the current RemoteInput target |
| Reply token | Binds publisher + record + section + revision | Reference form: publisher-scoped revision token |
| Removal flag | Tombstone vs update | Removal survives content updates until the claim completes |
| Attachment/icon references | Optional rich content | Not established as working |

## 4. Messages

| Message | Direction | Purpose | Known fields |
|---|---|---|---|
| Publish/update | phone → Watch | Add or replace a card | title, message, section id, section display name; revision identity |
| Remove | phone → Watch | Dismiss a card | record identity, removal flag |
| Settings read | phone → Watch | Notification policy | UNKNOWN |
| Action result | Watch → phone | User tapped an action | action id, revision token, publisher/record/section |
| Reply result | Watch → phone | User sent a quick reply | reply token, text, target input |
| ACK / response | both | Transport and semantic confirmation | message id correlation |

Wire encoding: **UNKNOWN** (protobuf or binary plist). Do not infer field numbers from the reference's prose.

## 5. Claim rules (the core of this spec)

1. A reply/action claim is persisted **before** the Android effect (commit-before-effect).
2. A claim is valid only for the exact publisher/record/section/revision it was created for.
3. A reply may be attempted once per revision of the content; new content starts a new reply state.
4. A dismiss claim survives content updates until removal completes.
5. Duplicate claims survive a restart; at-most-once is the guarantee, not exactly-once.
6. If Android rejects or the process crashes after commit, the outcome is UNKNOWN and there is no automatic retry.
7. A PendingIntent send is not proof that the recipient accepted anything.
8. Reply text is not stored in the claim journal.
9. The journal is pair-encrypted, versioned; an older schema migrates known fields; an unknown future schema is rejected.

## 6. State machine

```text
POSTED(record, revision r)
  ├─ update → POSTED(record, revision r+1); claims for r are dead
  ├─ remove → TOMBSTONE → (delivered) → GONE
  └─ user action on r ─▶ CLAIM(r, action) ─▶ EFFECT_PENDING ─▶ EFFECT_DONE(r)
                                                      └─ unknown/crash ─▶ UNKNOWN (no retry)
Reply on r ─▶ CLAIM(r, reply) ─▶ RemoteInput(r) ─▶ SENT ─▶ (recipient acceptance: unproven)
```

## 7. Error taxonomy

| Code | Cause | Required behaviour |
|---|---|---|
| `NOTIFY_NO_ACCESS` | Listener permission absent | Report; do not mirror |
| `NOTIFY_STALE_REVISION` | Action refers to an older revision | Drop; do not fall back to current |
| `NOTIFY_UNKNOWN_RECORD` | Record no longer exists | Drop |
| `NOTIFY_CLAIM_CONFLICT` | A claim already exists for this revision/action | Do not duplicate the effect |
| `NOTIFY_EFFECT_REJECTED` | Android refused | Mark rejected; no retry |
| `NOTIFY_EFFECT_UNKNOWN` | Crash or ambiguity after commit | Mark unknown; surface it |
| `NOTIFY_SCHEMA_UNSUPPORTED` | Journal schema newer than the app | Refuse to read; do not guess |
| `NOTIFY_PAYLOAD_TOO_LARGE` | Content exceeds the transport budget | Truncate explicitly or refuse; never silently |

## 8. UNKNOWN

- **UNKNOWN:** the bulletin payload encoding and all field numbers.
- **UNKNOWN:** icon/attachment representation and whether images are pushed or fetched.
- **UNKNOWN:** sound, haptic, Focus, and grouping policy semantics.
- **UNKNOWN:** the notification-settings Channel's schema and whether it is bidirectional.
- **UNKNOWN:** how many actions a card can carry and how action identifiers are represented.
- **UNKNOWN:** whether the Watch ever originates a dismiss or reply without a prior phone publish.
- **UNKNOWN:** maximum content sizes and truncation rules.
- **UNKNOWN:** whether delivery is acknowledged per card or per batch, and what a missing ACK implies.

## 9. Acceptance criteria

```text
Given two apps and two chats, when both post, then two distinct cards appear with correct content.

Given a card at revision r, when its content updates to r+1,
then an action claim from r is rejected and no Android effect occurs.

Given a reply on revision r, then the text reaches the RemoteInput target of that revision and no other chat.

Given a removal, then the card disappears and a late update cannot resurrect it.

Given a process restart with a pending claim, then the claim is not replayed and the outcome is reported as unknown.

Given an app ACK, then the UI does not claim the user saw the card.
```

## 10. Evidence

| Fact | Reference source |
|---|---|
| Route, listener, revision-bound actions, claim rules | README §Notifications; `docs/live-20261005-sync/NOTIFICATION-345-ACTION-CLAIMS-EVIDENCE.md` "Implementation and limits" |
| Migration schema 1→2, tombstones, at-most-once | Same evidence document, "Hardware migration" |
| Limits and open items (attachments, sound, haptics, Focus) | README line 105; README line 732 |
| Command fields (title, message, section) | README §Operational commands (`sendNotification`) |

**Must be confirmed on hardware:** the bulletin payload; that a real Watch tap produces the claimed action/reply; attachment and icon behaviour; and whether any of it survives a Watch locked state. The reference's own evidence states that its run confirmed neither a callback from a real reply or dismiss nor visibility on the Watch.
