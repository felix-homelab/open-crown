//! Capability policy.
//!
//! The app is untrusted. Every request is checked against the capability set
//! from the payload manifest, the boot id, and the rate limits.
