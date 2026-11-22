//! Settings preference and pigment rules.
//!
//! NPS value encodings beyond "binary plist" are **UNKNOWN**
//! (docs/cleanroom/spec/07-settings-wifi-health.md section 12). This module
//! implements the established behaviour: a missing or malformed value is
//! UNKNOWN rather than false, a write needs an observed baseline and an exact
//! read-back, and pigment writes carry both complete sets with their bounds.

use std::fmt;

/// Apple reference-date seconds use 2001-01-01; Unix uses 1970-01-01.
pub const APPLE_EPOCH_OFFSET_SECS: i64 = 978_307_200;
pub const MAX_PIGMENT_NAMES: usize = 1023;
pub const MAX_PIGMENT_BYTES: usize = 64 * 1024;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PreferenceKey {
    WornOnRightArm,
    InvertUi,
    Force24HourTime,
}

impl PreferenceKey {
    pub fn domain(self) -> &'static str {
        match self {
            Self::WornOnRightArm | Self::InvertUi => "com.apple.nano",
            Self::Force24HourTime => ".GlobalPreferences",
        }
    }

    pub fn native_key(self) -> &'static str {
        match self {
            Self::WornOnRightArm => "wornOnRightArm",
            Self::InvertUi => "invertUI",
            Self::Force24HourTime => "AppleICUForce24HourTime",
        }
    }

    /// The 24-hour key is not marked two-way by the reference.
    pub fn two_way(self) -> bool {
        !matches!(self, Self::Force24HourTime)
    }
}

/// A preference value. Unknown is a first-class state, not false.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PreferenceValue {
    Known(bool),
    Unknown,
}

impl PreferenceValue {
    pub fn from_optional(value: Option<bool>) -> Self {
        value.map_or(Self::Unknown, Self::Known)
    }

    pub fn as_bool(self) -> Option<bool> {
        match self {
            Self::Known(value) => Some(value),
            Self::Unknown => None,
        }
    }
}

pub fn apple_to_unix(seconds: i64) -> i64 {
    seconds.saturating_add(APPLE_EPOCH_OFFSET_SECS)
}

pub fn unix_to_apple(seconds: i64) -> Option<i64> {
    seconds.checked_sub(APPLE_EPOCH_OFFSET_SECS)
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PreferenceBaseline {
    pub pair_epoch: u64,
    pub session_epoch: u64,
    pub revision: u64,
}

/// A write is confirmed only by a newer observation of the exact key.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PreferencePhase {
    Planned,
    Sent,
    Applied,
    Rejected,
    Unknown,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SettingsError {
    StaleBaseline,
    KeyMismatch,
    ContradictoryObservation,
    InvalidPhase(&'static str),
    PigmentTooManyNames,
    PigmentTooLarge,
    PigmentEmptyName,
    PigmentDuplicate,
    PigmentBaselineMismatch,
    PigmentSetsDiffer,
}

impl fmt::Display for SettingsError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let code = match self {
            Self::StaleBaseline => "PREF_STALE_BASELINE",
            Self::KeyMismatch => "PREF_KEY_MISMATCH",
            Self::ContradictoryObservation => "PREF_CONTRADICTORY",
            Self::InvalidPhase(_) => "PREF_INVALID_PHASE",
            Self::PigmentTooManyNames => "PREF_SET_TOO_LARGE",
            Self::PigmentTooLarge => "PREF_SET_TOO_LARGE",
            Self::PigmentEmptyName => "PREF_SET_EMPTY_NAME",
            Self::PigmentDuplicate => "PREF_SET_DUPLICATE",
            Self::PigmentBaselineMismatch => "PREF_STALE_BASELINE",
            Self::PigmentSetsDiffer => "PREF_SETS_DIFFER",
        };
        write!(f, "{code}")
    }
}

impl std::error::Error for SettingsError {}

#[derive(Debug, Clone)]
pub struct PreferenceWrite {
    key: PreferenceKey,
    baseline: PreferenceBaseline,
    requested: bool,
    phase: PreferencePhase,
}

impl PreferenceWrite {
    pub fn new(key: PreferenceKey, baseline: PreferenceBaseline, requested: bool) -> Self {
        Self {
            key,
            baseline,
            requested,
            phase: PreferencePhase::Planned,
        }
    }

    pub fn key(&self) -> PreferenceKey {
        self.key
    }

    pub fn phase(&self) -> PreferencePhase {
        self.phase
    }

    pub fn sent(&mut self) -> Result<(), SettingsError> {
        if self.phase != PreferencePhase::Planned {
            return Err(SettingsError::InvalidPhase("sent"));
        }
        self.phase = PreferencePhase::Sent;
        Ok(())
    }

    /// Requires the same pair and session, an unchanged key, a revision at least
    /// as new as the baseline, and the exact requested boolean.
    pub fn observe(
        &mut self,
        pair_epoch: u64,
        session_epoch: u64,
        revision: u64,
        key: PreferenceKey,
        value: PreferenceValue,
    ) -> Result<(), SettingsError> {
        if !matches!(self.phase, PreferencePhase::Sent | PreferencePhase::Planned) {
            return Err(SettingsError::InvalidPhase("observe"));
        }
        if pair_epoch != self.baseline.pair_epoch || session_epoch != self.baseline.session_epoch {
            return Err(SettingsError::StaleBaseline);
        }
        if revision < self.baseline.revision {
            return Err(SettingsError::StaleBaseline);
        }
        if key != self.key {
            return Err(SettingsError::KeyMismatch);
        }
        match value {
            PreferenceValue::Unknown => {
                // Unknown never counts as the requested value.
                Err(SettingsError::ContradictoryObservation)
            }
            PreferenceValue::Known(observed) if observed == self.requested => {
                self.phase = PreferencePhase::Applied;
                Ok(())
            }
            PreferenceValue::Known(_) => {
                self.phase = PreferencePhase::Rejected;
                Err(SettingsError::ContradictoryObservation)
            }
        }
    }

    pub fn timeout(&mut self) {
        if !matches!(
            self.phase,
            PreferencePhase::Applied | PreferencePhase::Rejected
        ) {
            self.phase = PreferencePhase::Unknown;
        }
    }
}

/// A set of palette fullnames. Order is preserved, but duplicate names are
/// refused because the wire value is a set.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PigmentSet {
    names: Vec<String>,
}

impl PigmentSet {
    pub fn new(names: Vec<String>) -> Result<Self, SettingsError> {
        if names.len() > MAX_PIGMENT_NAMES {
            return Err(SettingsError::PigmentTooManyNames);
        }
        let mut seen = std::collections::BTreeSet::new();
        let mut bytes = 0usize;
        for name in &names {
            if name.trim().is_empty() {
                return Err(SettingsError::PigmentEmptyName);
            }
            if !seen.insert(name) {
                return Err(SettingsError::PigmentDuplicate);
            }
            bytes = bytes.saturating_add(name.len());
        }
        if bytes > MAX_PIGMENT_BYTES {
            return Err(SettingsError::PigmentTooLarge);
        }
        Ok(Self { names })
    }

    pub fn names(&self) -> &[String] {
        &self.names
    }

    pub fn len(&self) -> usize {
        self.names.len()
    }

    pub fn is_empty(&self) -> bool {
        self.names.is_empty()
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PigmentBaseline {
    pub pair_epoch: u64,
    pub session_epoch: u64,
    pub selected_timestamp: i64,
    pub auto_timestamp: i64,
    pub selected: PigmentSet,
    pub auto: PigmentSet,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PigmentWrite {
    baseline: PigmentBaseline,
    selected: PigmentSet,
    auto: PigmentSet,
    applied: bool,
}

impl PigmentWrite {
    /// Both complete sets are required; the automatic and manual sets do not
    /// share a lifecycle.
    pub fn new(
        baseline: PigmentBaseline,
        selected: PigmentSet,
        auto: PigmentSet,
    ) -> Result<Self, SettingsError> {
        if baseline.pair_epoch == 0 || baseline.session_epoch == 0 {
            return Err(SettingsError::PigmentBaselineMismatch);
        }
        Ok(Self {
            baseline,
            selected,
            auto,
            applied: false,
        })
    }

    pub fn applied(&self) -> bool {
        self.applied
    }

    /// New observed mirror; pair/session must match and both sets must equal the
    /// requested sets exactly. A queue or ACK is not confirmation.
    pub fn confirm(
        &mut self,
        pair_epoch: u64,
        session_epoch: u64,
        selected: &PigmentSet,
        auto: &PigmentSet,
    ) -> Result<(), SettingsError> {
        if pair_epoch != self.baseline.pair_epoch || session_epoch != self.baseline.session_epoch {
            return Err(SettingsError::PigmentBaselineMismatch);
        }
        if selected != &self.selected || auto != &self.auto {
            return Err(SettingsError::PigmentSetsDiffer);
        }
        self.applied = true;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn baseline() -> PreferenceBaseline {
        PreferenceBaseline {
            pair_epoch: 4,
            session_epoch: 9,
            revision: 12,
        }
    }

    #[test]
    fn missing_values_are_unknown_not_false() {
        assert_eq!(
            PreferenceValue::from_optional(None),
            PreferenceValue::Unknown
        );
        assert_eq!(PreferenceValue::Unknown.as_bool(), None);
        assert_eq!(
            PreferenceValue::from_optional(Some(false)),
            PreferenceValue::Known(false)
        );
    }

    #[test]
    fn apple_and_unix_epochs_are_not_mixed() {
        assert_eq!(apple_to_unix(0), 978_307_200);
        assert_eq!(unix_to_apple(978_307_200), Some(0));
        assert_eq!(unix_to_apple(0), Some(-978_307_200));
    }

    #[test]
    fn preference_write_requires_exact_newer_observation() {
        let mut write = PreferenceWrite::new(PreferenceKey::WornOnRightArm, baseline(), true);
        write.sent().unwrap();
        assert!(matches!(
            write.observe(
                4,
                9,
                11,
                PreferenceKey::WornOnRightArm,
                PreferenceValue::Known(true)
            ),
            Err(SettingsError::StaleBaseline)
        ));
        assert!(matches!(
            write.observe(
                4,
                9,
                12,
                PreferenceKey::InvertUi,
                PreferenceValue::Known(true)
            ),
            Err(SettingsError::KeyMismatch)
        ));
        assert!(matches!(
            write.observe(
                4,
                9,
                12,
                PreferenceKey::WornOnRightArm,
                PreferenceValue::Unknown
            ),
            Err(SettingsError::ContradictoryObservation)
        ));
        write
            .observe(
                4,
                9,
                13,
                PreferenceKey::WornOnRightArm,
                PreferenceValue::Known(true),
            )
            .unwrap();
        assert_eq!(write.phase(), PreferencePhase::Applied);
    }

    #[test]
    fn timeout_is_unknown_and_not_applied() {
        let mut write = PreferenceWrite::new(PreferenceKey::InvertUi, baseline(), false);
        write.sent().unwrap();
        write.timeout();
        assert_eq!(write.phase(), PreferencePhase::Unknown);
        assert!(matches!(
            write.observe(
                4,
                9,
                12,
                PreferenceKey::InvertUi,
                PreferenceValue::Known(false)
            ),
            Err(SettingsError::InvalidPhase(_))
        ));
    }

    #[test]
    fn pigment_sets_are_bounded_and_unique() {
        assert!(matches!(
            PigmentSet::new(vec!["".into()]),
            Err(SettingsError::PigmentEmptyName)
        ));
        assert!(matches!(
            PigmentSet::new(vec!["a".into(), "a".into()]),
            Err(SettingsError::PigmentDuplicate)
        ));
        let many: Vec<String> = (0..=MAX_PIGMENT_NAMES).map(|i| format!("p{i}")).collect();
        assert!(matches!(
            PigmentSet::new(many),
            Err(SettingsError::PigmentTooManyNames)
        ));
    }

    #[test]
    fn pigment_write_needs_exact_mirror_and_same_pair_session() {
        let selected = PigmentSet::new(vec!["red".into(), "blue".into()]).unwrap();
        let auto = PigmentSet::new(vec!["green".into()]).unwrap();
        let baseline = PigmentBaseline {
            pair_epoch: 4,
            session_epoch: 9,
            selected_timestamp: 1,
            auto_timestamp: 2,
            selected: selected.clone(),
            auto: auto.clone(),
        };
        let mut write = PigmentWrite::new(
            baseline,
            PigmentSet::new(vec!["red".into(), "blue".into(), "pink".into()]).unwrap(),
            auto.clone(),
        )
        .unwrap();
        assert!(matches!(
            write.confirm(4, 9, &selected, &auto),
            Err(SettingsError::PigmentSetsDiffer)
        ));
        let requested_selected =
            PigmentSet::new(vec!["red".into(), "blue".into(), "pink".into()]).unwrap();
        write.confirm(4, 9, &requested_selected, &auto).unwrap();
        assert!(write.applied());
    }
}
