//! Notification claims and revisions.
//!
//! Bulletin payload encoding is **UNKNOWN** (docs/cleanroom/spec/06-notifications.md
//! section 8). This module implements the claim rules the spec does establish:
//! revision-bound actions, commit-before-effect, at-most-once, tombstones that
//! cannot be resurrected, and no automatic retry after an unknown outcome.
//! Reply text is returned to the caller for immediate use and is not stored in
//! the journal.

use std::collections::BTreeMap;
use std::fmt;

/// Current claim-journal schema. A newer schema is refused, never guessed.
pub const CURRENT_JOURNAL_SCHEMA: u32 = 2;
/// Bound on notification content accepted for mirroring.
pub const MAX_CONTENT_BYTES: usize = 4096;
/// Bound on the in-memory journal, so a noisy app cannot grow it forever.
pub const MAX_CLAIMS: usize = 256;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum NotificationError {
    StaleRevision,
    UnknownRecord,
    ClaimConflict,
    EffectRejected,
    EffectUnknown,
    SchemaUnsupported,
    PayloadTooLarge,
    InvalidState(&'static str),
    JournalFull,
}

impl fmt::Display for NotificationError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let code = match self {
            Self::StaleRevision => "NOTIFY_STALE_REVISION",
            Self::UnknownRecord => "NOTIFY_UNKNOWN_RECORD",
            Self::ClaimConflict => "NOTIFY_CLAIM_CONFLICT",
            Self::EffectRejected => "NOTIFY_EFFECT_REJECTED",
            Self::EffectUnknown => "NOTIFY_EFFECT_UNKNOWN",
            Self::SchemaUnsupported => "NOTIFY_SCHEMA_UNSUPPORTED",
            Self::PayloadTooLarge => "NOTIFY_PAYLOAD_TOO_LARGE",
            Self::InvalidState(_) => "NOTIFY_INVALID_STATE",
            Self::JournalFull => "NOTIFY_QUEUE_FULL",
        };
        write!(f, "{code}")
    }
}

impl std::error::Error for NotificationError {}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RecordKey {
    pub publisher: String,
    pub record: String,
}

impl RecordKey {
    pub fn new(publisher: impl Into<String>, record: impl Into<String>) -> Self {
        Self {
            publisher: publisher.into(),
            record: record.into(),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ClaimKind {
    Action,
    Reply,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ClaimState {
    /// Persisted before the Android effect. This is the only state in which an
    /// effect may be attempted.
    Claimed,
    EffectDone,
    EffectRejected,
    /// The process died or Android returned an ambiguous result. No retry.
    EffectUnknown,
}

impl ClaimState {
    pub fn is_terminal(self) -> bool {
        !matches!(self, Self::Claimed)
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Claim {
    pub id: u64,
    pub key: RecordKey,
    pub section: String,
    pub revision: u32,
    pub kind: ClaimKind,
    pub action_id: Option<String>,
    pub state: ClaimState,
}

/// The text is returned for the immediate RemoteInput call; the journal does
/// not keep it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ReplyAttempt {
    pub claim_id: u64,
    pub text: String,
}

#[derive(Debug, Clone)]
struct Record {
    section: String,
    revision: u32,
    tombstoned: bool,
}

/// In-memory claim journal model. Persistence and encryption belong to the app
/// store; this type decides identity, revision and at-most-once behaviour.
#[derive(Debug)]
pub struct ClaimJournal {
    schema: u32,
    records: BTreeMap<(String, String), Record>,
    claims: BTreeMap<u64, Claim>,
    next_id: u64,
}

impl ClaimJournal {
    pub fn new(schema: u32) -> Result<Self, NotificationError> {
        if schema == 0 || schema > CURRENT_JOURNAL_SCHEMA {
            return Err(NotificationError::SchemaUnsupported);
        }
        Ok(Self {
            schema,
            records: BTreeMap::new(),
            claims: BTreeMap::new(),
            next_id: 1,
        })
    }

    pub fn schema(&self) -> u32 {
        self.schema
    }

    /// Publishes or updates a record. Returns its new revision. A removed
    /// record is not resurrected by a late update.
    pub fn publish(
        &mut self,
        publisher: &str,
        record: &str,
        section: &str,
        content_bytes: usize,
    ) -> Result<u32, NotificationError> {
        if publisher.is_empty() || record.is_empty() || section.is_empty() {
            return Err(NotificationError::InvalidState("empty identity"));
        }
        if content_bytes == 0 || content_bytes > MAX_CONTENT_BYTES {
            return Err(NotificationError::PayloadTooLarge);
        }
        let key = (publisher.to_string(), record.to_string());
        match self.records.get_mut(&key) {
            Some(existing) if existing.tombstoned => Err(NotificationError::UnknownRecord),
            Some(existing) => {
                existing.revision = existing.revision.wrapping_add(1).max(1);
                existing.section = section.to_string();
                Ok(existing.revision)
            }
            None => {
                self.records.insert(
                    key,
                    Record {
                        section: section.to_string(),
                        revision: 1,
                        tombstoned: false,
                    },
                );
                Ok(1)
            }
        }
    }

    /// Removal is a tombstone. A later publish must not resurrect it.
    pub fn remove(&mut self, publisher: &str, record: &str) -> Result<(), NotificationError> {
        let record = self
            .records
            .get_mut(&(publisher.to_string(), record.to_string()))
            .ok_or(NotificationError::UnknownRecord)?;
        record.tombstoned = true;
        Ok(())
    }

    pub fn claim_action(
        &mut self,
        key: &RecordKey,
        section: &str,
        revision: u32,
        action_id: &str,
    ) -> Result<u64, NotificationError> {
        if action_id.is_empty() {
            return Err(NotificationError::InvalidState("empty action id"));
        }
        self.validate(&key.publisher, &key.record, section, revision)?;
        if self.claims.values().any(|claim| {
            claim.key == *key
                && claim.revision == revision
                && claim.kind == ClaimKind::Action
                && claim.action_id.as_deref() == Some(action_id)
        }) {
            return Err(NotificationError::ClaimConflict);
        }
        self.insert_claim(key, section, revision, ClaimKind::Action, Some(action_id))
    }

    /// One reply is allowed per revision. New content starts a new reply state.
    pub fn claim_reply(
        &mut self,
        key: &RecordKey,
        section: &str,
        revision: u32,
        text: &str,
    ) -> Result<ReplyAttempt, NotificationError> {
        let clean = text.trim();
        if clean.is_empty() || clean.len() > MAX_CONTENT_BYTES {
            return Err(NotificationError::PayloadTooLarge);
        }
        self.validate(&key.publisher, &key.record, section, revision)?;
        if self.claims.values().any(|claim| {
            claim.key == *key && claim.revision == revision && claim.kind == ClaimKind::Reply
        }) {
            return Err(NotificationError::ClaimConflict);
        }
        let claim_id = self.insert_claim(key, section, revision, ClaimKind::Reply, None)?;
        Ok(ReplyAttempt {
            claim_id,
            text: clean.to_string(),
        })
    }

    /// Records the outcome after the Android effect. A terminal claim cannot be
    /// retried; a second attempt is an error.
    pub fn effect_done(&mut self, claim_id: u64) -> Result<(), NotificationError> {
        self.set_effect(claim_id, ClaimState::EffectDone)
    }

    pub fn effect_rejected(&mut self, claim_id: u64) -> Result<(), NotificationError> {
        self.set_effect(claim_id, ClaimState::EffectRejected)
    }

    pub fn effect_unknown(&mut self, claim_id: u64) -> Result<(), NotificationError> {
        self.set_effect(claim_id, ClaimState::EffectUnknown)
    }

    pub fn claim(&self, claim_id: u64) -> Option<&Claim> {
        self.claims.get(&claim_id)
    }

    pub fn claim_count(&self) -> usize {
        self.claims.len()
    }

    fn validate(
        &self,
        publisher: &str,
        record: &str,
        section: &str,
        revision: u32,
    ) -> Result<(), NotificationError> {
        let stored = self
            .records
            .get(&(publisher.to_string(), record.to_string()))
            .ok_or(NotificationError::UnknownRecord)?;
        if stored.tombstoned {
            return Err(NotificationError::UnknownRecord);
        }
        if stored.revision != revision {
            return Err(NotificationError::StaleRevision);
        }
        if stored.section != section {
            return Err(NotificationError::UnknownRecord);
        }
        Ok(())
    }

    fn insert_claim(
        &mut self,
        key: &RecordKey,
        section: &str,
        revision: u32,
        kind: ClaimKind,
        action_id: Option<&str>,
    ) -> Result<u64, NotificationError> {
        if self.claims.len() >= MAX_CLAIMS {
            return Err(NotificationError::JournalFull);
        }
        let id = self.next_id;
        self.next_id = self.next_id.wrapping_add(1).max(1);
        self.claims.insert(
            id,
            Claim {
                id,
                key: key.clone(),
                section: section.to_string(),
                revision,
                kind,
                action_id: action_id.map(str::to_string),
                state: ClaimState::Claimed,
            },
        );
        Ok(id)
    }

    fn set_effect(&mut self, claim_id: u64, next: ClaimState) -> Result<(), NotificationError> {
        let claim = self
            .claims
            .get_mut(&claim_id)
            .ok_or(NotificationError::UnknownRecord)?;
        if claim.state.is_terminal() {
            return Err(match claim.state {
                ClaimState::EffectRejected => NotificationError::EffectRejected,
                ClaimState::EffectUnknown => NotificationError::EffectUnknown,
                _ => NotificationError::InvalidState("effect after completion"),
            });
        }
        claim.state = next;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn key() -> RecordKey {
        RecordKey::new("com.example.chat", "record-1")
    }

    #[test]
    fn a_revision_change_invalidates_old_claims() {
        let mut journal = ClaimJournal::new(CURRENT_JOURNAL_SCHEMA).unwrap();
        let revision = journal
            .publish("com.example.chat", "record-1", "chat", 100)
            .unwrap();
        let claim = journal
            .claim_action(&key(), "chat", revision, "reply")
            .unwrap();
        let next = journal
            .publish("com.example.chat", "record-1", "chat", 120)
            .unwrap();
        assert_eq!(next, revision + 1);
        assert!(matches!(
            journal.claim_action(&key(), "chat", revision, "reply"),
            Err(NotificationError::StaleRevision)
        ));
        assert_eq!(journal.claim(claim).unwrap().revision, revision);
    }

    #[test]
    fn duplicate_action_is_refused() {
        let mut journal = ClaimJournal::new(2).unwrap();
        let k = RecordKey::new("p", "r");
        let revision = journal.publish("p", "r", "s", 10).unwrap();
        journal.claim_action(&k, "s", revision, "open").unwrap();
        assert!(matches!(
            journal.claim_action(&k, "s", revision, "open"),
            Err(NotificationError::ClaimConflict)
        ));
    }

    #[test]
    fn reply_is_once_per_revision_and_text_is_not_journaled() {
        let mut journal = ClaimJournal::new(2).unwrap();
        let revision = journal
            .publish("com.example.chat", "record-1", "chat", 10)
            .unwrap();
        let attempt = journal
            .claim_reply(&key(), "chat", revision, "hello")
            .unwrap();
        assert_eq!(attempt.text, "hello");
        let claim = journal.claim(attempt.claim_id).unwrap();
        assert_eq!(claim.kind, ClaimKind::Reply);
        assert!(claim.action_id.is_none());
        assert!(matches!(
            journal.claim_reply(&key(), "chat", revision, "again"),
            Err(NotificationError::ClaimConflict)
        ));
        let next = journal
            .publish("com.example.chat", "record-1", "chat", 11)
            .unwrap();
        assert!(journal.claim_reply(&key(), "chat", next, "new").is_ok());
    }

    #[test]
    fn tombstone_cannot_be_resurrected() {
        let mut journal = ClaimJournal::new(2).unwrap();
        let k = RecordKey::new("p", "r");
        let revision = journal.publish("p", "r", "s", 10).unwrap();
        journal.remove("p", "r").unwrap();
        assert!(matches!(
            journal.claim_action(&k, "s", revision, "open"),
            Err(NotificationError::UnknownRecord)
        ));
        assert!(matches!(
            journal.publish("p", "r", "s", 10),
            Err(NotificationError::UnknownRecord)
        ));
    }

    #[test]
    fn unknown_record_and_section_are_rejected() {
        let mut journal = ClaimJournal::new(2).unwrap();
        assert!(matches!(
            journal.claim_action(&key(), "s", 1, "open"),
            Err(NotificationError::UnknownRecord)
        ));
        let revision = journal
            .publish("com.example.chat", "record-1", "chat", 10)
            .unwrap();
        assert!(matches!(
            journal.claim_action(&key(), "other", revision, "open"),
            Err(NotificationError::UnknownRecord)
        ));
    }

    #[test]
    fn terminal_effects_are_not_retried() {
        let mut journal = ClaimJournal::new(2).unwrap();
        let k = RecordKey::new("p", "r");
        let revision = journal.publish("p", "r", "s", 10).unwrap();
        let id = journal.claim_action(&k, "s", revision, "open").unwrap();
        journal.effect_unknown(id).unwrap();
        assert!(matches!(
            journal.effect_done(id),
            Err(NotificationError::EffectUnknown)
        ));
        assert_eq!(journal.claim(id).unwrap().state, ClaimState::EffectUnknown);
    }

    #[test]
    fn payload_bounds_are_enforced() {
        let mut journal = ClaimJournal::new(2).unwrap();
        assert!(matches!(
            journal.publish("p", "r", "s", MAX_CONTENT_BYTES + 1),
            Err(NotificationError::PayloadTooLarge)
        ));
        let revision = journal.publish("p", "r", "s", 10).unwrap();
        assert!(matches!(
            journal.claim_reply(
                &key(),
                "s",
                revision,
                "x".repeat(MAX_CONTENT_BYTES + 1).as_str()
            ),
            Err(NotificationError::PayloadTooLarge)
        ));
    }

    #[test]
    fn newer_schema_is_refused_but_older_is_readable() {
        assert!(matches!(
            ClaimJournal::new(CURRENT_JOURNAL_SCHEMA + 1),
            Err(NotificationError::SchemaUnsupported)
        ));
        assert!(ClaimJournal::new(1).is_ok());
        assert!(matches!(
            ClaimJournal::new(0),
            Err(NotificationError::SchemaUnsupported)
        ));
    }
}
