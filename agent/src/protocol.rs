//! Agent IPC envelope, version 1.
//!
//! One protobuf envelope per connection. Validation happens before any
//! controller operation, and every limit is explicit here rather than trusted
//! from the wire (docs/02-architecture.md section 2.3).

pub mod bridge {
    include!(concat!(env!("OUT_DIR"), "/opencrown.agent.v1.rs"));
}

pub use bridge::*;

use bridge::envelope::Request;

use std::fmt;

use prost::Message;

pub const VERSION: u32 = 1;
pub const MAX_ENVELOPE: usize = 4096;
pub const MAX_DATA: usize = 512;
pub const MIN_PSM: u32 = 0x1001;
pub const MAX_PSM: u32 = 0xFFFF;
pub const MAX_HANDLE: u32 = 255;
pub const MAX_TIMEOUT_MS: u32 = 30_000;
pub const TOKEN_HEX_LEN: usize = 64;

#[derive(Debug, PartialEq, Eq)]
pub enum IpcError {
    TooLarge(usize),
    Decode(String),
    UnsupportedVersion(u32),
    BadToken,
    BadBootId,
    MissingRequest,
    BadAddress(usize),
    BadPsm(u32),
    BadHandle(u32),
    BadMode(i32),
    PayloadTooLarge(usize),
    TimeoutTooLarge(u32),
}

impl fmt::Display for IpcError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::TooLarge(size) => write!(f, "envelope too large: {size}"),
            Self::Decode(why) => write!(f, "decode failed: {why}"),
            Self::UnsupportedVersion(version) => {
                write!(f, "unsupported envelope version: {version}")
            }
            Self::BadToken => write!(f, "token must be 64 lowercase hex characters"),
            Self::BadBootId => write!(f, "boot id must be 1..=128 safe characters"),
            Self::MissingRequest => write!(f, "envelope has no request"),
            Self::BadAddress(len) => write!(f, "address must be 6 bytes, got {len}"),
            Self::BadPsm(psm) => write!(f, "psm out of range: {psm:#x}"),
            Self::BadHandle(handle) => write!(f, "handle out of range: {handle}"),
            Self::BadMode(mode) => write!(f, "channel mode is unspecified: {mode}"),
            Self::PayloadTooLarge(size) => write!(f, "send payload too large: {size}"),
            Self::TimeoutTooLarge(ms) => write!(f, "timeout too large: {ms}"),
        }
    }
}

impl std::error::Error for IpcError {}

pub fn decode(bytes: &[u8]) -> Result<Envelope, IpcError> {
    if bytes.len() > MAX_ENVELOPE {
        return Err(IpcError::TooLarge(bytes.len()));
    }
    Envelope::decode(bytes).map_err(|err| IpcError::Decode(err.to_string()))
}

pub fn encode(envelope: &Envelope) -> Vec<u8> {
    envelope.encode_to_vec()
}

pub fn validate(envelope: &Envelope) -> Result<(), IpcError> {
    if envelope.version != VERSION {
        return Err(IpcError::UnsupportedVersion(envelope.version));
    }
    if !valid_token(&envelope.token) {
        return Err(IpcError::BadToken);
    }
    if !valid_boot_id(&envelope.boot_id) {
        return Err(IpcError::BadBootId);
    }

    match envelope.request.as_ref().ok_or(IpcError::MissingRequest)? {
        Request::Scan(scan) => {
            if scan.timeout_ms > MAX_TIMEOUT_MS {
                return Err(IpcError::TimeoutTooLarge(scan.timeout_ms));
            }
        }
        Request::Connect(connect) => {
            if connect.address.len() != 6 {
                return Err(IpcError::BadAddress(connect.address.len()));
            }
        }
        Request::OpenChannel(open) => {
            if !(MIN_PSM..=MAX_PSM).contains(&open.psm) {
                return Err(IpcError::BadPsm(open.psm));
            }
            if ChannelMode::try_from(open.mode).unwrap_or(ChannelMode::Unspecified)
                == ChannelMode::Unspecified
            {
                return Err(IpcError::BadMode(open.mode));
            }
        }
        Request::Send(send) => {
            if !(1..=MAX_HANDLE).contains(&send.handle) {
                return Err(IpcError::BadHandle(send.handle));
            }
            if send.data.len() > MAX_DATA {
                return Err(IpcError::PayloadTooLarge(send.data.len()));
            }
        }
        Request::Close(close) => {
            if !(1..=MAX_HANDLE).contains(&close.handle) {
                return Err(IpcError::BadHandle(close.handle));
            }
        }
        Request::Diag(_) => {}
    }

    Ok(())
}

fn valid_token(token: &str) -> bool {
    token.len() == TOKEN_HEX_LEN
        && token
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
}

fn valid_boot_id(boot_id: &str) -> bool {
    !boot_id.is_empty()
        && boot_id.len() <= 128
        && boot_id
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || byte == b'-' || byte == b'_')
}

#[cfg(test)]
mod tests {
    use super::*;

    fn envelope(request: Request) -> Envelope {
        Envelope {
            version: VERSION,
            boot_id: "boot-1".to_string(),
            token: "a".repeat(64),
            request: Some(request),
        }
    }

    #[test]
    fn valid_scan_roundtrips() {
        let original = envelope(Request::Scan(ScanRequest {
            setup_only: true,
            timeout_ms: 1000,
        }));
        let bytes = encode(&original);
        let decoded = decode(&bytes).unwrap();
        validate(&decoded).unwrap();
        assert_eq!(decoded, original);
    }

    #[test]
    fn rejects_wrong_version() {
        let mut value = envelope(Request::Diag(DiagRequest {}));
        value.version = 2;
        assert_eq!(validate(&value), Err(IpcError::UnsupportedVersion(2)));
    }

    #[test]
    fn rejects_bad_tokens() {
        for token in ["", "abc", &"A".repeat(64), &"g".repeat(64)] {
            let mut value = envelope(Request::Diag(DiagRequest {}));
            value.token = token.to_string();
            assert_eq!(validate(&value), Err(IpcError::BadToken));
        }
    }

    #[test]
    fn rejects_bad_boot_id() {
        let mut value = envelope(Request::Diag(DiagRequest {}));
        value.boot_id = "boot 1".to_string();
        assert_eq!(validate(&value), Err(IpcError::BadBootId));
    }

    #[test]
    fn requires_a_request() {
        let mut value = envelope(Request::Diag(DiagRequest {}));
        value.request = None;
        assert_eq!(validate(&value), Err(IpcError::MissingRequest));
    }

    #[test]
    fn rejects_bad_address() {
        let value = envelope(Request::Connect(ConnectRequest {
            address: vec![1, 2, 3],
        }));
        assert_eq!(validate(&value), Err(IpcError::BadAddress(3)));
    }

    #[test]
    fn rejects_bad_psm_and_mode() {
        let low = envelope(Request::OpenChannel(OpenChannelRequest {
            psm: 0x1000,
            mode: ChannelMode::Ertm as i32,
        }));
        assert_eq!(validate(&low), Err(IpcError::BadPsm(0x1000)));

        let mode = envelope(Request::OpenChannel(OpenChannelRequest {
            psm: 0x1001,
            mode: ChannelMode::Unspecified as i32,
        }));
        assert_eq!(validate(&mode), Err(IpcError::BadMode(0)));
    }

    #[test]
    fn rejects_oversized_payload() {
        let value = envelope(Request::Send(SendRequest {
            handle: 1,
            data: vec![0u8; MAX_DATA + 1],
        }));
        assert_eq!(
            validate(&value),
            Err(IpcError::PayloadTooLarge(MAX_DATA + 1))
        );
    }

    #[test]
    fn rejects_oversized_envelope_before_decoding() {
        let bytes = vec![0u8; MAX_ENVELOPE + 1];
        assert_eq!(decode(&bytes), Err(IpcError::TooLarge(MAX_ENVELOPE + 1)));
    }

    #[test]
    fn rejects_zero_handle() {
        let value = envelope(Request::Close(CloseRequest { handle: 0 }));
        assert_eq!(validate(&value), Err(IpcError::BadHandle(0)));
    }
}
