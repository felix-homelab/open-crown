//! Token generation and agent launch.
//!
//! The token is passed in the environment; the agent is expected to validate it
//! and to reject a second start within the same boot. The launch itself is
//! deliberately thin so it can be replaced on Android by a JNI call into the
//! stager without changing the contract.

use std::io::Read;
use std::path::Path;
use std::process::{Child, Command};

use crate::error::RuntimeError;
use crate::hex;

/// A one-time value the agent must see before it accepts the socket.
#[derive(Debug, Clone)]
pub struct Token(String);

impl Token {
    pub fn expose(&self) -> &str {
        &self.0
    }
}

pub fn new_token() -> Result<Token, RuntimeError> {
    #[cfg(unix)]
    {
        let mut bytes = [0u8; 32];
        let mut file = std::fs::File::open("/dev/urandom")?;
        file.read_exact(&mut bytes)?;
        Ok(Token(hex::encode(&bytes)))
    }
    #[cfg(not(unix))]
    {
        use std::time::{SystemTime, UNIX_EPOCH};
        let nanos = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|value| value.as_nanos())
            .unwrap_or(0);
        Ok(Token(format!("{nanos:032x}{:08x}", std::process::id())))
    }
}

pub fn launch(program: &Path, args: &[String], token: &Token) -> Result<Child, RuntimeError> {
    let child = Command::new(program)
        .args(args)
        .env("CROWN_TOKEN", token.expose())
        .spawn()?;
    Ok(child)
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

    #[cfg(unix)]
    #[test]
    fn token_reaches_the_child() {
        let token = new_token().unwrap();
        let mut child = launch(
            Path::new("/bin/sh"),
            &["-c".to_string(), "test -n \"$CROWN_TOKEN\"".to_string()],
            &token,
        )
        .unwrap();
        assert!(child.wait().unwrap().success());
    }
}
