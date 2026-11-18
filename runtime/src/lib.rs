//! Payload runtime.
//!
//! Loaded by the app; the only code that runs before privilege is obtained. It
//! matches the device, verifies the signed manifest, stages the artifacts, runs
//! the payload, and starts the agent with a one-time token
//! (docs/02-architecture.md section 2.2, docs/04-payload-system.md section 9).
//!
//! Status: skeleton. K2a decides whether this crate has a product.

pub mod manifest;
pub mod matcher;
pub mod stage;
