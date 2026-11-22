//! Open Crown protocol core.
//!
//! Pure logic: bytes and events in, actions out. No sockets, no files, no
//! Android APIs. Everything here must be testable with `cargo test` on the
//! host, which is what makes the golden-vector suite possible.
//!
//! Status: partial. Discovery inventory/selection rules, IDS route/session
//! rules, provisioning checkpoints/completion and face read-back rules are
//! implemented and host-tested. Wire codecs, pairing, L2CAP/ERTM, SMP,
//! NetworkRelay, NWSC and every feature service are still pending. No protocol
//! revision is claimed until a spec is verified on hardware.

pub mod bytes;
pub mod checkpoint;
pub mod discovery;
pub mod error;
pub mod faces;
pub mod health;
pub mod ids;
pub mod notifications;
pub mod session;
pub mod setup;
pub mod telephony;
pub mod transport;
pub mod wifi;
pub mod write;

/// Protocol revision this build understands.
///
/// Zero until the first spec is verified on hardware. The app refuses to claim
/// support for a version it has not seen (docs/07-protocol-research.md).
pub const PROTOCOL_VERSION: u32 = 0;
