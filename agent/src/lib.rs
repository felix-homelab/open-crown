//! Crown Agent library.
//!
//! The binary is thin; the policy and audit logic live here so they can be
//! unit-tested without root or a controller.

pub mod audit;
pub mod broker;
#[cfg(unix)]
pub mod ipc;
pub mod policy;
pub mod protocol;
