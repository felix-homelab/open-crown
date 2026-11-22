//! Write-then-observe confirmation.
//!
//! A command that was sent is not a command that was applied. This type owns
//! the lifecycle for a single write: queued, sent, acknowledged by the agent,
//! then either observed on the watch or failed/expired. Only an observation
//! that matches the pair epoch and the target may produce [WriteState::Observed].
//!
//! The state model is docs/design/state-model.md section 4. The transport and
//! wire payloads are not part of this module.

use std::fmt;

/// Bound on a value this primitive will compare.
pub const MAX_VALUE_BYTES: usize = 4096;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WriteState {
    Queued,
    Sent,
    Acknowledged,
    Observed,
    Failed,
    Expired,
    Unconfirmed,
}

impl WriteState {
    pub fn is_terminal(self) -> bool {
        matches!(
            self,
            Self::Observed | Self::Failed | Self::Expired | Self::Unconfirmed
        )
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum WriteError {
    StaleEpoch,
    TargetMismatch,
    AlreadyTerminal,
    ObservationMismatch,
    Timeout,
    ValueTooLarge,
    InvalidState(&'static str),
}

impl fmt::Display for WriteError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let code = match self {
            Self::StaleEpoch => "WRITE_STALE_EPOCH",
            Self::TargetMismatch => "WRITE_TARGET_MISMATCH",
            Self::AlreadyTerminal => "WRITE_ALREADY_TERMINAL",
            Self::ObservationMismatch => "WRITE_OBSERVATION_MISMATCH",
            Self::Timeout => "WRITE_TIMEOUT",
            Self::ValueTooLarge => "WRITE_VALUE_TOO_LARGE",
            Self::InvalidState(_) => "WRITE_INVALID_STATE",
        };
        write!(f, "{code}")
    }
}

impl std::error::Error for WriteError {}

/// One outstanding write. It is deliberately not generic over a payload type:
/// the core compares bounded strings and the app decides how to render them.
#[derive(Debug, Clone)]
pub struct PendingWrite {
    id: u64,
    epoch: u64,
    target: String,
    requested: String,
    state: WriteState,
    deadline_ms: u64,
}

impl PendingWrite {
    pub fn new(
        id: u64,
        epoch: u64,
        target: impl Into<String>,
        requested: impl Into<String>,
        now_ms: u64,
        timeout_ms: u64,
    ) -> Result<Self, WriteError> {
        if id == 0 {
            return Err(WriteError::InvalidState("zero id"));
        }
        let target = target.into();
        let requested = requested.into();
        if target.is_empty() || requested.len() > MAX_VALUE_BYTES {
            return Err(WriteError::ValueTooLarge);
        }
        Ok(Self {
            id,
            epoch,
            target,
            requested,
            state: WriteState::Queued,
            deadline_ms: now_ms.saturating_add(timeout_ms.max(1)),
        })
    }

    pub fn id(&self) -> u64 {
        self.id
    }

    pub fn epoch(&self) -> u64 {
        self.epoch
    }

    pub fn target(&self) -> &str {
        &self.target
    }

    pub fn requested(&self) -> &str {
        &self.requested
    }

    pub fn state(&self) -> WriteState {
        self.state
    }

    pub fn deadline_ms(&self) -> u64 {
        self.deadline_ms
    }

    pub fn sent(&mut self) -> Result<(), WriteError> {
        self.expect(WriteState::Queued, "sent")?;
        self.state = WriteState::Sent;
        Ok(())
    }

    /// An agent ACK is a receipt, not an effect.
    pub fn acknowledged(&mut self) -> Result<(), WriteError> {
        if !matches!(self.state, WriteState::Queued | WriteState::Sent) {
            return Err(WriteError::InvalidState("acknowledged"));
        }
        self.state = WriteState::Acknowledged;
        Ok(())
    }

    /// The only path to APPLIED. A mismatched observation fails the write; a
    /// stale epoch or wrong target is dropped without changing state.
    pub fn observe(
        &mut self,
        now_ms: u64,
        epoch: u64,
        target: &str,
        observed: &str,
    ) -> Result<WriteState, WriteError> {
        if self.state.is_terminal() {
            return Err(WriteError::AlreadyTerminal);
        }
        if epoch != self.epoch {
            return Err(WriteError::StaleEpoch);
        }
        if target != self.target {
            return Err(WriteError::TargetMismatch);
        }
        if now_ms > self.deadline_ms {
            self.state = WriteState::Expired;
            return Err(WriteError::Timeout);
        }
        if observed == self.requested {
            self.state = WriteState::Observed;
            Ok(WriteState::Observed)
        } else {
            self.state = WriteState::Failed;
            Err(WriteError::ObservationMismatch)
        }
    }

    pub fn reject(&mut self) -> Result<(), WriteError> {
        if self.state.is_terminal() {
            return Err(WriteError::AlreadyTerminal);
        }
        self.state = WriteState::Failed;
        Ok(())
    }

    /// No observation inside the deadline. The value is unavailable, not old
    /// and not applied.
    pub fn expire_if_due(&mut self, now_ms: u64) -> Result<(), WriteError> {
        if self.state.is_terminal() {
            return Ok(());
        }
        if now_ms > self.deadline_ms {
            self.state = WriteState::Expired;
            return Err(WriteError::Timeout);
        }
        Ok(())
    }

    fn expect(&self, expected: WriteState, operation: &'static str) -> Result<(), WriteError> {
        if self.state != expected {
            return Err(WriteError::InvalidState(operation));
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn pending() -> PendingWrite {
        PendingWrite::new(1, 7, "brightness", "0.6", 1_000, 10_000).unwrap()
    }

    #[test]
    fn ack_is_not_observed() {
        let mut write = pending();
        write.sent().unwrap();
        write.acknowledged().unwrap();
        assert_eq!(write.state(), WriteState::Acknowledged);
        assert!(!write.state().is_terminal());
        let state = write.observe(2_000, 7, "brightness", "0.6").unwrap();
        assert_eq!(state, WriteState::Observed);
        assert!(write.state().is_terminal());
    }

    #[test]
    fn stale_epoch_and_wrong_target_are_dropped() {
        let mut write = pending();
        assert!(matches!(
            write.observe(2_000, 8, "brightness", "0.6"),
            Err(WriteError::StaleEpoch)
        ));
        assert!(matches!(
            write.observe(2_000, 7, "wrist", "0.6"),
            Err(WriteError::TargetMismatch)
        ));
        assert_eq!(write.state(), WriteState::Queued);
    }

    #[test]
    fn a_mismatched_observation_fails_the_write() {
        let mut write = pending();
        write.sent().unwrap();
        assert!(matches!(
            write.observe(2_000, 7, "brightness", "0.1"),
            Err(WriteError::ObservationMismatch)
        ));
        assert_eq!(write.state(), WriteState::Failed);
        assert!(write.state().is_terminal());
    }

    #[test]
    fn timeout_never_applies_and_late_observation_is_rejected() {
        let mut write = pending();
        write.sent().unwrap();
        assert!(matches!(
            write.expire_if_due(11_001),
            Err(WriteError::Timeout)
        ));
        assert_eq!(write.state(), WriteState::Expired);
        assert!(matches!(
            write.observe(11_002, 7, "brightness", "0.6"),
            Err(WriteError::AlreadyTerminal)
        ));
    }

    #[test]
    fn expired_observation_does_not_apply() {
        let mut write = pending();
        assert!(matches!(
            write.observe(11_001, 7, "brightness", "0.6"),
            Err(WriteError::Timeout)
        ));
        assert_eq!(write.state(), WriteState::Expired);
    }

    #[test]
    fn terminal_states_cannot_move() {
        let mut write = pending();
        write.reject().unwrap();
        assert!(matches!(write.sent(), Err(WriteError::InvalidState(_))));
        assert!(matches!(
            write.acknowledged(),
            Err(WriteError::InvalidState(_))
        ));
        assert!(matches!(write.reject(), Err(WriteError::AlreadyTerminal)));
    }

    #[test]
    fn oversized_values_are_refused_at_construction() {
        assert!(matches!(
            PendingWrite::new(1, 7, "x", "a".repeat(MAX_VALUE_BYTES + 1), 0, 10),
            Err(WriteError::ValueTooLarge)
        ));
        assert!(matches!(
            PendingWrite::new(0, 7, "x", "y", 0, 10),
            Err(WriteError::InvalidState(_))
        ));
    }
}
