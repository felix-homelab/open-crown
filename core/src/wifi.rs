//! Wi-Fi provisioning guards.
//!
//! The Wi-Fi archive byte layout would be a secret carrier, so this module
//! deliberately does not hold a passphrase or encode an archive. It implements
//! the established guards: WPA2-PSK/CCMP only, at most three reads five seconds
//! apart, a ten-second archive lifetime, one successful dispatch per setup
//! generation, and the rule that an ACK means delivery, never a join.

use std::fmt;

pub const ARCHIVE_LIFETIME_MS: u64 = 10_000;
pub const MAX_READS: u8 = 3;
pub const READ_INTERVAL_MS: u64 = 5_000;
pub const MAX_SSID_BYTES: usize = 32;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Security {
    Wpa2PskCcmp,
    Wpa3,
    Open,
    Other,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct NetworkProfile {
    ssid: String,
    security: Security,
}

impl NetworkProfile {
    pub fn new(ssid: impl Into<String>, security: Security) -> Result<Self, WifiError> {
        let ssid = ssid.into();
        if ssid.is_empty() || ssid.len() > MAX_SSID_BYTES {
            return Err(WifiError::InvalidSsid);
        }
        Ok(Self { ssid, security })
    }

    pub fn ssid(&self) -> &str {
        &self.ssid
    }

    pub fn security(&self) -> Security {
        self.security
    }
}

/// An encoded archive handed in by the caller. It carries no plaintext
/// passphrase into this type's debug output.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Archive {
    generation: u64,
    created_ms: u64,
}

impl Archive {
    pub fn generation(&self) -> u64 {
        self.generation
    }

    pub fn created_ms(&self) -> u64 {
        self.created_ms
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Delivery {
    /// The Watch received the archive. This says nothing about joining.
    Delivered,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum WifiError {
    Unsupported,
    InvalidSsid,
    ReadTooSoon,
    TooManyReads,
    GenerationChanged,
    AlreadyDispatched,
    Expired,
    NotDispatched,
}

impl fmt::Display for WifiError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let code = match self {
            Self::Unsupported => "WIFI_UNSUPPORTED",
            Self::InvalidSsid => "WIFI_INVALID_SSID",
            Self::ReadTooSoon => "WIFI_READ_TOO_SOON",
            Self::TooManyReads => "WIFI_TOO_MANY_READS",
            Self::GenerationChanged => "WIFI_GENERATION_CHANGED",
            Self::AlreadyDispatched => "WIFI_ALREADY_DISPATCHED",
            Self::Expired => "WIFI_EXPIRED",
            Self::NotDispatched => "WIFI_NOT_DISPATCHED",
        };
        write!(f, "{code}")
    }
}

impl std::error::Error for WifiError {}

/// One setup generation's Wi-Fi worker state.
#[derive(Debug)]
pub struct WifiProvisioner {
    generation: u64,
    reads: u8,
    last_read_ms: Option<u64>,
    dispatched_generation: Option<u64>,
}

impl WifiProvisioner {
    pub fn new(generation: u64) -> Self {
        Self {
            generation,
            reads: 0,
            last_read_ms: None,
            dispatched_generation: None,
        }
    }

    pub fn generation(&self) -> u64 {
        self.generation
    }

    pub fn reads(&self) -> u8 {
        self.reads
    }

    pub fn has_dispatched(&self) -> bool {
        self.dispatched_generation == Some(self.generation)
    }

    /// Reads the platform network. The caller is responsible for not putting the
    /// passphrase in logs; this type only sees the SSID and security class.
    pub fn read_archive(
        &mut self,
        profile: &NetworkProfile,
        now_ms: u64,
    ) -> Result<Archive, WifiError> {
        if profile.security != Security::Wpa2PskCcmp {
            return Err(WifiError::Unsupported);
        }
        if self.reads >= MAX_READS {
            return Err(WifiError::TooManyReads);
        }
        if let Some(last) = self.last_read_ms {
            if now_ms.saturating_sub(last) < READ_INTERVAL_MS {
                return Err(WifiError::ReadTooSoon);
            }
        }
        self.reads = self.reads.saturating_add(1);
        self.last_read_ms = Some(now_ms);
        Ok(Archive {
            generation: self.generation,
            created_ms: now_ms,
        })
    }

    /// Dispatches an archive for this setup generation at most once.
    pub fn dispatch(
        &mut self,
        archive: &Archive,
        current_generation: u64,
        now_ms: u64,
    ) -> Result<Delivery, WifiError> {
        if current_generation != self.generation || archive.generation != self.generation {
            return Err(WifiError::GenerationChanged);
        }
        if now_ms.saturating_sub(archive.created_ms) > ARCHIVE_LIFETIME_MS {
            return Err(WifiError::Expired);
        }
        if self.dispatched_generation == Some(self.generation) {
            return Err(WifiError::AlreadyDispatched);
        }
        self.dispatched_generation = Some(self.generation);
        Ok(Delivery::Delivered)
    }

    /// A join is only reported from independent evidence, never from the ACK.
    pub fn join_state(&self, independently_observed: bool) -> JoinState {
        if !self.has_dispatched() {
            JoinState::NotAttempted
        } else if independently_observed {
            JoinState::ObservedJoin
        } else {
            JoinState::DeliveryOnly
        }
    }

    /// A new pair/setup generation cancels the old worker.
    pub fn rebind(&mut self, generation: u64) {
        self.generation = generation;
        self.reads = 0;
        self.last_read_ms = None;
        self.dispatched_generation = None;
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum JoinState {
    NotAttempted,
    DeliveryOnly,
    ObservedJoin,
}

#[cfg(test)]
mod tests {
    use super::*;

    fn network() -> NetworkProfile {
        NetworkProfile::new("CrownTest", Security::Wpa2PskCcmp).unwrap()
    }

    #[test]
    fn only_wpa2_psk_ccmp_is_supported() {
        let mut worker = WifiProvisioner::new(1);
        let wpa3 = NetworkProfile::new("CrownTest", Security::Wpa3).unwrap();
        assert!(matches!(
            worker.read_archive(&wpa3, 0),
            Err(WifiError::Unsupported)
        ));
        assert!(worker.read_archive(&network(), 0).is_ok());
    }

    #[test]
    fn reads_are_bounded_and_spaced() {
        let mut worker = WifiProvisioner::new(1);
        assert!(worker.read_archive(&network(), 0).is_ok());
        assert!(matches!(
            worker.read_archive(&network(), 1_000),
            Err(WifiError::ReadTooSoon)
        ));
        assert!(worker.read_archive(&network(), 5_000).is_ok());
        assert!(worker.read_archive(&network(), 10_000).is_ok());
        assert!(matches!(
            worker.read_archive(&network(), 15_000),
            Err(WifiError::TooManyReads)
        ));
    }

    #[test]
    fn archive_expires_after_ten_seconds() {
        let mut worker = WifiProvisioner::new(4);
        let archive = worker.read_archive(&network(), 1_000).unwrap();
        assert!(matches!(
            worker.dispatch(&archive, 4, 11_001),
            Err(WifiError::Expired)
        ));
        let archive = worker.read_archive(&network(), 20_000).unwrap();
        assert_eq!(
            worker.dispatch(&archive, 4, 20_500).unwrap(),
            Delivery::Delivered
        );
    }

    #[test]
    fn one_dispatch_per_generation() {
        let mut worker = WifiProvisioner::new(4);
        let archive = worker.read_archive(&network(), 0).unwrap();
        worker.dispatch(&archive, 4, 1).unwrap();
        let archive = worker.read_archive(&network(), 5_000).unwrap();
        assert!(matches!(
            worker.dispatch(&archive, 4, 5_100),
            Err(WifiError::AlreadyDispatched)
        ));
    }

    #[test]
    fn generation_change_refuses_the_archive() {
        let mut worker = WifiProvisioner::new(4);
        let archive = worker.read_archive(&network(), 0).unwrap();
        assert!(matches!(
            worker.dispatch(&archive, 5, 1),
            Err(WifiError::GenerationChanged)
        ));
        worker.rebind(5);
        assert!(matches!(
            worker.dispatch(&archive, 5, 1),
            Err(WifiError::GenerationChanged)
        ));
    }

    #[test]
    fn ack_is_delivery_not_join() {
        let mut worker = WifiProvisioner::new(1);
        assert_eq!(worker.join_state(false), JoinState::NotAttempted);
        let archive = worker.read_archive(&network(), 0).unwrap();
        worker.dispatch(&archive, 1, 1).unwrap();
        assert_eq!(worker.join_state(false), JoinState::DeliveryOnly);
        assert_eq!(worker.join_state(true), JoinState::ObservedJoin);
    }

    #[test]
    fn invalid_ssid_is_refused() {
        assert!(matches!(
            NetworkProfile::new("", Security::Wpa2PskCcmp),
            Err(WifiError::InvalidSsid)
        ));
        let too_long = "x".repeat(MAX_SSID_BYTES + 1);
        assert!(matches!(
            NetworkProfile::new(too_long, Security::Wpa2PskCcmp),
            Err(WifiError::InvalidSsid)
        ));
    }
}
