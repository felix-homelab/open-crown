//! Pairing branch and bond-ordering rules.
//!
//! uIKE, SPAKE2+, OOB and SMP wire formats are **UNKNOWN**
//! (docs/cleanroom/spec/02-pairing-security.md section 9). This module models
//! what is established: the two mutually exclusive branches, bounded PIN
//! retries, the optical PIN-downgrade refusal, bond persistence as a condition
//! of BONDED, normal-link ordering, and replacement safety. No cryptography is
//! implemented here.

use std::fmt;

/// Number of PIN attempts a session permits before closing. The reference does
/// not publish a limit; this is an Open Crown product policy.
pub const MAX_PIN_ATTEMPTS: u8 = 3;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Branch {
    Pin,
    Optical,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Phase {
    Selected,
    LinkUp,
    ControlChannel,
    ControlSa,
    PinRequired,
    PinAuth,
    PskNegotiate,
    PskAuth,
    PrivateNotify,
    Smp,
    BondPending,
    Bonded,
    NormalLink,
    Closed,
}

impl Phase {
    pub fn is_terminal(self) -> bool {
        matches!(self, Self::NormalLink | Self::Closed)
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum PairingError {
    InvalidPhase(&'static str),
    WrongBranch,
    InvalidPinFormat,
    PinRejected { attempts_left: u8 },
    OpticalMalformed(&'static str),
    OpticalStale,
    OpticalDowngrade,
    OpticalMacInvalid,
    SmpTimeout,
    BondStoreFailed,
    DuplicateOrReordered,
    ReplacementNotVerified,
    Closed,
}

impl fmt::Display for PairingError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let code = match self {
            Self::InvalidPhase(_) => "PAIR_INVALID_PHASE",
            Self::WrongBranch => "PAIR_WRONG_BRANCH",
            Self::InvalidPinFormat => "PAIR_PIN_FORMAT",
            Self::PinRejected { .. } => "PAIR_PIN_REJECTED",
            Self::OpticalMalformed(_) => "PAIR_OPTICAL_MALFORMED",
            Self::OpticalStale => "PAIR_OPTICAL_STALE",
            Self::OpticalDowngrade => "PAIR_OPTICAL_METHOD_MISMATCH",
            Self::OpticalMacInvalid => "PAIR_OPTICAL_MAC_INVALID",
            Self::SmpTimeout => "PAIR_SMP_TIMEOUT",
            Self::BondStoreFailed => "PAIR_BOND_STORE_FAILED",
            Self::DuplicateOrReordered => "PAIR_DUPLICATE_OR_REORDERED",
            Self::ReplacementNotVerified => "PAIR_REPLACEMENT_NOT_VERIFIED",
            Self::Closed => "PAIR_CLOSED",
        };
        write!(f, "{code}")
    }
}

impl std::error::Error for PairingError {}

/// The established optical payload fields. Decoding the image and checking the
/// responder MAC are separate, unverified work.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct OpticalPayload {
    format: u8,
    compatibility: u32,
    advertised_name: String,
    key: [u8; 32],
    material: u8,
    size: u16,
    os: String,
}

impl OpticalPayload {
    pub fn format(&self) -> u8 {
        self.format
    }

    pub fn compatibility(&self) -> u32 {
        self.compatibility
    }

    pub fn advertised_name(&self) -> &str {
        &self.advertised_name
    }

    pub fn key(&self) -> &[u8; 32] {
        &self.key
    }

    pub fn material(&self) -> u8 {
        self.material
    }

    pub fn size(&self) -> u16 {
        self.size
    }

    pub fn os(&self) -> &str {
        &self.os
    }

    /// The 44-byte derived secret is the 32-byte key followed by the 12-byte
    /// advertisement. Callers must still verify the responder MAC.
    pub fn derived_secret(&self, advertisement: &[u8]) -> Result<[u8; 44], PairingError> {
        if advertisement.len() != 12 {
            return Err(PairingError::OpticalStale);
        }
        let mut out = [0u8; 44];
        out[..32].copy_from_slice(&self.key);
        out[32..].copy_from_slice(advertisement);
        Ok(out)
    }
}

/// Parses the text form established by the reference:
/// format--compatibility--advertisedName--hexKey--material--size&&OS
///
/// The image/watermark/CRC layer is not implemented; this only parses an
/// already-decoded 110-byte payload's text projection.
pub fn parse_optical_payload(text: &str) -> Result<OpticalPayload, PairingError> {
    if text.len() > 512 {
        return Err(PairingError::OpticalMalformed("payload too long"));
    }
    let (fields, os) = text
        .split_once("&&")
        .ok_or(PairingError::OpticalMalformed("missing OS suffix"))?;
    if os.is_empty() || !os.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'.') {
        return Err(PairingError::OpticalMalformed("bad OS suffix"));
    }
    let parts: Vec<&str> = fields.split("--").collect();
    if parts.len() != 6 {
        return Err(PairingError::OpticalMalformed("expected six fields"));
    }
    let format = parts[0]
        .parse::<u8>()
        .map_err(|_| PairingError::OpticalMalformed("format"))?;
    if format != 4 {
        return Err(PairingError::OpticalMalformed("unsupported format"));
    }
    let compatibility = parts[1]
        .parse::<u32>()
        .map_err(|_| PairingError::OpticalMalformed("compatibility"))?;
    let advertised_name = parts[2];
    if advertised_name.is_empty()
        || advertised_name.len() > 64
        || !advertised_name
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
    {
        return Err(PairingError::OpticalMalformed("advertised name"));
    }
    let key_hex = parts[3];
    if key_hex.len() != 64 || !key_hex.bytes().all(|b| b.is_ascii_hexdigit()) {
        return Err(PairingError::OpticalMalformed("key not 32 bytes of hex"));
    }
    let mut key = [0u8; 32];
    for index in 0..32 {
        key[index] = u8::from_str_radix(&key_hex[index * 2..index * 2 + 2], 16)
            .map_err(|_| PairingError::OpticalMalformed("key hex"))?;
    }
    let material = parts[4]
        .parse::<u8>()
        .map_err(|_| PairingError::OpticalMalformed("material"))?;
    let size = parts[5]
        .parse::<u16>()
        .map_err(|_| PairingError::OpticalMalformed("size"))?;
    Ok(OpticalPayload {
        format,
        compatibility,
        advertised_name: advertised_name.to_string(),
        key,
        material,
        size,
        os: os.to_string(),
    })
}

/// A single pairing attempt. Counters and transient material do not carry over
/// to a new attempt.
#[derive(Debug)]
pub struct PairingSession {
    session: u64,
    phase: Phase,
    branch: Option<Branch>,
    pin_attempts: u8,
    replacement: bool,
    old_bond_deleted: bool,
}

impl PairingSession {
    pub fn new(session: u64, replacement: bool) -> Self {
        Self {
            session,
            phase: Phase::Selected,
            branch: None,
            pin_attempts: 0,
            replacement,
            old_bond_deleted: false,
        }
    }

    pub fn session(&self) -> u64 {
        self.session
    }

    pub fn phase(&self) -> Phase {
        self.phase
    }

    pub fn branch(&self) -> Option<Branch> {
        self.branch
    }

    pub fn pin_attempts(&self) -> u8 {
        self.pin_attempts
    }

    pub fn connect(&mut self) -> Result<(), PairingError> {
        self.transition(Phase::LinkUp, "connect")
    }

    pub fn control_channel(&mut self) -> Result<(), PairingError> {
        self.transition(Phase::ControlChannel, "control_channel")
    }

    pub fn control_sa(&mut self) -> Result<(), PairingError> {
        self.transition(Phase::ControlSa, "control_sa")
    }

    pub fn begin_pin(&mut self) -> Result<(), PairingError> {
        self.require(Phase::ControlSa, "begin_pin")?;
        if self.branch.is_some() {
            return Err(PairingError::WrongBranch);
        }
        self.branch = Some(Branch::Pin);
        self.phase = Phase::PinRequired;
        Ok(())
    }

    pub fn begin_optical(&mut self) -> Result<(), PairingError> {
        self.require(Phase::ControlSa, "begin_optical")?;
        if self.branch.is_some() {
            return Err(PairingError::WrongBranch);
        }
        self.branch = Some(Branch::Optical);
        self.phase = Phase::PskNegotiate;
        Ok(())
    }

    /// Submits a six-digit PIN with the authentication result. The caller does
    /// not decide success; the protocol result does.
    pub fn submit_pin(&mut self, pin: &str, accepted: bool) -> Result<(), PairingError> {
        self.require(Phase::PinRequired, "submit_pin")?;
        if self.branch != Some(Branch::Pin) {
            return Err(PairingError::WrongBranch);
        }
        if pin.len() != 6 || !pin.bytes().all(|b| b.is_ascii_digit()) {
            return Err(PairingError::InvalidPinFormat);
        }
        if !accepted {
            self.pin_attempts = self.pin_attempts.saturating_add(1);
            if self.pin_attempts >= MAX_PIN_ATTEMPTS {
                self.phase = Phase::Closed;
                return Err(PairingError::PinRejected { attempts_left: 0 });
            }
            return Err(PairingError::PinRejected {
                attempts_left: MAX_PIN_ATTEMPTS - self.pin_attempts,
            });
        }
        self.phase = Phase::PinAuth;
        Ok(())
    }

    /// Completes the PIN branch after its authentication exchange. Counters are
    /// branch-specific; this model does not transport them.
    pub fn pin_authenticated(&mut self) -> Result<(), PairingError> {
        self.require(Phase::PinAuth, "pin_authenticated")?;
        self.phase = Phase::PrivateNotify;
        Ok(())
    }

    /// Optical branch: a PIN-capable response is a downgrade and aborts. A bad
    /// responder MAC aborts and wipes the attempt.
    pub fn optical_auth(
        &mut self,
        responder_mac_valid: bool,
        pin_selected_by_watch: bool,
    ) -> Result<(), PairingError> {
        self.require(Phase::PskNegotiate, "optical_auth")?;
        if self.branch != Some(Branch::Optical) {
            return Err(PairingError::WrongBranch);
        }
        if pin_selected_by_watch {
            self.phase = Phase::Closed;
            return Err(PairingError::OpticalDowngrade);
        }
        if !responder_mac_valid {
            self.phase = Phase::Closed;
            return Err(PairingError::OpticalMacInvalid);
        }
        self.phase = Phase::PskAuth;
        Ok(())
    }

    /// PSK_AUTH reaches private notifications after the responder MAC check.
    pub fn optical_private_notify(&mut self) -> Result<(), PairingError> {
        self.require(Phase::PskAuth, "optical_private_notify")?;
        self.phase = Phase::PrivateNotify;
        Ok(())
    }

    pub fn private_notified(&mut self) -> Result<(), PairingError> {
        self.require(Phase::PrivateNotify, "private_notified")?;
        self.phase = Phase::Smp;
        Ok(())
    }

    /// SMP completion moves to BOND_PENDING; it is not yet bonded.
    pub fn smp_complete(&mut self, timed_out: bool) -> Result<(), PairingError> {
        self.require(Phase::Smp, "smp_complete")?;
        if timed_out {
            self.phase = Phase::Closed;
            return Err(PairingError::SmpTimeout);
        }
        self.phase = Phase::BondPending;
        Ok(())
    }

    /// A storage failure must not produce BONDED.
    pub fn commit_bond(&mut self, store_ok: bool) -> Result<(), PairingError> {
        self.require(Phase::BondPending, "commit_bond")?;
        if !store_ok {
            self.phase = Phase::Closed;
            return Err(PairingError::BondStoreFailed);
        }
        self.phase = Phase::Bonded;
        Ok(())
    }

    pub fn open_normal_link(&mut self) -> Result<(), PairingError> {
        self.require(Phase::Bonded, "open_normal_link")?;
        self.phase = Phase::NormalLink;
        Ok(())
    }

    /// Replacement safety: the old pair may be deleted only after the new bond
    /// is verified. A session that is not replacing has nothing to delete.
    pub fn delete_old_bond(&mut self) -> Result<(), PairingError> {
        if self.phase == Phase::Closed {
            return Err(PairingError::Closed);
        }
        if !self.replacement {
            return Err(PairingError::ReplacementNotVerified);
        }
        if self.phase != Phase::Bonded && self.phase != Phase::NormalLink {
            return Err(PairingError::ReplacementNotVerified);
        }
        self.old_bond_deleted = true;
        Ok(())
    }

    pub fn old_bond_deleted(&self) -> bool {
        self.old_bond_deleted
    }

    pub fn fail(&mut self) {
        self.phase = Phase::Closed;
    }

    fn transition(&mut self, next: Phase, operation: &'static str) -> Result<(), PairingError> {
        if self.phase == Phase::Closed {
            return Err(PairingError::Closed);
        }
        if self.phase != Phase::Selected {
            let expected = match next {
                Phase::LinkUp => Phase::Selected,
                Phase::ControlChannel => Phase::LinkUp,
                Phase::ControlSa => Phase::ControlChannel,
                _ => return Err(PairingError::InvalidPhase(operation)),
            };
            self.require(expected, operation)?;
        }
        self.phase = next;
        Ok(())
    }

    fn require(&self, expected: Phase, operation: &'static str) -> Result<(), PairingError> {
        if self.phase == Phase::Closed {
            return Err(PairingError::Closed);
        }
        if self.phase != expected {
            return Err(PairingError::InvalidPhase(operation));
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn to_control_sa() -> PairingSession {
        let mut session = PairingSession::new(9, false);
        session.connect().unwrap();
        session.control_channel().unwrap();
        session.control_sa().unwrap();
        session
    }

    fn to_bond_pending() -> PairingSession {
        to_bond_pending_with(false)
    }

    fn to_bond_pending_with(replacement: bool) -> PairingSession {
        let mut session = PairingSession::new(9, replacement);
        session.connect().unwrap();
        session.control_channel().unwrap();
        session.control_sa().unwrap();
        session.begin_pin().unwrap();
        session.submit_pin("123456", true).unwrap();
        session.pin_authenticated().unwrap();
        session.private_notified().unwrap();
        session.smp_complete(false).unwrap();
        session
    }

    #[test]
    fn pin_path_reaches_normal_link_only_after_bond_commit() {
        let mut session = to_bond_pending();
        session.commit_bond(true).unwrap();
        assert_eq!(session.phase(), Phase::Bonded);
        session.open_normal_link().unwrap();
        assert_eq!(session.phase(), Phase::NormalLink);
    }

    #[test]
    fn wrong_pin_is_bounded_and_never_bonds() {
        let mut session = to_control_sa();
        session.begin_pin().unwrap();
        for attempt in 0..MAX_PIN_ATTEMPTS {
            let error = session.submit_pin("000000", false).unwrap_err();
            if attempt + 1 == MAX_PIN_ATTEMPTS {
                assert_eq!(session.phase(), Phase::Closed);
                assert!(matches!(
                    error,
                    PairingError::PinRejected { attempts_left: 0 }
                ));
            } else {
                assert!(matches!(error, PairingError::PinRejected { .. }));
            }
        }
        assert!(matches!(
            session.commit_bond(true),
            Err(PairingError::Closed)
        ));
        assert_ne!(session.phase(), Phase::Bonded);
    }

    #[test]
    fn malformed_pin_is_rejected_without_consuming_an_attempt() {
        let mut session = to_control_sa();
        session.begin_pin().unwrap();
        assert!(matches!(
            session.submit_pin("12ab", true),
            Err(PairingError::InvalidPinFormat)
        ));
        assert_eq!(session.pin_attempts(), 0);
        assert_eq!(session.phase(), Phase::PinRequired);
    }

    #[test]
    fn optical_rejects_a_pin_downgrade() {
        let mut session = to_control_sa();
        session.begin_optical().unwrap();
        assert!(matches!(
            session.optical_auth(true, true),
            Err(PairingError::OpticalDowngrade)
        ));
        assert_eq!(session.phase(), Phase::Closed);
    }

    #[test]
    fn optical_mac_failure_never_reaches_smp() {
        let mut session = to_control_sa();
        session.begin_optical().unwrap();
        assert!(matches!(
            session.optical_auth(false, false),
            Err(PairingError::OpticalMacInvalid)
        ));
        assert_eq!(session.phase(), Phase::Closed);
        assert!(matches!(
            session.private_notified(),
            Err(PairingError::Closed)
        ));
    }

    #[test]
    fn smp_timeout_and_store_failure_never_bond() {
        let mut session = to_control_sa();
        session.begin_pin().unwrap();
        session.submit_pin("123456", true).unwrap();
        session.pin_authenticated().unwrap();
        session.private_notified().unwrap();
        assert!(matches!(
            session.smp_complete(true),
            Err(PairingError::SmpTimeout)
        ));
        assert_eq!(session.phase(), Phase::Closed);
        assert!(matches!(
            session.commit_bond(true),
            Err(PairingError::Closed)
        ));

        let mut session = to_bond_pending();
        assert!(matches!(
            session.commit_bond(false),
            Err(PairingError::BondStoreFailed)
        ));
        assert_ne!(session.phase(), Phase::Bonded);
    }

    #[test]
    fn branches_are_exclusive() {
        let mut session = to_control_sa();
        session.begin_pin().unwrap();
        assert!(matches!(
            session.begin_optical(),
            Err(PairingError::InvalidPhase(_))
        ));
        assert_eq!(session.branch(), Some(Branch::Pin));
    }

    #[test]
    fn replacement_cannot_delete_old_bond_before_new_one_is_verified() {
        let mut session = PairingSession::new(10, true);
        session.connect().unwrap();
        assert!(matches!(
            session.delete_old_bond(),
            Err(PairingError::ReplacementNotVerified)
        ));
        let mut session = to_bond_pending_with(true);
        assert!(matches!(
            session.delete_old_bond(),
            Err(PairingError::ReplacementNotVerified)
        ));
        session.commit_bond(true).unwrap();
        session.delete_old_bond().unwrap();
        assert!(session.old_bond_deleted());
    }

    #[test]
    fn optical_payload_parses_established_fields() {
        let key = "11".repeat(32);
        let text = format!("4--25--Crown-Test--{key}--14--19&&26.2");
        let payload = parse_optical_payload(&text).unwrap();
        assert_eq!(payload.format(), 4);
        assert_eq!(payload.compatibility(), 25);
        assert_eq!(payload.advertised_name(), "Crown-Test");
        assert_eq!(payload.material(), 14);
        assert_eq!(payload.size(), 19);
        assert_eq!(payload.os(), "26.2");
        assert_eq!(payload.derived_secret(&[7; 12]).unwrap().len(), 44);
        assert!(matches!(
            payload.derived_secret(&[7; 11]),
            Err(PairingError::OpticalStale)
        ));
    }

    #[test]
    fn optical_payload_rejects_malformed_fields() {
        assert!(matches!(
            parse_optical_payload("3--25--Name--11--14--19&&26.2"),
            Err(PairingError::OpticalMalformed(_))
        ));
        assert!(matches!(
            parse_optical_payload("4--25--Name--zz--14--19&&26.2"),
            Err(PairingError::OpticalMalformed(_))
        ));
        assert!(matches!(
            parse_optical_payload("4--25--Name--1122--14--19&&26.2"),
            Err(PairingError::OpticalMalformed(_))
        ));
        assert!(matches!(
            parse_optical_payload("4--25--Name--1122--14--19"),
            Err(PairingError::OpticalMalformed(_))
        ));
    }
}
