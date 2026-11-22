//! Discovery lifecycle and advertisement inventory.
//!
//! The wire layout of the Watch's setup advertisement is **UNKNOWN**
//! (docs/cleanroom/spec/01-discovery.md section 6). This module therefore
//! implements only what is specified: a bounded inventory, opaque candidate
//! tokens, freshness, and a selection step that refuses anything without
//! decoded setup metadata. It never turns "Apple manufacturer data" into
//! "pairable watch".
//!
//! The decoder that will produce [SetupMetadata] belongs to a later change,
//! once a Lane A capture establishes FE25 offsets. Until then the app can
//! observe advertisements but cannot make any of them selectable.

use std::fmt;

/// How long an advertisement remains a live candidate without being refreshed.
///
/// This is an Open Crown product policy, not a protocol fact. The reference
/// polls at roughly 250 ms; the spec does not establish an expiry.
pub const CANDIDATE_TTL_MS: u64 = 10_000;

/// Hard bound so a noisy radio cannot grow the inventory without limit.
pub const MAX_CANDIDATES: usize = 32;

/// Bound on the opaque metadata retained for a candidate.
pub const MAX_METADATA_BYTES: usize = 64;

/// Bound on a display name. Names are display-only and never identity.
pub const MAX_NAME_LEN: usize = 64;

/// Identifier for one scan session. Tokens are only valid inside their session.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct ScanId(u64);

impl ScanId {
    pub fn value(self) -> u64 {
        self.0
    }
}

/// Opaque handle returned to the UI. It deliberately contains no address.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct CandidateToken {
    scan: ScanId,
    serial: u64,
    revision: u32,
}

impl CandidateToken {
    pub fn scan(self) -> ScanId {
        self.scan
    }
}

/// Decoded setup metadata from FE25.
///
/// The constructor is public for tests and for the future decoder in this
/// crate. It validates every field so a malformed advertisement can never
/// become a display string with control characters.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SetupMetadata {
    product_type: String,
    os_version: String,
    raw: Vec<u8>,
}

impl SetupMetadata {
    pub fn decoded(
        product_type: impl Into<String>,
        os_version: impl Into<String>,
        raw: &[u8],
    ) -> Result<Self, DiscoveryError> {
        let product_type = product_type.into();
        let os_version = os_version.into();
        validate_text(&product_type, "product type")?;
        validate_text(&os_version, "os version")?;
        if raw.is_empty() {
            return Err(DiscoveryError::InvalidMetadata("empty FE25 payload"));
        }
        if raw.len() > MAX_METADATA_BYTES {
            return Err(DiscoveryError::InvalidMetadata("FE25 payload too long"));
        }
        Ok(Self {
            product_type,
            os_version,
            raw: raw.to_vec(),
        })
    }

    pub fn product_type(&self) -> &str {
        &self.product_type
    }

    pub fn os_version(&self) -> &str {
        &self.os_version
    }

    /// Raw FE25 bytes, retained for a future spec and for diagnostics.
    pub fn raw(&self) -> &[u8] {
        &self.raw
    }
}

fn validate_text(value: &str, field: &'static str) -> Result<(), DiscoveryError> {
    if value.is_empty() || value.len() > MAX_NAME_LEN {
        return Err(DiscoveryError::InvalidMetadata(match field {
            "product type" => "product type length",
            _ => "os version length",
        }));
    }
    if !value
        .bytes()
        .all(|b| b.is_ascii_alphanumeric() || matches!(b, b'.' | b',' | b'-' | b'_' | b' '))
    {
        return Err(DiscoveryError::InvalidMetadata(match field {
            "product type" => "product type charset",
            _ => "os version charset",
        }));
    }
    Ok(())
}

/// One advertisement observation, already stripped of platform objects.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Advertisement {
    address: [u8; 6],
    rssi: i16,
    local_name: Option<String>,
    setup_metadata: Option<SetupMetadata>,
}

impl Advertisement {
    pub fn new(
        address: [u8; 6],
        rssi: i16,
        local_name: Option<String>,
        setup_metadata: Option<SetupMetadata>,
    ) -> Result<Self, DiscoveryError> {
        let local_name = match local_name {
            None => None,
            Some(name) => {
                let trimmed = name.trim();
                if trimmed.is_empty() {
                    None
                } else if trimmed.chars().count() > MAX_NAME_LEN {
                    Some(trimmed.chars().take(MAX_NAME_LEN).collect())
                } else if trimmed.chars().any(char::is_control) {
                    return Err(DiscoveryError::InvalidAdvertisement(
                        "control character in name",
                    ));
                } else {
                    Some(trimmed.to_string())
                }
            }
        };
        Ok(Self {
            address,
            rssi,
            local_name,
            setup_metadata,
        })
    }

    pub fn rssi(&self) -> i16 {
        self.rssi
    }

    pub fn local_name(&self) -> Option<&str> {
        self.local_name.as_deref()
    }

    pub fn setup_metadata(&self) -> Option<&SetupMetadata> {
        self.setup_metadata.as_ref()
    }
}

/// A live candidate. The address stays inside the state machine.
#[derive(Debug, Clone)]
struct Candidate {
    token: CandidateToken,
    address: [u8; 6],
    first_seen_ms: u64,
    last_seen_ms: u64,
    rssi: i16,
    local_name: Option<String>,
    setup_metadata: Option<SetupMetadata>,
    revision: u32,
}

impl Candidate {
    fn selectable(&self) -> bool {
        self.setup_metadata.is_some()
    }

    fn changed_by(&self, ad: &Advertisement) -> bool {
        self.local_name != ad.local_name || self.setup_metadata != ad.setup_metadata
    }

    fn view(&self) -> CandidateView {
        CandidateView {
            token: self.token,
            local_name: self.local_name.clone(),
            rssi: self.rssi,
            selectable: self.selectable(),
            metadata: self.setup_metadata.clone(),
            first_seen_ms: self.first_seen_ms,
            last_seen_ms: self.last_seen_ms,
        }
    }
}

/// What the UI may render. No transport address is exposed.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CandidateView {
    pub token: CandidateToken,
    pub local_name: Option<String>,
    pub rssi: i16,
    pub selectable: bool,
    pub metadata: Option<SetupMetadata>,
    pub first_seen_ms: u64,
    pub last_seen_ms: u64,
}

/// The result of a successful selection. Still no address: the transport gets
/// the token, and only the discovery owner can resolve it internally.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Selection {
    pub token: CandidateToken,
    pub metadata: SetupMetadata,
    pub rssi: i16,
}

/// A discovered token, its metadata, and no advertising identity.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum DiscoveryError {
    NotScanning,
    UnknownCandidate,
    StaleCandidate,
    NotSelectable,
    InvalidMetadata(&'static str),
    InvalidAdvertisement(&'static str),
}

impl fmt::Display for DiscoveryError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::NotScanning => write!(f, "DISCOVERY_SCAN_FAILED: not scanning"),
            Self::UnknownCandidate => write!(f, "DISCOVERY_STALE_TOKEN: unknown candidate"),
            Self::StaleCandidate => {
                write!(f, "DISCOVERY_STALE_TOKEN: candidate expired or changed")
            }
            Self::NotSelectable => {
                write!(f, "DISCOVERY_METADATA_INVALID: no decoded setup metadata")
            }
            Self::InvalidMetadata(what) => write!(f, "DISCOVERY_METADATA_INVALID: {what}"),
            Self::InvalidAdvertisement(what) => write!(f, "DISCOVERY_METADATA_INVALID: {what}"),
        }
    }
}

impl std::error::Error for DiscoveryError {}

#[derive(Debug)]
struct Scan {
    id: ScanId,
    candidates: Vec<Candidate>,
}

/// The discovery state machine.
#[derive(Debug)]
pub struct Discovery {
    scan: Option<Scan>,
    next_scan: u64,
    next_serial: u64,
    ttl_ms: u64,
}

impl Default for Discovery {
    fn default() -> Self {
        Self::new(CANDIDATE_TTL_MS)
    }
}

impl Discovery {
    pub fn new(ttl_ms: u64) -> Self {
        Self {
            scan: None,
            next_scan: 1,
            next_serial: 1,
            ttl_ms: ttl_ms.max(1),
        }
    }

    pub fn is_scanning(&self) -> bool {
        self.scan.is_some()
    }

    /// Starts a new session. Any previous inventory and token namespace dies.
    pub fn start_scan(&mut self) -> ScanId {
        let id = ScanId(self.next_scan);
        self.next_scan = self.next_scan.wrapping_add(1).max(1);
        self.next_serial = 1;
        self.scan = Some(Scan {
            id,
            candidates: Vec::new(),
        });
        id
    }

    /// Ends the session and invalidates every token from it.
    pub fn stop_scan(&mut self) {
        self.scan = None;
    }

    /// Records one advertisement and returns the token the UI may hold.
    pub fn observe(
        &mut self,
        advertisement: Advertisement,
        now_ms: u64,
    ) -> Result<CandidateToken, DiscoveryError> {
        if self.scan.is_none() {
            return Err(DiscoveryError::NotScanning);
        }
        self.expire(now_ms);
        let scan_id = self.scan.as_ref().expect("scan checked").id;
        let existing = self.scan.as_ref().and_then(|scan| {
            scan.candidates
                .iter()
                .position(|candidate| candidate.address == advertisement.address)
        });

        if let Some(index) = existing {
            let (changed, revision) = {
                let candidate = &self.scan.as_ref().expect("scan checked").candidates[index];
                (candidate.changed_by(&advertisement), candidate.revision)
            };
            let token = if changed {
                let token = self.mint(scan_id, revision.wrapping_add(1));
                let candidate = &mut self.scan.as_mut().expect("scan checked").candidates[index];
                candidate.revision = revision.wrapping_add(1);
                candidate.setup_metadata = advertisement.setup_metadata.clone();
                candidate.local_name = advertisement.local_name.clone();
                candidate.token = token;
                token
            } else {
                self.scan.as_ref().expect("scan checked").candidates[index].token
            };
            let candidate = &mut self.scan.as_mut().expect("scan checked").candidates[index];
            candidate.last_seen_ms = now_ms;
            candidate.rssi = advertisement.rssi;
            return Ok(token);
        }

        let scan = self.scan.as_mut().expect("scan checked");
        if scan.candidates.len() >= MAX_CANDIDATES {
            let oldest = scan
                .candidates
                .iter()
                .enumerate()
                .min_by_key(|(_, candidate)| candidate.last_seen_ms)
                .map(|(index, _)| index)
                .ok_or(DiscoveryError::NotScanning)?;
            scan.candidates.remove(oldest);
        }

        let token = self.mint(scan_id, 0);
        let scan = self.scan.as_mut().expect("scan checked");
        scan.candidates.push(Candidate {
            token,
            address: advertisement.address,
            first_seen_ms: now_ms,
            last_seen_ms: now_ms,
            rssi: advertisement.rssi,
            local_name: advertisement.local_name,
            setup_metadata: advertisement.setup_metadata,
            revision: 0,
        });
        Ok(token)
    }

    /// Drops candidates that have not been refreshed inside the TTL.
    pub fn expire(&mut self, now_ms: u64) {
        if let Some(scan) = self.scan.as_mut() {
            scan.candidates
                .retain(|candidate| now_ms.saturating_sub(candidate.last_seen_ms) <= self.ttl_ms);
        }
    }

    /// Current inventory, newest signal first. Expired entries are removed.
    pub fn candidates(&mut self, now_ms: u64) -> Vec<CandidateView> {
        self.expire(now_ms);
        let mut views: Vec<CandidateView> = self
            .scan
            .as_ref()
            .map(|scan| scan.candidates.iter().map(Candidate::view).collect())
            .unwrap_or_default();
        views.sort_by(|a, b| b.rssi.cmp(&a.rssi).then(a.token.cmp(&b.token)));
        views
    }

    /// Rechecks freshness and metadata before handing a candidate to pairing.
    pub fn select(
        &mut self,
        token: CandidateToken,
        now_ms: u64,
    ) -> Result<Selection, DiscoveryError> {
        self.expire(now_ms);
        let scan = self.scan.as_ref().ok_or(DiscoveryError::StaleCandidate)?;
        if token.scan != scan.id {
            return Err(DiscoveryError::StaleCandidate);
        }
        let candidate = scan
            .candidates
            .iter()
            .find(|candidate| candidate.token == token)
            .ok_or(DiscoveryError::StaleCandidate)?;
        let metadata = candidate
            .setup_metadata
            .clone()
            .ok_or(DiscoveryError::NotSelectable)?;
        Ok(Selection {
            token,
            metadata,
            rssi: candidate.rssi,
        })
    }

    fn mint(&mut self, scan: ScanId, revision: u32) -> CandidateToken {
        let serial = self.next_serial;
        self.next_serial = self.next_serial.wrapping_add(1).max(1);
        CandidateToken {
            scan,
            serial,
            revision,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn metadata() -> SetupMetadata {
        SetupMetadata::decoded("Watch7,5", "26.2.0", &[1, 2, 3, 4]).unwrap()
    }

    fn advertisement(metadata: Option<SetupMetadata>) -> Advertisement {
        Advertisement::new([1, 2, 3, 4, 5, 6], -42, Some("Watch".into()), metadata).unwrap()
    }

    #[test]
    fn unknown_metadata_is_visible_but_not_selectable() {
        let mut discovery = Discovery::default();
        discovery.start_scan();
        let token = discovery.observe(advertisement(None), 100).unwrap();
        let views = discovery.candidates(100);
        assert_eq!(views.len(), 1);
        assert!(!views[0].selectable);
        assert!(matches!(
            discovery.select(token, 100),
            Err(DiscoveryError::NotSelectable)
        ));
    }

    #[test]
    fn decoded_metadata_is_selectable_inside_the_ttl() {
        let mut discovery = Discovery::default();
        discovery.start_scan();
        let token = discovery
            .observe(advertisement(Some(metadata())), 100)
            .unwrap();
        let selection = discovery.select(token, 200).unwrap();
        assert_eq!(selection.metadata.product_type(), "Watch7,5");
        assert_eq!(selection.metadata.os_version(), "26.2.0");
    }

    #[test]
    fn expiry_invalidates_a_token() {
        let mut discovery = Discovery::new(50);
        discovery.start_scan();
        let token = discovery
            .observe(advertisement(Some(metadata())), 100)
            .unwrap();
        assert!(matches!(
            discovery.select(token, 151),
            Err(DiscoveryError::StaleCandidate)
        ));
        assert!(discovery.candidates(151).is_empty());
    }

    #[test]
    fn changed_metadata_invalidates_the_old_token() {
        let mut discovery = Discovery::default();
        discovery.start_scan();
        let first = discovery
            .observe(advertisement(Some(metadata())), 100)
            .unwrap();
        let changed = SetupMetadata::decoded("Watch7,6", "26.3.0", &[9, 9]).unwrap();
        let second = discovery
            .observe(advertisement(Some(changed)), 200)
            .unwrap();
        assert_ne!(first, second);
        assert!(matches!(
            discovery.select(first, 200),
            Err(DiscoveryError::StaleCandidate)
        ));
        assert_eq!(
            discovery
                .select(second, 200)
                .unwrap()
                .metadata
                .product_type(),
            "Watch7,6"
        );
    }

    #[test]
    fn a_new_scan_invalidates_old_tokens() {
        let mut discovery = Discovery::default();
        discovery.start_scan();
        let token = discovery
            .observe(advertisement(Some(metadata())), 100)
            .unwrap();
        discovery.start_scan();
        assert!(matches!(
            discovery.select(token, 100),
            Err(DiscoveryError::StaleCandidate)
        ));
    }

    #[test]
    fn stop_scan_invalidates_tokens() {
        let mut discovery = Discovery::default();
        discovery.start_scan();
        let token = discovery
            .observe(advertisement(Some(metadata())), 100)
            .unwrap();
        discovery.stop_scan();
        assert!(matches!(
            discovery.select(token, 100),
            Err(DiscoveryError::StaleCandidate)
        ));
        assert!(!discovery.is_scanning());
    }

    #[test]
    fn inventory_is_bounded_and_evicts_the_stalest() {
        let mut discovery = Discovery::default();
        discovery.start_scan();
        for i in 0..(MAX_CANDIDATES as u8) {
            let ad = Advertisement::new([i, 0, 0, 0, 0, 1], -40, None, None).unwrap();
            discovery.observe(ad, u64::from(i)).unwrap();
        }
        let extra = Advertisement::new([255, 0, 0, 0, 0, 1], -30, None, None).unwrap();
        discovery.observe(extra, MAX_CANDIDATES as u64).unwrap();
        assert!(discovery.candidates(MAX_CANDIDATES as u64).len() <= MAX_CANDIDATES);
    }

    #[test]
    fn malformed_metadata_is_rejected_at_construction() {
        assert!(matches!(
            SetupMetadata::decoded("", "26.2", &[1]),
            Err(DiscoveryError::InvalidMetadata(_))
        ));
        assert!(matches!(
            SetupMetadata::decoded(
                "Watch
7",
                "26.2",
                &[1]
            ),
            Err(DiscoveryError::InvalidMetadata(_))
        ));
        assert!(matches!(
            SetupMetadata::decoded("Watch7,5", "26.2", &[]),
            Err(DiscoveryError::InvalidMetadata(_))
        ));
        let too_long = vec![0u8; MAX_METADATA_BYTES + 1];
        assert!(matches!(
            SetupMetadata::decoded("Watch7,5", "26.2", &too_long),
            Err(DiscoveryError::InvalidMetadata(_))
        ));
    }

    #[test]
    fn control_characters_are_rejected_in_names() {
        assert!(matches!(
            Advertisement::new(
                [1; 6],
                -40,
                Some(
                    "bad
name"
                        .into()
                ),
                None
            ),
            Err(DiscoveryError::InvalidAdvertisement(_))
        ));
    }

    #[test]
    fn observing_without_a_scan_is_an_error() {
        let mut discovery = Discovery::default();
        assert!(matches!(
            discovery.observe(advertisement(None), 0),
            Err(DiscoveryError::NotScanning)
        ));
    }
}
