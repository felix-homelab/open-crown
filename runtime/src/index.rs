//! Payload index: source-level payloads and their published device targets.
//!
//! The index is not a signed bundle manifest and grants nothing. It exists so
//! the app can list a payload that has not been built yet and explain why it is
//! not ready (ADR 0014, catalog/schema/payload-index.schema.json).

use std::collections::BTreeSet;

use serde::Deserialize;

use crate::error::RuntimeError;
use crate::manifest::{FAMILIES, split_range};

pub const SCHEMA: u32 = 1;
pub const STATUSES: [&str; 3] = ["available", "pending", "retired"];

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct IndexFile {
    schema: u32,
    updated: String,
    sources: Vec<Source>,
    payloads: Vec<Payload>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Source {
    pub id: String,
    pub url: String,
    pub license: String,
    #[serde(default)]
    pub retrieved: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Payload {
    pub id: String,
    pub name: String,
    pub family: String,
    pub status: String,
    pub source: String,
    #[serde(default)]
    pub cves: Vec<String>,
    pub android: String,
    pub abi: String,
    #[serde(default)]
    pub source_status: Option<String>,
    #[serde(default)]
    pub notes: Option<String>,
    pub targets: Vec<Target>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Target {
    pub model: String,
    /// Exact `uname -r` string, or null when the upstream source only
    /// published a kernel version. A null value never matches a device.
    #[serde(default)]
    pub kernel_build: Option<String>,
    #[serde(default)]
    pub kernel_version: Option<String>,
    #[serde(default)]
    pub build: Option<String>,
    #[serde(default)]
    pub android: Option<String>,
    #[serde(default)]
    pub abi: Option<String>,
    #[serde(default)]
    pub status: Option<String>,
}

impl Target {
    pub fn android<'a>(&'a self, payload: &'a Payload) -> &'a str {
        self.android.as_deref().unwrap_or(&payload.android)
    }

    pub fn abi<'a>(&'a self, payload: &'a Payload) -> &'a str {
        self.abi.as_deref().unwrap_or(&payload.abi)
    }

    pub fn status<'a>(&'a self, payload: &'a Payload) -> &'a str {
        self.status.as_deref().unwrap_or(&payload.status)
    }

    /// Exact-match only. A null `kernel_build` is deliberately unmatchable.
    pub fn matches(&self, payload: &Payload, device: &crate::matcher::DeviceFingerprint) -> bool {
        let Some(kernel_build) = self.kernel_build.as_deref() else {
            return false;
        };
        self.model == device.model
            && kernel_build == device.kernel_release
            && self.abi(payload) == device.abi
            && split_range(self.android(payload)).is_some_and(|(op, value)| match op {
                ">=" => device.android_sdk >= value,
                "<=" => device.android_sdk <= value,
                ">" => device.android_sdk > value,
                "<" => device.android_sdk < value,
                "=" => device.android_sdk == value,
                _ => false,
            })
    }

    pub fn label(&self) -> String {
        match (&self.kernel_build, &self.kernel_version) {
            (Some(kernel), _) => format!("{} / {kernel}", self.model),
            (None, Some(version)) => {
                format!("{} / kernel {version} (no exact build)", self.model)
            }
            (None, None) => format!("{} / unknown kernel", self.model),
        }
    }
}

impl Payload {
    pub fn matching_target(&self, device: &crate::matcher::DeviceFingerprint) -> Option<&Target> {
        self.targets
            .iter()
            .find(|target| target.matches(self, device))
    }

    pub fn effective_status<'a>(&'a self, target: &'a Target) -> &'a str {
        target.status.as_deref().unwrap_or(&self.status)
    }
}

#[derive(Debug, Clone)]
pub struct PayloadIndex {
    updated: String,
    sources: Vec<Source>,
    payloads: Vec<Payload>,
}

impl PayloadIndex {
    pub fn parse(bytes: &[u8]) -> Result<Self, RuntimeError> {
        let file: IndexFile = serde_json::from_slice(bytes)?;
        validate(&file)?;
        Ok(Self {
            updated: file.updated,
            sources: file.sources,
            payloads: file.payloads,
        })
    }

    pub fn updated(&self) -> &str {
        &self.updated
    }

    pub fn sources(&self) -> &[Source] {
        &self.sources
    }

    pub fn payloads(&self) -> &[Payload] {
        &self.payloads
    }
}

fn validate(file: &IndexFile) -> Result<(), RuntimeError> {
    if file.schema != SCHEMA {
        return Err(RuntimeError::Invalid(format!(
            "unsupported payload-index schema {}",
            file.schema
        )));
    }
    if !valid_date(&file.updated) {
        return Err(RuntimeError::Invalid(
            "payload-index updated must be YYYY-MM-DD".to_string(),
        ));
    }
    if file.sources.is_empty() {
        return Err(RuntimeError::Invalid(
            "payload-index sources must not be empty".to_string(),
        ));
    }

    let mut source_ids = BTreeSet::new();
    for source in &file.sources {
        if !valid_id(&source.id, 64) {
            return Err(RuntimeError::Invalid(format!(
                "invalid source id {}",
                source.id
            )));
        }
        if !source.url.starts_with("https://") {
            return Err(RuntimeError::Invalid(format!(
                "source {} url must start with https://",
                source.id
            )));
        }
        if source.license.trim().is_empty() {
            return Err(RuntimeError::Invalid(format!(
                "source {} license is required",
                source.id
            )));
        }
        if let Some(retrieved) = &source.retrieved
            && !valid_date(retrieved)
        {
            return Err(RuntimeError::Invalid(format!(
                "source {} retrieved must be YYYY-MM-DD",
                source.id
            )));
        }
        if !source_ids.insert(source.id.as_str()) {
            return Err(RuntimeError::Invalid(format!(
                "duplicate source id {}",
                source.id
            )));
        }
    }

    let mut payload_ids = BTreeSet::new();
    for payload in &file.payloads {
        if !valid_id(&payload.id, 128) {
            return Err(RuntimeError::Invalid(format!(
                "invalid payload id {}",
                payload.id
            )));
        }
        if !payload_ids.insert(payload.id.as_str()) {
            return Err(RuntimeError::Invalid(format!(
                "duplicate payload id {}",
                payload.id
            )));
        }
        if payload.name.trim().is_empty() {
            return Err(RuntimeError::Invalid(format!(
                "payload {} name is required",
                payload.id
            )));
        }
        if !FAMILIES.contains(&payload.family.as_str()) {
            return Err(RuntimeError::Invalid(format!(
                "payload {} has unknown family {}",
                payload.id, payload.family
            )));
        }
        if !STATUSES.contains(&payload.status.as_str()) {
            return Err(RuntimeError::Invalid(format!(
                "payload {} has unknown status {}",
                payload.id, payload.status
            )));
        }
        if !source_ids.contains(payload.source.as_str()) {
            return Err(RuntimeError::Invalid(format!(
                "payload {} refers to undeclared source {}",
                payload.id, payload.source
            )));
        }
        if split_range(&payload.android).is_none() {
            return Err(RuntimeError::Invalid(format!(
                "payload {} android range is not valid",
                payload.id
            )));
        }
        if payload.abi != "arm64-v8a" {
            return Err(RuntimeError::Invalid(format!(
                "payload {} abi must be arm64-v8a",
                payload.id
            )));
        }
        for cve in &payload.cves {
            if !valid_cve(cve) {
                return Err(RuntimeError::Invalid(format!(
                    "payload {} has invalid CVE {cve}",
                    payload.id
                )));
            }
        }
        if !unique(&payload.cves) {
            return Err(RuntimeError::Invalid(format!(
                "payload {} cves must be unique",
                payload.id
            )));
        }

        let mut seen = BTreeSet::new();
        for target in &payload.targets {
            if target.model.trim().is_empty() {
                return Err(RuntimeError::Invalid(format!(
                    "payload {} target model is required",
                    payload.id
                )));
            }
            if let Some(kernel_build) = &target.kernel_build
                && kernel_build.trim().is_empty()
            {
                return Err(RuntimeError::Invalid(format!(
                    "payload {} target has an empty kernel_build",
                    payload.id
                )));
            }
            if target.kernel_build.is_none() && target.kernel_version.is_none() {
                return Err(RuntimeError::Invalid(format!(
                    "payload {} target {} needs kernel_build or kernel_version",
                    payload.id, target.model
                )));
            }
            if let Some(status) = &target.status
                && !STATUSES.contains(&status.as_str())
            {
                return Err(RuntimeError::Invalid(format!(
                    "payload {} target {} has unknown status {}",
                    payload.id, target.model, status
                )));
            }
            if split_range(target.android(payload)).is_none() {
                return Err(RuntimeError::Invalid(format!(
                    "payload {} target {} android range is not valid",
                    payload.id, target.model
                )));
            }
            if target.abi(payload) != "arm64-v8a" {
                return Err(RuntimeError::Invalid(format!(
                    "payload {} target {} abi must be arm64-v8a",
                    payload.id, target.model
                )));
            }
            let key = (
                target.model.clone(),
                target.kernel_build.clone(),
                target.build.clone(),
            );
            if !seen.insert(key) {
                return Err(RuntimeError::Invalid(format!(
                    "payload {} has duplicate target {}",
                    payload.id, target.model
                )));
            }
        }
    }

    Ok(())
}

fn unique(items: &[String]) -> bool {
    let mut seen = BTreeSet::new();
    items.iter().all(|item| seen.insert(item.as_str()))
}

fn valid_id(id: &str, max: usize) -> bool {
    let mut chars = id.chars();
    let Some(first) = chars.next() else {
        return false;
    };
    if !first.is_ascii_lowercase() && !first.is_ascii_digit() {
        return false;
    }
    id.len() >= 3
        && id.len() <= max
        && id
            .chars()
            .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || matches!(c, '.' | '_' | '-'))
}

fn valid_date(text: &str) -> bool {
    text.len() == 10
        && text.as_bytes().iter().enumerate().all(|(index, byte)| {
            if index == 4 || index == 7 {
                *byte == b'-'
            } else {
                byte.is_ascii_digit()
            }
        })
}

fn valid_cve(text: &str) -> bool {
    let Some(rest) = text.strip_prefix("CVE-") else {
        return false;
    };
    let mut parts = rest.split('-');
    let (Some(year), Some(number), None) = (parts.next(), parts.next(), parts.next()) else {
        return false;
    };
    year.len() == 4
        && year.chars().all(|c| c.is_ascii_digit())
        && !number.is_empty()
        && number.chars().all(|c| c.is_ascii_digit())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn json() -> String {
        r#"{
            "schema": 1,
            "updated": "2026-11-21",
            "sources": [
                {"id": "example-source", "url": "https://example.invalid/payload", "license": "MIT"}
            ],
            "payloads": [
                {
                    "id": "org.opencrown.payload.example",
                    "name": "Example",
                    "family": "page-cache",
                    "status": "available",
                    "source": "example-source",
                    "cves": ["CVE-2026-0001"],
                    "android": ">=31",
                    "abi": "arm64-v8a",
                    "targets": [
                        {"model": "EXAMPLE-1", "kernel_build": "example-6.1.0", "kernel_version": "6.1.0"},
                        {"model": "EXAMPLE-1", "kernel_build": null, "kernel_version": "6.1.0", "build": "EXAMPLE1"}
                    ]
                }
            ]
        }"#
        .to_string()
    }

    fn device() -> crate::matcher::DeviceFingerprint {
        crate::matcher::DeviceFingerprint {
            model: "EXAMPLE-1".into(),
            kernel_release: "example-6.1.0".into(),
            abi: "arm64-v8a".into(),
            android_sdk: 36,
            build_id: "EXAMPLE".into(),
        }
    }

    #[test]
    fn parses_and_matches_exact_target() {
        let index = PayloadIndex::parse(json().as_bytes()).unwrap();
        assert_eq!(index.updated(), "2026-11-21");
        assert_eq!(index.sources().len(), 1);
        let payload = &index.payloads()[0];
        let target = payload.matching_target(&device()).unwrap();
        assert_eq!(target.model, "EXAMPLE-1");
        assert_eq!(payload.effective_status(target), "available");
    }

    #[test]
    fn null_kernel_build_never_matches() {
        let mut value: serde_json::Value = serde_json::from_str(&json()).unwrap();
        value["payloads"][0]["targets"][0]["kernel_build"] = serde_json::Value::Null;
        let index = PayloadIndex::parse(serde_json::to_vec(&value).unwrap().as_slice()).unwrap();
        assert!(index.payloads()[0].matching_target(&device()).is_none());
    }

    #[test]
    fn target_status_overrides_payload() {
        let mut value: serde_json::Value = serde_json::from_str(&json()).unwrap();
        value["payloads"][0]["targets"][0]["status"] = "pending".into();
        let index = PayloadIndex::parse(serde_json::to_vec(&value).unwrap().as_slice()).unwrap();
        let payload = &index.payloads()[0];
        let target = payload.matching_target(&device()).unwrap();
        assert_eq!(payload.effective_status(target), "pending");
    }

    #[test]
    fn rejects_unknown_source() {
        let broken = json().replace(r#""source": "example-source""#, r#""source": "missing""#);
        assert!(PayloadIndex::parse(broken.as_bytes()).is_err());
    }

    #[test]
    fn rejects_bad_status() {
        let broken = json().replace(r#""status": "available""#, r#""status": "probably""#);
        assert!(PayloadIndex::parse(broken.as_bytes()).is_err());
    }

    #[test]
    fn rejects_null_kernel_without_version() {
        let mut value: serde_json::Value = serde_json::from_str(&json()).unwrap();
        value["payloads"][0]["targets"][1]
            .as_object_mut()
            .unwrap()
            .remove("kernel_version");
        assert!(PayloadIndex::parse(serde_json::to_vec(&value).unwrap().as_slice()).is_err());
    }

    #[test]
    fn parses_the_committed_index_and_has_no_a52_target() {
        let path =
            std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("../catalog/payload-index.json");
        let bytes = std::fs::read(path).unwrap();
        let index = PayloadIndex::parse(&bytes).unwrap();
        // The committed index currently contains no payloads: the external
        // bundles were removed and the in-tree A52 chain is not an indexed
        // bundle (ADR 0014). The invariant below is the one that matters: no
        // committed target may ever match the A52.
        assert!(!index.sources().is_empty());
        let a52 = crate::matcher::DeviceFingerprint {
            model: "SM-A528B".into(),
            kernel_release: "5.4.254-qgki-28575149-abA528BXXSBGYI3".into(),
            abi: "arm64-v8a".into(),
            android_sdk: 34,
            build_id: "UP1A.231005.007".into(),
        };
        assert!(
            index
                .payloads()
                .iter()
                .all(|payload| payload.matching_target(&a52).is_none())
        );
    }

    #[test]
    fn rejects_duplicate_payload_id() {
        let mut value: serde_json::Value = serde_json::from_str(&json()).unwrap();
        let payload = value["payloads"][0].clone();
        value["payloads"].as_array_mut().unwrap().push(payload);
        let bytes = serde_json::to_vec(&value).unwrap();
        assert!(PayloadIndex::parse(&bytes).is_err());
    }
}
