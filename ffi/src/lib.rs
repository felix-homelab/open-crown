//! UniFFI surface.
//!
//! The generated Kotlin is committed and diffed in CI (docs/05-stack.md).
//! UniFFI itself is wired in Phase 1; until then this crate exposes one
//! function so the round-trip has something to call.

use opencrown_core::PROTOCOL_VERSION;

/// The protocol revision this build understands.
pub fn protocol_version() -> u32 {
    PROTOCOL_VERSION
}
