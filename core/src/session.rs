//! Session state machine.
//!
//! The phases are the product's vocabulary, not an implementation detail. The
//! UI shows them separately so a partial success cannot look like a working
//! watch.

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
