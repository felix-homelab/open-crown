//! Supported-device list.
//!
//! The list is evidence, not intention: exact model and kernel build. The
//! runtime and the app both read it, so "unsupported" is a first-class state
//! rather than a crash (docs/04-payload-system.md section 8).

use std::collections::BTreeSet;

use serde::Deserialize;

use crate::error::RuntimeError;
use crate::manifest::{Target, split_range};
use crate::matcher::DeviceFingerprint;

const STATUSES: [&str; 3] = ["supported", "example", "retired"];

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct DevicesFile {
    schema: u32,
    updated: String,
    devices: Vec<Device>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Device {
    pub model: String,
    pub kernel_build: String,
    pub abi: String,
    pub android: String,
    pub status: String,
    #[serde(default)]
    pub note: Option<String>,
}

#[derive(Debug, Clone)]
pub struct SupportedDevices {
    updated: String,
    devices: Vec<Device>,
}

impl SupportedDevices {
    pub fn parse(bytes: &[u8]) -> Result<Self, RuntimeError> {
        let file: DevicesFile = serde_json::from_slice(bytes)?;
        if file.schema != 1 {
            return Err(RuntimeError::Invalid(format!(
                "unsupported device-list schema {}",
                file.schema
            )));
        }
        if !valid_date(&file.updated) {
            return Err(RuntimeError::Invalid(
                "updated must be YYYY-MM-DD".to_string(),
            ));
        }

        let mut seen = BTreeSet::new();
        for device in &file.devices {
            if device.model.is_empty() || device.kernel_build.is_empty() {
                return Err(RuntimeError::Invalid(
                    "device model and kernel_build are required".to_string(),
                ));
            }
            if device.abi != "arm64-v8a" {
                return Err(RuntimeError::Invalid(format!(
                    "device abi must be arm64-v8a: {}",
                    device.model
                )));
            }
            if split_range(&device.android).is_none() {
                return Err(RuntimeError::Invalid(format!(
                    "device android range is not valid: {}",
                    device.model
                )));
            }
            if !STATUSES.contains(&device.status.as_str()) {
                return Err(RuntimeError::Invalid(format!(
                    "unknown device status {}",
                    device.status
                )));
            }
            if !seen.insert((device.model.clone(), device.kernel_build.clone())) {
                return Err(RuntimeError::Invalid(format!(
                    "duplicate device {} / {}",
                    device.model, device.kernel_build
                )));
            }
        }

        Ok(Self {
            updated: file.updated,
            devices: file.devices,
        })
    }

    pub fn updated(&self) -> &str {
        &self.updated
    }

    pub fn devices(&self) -> &[Device] {
        &self.devices
    }

    pub fn find(&self, device: &DeviceFingerprint) -> Option<&Device> {
        self.devices.iter().find(|entry| {
            device.matches(&Target {
                model: entry.model.clone(),
                kernel_build: entry.kernel_build.clone(),
                abi: entry.abi.clone(),
                android: entry.android.clone(),
            })
        })
    }

    /// One of supported, example, retired, unknown.
    pub fn status_for(&self, device: &DeviceFingerprint) -> &'static str {
        match self.find(device).map(|entry| entry.status.as_str()) {
            Some("supported") => "supported",
            Some("example") => "example",
            Some("retired") => "retired",
            _ => "unknown",
        }
    }
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

#[cfg(test)]
mod tests {
    use super::*;

    fn json() -> String {
        r#"{
            "schema": 1,
            "updated": "2026-11-21",
            "devices": [
                {"model": "EXAMPLE-1", "kernel_build": "example-6.1.0", "abi": "arm64-v8a", "android": ">=31", "status": "example", "note": "structural"},
                {"model": "PHONE-2", "kernel_build": "6.1.0-android14", "abi": "arm64-v8a", "android": ">=34", "status": "supported"}
            ]
        }"#
        .to_string()
    }

    fn device(model: &str, kernel: &str) -> DeviceFingerprint {
        DeviceFingerprint {
            model: model.to_string(),
            kernel_release: kernel.to_string(),
            abi: "arm64-v8a".to_string(),
            android_sdk: 36,
            build_id: "BP1A".to_string(),
        }
    }

    #[test]
    fn parses_and_matches() {
        let list = SupportedDevices::parse(json().as_bytes()).unwrap();
        assert_eq!(list.devices().len(), 2);
        assert_eq!(list.updated(), "2026-11-21");
        assert_eq!(
            list.status_for(&device("EXAMPLE-1", "example-6.1.0")),
            "example"
        );
        assert_eq!(
            list.status_for(&device("PHONE-2", "6.1.0-android14")),
            "supported"
        );
        assert_eq!(
            list.status_for(&device("PHONE-2", "6.1.1-android14")),
            "unknown"
        );
        assert_eq!(
            list.status_for(&device("OTHER", "6.1.0-android14")),
            "unknown"
        );
    }

    #[test]
    fn rejects_duplicates() {
        let broken = json()
            .replace(r#""model": "PHONE-2""#, r#""model": "EXAMPLE-1""#)
            .replace(
                r#""kernel_build": "6.1.0-android14""#,
                r#""kernel_build": "example-6.1.0""#,
            );
        assert!(SupportedDevices::parse(broken.as_bytes()).is_err());
    }

    #[test]
    fn rejects_bad_status() {
        let broken = json().replace(r#""status": "example""#, r#""status": "probably""#);
        assert!(SupportedDevices::parse(broken.as_bytes()).is_err());
    }

    #[test]
    fn rejects_bad_date() {
        let broken = json().replace("2026-11-21", "21/11/2026");
        assert!(SupportedDevices::parse(broken.as_bytes()).is_err());
    }
}
