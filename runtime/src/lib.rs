//! Payload runtime.
//!
//! Loaded by the app; the only code that runs before privilege is obtained. It
//! matches the device, verifies the signed manifest, stages the artifacts, and
//! produces a one-time token for the agent launch
//! (docs/02-architecture.md section 2.2, docs/04-payload-system.md section 9).

pub mod devices;
pub mod error;
pub mod execution;
pub mod hex;
pub mod index;
pub mod launch;
pub mod manifest;
pub mod matcher;
pub mod readiness;
pub mod revocation;
pub mod stage;

use std::path::Path;

pub use opencrown_core::PROTOCOL_VERSION;

use error::RuntimeError;
use manifest::Manifest;
use matcher::DeviceFingerprint;
use stage::Staged;

#[derive(Debug)]
pub struct Prepared {
    pub manifest: Manifest,
    pub staged: Staged,
    pub token: launch::Token,
    pub boot_id: launch::BootId,
}

/// Verify, stage, and prepare a launch. The caller still decides when to run
/// the agent and must surface the reason when this returns an error.
pub fn prepare(
    bundle: &Path,
    work: &Path,
    device: &DeviceFingerprint,
    signature: &[u8],
    public_key: &[u8],
    revocations: &revocation::RevocationList,
) -> Result<Prepared, RuntimeError> {
    let manifest_bytes = std::fs::read(bundle.join("manifest.json"))?;
    let manifest = Manifest::parse(&manifest_bytes)?;
    if revocations.is_revoked(&manifest.id, &manifest.version) {
        return Err(RuntimeError::Invalid("payload is revoked".to_string()));
    }
    matcher::select(&manifest, device)?;
    manifest::verify_signature(&manifest, signature, public_key)?;
    let staged = stage::stage(bundle, &manifest, work)?;
    let token = launch::new_token()?;
    let boot_id = launch::new_boot_id()?;
    Ok(Prepared {
        manifest,
        staged,
        token,
        boot_id,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::manifest::{Artifact, Target};
    use crate::revocation::RevocationList;
    use ed25519_dalek::{Signer, SigningKey};
    use sha2::{Digest, Sha256};
    use std::fs;
    use std::sync::atomic::{AtomicUsize, Ordering};

    static COUNTER: AtomicUsize = AtomicUsize::new(0);

    fn temp(tag: &str) -> std::path::PathBuf {
        let mut path = std::env::temp_dir();
        path.push(format!(
            "opencrown-{tag}-{}-{}",
            std::process::id(),
            COUNTER.fetch_add(1, Ordering::SeqCst)
        ));
        path
    }

    fn device() -> DeviceFingerprint {
        DeviceFingerprint {
            model: "CPH2653".into(),
            kernel_release: "6.1.1-android14".into(),
            abi: "arm64-v8a".into(),
            android_sdk: 36,
            build_id: "BP1A".into(),
        }
    }

    fn bundle(tag: &str) -> (std::path::PathBuf, Manifest) {
        let dir = temp(tag);
        fs::create_dir_all(dir.join("files")).unwrap();
        let payload = b"payload-bytes";
        let agent = b"agent-bytes";
        fs::write(dir.join("files/payload.bin"), payload).unwrap();
        fs::write(dir.join("files/agent"), agent).unwrap();

        let manifest = Manifest {
            schema: 1,
            id: "org.opencrown.payload.example".into(),
            version: "1.0.0".into(),
            min_agent: "0.1.0".into(),
            family: "page-cache".into(),
            targets: vec![Target {
                model: "CPH2653".into(),
                kernel_build: "6.1.1-android14".into(),
                abi: "arm64-v8a".into(),
                android: ">=14".into(),
            }],
            capabilities: vec!["root".into()],
            entry: "files/payload.bin".into(),
            agent: "files/agent".into(),
            persistence: "boot".into(),
            reversible: "reboot".into(),
            artifacts: vec![
                Artifact {
                    path: "files/payload.bin".into(),
                    sha256: crate::hex::encode(&Sha256::digest(payload)),
                    size: payload.len() as u64,
                },
                Artifact {
                    path: "files/agent".into(),
                    sha256: crate::hex::encode(&Sha256::digest(agent)),
                    size: agent.len() as u64,
                },
            ],
            notes: None,
        };
        fs::write(
            dir.join("manifest.json"),
            serde_json::to_vec(&manifest).unwrap(),
        )
        .unwrap();
        (dir, manifest)
    }

    #[test]
    fn prepares_a_signed_bundle() {
        let (dir, manifest) = bundle("prepare-ok");
        let key = SigningKey::from_bytes(&[7u8; 32]);
        let signature = key.sign(&crate::manifest::digest(&manifest).unwrap());
        let work = temp("prepare-work");

        let prepared = prepare(
            &dir,
            &work,
            &device(),
            &signature.to_bytes(),
            key.verifying_key().as_bytes(),
            &RevocationList::empty(),
        )
        .unwrap();

        assert!(prepared.staged.entry.exists());
        assert_eq!(prepared.staged.agent, work.join("agent"));
        assert_eq!(prepared.token.expose().len(), 64);
        assert_eq!(prepared.boot_id.expose().len(), 32);
        let _ = fs::remove_dir_all(&dir);
        let _ = fs::remove_dir_all(&work);
    }

    #[test]
    fn rejects_a_wrong_signature() {
        let (dir, manifest) = bundle("prepare-sig");
        let key = SigningKey::from_bytes(&[7u8; 32]);
        let other = SigningKey::from_bytes(&[9u8; 32]);
        let signature = other.sign(&crate::manifest::digest(&manifest).unwrap());
        let work = temp("prepare-sig-work");

        let error = prepare(
            &dir,
            &work,
            &device(),
            &signature.to_bytes(),
            key.verifying_key().as_bytes(),
            &RevocationList::empty(),
        )
        .unwrap_err();
        assert!(error.to_string().contains("signature"));
        let _ = fs::remove_dir_all(&dir);
        let _ = fs::remove_dir_all(&work);
    }

    #[test]
    fn rejects_a_wrong_device() {
        let (dir, manifest) = bundle("prepare-device");
        let key = SigningKey::from_bytes(&[7u8; 32]);
        let signature = key.sign(&crate::manifest::digest(&manifest).unwrap());
        let work = temp("prepare-device-work");
        let mut other = device();
        other.model = "OTHER".into();

        let error = prepare(
            &dir,
            &work,
            &other,
            &signature.to_bytes(),
            key.verifying_key().as_bytes(),
            &RevocationList::empty(),
        )
        .unwrap_err();
        assert!(error.to_string().contains("target mismatch"));
        let _ = fs::remove_dir_all(&dir);
        let _ = fs::remove_dir_all(&work);
    }

    #[test]
    fn rejects_a_revoked_payload() {
        let (dir, manifest) = bundle("prepare-revoked");
        let key = SigningKey::from_bytes(&[7u8; 32]);
        let signature = key.sign(&crate::manifest::digest(&manifest).unwrap());
        let work = temp("prepare-revoked-work");
        let revocations = RevocationList::parse(
            br#"{"schema":1,"revoked":[{"id":"org.opencrown.payload.example","version":"1.0.0"}]}"#,
        )
        .unwrap();

        let error = prepare(
            &dir,
            &work,
            &device(),
            &signature.to_bytes(),
            key.verifying_key().as_bytes(),
            &revocations,
        )
        .unwrap_err();
        assert!(error.to_string().contains("revoked"));
        let _ = fs::remove_dir_all(&dir);
        let _ = fs::remove_dir_all(&work);
    }
}
