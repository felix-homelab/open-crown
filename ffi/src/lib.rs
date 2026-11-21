//! UniFFI surface.
//!
//! The generated Kotlin is committed and diffed in CI (docs/05-stack.md).
//! Regenerate it with tools/ffi-generate.sh.

uniffi::setup_scaffolding!();

/// Errors the Kotlin side can catch. The first one is the honest one: the
/// protocol has not been verified on hardware yet.
#[derive(Debug, uniffi::Error)]
pub enum FfiError {
    NotReady { reason: String },
}

impl std::fmt::Display for FfiError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::NotReady { reason } => write!(f, "not ready: {reason}"),
        }
    }
}

impl std::error::Error for FfiError {}

/// The protocol revision this build understands.
#[uniffi::export]
pub fn protocol_version() -> Result<u32, FfiError> {
    Ok(opencrown_core::PROTOCOL_VERSION)
}

/// A real error path, so the Kotlin binding has an exception type to catch.
#[uniffi::export]
pub fn require_verified_protocol(verified: bool) -> Result<u32, FfiError> {
    if verified {
        Ok(opencrown_core::PROTOCOL_VERSION)
    } else {
        Err(FfiError::NotReady {
            reason: "protocol has not been verified on hardware".to_string(),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_is_protocol_version() {
        assert_eq!(
            protocol_version().unwrap(),
            opencrown_core::PROTOCOL_VERSION
        );
    }

    #[test]
    fn unverified_is_an_error() {
        assert!(matches!(
            require_verified_protocol(false),
            Err(FfiError::NotReady { .. })
        ));
        assert!(require_verified_protocol(true).is_ok());
    }
}
