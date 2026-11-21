//! Payload manifest: parse, validate, canonicalise, verify.
//!
//! The canonical form is sorted-key JSON with no whitespace, the same rule the
//! catalog tooling uses. The signature covers SHA-256 of that form
//! (docs/04-payload-system.md section 7).

use std::collections::BTreeSet;
use std::path::Path;

use ed25519_dalek::{Signature, VerifyingKey};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use sha2::{Digest, Sha256};

use crate::error::RuntimeError;
use crate::hex;

pub const SCHEMA: u32 = 1;
pub const CAPABILITIES: [&str; 5] = ["root", "domain", "controller", "shim", "diag"];
pub const FAMILIES: [&str; 3] = ["page-cache", "futex-uaf", "future"];

#[derive(Debug, Clone, PartialEq, Eq, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
pub struct Target {
    pub model: String,
    pub kernel_build: String,
    pub abi: String,
    pub android: String,
}

impl Target {
    pub fn supports_sdk(&self, sdk: u32) -> bool {
        let Some((op, value)) = split_range(&self.android) else {
            return false;
        };
        match op {
            ">=" => sdk >= value,
            "<=" => sdk <= value,
            ">" => sdk > value,
            "<" => sdk < value,
            "=" => sdk == value,
            _ => false,
        }
    }

    pub fn label(&self) -> String {
        format!("{} / {}", self.model, self.kernel_build)
    }
}

pub(crate) fn split_range(text: &str) -> Option<(&str, u32)> {
    for op in [">=", "<=", ">", "<", "="] {
        if let Some(rest) = text.strip_prefix(op) {
            return rest.parse().ok().map(|value| (op, value));
        }
    }
    None
}

#[derive(Debug, Clone, PartialEq, Eq, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
pub struct Artifact {
    pub path: String,
    pub sha256: String,
    pub size: u64,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
pub struct Manifest {
    pub schema: u32,
    pub id: String,
    pub version: String,
    pub min_agent: String,
    pub family: String,
    pub targets: Vec<Target>,
    pub capabilities: Vec<String>,
    pub entry: String,
    pub agent: String,
    pub persistence: String,
    pub reversible: String,
    pub artifacts: Vec<Artifact>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub notes: Option<String>,
}

impl Manifest {
    pub fn parse(bytes: &[u8]) -> Result<Self, RuntimeError> {
        let manifest: Self = serde_json::from_slice(bytes)?;
        manifest.validate()?;
        Ok(manifest)
    }

    pub fn validate(&self) -> Result<(), RuntimeError> {
        if self.schema != SCHEMA {
            return Err(RuntimeError::Invalid(format!(
                "unsupported schema {}",
                self.schema
            )));
        }
        if !valid_id(&self.id) {
            return Err(RuntimeError::Invalid(
                "id must be lowercase alphanumeric with . _ -".to_string(),
            ));
        }
        for (name, value) in [("version", &self.version), ("min_agent", &self.min_agent)] {
            if !valid_semver(value) {
                return Err(RuntimeError::Invalid(format!("{name} must be semver")));
            }
        }
        if !FAMILIES.contains(&self.family.as_str()) {
            return Err(RuntimeError::Invalid(format!(
                "unknown family {}",
                self.family
            )));
        }
        if self.targets.is_empty() {
            return Err(RuntimeError::Invalid(
                "targets must not be empty".to_string(),
            ));
        }
        for target in &self.targets {
            if target.model.is_empty() || target.kernel_build.is_empty() {
                return Err(RuntimeError::Invalid(
                    "target model and kernel_build are required".to_string(),
                ));
            }
            if target.abi != "arm64-v8a" {
                return Err(RuntimeError::Invalid(
                    "abi must be arm64-v8a in v1".to_string(),
                ));
            }
            if split_range(&target.android).is_none() {
                return Err(RuntimeError::Invalid(format!(
                    "target android range is not valid: {}",
                    target.android
                )));
            }
        }

        if self.capabilities.is_empty() {
            return Err(RuntimeError::Invalid(
                "capabilities must not be empty".to_string(),
            ));
        }
        let mut seen = BTreeSet::new();
        for capability in &self.capabilities {
            if !CAPABILITIES.contains(&capability.as_str()) {
                return Err(RuntimeError::Invalid(format!(
                    "unknown capability {capability}"
                )));
            }
            if !seen.insert(capability.as_str()) {
                return Err(RuntimeError::Invalid(format!(
                    "duplicate capability {capability}"
                )));
            }
        }

        if !inside_files(&self.entry) || !inside_files(&self.agent) {
            return Err(RuntimeError::Invalid(
                "entry and agent must resolve inside files/".to_string(),
            ));
        }
        if self.persistence != "boot" {
            return Err(RuntimeError::Invalid(
                "persistence must be boot in v1".to_string(),
            ));
        }
        if self.reversible != "reboot" {
            return Err(RuntimeError::Invalid(
                "reversible must be reboot in v1".to_string(),
            ));
        }
        if self.artifacts.is_empty() {
            return Err(RuntimeError::Invalid(
                "artifacts must not be empty".to_string(),
            ));
        }

        let mut names = BTreeSet::new();
        for artifact in &self.artifacts {
            if !inside_files(&artifact.path) {
                return Err(RuntimeError::Invalid(format!(
                    "artifact path must resolve inside files/: {}",
                    artifact.path
                )));
            }
            if artifact.size == 0 {
                return Err(RuntimeError::Invalid(format!(
                    "artifact size must be positive: {}",
                    artifact.path
                )));
            }
            match hex::decode(&artifact.sha256) {
                Some(bytes) if bytes.len() == 32 => {}
                _ => {
                    return Err(RuntimeError::Invalid(format!(
                        "artifact sha256 must be 64 lowercase hex chars: {}",
                        artifact.path
                    )));
                }
            }
            let Some(name) = file_name(&artifact.path) else {
                return Err(RuntimeError::Invalid(format!(
                    "artifact has no file name: {}",
                    artifact.path
                )));
            };
            if !names.insert(name.to_string()) {
                return Err(RuntimeError::Invalid(format!(
                    "duplicate artifact file name: {name}"
                )));
            }
        }

        let paths: Vec<&str> = self
            .artifacts
            .iter()
            .map(|artifact| artifact.path.as_str())
            .collect();
        if !paths.contains(&self.entry.as_str()) {
            return Err(RuntimeError::Invalid(
                "entry is not declared as an artifact".to_string(),
            ));
        }
        if !paths.contains(&self.agent.as_str()) {
            return Err(RuntimeError::Invalid(
                "agent is not declared as an artifact".to_string(),
            ));
        }

        Ok(())
    }

    pub fn to_value(&self) -> Result<Value, RuntimeError> {
        Ok(serde_json::to_value(self)?)
    }
}

pub fn canonical_json(value: &Value) -> String {
    let mut out = String::new();
    write_value(value, &mut out);
    out
}

pub fn digest(manifest: &Manifest) -> Result<[u8; 32], RuntimeError> {
    let text = canonical_json(&manifest.to_value()?);
    Ok(Sha256::digest(text.as_bytes()).into())
}

pub fn verify_signature(
    manifest: &Manifest,
    signature: &[u8],
    public_key: &[u8],
) -> Result<(), RuntimeError> {
    let key_bytes: [u8; 32] = public_key
        .try_into()
        .map_err(|_| RuntimeError::Signature("public key must be 32 bytes".to_string()))?;
    let key = VerifyingKey::from_bytes(&key_bytes)
        .map_err(|err| RuntimeError::Signature(err.to_string()))?;
    let signature = Signature::from_slice(signature)
        .map_err(|_| RuntimeError::Signature("signature must be 64 bytes".to_string()))?;
    let message = digest(manifest)?;
    key.verify_strict(&message, &signature)
        .map_err(|_| RuntimeError::Signature("does not cover this manifest".to_string()))
}

fn write_value(value: &Value, out: &mut String) {
    match value {
        Value::Null => out.push_str("null"),
        Value::Bool(true) => out.push_str("true"),
        Value::Bool(false) => out.push_str("false"),
        Value::Number(number) => out.push_str(&number.to_string()),
        Value::String(text) => {
            out.push_str(&serde_json::to_string(text).unwrap_or_default());
        }
        Value::Array(items) => {
            out.push('[');
            for (index, item) in items.iter().enumerate() {
                if index > 0 {
                    out.push(',');
                }
                write_value(item, out);
            }
            out.push(']');
        }
        Value::Object(map) => {
            let mut keys: Vec<&String> = map.keys().collect();
            keys.sort();
            out.push('{');
            for (index, key) in keys.iter().enumerate() {
                if index > 0 {
                    out.push(',');
                }
                out.push_str(&serde_json::to_string(key).unwrap_or_default());
                out.push(':');
                write_value(&map[*key], out);
            }
            out.push('}');
        }
    }
}

fn valid_id(id: &str) -> bool {
    let mut chars = id.chars();
    let Some(first) = chars.next() else {
        return false;
    };
    if !first.is_ascii_lowercase() && !first.is_ascii_digit() {
        return false;
    }
    id.len() >= 3
        && id.len() <= 128
        && id
            .chars()
            .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || matches!(c, '.' | '_' | '-'))
}

fn valid_semver(text: &str) -> bool {
    let parts: Vec<&str> = text.split('.').collect();
    parts.len() == 3
        && parts
            .iter()
            .all(|part| !part.is_empty() && part.chars().all(|c| c.is_ascii_digit()))
}

fn inside_files(path: &str) -> bool {
    if !path.starts_with("files/") {
        return false;
    }
    let parts: Vec<&str> = path.split('/').collect();
    parts.len() > 1
        && parts
            .iter()
            .all(|part| !part.is_empty() && *part != ".." && *part != ".")
}

fn file_name(path: &str) -> Option<&str> {
    Path::new(path).file_name().and_then(|name| name.to_str())
}

#[cfg(test)]
mod tests {
    use super::*;
    use ed25519_dalek::SigningKey;

    fn manifest_json() -> String {
        r#"{
            "schema": 1,
            "id": "org.opencrown.payload.example",
            "version": "1.0.0",
            "min_agent": "0.1.0",
            "family": "page-cache",
            "targets": [{"model": "CPH2653", "kernel_build": "6.1.1-android14", "abi": "arm64-v8a", "android": ">=14"}],
            "capabilities": ["root", "domain", "controller"],
            "entry": "files/payload.bin",
            "agent": "files/agent",
            "persistence": "boot",
            "reversible": "reboot",
            "artifacts": [
                {"path": "files/payload.bin", "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "size": 10},
                {"path": "files/agent", "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "size": 20}
            ]
        }"#
        .to_string()
    }

    #[test]
    fn parses_and_validates() {
        let manifest = Manifest::parse(manifest_json().as_bytes()).unwrap();
        assert_eq!(manifest.id, "org.opencrown.payload.example");
    }

    #[test]
    fn rejects_unknown_field() {
        let json = manifest_json().replace("\"schema\": 1,", "\"schema\": 1, \"extra\": true,");
        assert!(Manifest::parse(json.as_bytes()).is_err());
    }

    #[test]
    fn rejects_unknown_capability() {
        let json = manifest_json().replace("\"controller\"", "\"telepathy\"");
        assert!(Manifest::parse(json.as_bytes()).is_err());
    }

    #[test]
    fn rejects_traversal() {
        let json = manifest_json().replace("files/payload.bin", "files/../../payload.bin");
        assert!(Manifest::parse(json.as_bytes()).is_err());
    }

    #[test]
    fn canonical_digest_ignores_key_order() {
        let first = Manifest::parse(manifest_json().as_bytes()).unwrap();
        let reordered = r#"{
            "artifacts": [
                {"size": 10, "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "path": "files/payload.bin"},
                {"size": 20, "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "path": "files/agent"}
            ],
            "reversible": "reboot", "persistence": "boot",
            "agent": "files/agent", "entry": "files/payload.bin",
            "capabilities": ["root", "domain", "controller"],
            "targets": [{"android": ">=14", "abi": "arm64-v8a", "kernel_build": "6.1.1-android14", "model": "CPH2653"}],
            "family": "page-cache", "min_agent": "0.1.0", "version": "1.0.0",
            "id": "org.opencrown.payload.example", "schema": 1
        }"#;
        let second = Manifest::parse(reordered.as_bytes()).unwrap();
        assert_eq!(digest(&first).unwrap(), digest(&second).unwrap());
    }

    #[test]
    fn signs_and_verifies() {
        use ed25519_dalek::Signer;

        let manifest = Manifest::parse(manifest_json().as_bytes()).unwrap();
        let key = SigningKey::from_bytes(&[7u8; 32]);
        let message = digest(&manifest).unwrap();
        let signature = key.sign(&message);
        verify_signature(
            &manifest,
            &signature.to_bytes(),
            key.verifying_key().as_bytes(),
        )
        .unwrap();

        let other = SigningKey::from_bytes(&[9u8; 32]);
        assert!(
            verify_signature(
                &manifest,
                &signature.to_bytes(),
                other.verifying_key().as_bytes()
            )
            .is_err()
        );
    }

    #[test]
    fn target_ranges() {
        assert!(
            Target {
                model: "m".into(),
                kernel_build: "k".into(),
                abi: "arm64-v8a".into(),
                android: ">=14".into()
            }
            .supports_sdk(14)
        );
        assert!(
            !Target {
                model: "m".into(),
                kernel_build: "k".into(),
                abi: "arm64-v8a".into(),
                android: ">=14".into()
            }
            .supports_sdk(13)
        );
        assert!(
            Target {
                model: "m".into(),
                kernel_build: "k".into(),
                abi: "arm64-v8a".into(),
                android: "=36".into()
            }
            .supports_sdk(36)
        );
        assert!(
            !Target {
                model: "m".into(),
                kernel_build: "k".into(),
                abi: "arm64-v8a".into(),
                android: "bogus".into()
            }
            .supports_sdk(36)
        );
    }
}
