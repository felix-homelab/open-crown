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
    Config { reason: String },
    Discovery { reason: String },
}

impl std::fmt::Display for FfiError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::NotReady { reason } => write!(f, "not ready: {reason}"),
            Self::Payload { reason } => write!(f, "payload: {reason}"),
            Self::Config { reason } => write!(f, "config: {reason}"),
            Self::Discovery { reason } => write!(f, "discovery: {reason}"),
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
    pub boot_id: String,
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
        boot_id: prepared.boot_id.expose().to_string(),
        manifest_digest: opencrown_runtime::hex::encode(&digest),
    })
}

/// What the app needs to show before a bundle is selected.
#[derive(Debug, uniffi::Record)]
pub struct BundleInfo {
    pub id: String,
    pub version: String,
    pub family: String,
    pub capabilities: Vec<String>,
    pub persistence: String,
    pub digest: String,
    pub targets: u32,
}

/// Parse and validate a bundle's manifest without verifying the signature.
///
/// Used to list installed bundles; `prepare_payload` is what actually verifies
/// and stages.
#[uniffi::export]
pub fn inspect_bundle(bundle_dir: String) -> Result<BundleInfo, FfiError> {
    let path = Path::new(&bundle_dir).join("manifest.json");
    let bytes = std::fs::read(&path).map_err(|err| FfiError::Payload {
        reason: err.to_string(),
    })?;
    let manifest =
        opencrown_runtime::manifest::Manifest::parse(&bytes).map_err(|err| FfiError::Payload {
            reason: err.to_string(),
        })?;
    let digest =
        opencrown_runtime::manifest::digest(&manifest).map_err(|err| FfiError::Payload {
            reason: err.to_string(),
        })?;
    Ok(BundleInfo {
        id: manifest.id,
        version: manifest.version,
        family: manifest.family,
        capabilities: manifest.capabilities,
        persistence: manifest.persistence,
        digest: opencrown_runtime::hex::encode(&digest),
        targets: manifest.targets.len() as u32,
    })
}

/// One entry in the supported-device list.
#[derive(Debug, uniffi::Record)]
pub struct SupportedDevice {
    pub model: String,
    pub kernel_build: String,
    pub abi: String,
    pub android: String,
    pub status: String,
    pub note: Option<String>,
}

#[uniffi::export]
pub fn parse_supported_devices(json: String) -> Result<Vec<SupportedDevice>, FfiError> {
    let list =
        opencrown_runtime::devices::SupportedDevices::parse(json.as_bytes()).map_err(|err| {
            FfiError::Config {
                reason: err.to_string(),
            }
        })?;
    Ok(list
        .devices()
        .iter()
        .map(|device| SupportedDevice {
            model: device.model.clone(),
            kernel_build: device.kernel_build.clone(),
            abi: device.abi.clone(),
            android: device.android.clone(),
            status: device.status.clone(),
            note: device.note.clone(),
        })
        .collect())
}

/// Device facts gathered by the app, plus the supported-list verdict and the
/// fingerprint digest. Pure: reads no files and starts nothing.
#[derive(Debug, uniffi::Record)]
pub struct DeviceProbe {
    pub model: String,
    pub kernel_release: String,
    pub abi: String,
    pub android_sdk: u32,
    pub build_id: String,
    pub build_fingerprint: String,
    pub digest: String,
    pub supported_status: String,
}

/// Probe a device. The caller supplies the platform facts; the runtime only
/// evaluates them against the supported-device list. No payload is run, no
/// root is requested, no file is staged, and no token is minted.
#[uniffi::export]
#[allow(clippy::too_many_arguments)]
pub fn probe_device(
    model: String,
    kernel_release: String,
    abi: String,
    android_sdk: u32,
    build_id: String,
    build_fingerprint: String,
    supported_devices_json: String,
) -> Result<DeviceProbe, FfiError> {
    let list =
        opencrown_runtime::devices::SupportedDevices::parse(supported_devices_json.as_bytes())
            .map_err(|err| FfiError::Config {
                reason: err.to_string(),
            })?;
    let device = opencrown_runtime::matcher::DeviceFingerprint {
        model,
        kernel_release,
        abi,
        android_sdk,
        build_id,
    };
    let supported_status = list.status_for(&device).to_string();
    let digest = device.digest();
    Ok(DeviceProbe {
        model: device.model,
        kernel_release: device.kernel_release,
        abi: device.abi,
        android_sdk: device.android_sdk,
        build_id: device.build_id,
        build_fingerprint,
        digest,
        supported_status,
    })
}

/// One payload's readiness verdict, with exactly one reason.
#[derive(Debug, uniffi::Record)]
pub struct PayloadStatus {
    pub id: String,
    pub family: String,
    pub index_status: String,
    pub reason: String,
    pub detail: String,
    pub bundle_version: Option<String>,
}

/// Evaluate the index without staging, executing, or minting anything.
#[uniffi::export]
#[allow(clippy::too_many_arguments)]
pub fn evaluate_readiness(
    index_json: String,
    bundles_dir: String,
    model: String,
    kernel_release: String,
    abi: String,
    android_sdk: u32,
    build_id: String,
    agent_version: String,
    pinned_keys_hex: Vec<String>,
    revocations_json: String,
) -> Result<Vec<PayloadStatus>, FfiError> {
    let index =
        opencrown_runtime::index::PayloadIndex::parse(index_json.as_bytes()).map_err(|err| {
            FfiError::Config {
                reason: err.to_string(),
            }
        })?;
    let revocations = if revocations_json.trim().is_empty() {
        opencrown_runtime::revocation::RevocationList::empty()
    } else {
        opencrown_runtime::revocation::RevocationList::parse(revocations_json.as_bytes()).map_err(
            |err| FfiError::Config {
                reason: err.to_string(),
            },
        )?
    };
    let mut keys = Vec::with_capacity(pinned_keys_hex.len());
    for text in pinned_keys_hex {
        let key = opencrown_runtime::hex::decode(text.trim()).ok_or_else(|| FfiError::Config {
            reason: "pinned catalog key must be lowercase hex".to_string(),
        })?;
        keys.push(key);
    }
    let device = opencrown_runtime::matcher::DeviceFingerprint {
        model,
        kernel_release,
        abi,
        android_sdk,
        build_id,
    };
    let results = opencrown_runtime::readiness::evaluate(
        &index,
        Path::new(&bundles_dir),
        &device,
        &agent_version,
        &keys,
        &revocations,
    )
    .map_err(|err| FfiError::Config {
        reason: err.to_string(),
    })?;
    Ok(results
        .into_iter()
        .map(|result| PayloadStatus {
            id: result.id,
            family: result.family,
            index_status: result.index_status,
            reason: result.reason.as_str().to_string(),
            detail: result.detail,
            bundle_version: result.bundle_version,
        })
        .collect())
}

/// One of supported, example, retired, unknown.
#[uniffi::export]
pub fn device_status(
    json: String,
    model: String,
    kernel_release: String,
    abi: String,
    android_sdk: u32,
) -> Result<String, FfiError> {
    let list =
        opencrown_runtime::devices::SupportedDevices::parse(json.as_bytes()).map_err(|err| {
            FfiError::Config {
                reason: err.to_string(),
            }
        })?;
    let device = opencrown_runtime::matcher::DeviceFingerprint {
        model,
        kernel_release,
        abi,
        android_sdk,
        build_id: String::new(),
    };
    Ok(list.status_for(&device).to_string())
}

/// Protocol rules the app may display or validate against. These are read from
/// the core modules, not duplicated in Kotlin.
#[derive(Debug, uniffi::Record)]
pub struct CoreRules {
    pub discovery_ttl_ms: u64,
    pub discovery_max_candidates: u32,
    pub ids_max_pending: u32,
    pub ids_backoff_secs: Vec<u32>,
    pub face_readback_timeout_ms: u64,
    pub face_max_archive_bytes: u64,
    pub setup_checkpoint_first: u32,
    pub setup_checkpoint_last: u32,
}

#[uniffi::export]
pub fn core_rules() -> CoreRules {
    CoreRules {
        discovery_ttl_ms: opencrown_core::discovery::CANDIDATE_TTL_MS,
        discovery_max_candidates: opencrown_core::discovery::MAX_CANDIDATES as u32,
        ids_max_pending: opencrown_core::ids::MAX_PENDING_REQUESTS as u32,
        ids_backoff_secs: opencrown_core::ids::RECONNECT_BACKOFF_SECS
            .iter()
            .map(|value| *value as u32)
            .collect(),
        face_readback_timeout_ms: opencrown_core::faces::READBACK_TIMEOUT_MS,
        face_max_archive_bytes: opencrown_core::faces::MAX_ARCHIVE_BYTES as u64,
        setup_checkpoint_first: opencrown_core::setup::FIRST_CHECKPOINT,
        setup_checkpoint_last: opencrown_core::setup::LAST_CHECKPOINT,
    }
}

/// One candidate as the app may display it. No transport address is exposed.
#[derive(Debug, uniffi::Record)]
pub struct FfiCandidate {
    pub token: String,
    pub name: Option<String>,
    pub rssi: i32,
    pub selectable: bool,
    pub product_type: Option<String>,
    pub os_version: Option<String>,
    pub last_seen_ms: u64,
}

#[derive(Debug, uniffi::Record)]
pub struct FfiSelection {
    pub token: String,
    pub product_type: String,
    pub os_version: String,
    pub rssi: i32,
}

/// A stateful discovery session owned by the app's scanner.
///
/// Inventory, expiry, token generation and selection validation live in the
/// Rust core. Kotlin keeps only display metadata (advertisement type and raw
/// bytes) that the core does not need.
#[derive(uniffi::Object)]
pub struct DiscoveryBridge {
    inner: std::sync::Mutex<opencrown_core::discovery::Discovery>,
}

#[uniffi::export]
impl DiscoveryBridge {
    #[uniffi::constructor]
    pub fn new(ttl_ms: u64) -> std::sync::Arc<Self> {
        std::sync::Arc::new(Self {
            inner: std::sync::Mutex::new(opencrown_core::discovery::Discovery::new(ttl_ms)),
        })
    }

    pub fn start_scan(&self) -> Result<u64, FfiError> {
        Ok(self.lock()?.start_scan().value())
    }

    pub fn stop_scan(&self) -> Result<(), FfiError> {
        self.lock()?.stop_scan();
        Ok(())
    }

    #[allow(clippy::too_many_arguments)]
    pub fn observe(
        &self,
        address_hex: String,
        rssi: i32,
        name: Option<String>,
        product_type: Option<String>,
        os_version: Option<String>,
        metadata_hex: Option<String>,
        now_ms: u64,
    ) -> Result<String, FfiError> {
        let address = decode_address(&address_hex)?;
        let metadata = match (product_type, os_version, metadata_hex) {
            (None, None, None) => None,
            (Some(product_type), Some(os_version), Some(metadata_hex)) => {
                let raw = opencrown_runtime::hex::decode(&metadata_hex).ok_or_else(|| {
                    FfiError::Discovery {
                        reason: "metadata is not hex".into(),
                    }
                })?;
                Some(
                    opencrown_core::discovery::SetupMetadata::decoded(
                        product_type,
                        os_version,
                        &raw,
                    )
                    .map_err(|error| FfiError::Discovery {
                        reason: error.to_string(),
                    })?,
                )
            }
            _ => {
                return Err(FfiError::Discovery {
                    reason: "metadata fields must be all present or all absent".into(),
                });
            }
        };
        let advertisement = opencrown_core::discovery::Advertisement::new(
            address,
            rssi.clamp(i32::from(i16::MIN), i32::from(i16::MAX)) as i16,
            name,
            metadata,
        )
        .map_err(|error| FfiError::Discovery {
            reason: error.to_string(),
        })?;
        self.lock()?
            .observe(advertisement, now_ms)
            .map(|token| token.to_string())
            .map_err(|error| FfiError::Discovery {
                reason: error.to_string(),
            })
    }

    pub fn candidates(&self, now_ms: u64) -> Result<Vec<FfiCandidate>, FfiError> {
        Ok(self
            .lock()?
            .candidates(now_ms)
            .into_iter()
            .map(|candidate| FfiCandidate {
                token: candidate.token.to_string(),
                name: candidate.local_name,
                rssi: i32::from(candidate.rssi),
                selectable: candidate.selectable,
                product_type: candidate
                    .metadata
                    .as_ref()
                    .map(|m| m.product_type().to_string()),
                os_version: candidate
                    .metadata
                    .as_ref()
                    .map(|m| m.os_version().to_string()),
                last_seen_ms: candidate.last_seen_ms,
            })
            .collect())
    }

    pub fn select(&self, token: String, now_ms: u64) -> Result<FfiSelection, FfiError> {
        let token = token
            .parse::<opencrown_core::discovery::CandidateToken>()
            .map_err(|error| FfiError::Discovery {
                reason: error.to_string(),
            })?;
        let selection =
            self.lock()?
                .select(token, now_ms)
                .map_err(|error| FfiError::Discovery {
                    reason: error.to_string(),
                })?;
        Ok(FfiSelection {
            token: selection.token.to_string(),
            product_type: selection.metadata.product_type().to_string(),
            os_version: selection.metadata.os_version().to_string(),
            rssi: i32::from(selection.rssi),
        })
    }
}

impl DiscoveryBridge {
    fn lock(
        &self,
    ) -> Result<std::sync::MutexGuard<'_, opencrown_core::discovery::Discovery>, FfiError> {
        self.inner.lock().map_err(|_| FfiError::Config {
            reason: "discovery lock poisoned".into(),
        })
    }
}

fn decode_address(value: &str) -> Result<[u8; 6], FfiError> {
    let bytes = opencrown_runtime::hex::decode(value).ok_or(FfiError::Discovery {
        reason: "address is not hex".into(),
    })?;
    bytes.try_into().map_err(|_| FfiError::Discovery {
        reason: "address must be six bytes".into(),
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
        assert_eq!(prepared.boot_id.len(), 32);
        assert!(Path::new(&prepared.entry_path).exists());
        let _ = std::fs::remove_dir_all(&work);
    }

    #[test]
    fn inspects_the_example_bundle() {
        let bundle = Path::new(env!("CARGO_MANIFEST_DIR")).join("../catalog/examples/signed");
        let info = inspect_bundle(bundle.to_string_lossy().into_owned()).unwrap();
        assert_eq!(info.id, "org.opencrown.payload.example");
        assert_eq!(info.version, "1.0.0");
        assert_eq!(info.capabilities, vec!["root", "domain", "controller"]);
        assert_eq!(info.targets, 1);
        assert_eq!(info.digest.len(), 64);
    }

    #[test]
    fn inspects_the_committed_device_list() {
        let path = Path::new(env!("CARGO_MANIFEST_DIR")).join("../catalog/supported-devices.json");
        let json = std::fs::read_to_string(path).unwrap();
        let devices = parse_supported_devices(json.clone()).unwrap();
        assert_eq!(devices.len(), 2);
        assert_eq!(devices[0].model, "EXAMPLE-1");
        assert_eq!(devices[0].status, "example");
        assert_eq!(devices[1].model, "SM-A528B");
        assert_eq!(devices[1].status, "example");

        assert_eq!(
            device_status(
                json,
                "EXAMPLE-1".into(),
                "example-6.1.0".into(),
                "arm64-v8a".into(),
                36
            )
            .unwrap(),
            "example"
        );
    }

    #[test]
    fn unknown_device_status() {
        let path = Path::new(env!("CARGO_MANIFEST_DIR")).join("../catalog/supported-devices.json");
        let json = std::fs::read_to_string(path).unwrap();
        assert_eq!(
            device_status(json, "OTHER".into(), "other".into(), "arm64-v8a".into(), 36).unwrap(),
            "unknown"
        );
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
    #[test]
    fn core_rules_come_from_the_core() {
        let rules = core_rules();
        assert_eq!(rules.discovery_ttl_ms, 10_000);
        assert_eq!(rules.discovery_max_candidates, 32);
        assert_eq!(rules.ids_backoff_secs, vec![15, 30, 60]);
        assert_eq!(rules.face_readback_timeout_ms, 180_000);
        assert_eq!(rules.setup_checkpoint_first, 1);
        assert_eq!(rules.setup_checkpoint_last, 18);
    }

    #[test]
    fn discovery_bridge_keeps_inventory_and_selection_in_rust() {
        let bridge = DiscoveryBridge::new(10_000);
        bridge.start_scan().unwrap();
        let token = bridge
            .observe(
                "010203040506".into(),
                -40,
                Some("Watch".into()),
                None,
                None,
                None,
                100,
            )
            .unwrap();
        let candidates = bridge.candidates(100).unwrap();
        assert_eq!(candidates.len(), 1);
        assert!(!candidates[0].selectable);
        assert!(bridge.select(token, 100).is_err());

        let token = bridge
            .observe(
                "010203040506".into(),
                -38,
                Some("Watch".into()),
                Some("Watch7,5".into()),
                Some("26.2.0".into()),
                Some("0102".into()),
                110,
            )
            .unwrap();
        let candidates = bridge.candidates(110).unwrap();
        assert_eq!(candidates.len(), 1);
        assert!(candidates[0].selectable);
        assert_eq!(candidates[0].product_type.as_deref(), Some("Watch7,5"));
        let selection = bridge.select(token, 110).unwrap();
        assert_eq!(selection.os_version, "26.2.0");
        bridge.stop_scan().unwrap();
    }

    fn example_root(tag: &str) -> PathBuf {
        let source = example_bundle();
        let root = std::env::temp_dir().join(format!("opencrown-ffi-{tag}-{}", std::process::id()));
        let destination = root.join("org.opencrown.payload.example");
        std::fs::create_dir_all(destination.join("files")).unwrap();
        for name in [
            "manifest.json",
            "manifest.sig",
            "files/payload.bin",
            "files/agent",
        ] {
            std::fs::copy(source.join(name), destination.join(name)).unwrap();
        }
        root
    }

    fn readiness_index() -> String {
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
                    "android": ">=31",
                    "abi": "arm64-v8a",
                    "targets": [
                        {"model": "EXAMPLE-1", "kernel_build": "example-6.1.0"}
                    ]
                }
            ]
        }"#
        .to_string()
    }

    #[test]
    fn probes_a_known_and_an_unknown_device() {
        let path = Path::new(env!("CARGO_MANIFEST_DIR")).join("../catalog/supported-devices.json");
        let json = std::fs::read_to_string(path).unwrap();
        let known = probe_device(
            "EXAMPLE-1".into(),
            "example-6.1.0".into(),
            "arm64-v8a".into(),
            36,
            "EXAMPLE".into(),
            "example/fingerprint".into(),
            json.clone(),
        )
        .unwrap();
        assert_eq!(known.supported_status, "example");
        assert_eq!(known.digest.len(), 64);
        assert_eq!(known.build_fingerprint, "example/fingerprint");

        let unknown = probe_device(
            "OTHER".into(),
            "other".into(),
            "arm64-v8a".into(),
            36,
            "OTHER".into(),
            "other/fingerprint".into(),
            json,
        )
        .unwrap();
        assert_eq!(unknown.supported_status, "unknown");
    }

    #[test]
    fn readiness_ready_bundle_crosses_the_boundary() {
        let bundle = example_bundle();
        let public_hex = std::fs::read_to_string(bundle.join("public_key.hex")).unwrap();
        let root = example_root("ready");
        let results = evaluate_readiness(
            readiness_index(),
            root.to_string_lossy().into_owned(),
            "EXAMPLE-1".into(),
            "example-6.1.0".into(),
            "arm64-v8a".into(),
            36,
            "EXAMPLE".into(),
            "0.1.0".into(),
            vec![public_hex.trim().into()],
            String::new(),
        )
        .unwrap();
        assert_eq!(results.len(), 1);
        assert_eq!(results[0].reason, "ready");
        assert_eq!(results[0].bundle_version.as_deref(), Some("1.0.0"));
        let _ = std::fs::remove_dir_all(root);
    }

    #[test]
    fn readiness_without_bundle_crosses_the_boundary() {
        let bundle = example_bundle();
        let public_hex = std::fs::read_to_string(bundle.join("public_key.hex")).unwrap();
        let root = std::env::temp_dir().join(format!("opencrown-ffi-empty-{}", std::process::id()));
        let results = evaluate_readiness(
            readiness_index(),
            root.to_string_lossy().into_owned(),
            "EXAMPLE-1".into(),
            "example-6.1.0".into(),
            "arm64-v8a".into(),
            36,
            "EXAMPLE".into(),
            "0.1.0".into(),
            vec![public_hex.trim().into()],
            String::new(),
        )
        .unwrap();
        assert_eq!(results[0].reason, "no_bundle");
        let _ = std::fs::remove_dir_all(root);
    }
}
