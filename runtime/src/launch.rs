//! Session identity and agent launch.
//!
//! The agent is started with CROWN_TOKEN and CROWN_BOOT_ID; it refuses an
//! envelope that does not carry both. The launch itself is deliberately thin so
//! it can be replaced on Android by the payload's bootstrap path without
//! changing the contract.

use std::io::Read;
use std::path::Path;
use std::process::{Child, Command};

use crate::error::RuntimeError;
use crate::hex;

/// A one-time value the agent must see before it accepts a request.
#[derive(Debug, Clone)]
pub struct Token(String);

impl Token {
    pub fn expose(&self) -> &str {
        &self.0
    }
}

/// Identifies the boot that minted the token. Reusing a token from another boot
/// fails the agent's check.
#[derive(Debug, Clone)]
pub struct BootId(String);

impl BootId {
    pub fn expose(&self) -> &str {
        &self.0
    }
}

pub fn new_token() -> Result<Token, RuntimeError> {
    Ok(Token(random_hex(32)?))
}

pub fn new_boot_id() -> Result<BootId, RuntimeError> {
    Ok(BootId(random_hex(16)?))
}

pub fn launch(
    program: &Path,
    args: &[String],
    token: &Token,
    boot_id: &BootId,
) -> Result<Child, RuntimeError> {
    let child = Command::new(program)
        .args(args)
        .env("CROWN_TOKEN", token.expose())
        .env("CROWN_BOOT_ID", boot_id.expose())
        .spawn()?;
    Ok(child)
}

fn random_hex(len: usize) -> Result<String, RuntimeError> {
    #[cfg(unix)]
    {
        let mut bytes = vec![0u8; len];
        let mut file = std::fs::File::open("/dev/urandom")?;
        file.read_exact(&mut bytes)?;
        Ok(hex::encode(&bytes))
    }
    #[cfg(not(unix))]
    {
        use sha2::{Digest, Sha256};
        use std::time::{SystemTime, UNIX_EPOCH};

        let mut hasher = Sha256::new();
        hasher.update(std::process::id().to_le_bytes());
        if let Ok(now) = SystemTime::now().duration_since(UNIX_EPOCH) {
            hasher.update(now.as_nanos().to_le_bytes());
        }
        let digest = hasher.finalize();
        Ok(hex::encode(&digest[..len.min(32)]))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn tokens_are_hex_and_distinct() {
        let first = new_token().unwrap();
        let second = new_token().unwrap();
        assert_eq!(first.expose().len(), 64);
        assert!(first.expose().chars().all(|c| c.is_ascii_hexdigit()));
        assert_ne!(first.expose(), second.expose());
    }

    #[test]
    fn boot_ids_are_hex_and_distinct() {
        let first = new_boot_id().unwrap();
        let second = new_boot_id().unwrap();
        assert_eq!(first.expose().len(), 32);
        assert!(first.expose().chars().all(|c| c.is_ascii_hexdigit()));
        assert_ne!(first.expose(), second.expose());
    }

    #[cfg(unix)]
    #[test]
    fn token_and_boot_reach_the_child() {
        let token = new_token().unwrap();
        let boot_id = new_boot_id().unwrap();
        let mut child = launch(
            Path::new("/bin/sh"),
            &[
                "-c".to_string(),
                "test -n \"$CROWN_TOKEN\" && test -n \"$CROWN_BOOT_ID\"".to_string(),
            ],
            &token,
            &boot_id,
        )
        .unwrap();
        assert!(child.wait().unwrap().success());
    }
}
