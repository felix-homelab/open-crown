//! UniFFI surface.
//!
//! The generated Kotlin is committed and diffed in CI (docs/05-stack.md).
//! Regenerate it with tools/ffi-generate.sh.
//!
//! Two things cross this boundary: the protocol revision, and the runtime's
//! prepare() result. Errors become catchable Kotlin exceptions.

use std::path::Path;

uniffi::setup_scaffolding!();

#[derive(Debug, uniffi::Error)]
pub enum FfiError {
    NotReady { reason: String },
    Payload { reason: String },
}

impl std::fmt::Display for FfiError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::NotReady { reason } => write!(f, "not ready: {reason}"),
            Self::Payload { reason } => write!(f, "payload: {reason}"),
        }
    }
}

impl std::error::Error for FfiError {}

/// A verified, staged payload. The caller launches entry or agent and must
/// present the token.
#[derive(Debug, uniffi::Record)]
pub struct PreparedBundle {
    pub id: String,
    pub version: String,
    pub entry_path: String,
    pub agent_path: String,
    pub token: String,
    pub manifest_digest: String,
}

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

/// Verify, check revocation, stage, and mint a token for a payload bundle.
#[uniffi::export]
#[allow(clippy::too_many_arguments)]
pub fn prepare_payload(
    bundle_dir: String,
    work_dir: String,
    model: String,
    kernel_release: String,
    abi: String,
    android_sdk: u32,
    build_id: String,
    signature: Vec<u8>,
    public_key: Vec<u8>,
    revocations_json: String,
) -> Result<PreparedBundle, FfiError> {
    let device = opencrown_runtime::matcher::DeviceFingerprint {
        model,
        kernel_release,
        abi,
        android_sdk,
        build_id,
    };

    let revocations = if revocations_json.trim().is_empty() {
        opencrown_runtime::revocation::RevocationList::empty()
    } else {
        opencrown_runtime::revocation::RevocationList::parse(revocations_json.as_bytes()).map_err(
            |err| FfiError::Payload {
                reason: err.to_string(),
            },
        )?
    };

    let prepared = opencrown_runtime::prepare(
        Path::new(&bundle_dir),
        Path::new(&work_dir),
        &device,
        &signature,
        &public_key,
        &revocations,
    )
    .map_err(|err| FfiError::Payload {
        reason: err.to_string(),
    })?;

    let digest = opencrown_runtime::manifest::digest(&prepared.manifest).map_err(|err| {
        FfiError::Payload {
            reason: err.to_string(),
        }
    })?;

    Ok(PreparedBundle {
        id: prepared.manifest.id.clone(),
        version: prepared.manifest.version.clone(),
        entry_path: prepared.staged.entry.to_string_lossy().into_owned(),
        agent_path: prepared.staged.agent.to_string_lossy().into_owned(),
        token: prepared.token.expose().to_string(),
        manifest_digest: opencrown_runtime::hex::encode(&digest),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;

    fn example_bundle() -> PathBuf {
        Path::new(env!("CARGO_MANIFEST_DIR")).join("../catalog/examples/signed")
    }

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
    }

    #[test]
    fn prepares_the_example_bundle() {
        let bundle = example_bundle();
        let signature = std::fs::read(bundle.join("manifest.sig")).unwrap();
        let public_hex = std::fs::read_to_string(bundle.join("public_key.hex")).unwrap();
        let public = opencrown_runtime::hex::decode(public_hex.trim()).unwrap();
        let work = std::env::temp_dir().join(format!("opencrown-ffi-{}", std::process::id()));

        let prepared = prepare_payload(
            bundle.to_string_lossy().into_owned(),
            work.to_string_lossy().into_owned(),
            "EXAMPLE-1".to_string(),
            "example-6.1.0".to_string(),
            "arm64-v8a".to_string(),
            36,
            "EXAMPLE".to_string(),
            signature,
            public,
            String::new(),
        )
        .unwrap();

        assert_eq!(prepared.id, "org.opencrown.payload.example");
        assert_eq!(prepared.token.len(), 64);
        assert!(Path::new(&prepared.entry_path).exists());
        let _ = std::fs::remove_dir_all(&work);
    }

    #[test]
    fn revoked_bundle_is_refused() {
        let bundle = example_bundle();
        let signature = std::fs::read(bundle.join("manifest.sig")).unwrap();
        let public_hex = std::fs::read_to_string(bundle.join("public_key.hex")).unwrap();
        let public = opencrown_runtime::hex::decode(public_hex.trim()).unwrap();
        let revocations =
            r#"{"schema":1,"revoked":[{"id":"org.opencrown.payload.example","version":"1.0.0"}]}"#;

        let error = prepare_payload(
            bundle.to_string_lossy().into_owned(),
            std::env::temp_dir()
                .join(format!("opencrown-ffi-revoked-{}", std::process::id()))
                .to_string_lossy()
                .into_owned(),
            "EXAMPLE-1".to_string(),
            "example-6.1.0".to_string(),
            "arm64-v8a".to_string(),
            36,
            "EXAMPLE".to_string(),
            signature,
            public,
            revocations.to_string(),
        )
        .unwrap_err();

        assert!(error.to_string().contains("revoked"));
    }
}
