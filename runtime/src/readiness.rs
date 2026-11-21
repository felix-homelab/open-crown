//! Readiness: answer "would this payload be ready?" without running anything.
//!
//! The evaluator reads the index, the bundle directory, the manifest, the
//! signature, the artifacts and the revocation list. It never stages files,
//! mints a token, spawns a process, or requests privilege (ADR 0014).

use std::path::Path;

use sha2::{Digest, Sha256};

use crate::error::RuntimeError;
use crate::hex;
use crate::index::{Payload, PayloadIndex};
use crate::manifest::{Manifest, verify_signature};
use crate::matcher::DeviceFingerprint;
use crate::revocation::RevocationList;

/// Exactly one reason is returned for every payload.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Reason {
    Ready,
    NoMatch,
    NotAvailable,
    NoBundle,
    InvalidManifest,
    BadSignature,
    ArtifactMismatch,
    Revoked,
    AgentTooOld,
}

impl Reason {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Ready => "ready",
            Self::NoMatch => "no_match",
            Self::NotAvailable => "not_available",
            Self::NoBundle => "no_bundle",
            Self::InvalidManifest => "invalid_manifest",
            Self::BadSignature => "bad_signature",
            Self::ArtifactMismatch => "artifact_mismatch",
            Self::Revoked => "revoked",
            Self::AgentTooOld => "agent_too_old",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PayloadReadiness {
    pub id: String,
    pub family: String,
    pub index_status: String,
    pub reason: Reason,
    pub detail: String,
    pub bundle_version: Option<String>,
}

impl PayloadReadiness {
    pub fn is_ready(&self) -> bool {
        self.reason == Reason::Ready
    }

    fn new(payload: &Payload, reason: Reason, detail: String) -> Self {
        Self {
            id: payload.id.clone(),
            family: payload.family.clone(),
            index_status: payload.status.clone(),
            reason,
            detail,
            bundle_version: None,
        }
    }
}

/// Evaluate every payload in the index, in index order.
pub fn evaluate(
    index: &PayloadIndex,
    bundles_root: &Path,
    device: &DeviceFingerprint,
    agent_version: &str,
    pinned_keys: &[Vec<u8>],
    revocations: &RevocationList,
) -> Result<Vec<PayloadReadiness>, RuntimeError> {
    let agent = parse_semver(agent_version).ok_or_else(|| {
        RuntimeError::Invalid(format!("agent version must be semver: {agent_version}"))
    })?;

    let mut results = Vec::with_capacity(index.payloads().len());
    for payload in index.payloads() {
        results.push(evaluate_one(
            payload,
            bundles_root,
            device,
            agent,
            pinned_keys,
            revocations,
        ));
    }
    Ok(results)
}

fn evaluate_one(
    payload: &Payload,
    bundles_root: &Path,
    device: &DeviceFingerprint,
    agent: (u64, u64, u64),
    pinned_keys: &[Vec<u8>],
    revocations: &RevocationList,
) -> PayloadReadiness {
    let Some(target) = payload.matching_target(device) else {
        let detail = if payload.targets.is_empty() {
            "index declares no exact device targets (the upstream source is device-generic)"
                .to_string()
        } else {
            let inexact = payload
                .targets
                .iter()
                .filter(|target| target.kernel_build.is_none())
                .count();
            let mut detail = format!(
                "no exact target matches {}; {} indexed target(s) checked",
                device.label(),
                payload.targets.len()
            );
            if inexact > 0 {
                detail.push_str(&format!(
                    "; {inexact} target(s) publish only a kernel version, not an exact build"
                ));
            }
            detail
        };
        return PayloadReadiness::new(payload, Reason::NoMatch, detail);
    };

    let status = payload.effective_status(target);
    if status != "available" {
        return PayloadReadiness::new(
            payload,
            Reason::NotAvailable,
            format!(
                "index status is {status} for {} / {}",
                target.model,
                target
                    .kernel_build
                    .as_deref()
                    .unwrap_or("unknown kernel build")
            ),
        );
    }

    let bundle = bundles_root.join(&payload.id);
    if !bundle.is_dir() {
        return PayloadReadiness::new(
            payload,
            Reason::NoBundle,
            format!("no bundle directory at {}", bundle.display()),
        );
    }

    let manifest_bytes = match std::fs::read(bundle.join("manifest.json")) {
        Ok(bytes) => bytes,
        Err(err) => {
            return PayloadReadiness::new(
                payload,
                Reason::InvalidManifest,
                format!("cannot read manifest.json: {err}"),
            );
        }
    };
    let manifest = match Manifest::parse(&manifest_bytes) {
        Ok(manifest) => manifest,
        Err(err) => {
            return PayloadReadiness::new(
                payload,
                Reason::InvalidManifest,
                format!("manifest rejected: {err}"),
            );
        }
    };

    let mut result = PayloadReadiness::new(payload, Reason::Ready, String::new());
    result.bundle_version = Some(manifest.version.clone());

    if manifest.id != payload.id {
        result.reason = Reason::InvalidManifest;
        result.detail = format!(
            "bundle id {} does not match index id {}",
            manifest.id, payload.id
        );
        return result;
    }
    if manifest.family != payload.family {
        result.reason = Reason::InvalidManifest;
        result.detail = format!(
            "bundle family {} does not match index family {}",
            manifest.family, payload.family
        );
        return result;
    }
    if !manifest.targets.iter().any(|target| device.matches(target)) {
        result.reason = Reason::NoMatch;
        result.detail = format!("bundle manifest does not target {}", device.label());
        return result;
    }

    if revocations.is_revoked(&manifest.id, &manifest.version) {
        result.reason = Reason::Revoked;
        result.detail = format!("{} {} is revoked", manifest.id, manifest.version);
        return result;
    }

    let signature = match std::fs::read(bundle.join("manifest.sig")) {
        Ok(bytes) => bytes,
        Err(err) => {
            result.reason = Reason::BadSignature;
            result.detail = format!("cannot read manifest.sig: {err}");
            return result;
        }
    };
    if pinned_keys.is_empty() {
        result.reason = Reason::BadSignature;
        result.detail = "no pinned catalog keys".to_string();
        return result;
    }
    if !pinned_keys
        .iter()
        .any(|key| verify_signature(&manifest, &signature, key).is_ok())
    {
        result.reason = Reason::BadSignature;
        result.detail = format!(
            "none of the {} pinned key(s) verifies manifest.sig",
            pinned_keys.len()
        );
        return result;
    }

    for artifact in &manifest.artifacts {
        let path = bundle.join(&artifact.path);
        let bytes = match std::fs::read(&path) {
            Ok(bytes) => bytes,
            Err(err) => {
                result.reason = Reason::ArtifactMismatch;
                result.detail = format!("cannot read {}: {err}", artifact.path);
                return result;
            }
        };
        if bytes.len() as u64 != artifact.size {
            result.reason = Reason::ArtifactMismatch;
            result.detail = format!(
                "size mismatch for {}: declared {}, found {}",
                artifact.path,
                artifact.size,
                bytes.len()
            );
            return result;
        }
        let found = hex::encode(&Sha256::digest(&bytes));
        if found != artifact.sha256 {
            result.reason = Reason::ArtifactMismatch;
            result.detail = format!("SHA-256 mismatch for {}", artifact.path);
            return result;
        }
    }

    if version_lt(
        agent,
        parse_semver(&manifest.min_agent).unwrap_or((0, 0, 0)),
    ) {
        result.reason = Reason::AgentTooOld;
        result.detail = format!(
            "bundle needs agent {} but this app is {}",
            manifest.min_agent,
            agent_version_string(agent)
        );
        return result;
    }

    result.detail = format!(
        "bundle {} at {} is valid for this device",
        manifest.version,
        bundle.display()
    );
    result
}

fn parse_semver(text: &str) -> Option<(u64, u64, u64)> {
    let parts: Vec<&str> = text.split('.').collect();
    if parts.len() != 3 {
        return None;
    }
    Some((
        parts[0].parse().ok()?,
        parts[1].parse().ok()?,
        parts[2].parse().ok()?,
    ))
}

fn version_lt(left: (u64, u64, u64), right: (u64, u64, u64)) -> bool {
    left < right
}

fn agent_version_string(version: (u64, u64, u64)) -> String {
    format!("{}.{}.{}", version.0, version.1, version.2)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::index::PayloadIndex;
    use crate::manifest::{Artifact, Target};
    use ed25519_dalek::{Signer, SigningKey};
    use std::fs;
    use std::path::PathBuf;
    use std::sync::atomic::{AtomicUsize, Ordering};

    static COUNTER: AtomicUsize = AtomicUsize::new(0);

    const ID: &str = "org.opencrown.payload.example";

    fn temp(tag: &str) -> PathBuf {
        let mut path = std::env::temp_dir();
        path.push(format!(
            "opencrown-readiness-{tag}-{}-{}",
            std::process::id(),
            COUNTER.fetch_add(1, Ordering::SeqCst)
        ));
        path
    }

    fn device() -> DeviceFingerprint {
        DeviceFingerprint {
            model: "EXAMPLE-1".into(),
            kernel_release: "example-6.1.0".into(),
            abi: "arm64-v8a".into(),
            android_sdk: 36,
            build_id: "EXAMPLE".into(),
        }
    }

    fn manifest() -> Manifest {
        Manifest {
            schema: 1,
            id: ID.into(),
            version: "1.0.0".into(),
            min_agent: "0.1.0".into(),
            family: "page-cache".into(),
            targets: vec![Target {
                model: "EXAMPLE-1".into(),
                kernel_build: "example-6.1.0".into(),
                abi: "arm64-v8a".into(),
                android: ">=31".into(),
            }],
            capabilities: vec!["root".into()],
            entry: "files/payload.bin".into(),
            agent: "files/agent".into(),
            persistence: "boot".into(),
            reversible: "reboot".into(),
            artifacts: vec![
                Artifact {
                    path: "files/payload.bin".into(),
                    sha256: "0000000000000000000000000000000000000000000000000000000000000000"
                        .into(),
                    size: 1,
                },
                Artifact {
                    path: "files/agent".into(),
                    sha256: "0000000000000000000000000000000000000000000000000000000000000000"
                        .into(),
                    size: 1,
                },
            ],
            notes: None,
        }
    }

    fn index(status: &str, target_status: Option<&str>) -> PayloadIndex {
        let target_status = target_status
            .map(|status| format!(r#", "status": "{status}""#))
            .unwrap_or_default();
        let json = format!(
            r#"{{
                "schema": 1,
                "updated": "2026-11-21",
                "sources": [
                    {{"id": "example-source", "url": "https://example.invalid/payload", "license": "MIT"}}
                ],
                "payloads": [
                    {{
                        "id": "{ID}",
                        "name": "Example",
                        "family": "page-cache",
                        "status": "{status}",
                        "source": "example-source",
                        "android": ">=31",
                        "abi": "arm64-v8a",
                        "targets": [
                            {{"model": "EXAMPLE-1", "kernel_build": "example-6.1.0"{target_status}}}
                        ]
                    }}
                ]
            }}"#
        );
        PayloadIndex::parse(json.as_bytes()).unwrap()
    }

    /// Build a signed bundle. Returns (root, bundle, public key).
    fn bundle(tag: &str) -> (PathBuf, PathBuf, Vec<u8>) {
        let root = temp(tag);
        let bundle = root.join(ID);
        fs::create_dir_all(bundle.join("files")).unwrap();
        let payload = b"payload-bytes";
        let agent = b"agent-bytes";
        fs::write(bundle.join("files/payload.bin"), payload).unwrap();
        fs::write(bundle.join("files/agent"), agent).unwrap();

        let mut manifest = manifest();
        manifest.artifacts = vec![
            Artifact {
                path: "files/payload.bin".into(),
                sha256: hex::encode(&Sha256::digest(payload)),
                size: payload.len() as u64,
            },
            Artifact {
                path: "files/agent".into(),
                sha256: hex::encode(&Sha256::digest(agent)),
                size: agent.len() as u64,
            },
        ];
        fs::write(
            bundle.join("manifest.json"),
            serde_json::to_vec(&manifest).unwrap(),
        )
        .unwrap();

        let key = SigningKey::from_bytes(&[7u8; 32]);
        let signature = key.sign(&crate::manifest::digest(&manifest).unwrap());
        fs::write(bundle.join("manifest.sig"), signature.to_bytes()).unwrap();
        (root, bundle, key.verifying_key().as_bytes().to_vec())
    }

    fn copy_example(tag: &str) -> PathBuf {
        let source = Path::new(env!("CARGO_MANIFEST_DIR")).join("../catalog/examples/signed");
        let root = temp(tag);
        let destination = root.join(ID);
        fs::create_dir_all(destination.join("files")).unwrap();
        for name in [
            "manifest.json",
            "manifest.sig",
            "files/payload.bin",
            "files/agent",
        ] {
            fs::copy(source.join(name), destination.join(name)).unwrap();
        }
        root
    }

    fn eval(
        index: &PayloadIndex,
        root: &Path,
        keys: &[Vec<u8>],
        revocations: &RevocationList,
    ) -> PayloadReadiness {
        evaluate(index, root, &device(), "0.1.0", keys, revocations)
            .unwrap()
            .into_iter()
            .next()
            .unwrap()
    }

    #[test]
    fn ready_bundle() {
        let (root, _bundle, key) = bundle("ready");
        let result = eval(
            &index("available", None),
            &root,
            &[key],
            &RevocationList::empty(),
        );
        assert_eq!(result.reason, Reason::Ready);
        assert!(result.is_ready());
        assert_eq!(result.bundle_version.as_deref(), Some("1.0.0"));
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn no_match_on_another_device() {
        let (root, _bundle, key) = bundle("no-match");
        let index = index("available", None);
        let mut other = device();
        other.model = "OTHER".into();
        let result = evaluate(
            &index,
            &root,
            &other,
            "0.1.0",
            &[key],
            &RevocationList::empty(),
        )
        .unwrap()
        .remove(0);
        assert_eq!(result.reason, Reason::NoMatch);
        assert!(result.detail.contains("OTHER"));
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn not_available_when_index_is_pending() {
        let (root, _bundle, key) = bundle("pending");
        let result = eval(
            &index("pending", None),
            &root,
            &[key],
            &RevocationList::empty(),
        );
        assert_eq!(result.reason, Reason::NotAvailable);
        assert!(result.detail.contains("pending"));
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn target_status_overrides_payload_status() {
        let (root, _bundle, key) = bundle("target-pending");
        let result = eval(
            &index("available", Some("retired")),
            &root,
            &[key],
            &RevocationList::empty(),
        );
        assert_eq!(result.reason, Reason::NotAvailable);
        assert!(result.detail.contains("retired"));
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn no_bundle_when_directory_is_absent() {
        let root = temp("none");
        let result = eval(
            &index("available", None),
            &root,
            &[vec![0u8; 32]],
            &RevocationList::empty(),
        );
        assert_eq!(result.reason, Reason::NoBundle);
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn invalid_manifest_is_reported() {
        let (root, bundle, key) = bundle("invalid");
        fs::write(bundle.join("manifest.json"), b"{}").unwrap();
        let result = eval(
            &index("available", None),
            &root,
            &[key],
            &RevocationList::empty(),
        );
        assert_eq!(result.reason, Reason::InvalidManifest);
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn bad_signature_is_reported() {
        let (root, _bundle, _key) = bundle("signature");
        let other = SigningKey::from_bytes(&[9u8; 32]);
        let result = eval(
            &index("available", None),
            &root,
            &[other.verifying_key().as_bytes().to_vec()],
            &RevocationList::empty(),
        );
        assert_eq!(result.reason, Reason::BadSignature);
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn artifact_mismatch_is_reported() {
        let (root, bundle, key) = bundle("artifact");
        fs::write(bundle.join("files/payload.bin"), b"tampered").unwrap();
        let result = eval(
            &index("available", None),
            &root,
            &[key],
            &RevocationList::empty(),
        );
        assert_eq!(result.reason, Reason::ArtifactMismatch);
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn revoked_is_reported() {
        let (root, _bundle, key) = bundle("revoked");
        let revocations = RevocationList::parse(
            br#"{"schema":1,"revoked":[{"id":"org.opencrown.payload.example","version":"1.0.0"}]}"#,
        )
        .unwrap();
        let result = eval(&index("available", None), &root, &[key], &revocations);
        assert_eq!(result.reason, Reason::Revoked);
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn agent_too_old_is_reported() {
        let (root, bundle, key) = bundle("agent");
        let mut manifest = manifest();
        manifest.min_agent = "9.0.0".into();
        let payload = b"payload-bytes";
        let agent = b"agent-bytes";
        manifest.artifacts = vec![
            Artifact {
                path: "files/payload.bin".into(),
                sha256: hex::encode(&Sha256::digest(payload)),
                size: payload.len() as u64,
            },
            Artifact {
                path: "files/agent".into(),
                sha256: hex::encode(&Sha256::digest(agent)),
                size: agent.len() as u64,
            },
        ];
        fs::write(
            bundle.join("manifest.json"),
            serde_json::to_vec(&manifest).unwrap(),
        )
        .unwrap();
        let signature =
            SigningKey::from_bytes(&[7u8; 32]).sign(&crate::manifest::digest(&manifest).unwrap());
        fs::write(bundle.join("manifest.sig"), signature.to_bytes()).unwrap();
        let result = eval(
            &index("available", None),
            &root,
            &[key],
            &RevocationList::empty(),
        );
        assert_eq!(result.reason, Reason::AgentTooOld);
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn cross_language_signed_example_is_ready() {
        let example = Path::new(env!("CARGO_MANIFEST_DIR")).join("../catalog/examples/signed");
        let public_hex = fs::read_to_string(example.join("public_key.hex")).unwrap();
        let public = hex::decode(public_hex.trim()).unwrap();
        let root = copy_example("cross-language-ready");
        let json = format!(
            r#"{{
                "schema": 1,
                "updated": "2026-11-21",
                "sources": [
                    {{"id": "example-source", "url": "https://example.invalid/payload", "license": "MIT"}}
                ],
                "payloads": [
                    {{
                        "id": "{ID}",
                        "name": "Example",
                        "family": "page-cache",
                        "status": "available",
                        "source": "example-source",
                        "android": ">=31",
                        "abi": "arm64-v8a",
                        "targets": [
                            {{"model": "EXAMPLE-1", "kernel_build": "example-6.1.0"}}
                        ]
                    }}
                ]
            }}"#
        );
        let index = PayloadIndex::parse(json.as_bytes()).unwrap();
        let result = evaluate(
            &index,
            &root,
            &DeviceFingerprint {
                model: "EXAMPLE-1".into(),
                kernel_release: "example-6.1.0".into(),
                abi: "arm64-v8a".into(),
                android_sdk: 36,
                build_id: "EXAMPLE".into(),
            },
            "0.1.0",
            &[public],
            &RevocationList::empty(),
        )
        .unwrap()
        .remove(0);
        assert_eq!(result.reason, Reason::Ready, "{}", result.detail);
    }

    #[test]
    fn cross_language_example_can_be_revoked() {
        let example = Path::new(env!("CARGO_MANIFEST_DIR")).join("../catalog/examples/signed");
        let public_hex = fs::read_to_string(example.join("public_key.hex")).unwrap();
        let public = hex::decode(public_hex.trim()).unwrap();
        let root = copy_example("cross-language-revoked");
        let revocations = RevocationList::parse(
            br#"{"schema":1,"revoked":[{"id":"org.opencrown.payload.example","version":"1.0.0"}]}"#,
        )
        .unwrap();
        let json = format!(
            r#"{{
                "schema": 1,
                "updated": "2026-11-21",
                "sources": [
                    {{"id": "example-source", "url": "https://example.invalid/payload", "license": "MIT"}}
                ],
                "payloads": [
                    {{
                        "id": "{ID}",
                        "name": "Example",
                        "family": "page-cache",
                        "status": "available",
                        "source": "example-source",
                        "android": ">=31",
                        "abi": "arm64-v8a",
                        "targets": [
                            {{"model": "EXAMPLE-1", "kernel_build": "example-6.1.0"}}
                        ]
                    }}
                ]
            }}"#
        );
        let index = PayloadIndex::parse(json.as_bytes()).unwrap();
        let result = evaluate(&index, &root, &device(), "0.1.0", &[public], &revocations)
            .unwrap()
            .remove(0);
        assert_eq!(result.reason, Reason::Revoked);
    }
}
