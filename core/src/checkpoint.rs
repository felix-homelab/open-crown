//! Durable, ordered progress markers.
//!
//! A checkpoint records what was observed, not what was hoped for. Resuming
//! never replays a non-idempotent step (docs/02-architecture.md section 7).

/// One committed step of a session.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Checkpoint {
    /// Strictly increasing within a pair record.
    pub ordinal: u32,
    /// What justified the transition. A capture hash, an observation, or an
    /// explicit owner confirmation.
    pub evidence: String,
    /// The protocol revision the step was verified against.
    pub protocol_version: u32,
}
