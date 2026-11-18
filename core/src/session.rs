//! Session state machine.
//!
//! The phases are the product's vocabulary, not an implementation detail. The
//! UI shows them separately so a partial success cannot look like a working
//! watch (docs/02-architecture.md section 7).

/// Where a session is in the flow.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Phase {
    Idle,
    Discovering,
    Selected,
    Privilege,
    Pairing,
    Securing,
    Activating,
    Syncing,
    Ready,
    Reconnecting,
}

impl Phase {
    /// The documented forward edges, plus a rollback to Idle from anywhere.
    ///
    /// A failure never jumps forward: the state machine returns to Idle and the
    /// reason is recorded, rather than leaving a half-advanced session.
    pub fn can_transition_to(self, next: Phase) -> bool {
        use Phase::*;

        if next == Idle {
            return self != Idle;
        }

        matches!(
            (self, next),
            (Idle, Discovering)
                | (Discovering, Selected)
                | (Selected, Privilege)
                | (Privilege, Pairing)
                | (Pairing, Securing)
                | (Securing, Activating)
                | (Activating, Syncing)
                | (Syncing, Ready)
                | (Ready, Reconnecting)
                | (Reconnecting, Ready)
        )
    }
}

#[cfg(test)]
mod tests {
    use super::Phase;

    const PATH: [Phase; 10] = [
        Phase::Idle,
        Phase::Discovering,
        Phase::Selected,
        Phase::Privilege,
        Phase::Pairing,
        Phase::Securing,
        Phase::Activating,
        Phase::Syncing,
        Phase::Ready,
        Phase::Reconnecting,
    ];

    #[test]
    fn happy_path_advances_one_step() {
        for pair in PATH.windows(2) {
            assert!(pair[0].can_transition_to(pair[1]), "{pair:?}");
        }
    }

    #[test]
    fn no_skipping() {
        assert!(!Phase::Idle.can_transition_to(Phase::Pairing));
        assert!(!Phase::Discovering.can_transition_to(Phase::Ready));
        assert!(!Phase::Securing.can_transition_to(Phase::Ready));
    }

    #[test]
    fn failure_rolls_back_to_idle() {
        for phase in PATH {
            assert_eq!(phase.can_transition_to(Phase::Idle), phase != Phase::Idle);
        }
    }

    #[test]
    fn reconnect_returns_to_ready() {
        assert!(Phase::Reconnecting.can_transition_to(Phase::Ready));
        assert!(!Phase::Ready.can_transition_to(Phase::Syncing));
    }
}
