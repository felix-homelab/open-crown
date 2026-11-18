//! Errors shared by the core.
//!
//! Protocol drift is a first-class error, not a parse crash
//! (docs/02-architecture.md section 10).

use std::fmt;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Error {
    /// A message did not match its spec.
    Malformed(&'static str),
    /// The step exists in the design but not in the code yet.
    Unimplemented(&'static str),
    /// The state machine is not in a valid state for this action.
    InvalidState(&'static str),
    /// The transport refused or lost the link.
    Transport(&'static str),
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Malformed(what) => write!(f, "malformed: {what}"),
            Self::Unimplemented(what) => write!(f, "unimplemented: {what}"),
            Self::InvalidState(what) => write!(f, "invalid state: {what}"),
            Self::Transport(what) => write!(f, "transport: {what}"),
        }
    }
}

impl std::error::Error for Error {}
