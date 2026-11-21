//! Device fingerprint and target selection.

use sha2::{Digest, Sha256};

use crate::error::RuntimeError;
use crate::hex;
use crate::manifest::{Manifest, Target};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DeviceFingerprint {
    pub model: String,
    pub kernel_release: String,
    pub abi: String,
    pub android_sdk: u32,
    pub build_id: String,
}

impl DeviceFingerprint {
    /// SHA-256(model || kernel_release || abi || android_sdk || build_id),
    /// as written in docs/04-payload-system.md section 8.
    pub fn digest(&self) -> String {
        let mut hasher = Sha256::new();
        hasher.update(self.model.as_bytes());
        hasher.update(self.kernel_release.as_bytes());
        hasher.update(self.abi.as_bytes());
        hasher.update(self.android_sdk.to_string().as_bytes());
        hasher.update(self.build_id.as_bytes());
        hex::encode(&hasher.finalize())
    }

    pub fn matches(&self, target: &Target) -> bool {
        target.model == self.model
            && target.kernel_build == self.kernel_release
            && target.abi == self.abi
            && target.supports_sdk(self.android_sdk)
    }

    pub fn label(&self) -> String {
        format!(
            "{} / {} / sdk {}",
            self.model, self.kernel_release, self.android_sdk
        )
    }
}

pub fn select<'a>(
    manifest: &'a Manifest,
    device: &DeviceFingerprint,
) -> Result<&'a Target, RuntimeError> {
    manifest
        .targets
        .iter()
        .find(|target| device.matches(target))
        .ok_or_else(|| RuntimeError::TargetMismatch {
            device: device.label(),
            targets: manifest.targets.iter().map(Target::label).collect(),
        })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn device() -> DeviceFingerprint {
        DeviceFingerprint {
            model: "CPH2653".into(),
            kernel_release: "6.1.1-android14".into(),
            abi: "arm64-v8a".into(),
            android_sdk: 36,
            build_id: "BP1A".into(),
        }
    }

    fn manifest(target_model: &str) -> Manifest {
        Manifest {
            schema: 1,
            id: "org.opencrown.payload.example".into(),
            version: "1.0.0".into(),
            min_agent: "0.1.0".into(),
            family: "page-cache".into(),
            targets: vec![Target {
                model: target_model.into(),
                kernel_build: "6.1.1-android14".into(),
                abi: "arm64-v8a".into(),
                android: ">=14".into(),
            }],
            capabilities: vec!["root".into()],
            entry: "files/payload.bin".into(),
            agent: "files/agent".into(),
            persistence: "boot".into(),
            reversible: "reboot".into(),
            artifacts: vec![],
            notes: None,
        }
    }

    #[test]
    fn digest_is_stable_and_hex() {
        let first = device().digest();
        assert_eq!(first.len(), 64);
        assert_eq!(first, device().digest());
        assert!(first.chars().all(|c| c.is_ascii_hexdigit()));
    }

    #[test]
    fn digest_changes_with_sdk() {
        let mut other = device();
        other.android_sdk = 35;
        assert_ne!(device().digest(), other.digest());
    }

    #[test]
    fn selects_exact_target() {
        let manifest = manifest("CPH2653");
        assert!(select(&manifest, &device()).is_ok());
    }

    #[test]
    fn mismatch_lists_targets() {
        let manifest = manifest("OTHER");
        let error = select(&manifest, &device()).unwrap_err();
        let text = error.to_string();
        assert!(text.contains("CPH2653"));
        assert!(text.contains("OTHER"));
    }
}
