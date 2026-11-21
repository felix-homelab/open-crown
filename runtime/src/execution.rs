//! Execution supervisor: consent-gated launch of a staged payload entry.
//!
//! This is the ground layer between "the bundle is ready" and "the payload
//! bootstrap runs". It re-checks the staged entry, requires consent that
//! matches the manifest, and launches the entry with the one-time token and
//! boot id. It does not gain privilege, write policy, or know about HCI
//! (ADR 0015).

use std::fmt;
use std::io::Read;
use std::path::Path;
use std::process::{Command, Stdio};
use std::thread;
use std::time::{Duration, Instant};

use sha2::{Digest, Sha256};

use crate::Prepared;
use crate::hex;
use crate::manifest::Manifest;
use crate::stage::Staged;

/// What the user approved. The app turns its typed confirmation into this
/// record only after the consent screen's blockers are clear.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Consent {
    pub payload_id: String,
    pub version: String,
    pub capabilities: Vec<String>,
    pub accepted_at_epoch_ms: u64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ExecutionReport {
    pub exit_code: Option<i32>,
    pub timed_out: bool,
    pub stdout: String,
    pub stderr: String,
}

impl ExecutionReport {
    pub fn succeeded(&self) -> bool {
        !self.timed_out && self.exit_code == Some(0)
    }
}

#[derive(Debug)]
pub enum ExecutionError {
    Consent(String),
    Staged(String),
    Io(std::io::Error),
    Timeout,
}

impl fmt::Display for ExecutionError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Consent(what) => write!(f, "consent: {what}"),
            Self::Staged(what) => write!(f, "staged entry: {what}"),
            Self::Io(err) => write!(f, "execution io: {err}"),
            Self::Timeout => write!(f, "execution timed out"),
        }
    }
}

impl std::error::Error for ExecutionError {}

impl From<std::io::Error> for ExecutionError {
    fn from(err: std::io::Error) -> Self {
        Self::Io(err)
    }
}

/// Execute a prepared entry. The caller has already passed readiness and
/// consent; this function re-checks the bytes and the consent record, then
/// spawns the entry with `CROWN_TOKEN` and `CROWN_BOOT_ID`.
pub fn execute(
    prepared: &Prepared,
    consent: &Consent,
    args: &[String],
    timeout: Duration,
) -> Result<ExecutionReport, ExecutionError> {
    verify_consent(&prepared.manifest, consent)?;
    verify_staged_entry(&prepared.manifest, &prepared.staged)?;
    make_executable(&prepared.staged.entry)?;

    let mut command = Command::new(&prepared.staged.entry);
    command
        .args(args)
        .env("CROWN_TOKEN", prepared.token.expose())
        .env("CROWN_BOOT_ID", prepared.boot_id.expose())
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    #[cfg(unix)]
    {
        use std::os::unix::process::CommandExt;
        command.process_group(0);
    }
    let mut child = command.spawn()?;

    let stdout = child
        .stdout
        .take()
        .map(read_limited)
        .unwrap_or_else(|| thread::spawn(String::new));
    let stderr = child
        .stderr
        .take()
        .map(read_limited)
        .unwrap_or_else(|| thread::spawn(String::new));

    let started = Instant::now();
    let mut timed_out = false;
    let status = loop {
        if let Some(status) = child.try_wait()? {
            break Some(status);
        }
        if started.elapsed() >= timeout {
            #[cfg(unix)]
            unsafe {
                libc::kill(-(child.id() as i32), libc::SIGKILL);
            }
            #[cfg(not(unix))]
            {
                let _ = child.kill();
            }
            timed_out = true;
            break child.wait().ok();
        }
        thread::sleep(Duration::from_millis(10));
    };

    Ok(ExecutionReport {
        exit_code: status.and_then(|status| status.code()),
        timed_out,
        stdout: stdout.join().unwrap_or_default(),
        stderr: stderr.join().unwrap_or_default(),
    })
}

fn verify_consent(manifest: &Manifest, consent: &Consent) -> Result<(), ExecutionError> {
    if consent.payload_id != manifest.id {
        return Err(ExecutionError::Consent(format!(
            "approved {} but bundle is {}",
            consent.payload_id, manifest.id
        )));
    }
    if consent.version != manifest.version {
        return Err(ExecutionError::Consent(format!(
            "approved version {} but bundle is {}",
            consent.version, manifest.version
        )));
    }
    if consent.accepted_at_epoch_ms == 0 {
        return Err(ExecutionError::Consent(
            "consent has no acceptance time".to_string(),
        ));
    }
    let mut approved = consent.capabilities.clone();
    approved.sort();
    let mut required = manifest.capabilities.clone();
    required.sort();
    if approved != required {
        return Err(ExecutionError::Consent(format!(
            "approved capabilities [{}] do not match manifest [{}]",
            approved.join(","),
            required.join(",")
        )));
    }
    Ok(())
}

fn verify_staged_entry(manifest: &Manifest, staged: &Staged) -> Result<(), ExecutionError> {
    let artifact = manifest
        .artifacts
        .iter()
        .find(|artifact| artifact.path == manifest.entry)
        .ok_or_else(|| ExecutionError::Staged("entry is not an artifact".to_string()))?;
    let bytes = std::fs::read(&staged.entry)
        .map_err(|err| ExecutionError::Staged(format!("cannot read entry: {err}")))?;
    if bytes.len() as u64 != artifact.size {
        return Err(ExecutionError::Staged(format!(
            "entry size changed: expected {}, found {}",
            artifact.size,
            bytes.len()
        )));
    }
    let found = hex::encode(&Sha256::digest(&bytes));
    if found != artifact.sha256 {
        return Err(ExecutionError::Staged(
            "entry SHA-256 changed after staging".to_string(),
        ));
    }
    Ok(())
}

#[cfg(unix)]
fn make_executable(path: &Path) -> Result<(), ExecutionError> {
    use std::os::unix::fs::PermissionsExt;
    let mut permissions = std::fs::metadata(path)?.permissions();
    permissions.set_mode(0o700);
    std::fs::set_permissions(path, permissions)?;
    Ok(())
}

#[cfg(not(unix))]
fn make_executable(_path: &Path) -> Result<(), ExecutionError> {
    Ok(())
}

fn read_limited(mut stream: impl Read + Send + 'static) -> thread::JoinHandle<String> {
    thread::spawn(move || {
        let mut text = String::new();
        let _ = stream.by_ref().take(64 * 1024).read_to_string(&mut text);
        text
    })
}

/// In-memory retry budget for one boot. The app holds one instance; a future
/// service can persist the same rule. The limit is three attempts.
#[derive(Debug)]
pub struct ExecutionBudget {
    limit: u32,
    used: std::sync::atomic::AtomicU32,
}

impl ExecutionBudget {
    pub fn new(limit: u32) -> Self {
        Self {
            limit,
            used: std::sync::atomic::AtomicU32::new(0),
        }
    }

    pub fn default_for_boot() -> Self {
        Self::new(3)
    }

    pub fn try_acquire(&self) -> Option<u32> {
        use std::sync::atomic::Ordering;
        let attempt = self.used.fetch_add(1, Ordering::SeqCst) + 1;
        if attempt > self.limit {
            self.used.fetch_sub(1, Ordering::SeqCst);
            None
        } else {
            Some(attempt)
        }
    }

    pub fn used(&self) -> u32 {
        self.used.load(std::sync::atomic::Ordering::SeqCst)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::manifest::{Artifact, Target};
    use crate::stage;
    use std::fs;
    use std::path::PathBuf;
    use std::sync::atomic::{AtomicUsize, Ordering};

    static COUNTER: AtomicUsize = AtomicUsize::new(0);

    fn temp(tag: &str) -> PathBuf {
        let mut path = std::env::temp_dir();
        path.push(format!(
            "opencrown-execution-{tag}-{}-{}",
            std::process::id(),
            COUNTER.fetch_add(1, Ordering::SeqCst)
        ));
        path
    }

    fn prepared(tag: &str, script: &str) -> (Prepared, PathBuf) {
        let root = temp(tag);
        let bundle = root.join("bundle");
        let work = root.join("work");
        fs::create_dir_all(bundle.join("files")).unwrap();
        let agent = b"#!/bin/sh\n";
        fs::write(bundle.join("files/payload.sh"), script).unwrap();
        fs::write(bundle.join("files/agent"), agent).unwrap();
        let manifest = Manifest {
            schema: 1,
            id: "org.opencrown.payload.example".into(),
            version: "1.0.0".into(),
            min_agent: "0.1.0".into(),
            family: "page-cache".into(),
            targets: vec![Target {
                model: "EXAMPLE-1".into(),
                kernel_build: "example-6.1.0".into(),
                abi: "arm64-v8a".into(),
                android: ">=31".into(),
            }],
            capabilities: vec!["diag".into()],
            entry: "files/payload.sh".into(),
            agent: "files/agent".into(),
            persistence: "boot".into(),
            reversible: "reboot".into(),
            artifacts: vec![
                Artifact {
                    path: "files/payload.sh".into(),
                    sha256: hex::encode(&Sha256::digest(script.as_bytes())),
                    size: script.len() as u64,
                },
                Artifact {
                    path: "files/agent".into(),
                    sha256: hex::encode(&Sha256::digest(agent)),
                    size: agent.len() as u64,
                },
            ],
            notes: None,
        };
        let staged = stage::stage(&bundle, &manifest, &work).unwrap();
        let token = crate::launch::new_token().unwrap();
        let boot_id = crate::launch::new_boot_id().unwrap();
        (
            Prepared {
                manifest,
                staged,
                token,
                boot_id,
            },
            root,
        )
    }

    fn consent(capabilities: &[&str]) -> Consent {
        Consent {
            payload_id: "org.opencrown.payload.example".into(),
            version: "1.0.0".into(),
            capabilities: capabilities.iter().map(|value| value.to_string()).collect(),
            accepted_at_epoch_ms: 1_700_000_000_000,
        }
    }

    #[cfg(unix)]
    #[test]
    fn runs_the_staged_entry_with_session_env() {
        let (prepared, root) = prepared(
            "run",
            "#!/bin/sh\nprintf '%s:%s' \"$CROWN_TOKEN\" \"$CROWN_BOOT_ID\"\n",
        );
        let report = execute(&prepared, &consent(&["diag"]), &[], Duration::from_secs(2)).unwrap();
        assert!(
            report.succeeded(),
            "stdout={} stderr={}",
            report.stdout,
            report.stderr
        );
        assert_eq!(
            report.stdout,
            format!("{}:{}", prepared.token.expose(), prepared.boot_id.expose())
        );
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn rejects_consent_for_another_payload() {
        let (prepared, root) = prepared("consent-id", "#!/bin/sh\nexit 0\n");
        let mut approval = consent(&["diag"]);
        approval.payload_id = "org.opencrown.payload.other".into();
        let error = execute(&prepared, &approval, &[], Duration::from_secs(1)).unwrap_err();
        assert!(matches!(error, ExecutionError::Consent(_)));
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn rejects_missing_capability() {
        let (prepared, root) = prepared("consent-caps", "#!/bin/sh\nexit 0\n");
        let error = execute(&prepared, &consent(&[]), &[], Duration::from_secs(1)).unwrap_err();
        assert!(matches!(error, ExecutionError::Consent(_)));
        let _ = fs::remove_dir_all(root);
    }

    #[cfg(unix)]
    #[test]
    fn detects_tampering_after_staging() {
        let (prepared, root) = prepared("tamper", "#!/bin/sh\nexit 0\n");
        fs::write(&prepared.staged.entry, "#!/bin/sh\necho tampered\n").unwrap();
        let error =
            execute(&prepared, &consent(&["diag"]), &[], Duration::from_secs(1)).unwrap_err();
        assert!(matches!(error, ExecutionError::Staged(_)));
        let _ = fs::remove_dir_all(root);
    }

    #[cfg(unix)]
    #[test]
    fn kills_a_hung_entry() {
        let (prepared, root) = prepared("timeout", "#!/bin/sh\nsleep 5\n");
        let started = Instant::now();
        let report = execute(
            &prepared,
            &consent(&["diag"]),
            &[],
            Duration::from_millis(100),
        )
        .unwrap();
        assert!(started.elapsed() < Duration::from_secs(2));
        assert!(report.timed_out);
        assert!(!report.succeeded());
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn budget_is_three_per_boot() {
        let budget = ExecutionBudget::default_for_boot();
        assert_eq!(budget.try_acquire(), Some(1));
        assert_eq!(budget.try_acquire(), Some(2));
        assert_eq!(budget.try_acquire(), Some(3));
        assert_eq!(budget.try_acquire(), None);
        assert_eq!(budget.used(), 3);
    }
}
