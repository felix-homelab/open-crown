//! Durable, ordered progress markers.
//!
//! A checkpoint records what was observed, not what was hoped for. Resuming
//! never replays a non-idempotent step (docs/02-architecture.md section 7).

use crate::error::Error;

/// One committed step of a session.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Checkpoint {
    /// Strictly increasing within a pair record.
    pub ordinal: u32,
    /// What justified the transition: a capture hash, an observation, or an
    /// explicit owner confirmation.
    pub evidence: String,
    /// The protocol revision the step was verified against.
    pub protocol_version: u32,
}

/// Append-only log that refuses to go backwards.
#[derive(Debug, Default)]
pub struct CheckpointLog {
    entries: Vec<Checkpoint>,
}

impl CheckpointLog {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn push(&mut self, checkpoint: Checkpoint) -> Result<(), Error> {
        match self.entries.last() {
            None if checkpoint.ordinal != 1 => {
                return Err(Error::InvalidState("first checkpoint must be ordinal 1"));
            }
            Some(last) if checkpoint.ordinal <= last.ordinal => {
                return Err(Error::InvalidState("checkpoint ordinals must increase"));
            }
            _ => {}
        }
        self.entries.push(checkpoint);
        Ok(())
    }

    pub fn last(&self) -> Option<&Checkpoint> {
        self.entries.last()
    }

    pub fn entries(&self) -> &[Checkpoint] {
        &self.entries
    }

    pub fn len(&self) -> usize {
        self.entries.len()
    }

    pub fn is_empty(&self) -> bool {
        self.entries.is_empty()
    }
}

#[cfg(test)]
mod tests {
    use super::{Checkpoint, CheckpointLog};

    fn checkpoint(ordinal: u32) -> Checkpoint {
        Checkpoint {
            ordinal,
            evidence: format!("capture:{ordinal}"),
            protocol_version: 0,
        }
    }

    #[test]
    fn appends_in_order() {
        let mut log = CheckpointLog::new();
        log.push(checkpoint(1)).unwrap();
        log.push(checkpoint(2)).unwrap();
        assert_eq!(log.len(), 2);
        assert_eq!(log.last().unwrap().ordinal, 2);
    }

    #[test]
    fn first_ordinal_must_be_one() {
        let mut log = CheckpointLog::new();
        assert!(log.push(checkpoint(2)).is_err());
        assert!(log.is_empty());
    }

    #[test]
    fn ordinals_must_increase() {
        let mut log = CheckpointLog::new();
        log.push(checkpoint(1)).unwrap();
        assert!(log.push(checkpoint(1)).is_err());
        assert!(log.push(checkpoint(0)).is_err());
        assert_eq!(log.len(), 1);
    }
}
