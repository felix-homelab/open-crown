//! Runtime errors.
//!
//! The caller maps these to a user-visible reason and an audit event. They are
//! intentionally coarse: a signature failure is not a parse error.

use std::fmt;

#[derive(Debug)]
pub enum RuntimeError {
    Io(std::io::Error),
    Json(serde_json::Error),
    /// The manifest is well-formed but breaks a rule.
    Invalid(String),
    /// No target in the manifest matches this device.
    TargetMismatch {
        device: String,
        targets: Vec<String>,
    },
    /// The signature does not cover this manifest.
    Signature(String),
    /// A declared artifact is missing from the bundle.
    Missing(String),
}

impl fmt::Display for RuntimeError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Io(err) => write!(f, "io: {err}"),
            Self::Json(err) => write!(f, "manifest json: {err}"),
            Self::Invalid(what) => write!(f, "invalid manifest: {what}"),
            Self::TargetMismatch { device, targets } => {
                write!(
                    f,
                    "target mismatch: {device} not in [{}]",
                    targets.join(", ")
                )
            }
            Self::Signature(what) => write!(f, "signature: {what}"),
            Self::Missing(what) => write!(f, "missing: {what}"),
        }
    }
}

impl std::error::Error for RuntimeError {}

impl From<std::io::Error> for RuntimeError {
    fn from(err: std::io::Error) -> Self {
        Self::Io(err)
    }
}

impl From<serde_json::Error> for RuntimeError {
    fn from(err: serde_json::Error) -> Self {
        Self::Json(err)
    }
}
