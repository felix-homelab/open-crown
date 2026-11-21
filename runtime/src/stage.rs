//! Staging: copy verified artifacts into a private directory.
//!
//! Every byte is checked again here. The runtime never trusts the bundle just
//! because the manifest was valid.

use std::fs;
use std::path::{Path, PathBuf};

use sha2::{Digest, Sha256};

use crate::error::RuntimeError;
use crate::hex;
use crate::manifest::Manifest;

#[derive(Debug, Clone)]
pub struct Staged {
    pub dir: PathBuf,
    pub entry: PathBuf,
    pub agent: PathBuf,
}

pub fn stage(bundle: &Path, manifest: &Manifest, work: &Path) -> Result<Staged, RuntimeError> {
    fs::create_dir_all(work)?;
    restrict(work, 0o700)?;

    for artifact in &manifest.artifacts {
        let source = bundle.join(&artifact.path);
        let bytes = match fs::read(&source) {
            Ok(bytes) => bytes,
            Err(err) if err.kind() == std::io::ErrorKind::NotFound => {
                return Err(RuntimeError::Missing(artifact.path.clone()));
            }
            Err(err) => return Err(err.into()),
        };

        if bytes.len() as u64 != artifact.size {
            return Err(RuntimeError::Invalid(format!(
                "size mismatch for {}: declared {}, found {}",
                artifact.path,
                artifact.size,
                bytes.len()
            )));
        }

        let found = hex::encode(&Sha256::digest(&bytes));
        if found != artifact.sha256 {
            return Err(RuntimeError::Invalid(format!(
                "hash mismatch for {}",
                artifact.path
            )));
        }

        let name = name_of(&artifact.path)?;
        let destination = work.join(name);
        fs::write(&destination, &bytes)?;
        restrict(&destination, 0o600)?;
    }

    let entry = work.join(name_of(&manifest.entry)?);
    let agent = work.join(name_of(&manifest.agent)?);
    if !entry.is_file() {
        return Err(RuntimeError::Missing(manifest.entry.clone()));
    }
    if !agent.is_file() {
        return Err(RuntimeError::Missing(manifest.agent.clone()));
    }

    Ok(Staged {
        dir: work.to_path_buf(),
        entry,
        agent,
    })
}

fn name_of(path: &str) -> Result<&str, RuntimeError> {
    Path::new(path)
        .file_name()
        .and_then(|name| name.to_str())
        .ok_or_else(|| RuntimeError::Invalid(format!("artifact has no file name: {path}")))
}

#[cfg(unix)]
fn restrict(path: &Path, mode: u32) -> std::io::Result<()> {
    use std::os::unix::fs::PermissionsExt;
    fs::set_permissions(path, fs::Permissions::from_mode(mode))
}

#[cfg(not(unix))]
fn restrict(_path: &Path, _mode: u32) -> std::io::Result<()> {
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::manifest::{Artifact, Manifest, Target};
    use std::sync::atomic::{AtomicUsize, Ordering};

    static COUNTER: AtomicUsize = AtomicUsize::new(0);

    fn temp(tag: &str) -> PathBuf {
        let mut path = std::env::temp_dir();
        path.push(format!(
            "opencrown-{tag}-{}-{}",
            std::process::id(),
            COUNTER.fetch_add(1, Ordering::SeqCst)
        ));
        path
    }

    fn bundle(tag: &str) -> (PathBuf, Manifest) {
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
                    sha256: hex::encode(&Sha256::digest(payload)),
                    size: payload.len() as u64,
                },
                Artifact {
                    path: "files/agent".into(),
                    sha256: hex::encode(&Sha256::digest(agent)),
                    size: agent.len() as u64,
                },
            ],
            notes: None,
        };
        (dir, manifest)
    }

    #[test]
    fn stages_verified_artifacts() {
        let (dir, manifest) = bundle("stage-ok");
        let work = temp("stage-work");
        let staged = stage(&dir, &manifest, &work).unwrap();
        assert_eq!(fs::read(&staged.entry).unwrap(), b"payload-bytes");
        assert_eq!(fs::read(&staged.agent).unwrap(), b"agent-bytes");
        assert!(staged.dir.ends_with(work.file_name().unwrap()));
        let _ = fs::remove_dir_all(&dir);
        let _ = fs::remove_dir_all(&work);
    }

    #[test]
    fn rejects_tampered_artifact() {
        let (dir, manifest) = bundle("stage-tamper");
        fs::write(dir.join("files/payload.bin"), b"evil").unwrap();
        let work = temp("stage-tamper-work");
        let error = stage(&dir, &manifest, &work).unwrap_err();
        assert!(error.to_string().contains("mismatch"));
        let _ = fs::remove_dir_all(&dir);
        let _ = fs::remove_dir_all(&work);
    }

    #[test]
    fn rejects_missing_artifact() {
        let (dir, manifest) = bundle("stage-missing");
        fs::remove_file(dir.join("files/agent")).unwrap();
        let work = temp("stage-missing-work");
        let error = stage(&dir, &manifest, &work).unwrap_err();
        assert!(error.to_string().contains("missing"));
        let _ = fs::remove_dir_all(&dir);
        let _ = fs::remove_dir_all(&work);
    }

    #[cfg(unix)]
    #[test]
    fn restricts_permissions() {
        use std::os::unix::fs::PermissionsExt;

        let (dir, manifest) = bundle("stage-mode");
        let work = temp("stage-mode-work");
        let staged = stage(&dir, &manifest, &work).unwrap();
        let mode = fs::metadata(&staged.agent).unwrap().permissions().mode() & 0o777;
        assert_eq!(mode, 0o600);
        let dir_mode = fs::metadata(&staged.dir).unwrap().permissions().mode() & 0o777;
        assert_eq!(dir_mode, 0o700);
        let _ = fs::remove_dir_all(&dir);
        let _ = fs::remove_dir_all(&work);
    }
}
