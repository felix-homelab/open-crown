//! Provisioning (setup) state, checkpoints and completion rules.
//!
//! The wire payloads are **UNKNOWN** (docs/cleanroom/spec/03-activation-setup.md
//! section 9). This module implements the behaviour that is established:
//! ordered durable checkpoints, phase progression without regression, the
//! Watch's own locale (never the phone's), and two completion routes with
//! different evidence. An outgoing send or an ACK never completes provisioning.

use std::fmt;

/// The durable checkpoint ordinals defined by the Lane A spec.
pub const FIRST_CHECKPOINT: u32 = 1;
pub const LAST_CHECKPOINT: u32 = 18;

/// Phases from the Lane A spec, limited to the provisioning half of the flow.
/// Discovery, pairing and IDS have their own modules.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Phase {
    Bonded,
    Registry,
    Configuring,
    Activating,
    ActivationInput,
    Activated,
    Syncing,
    WaitingForWatch,
    VerifyingReconnect,
    Verified,
    Stopped,
    Failed,
}

impl Phase {
    pub fn is_terminal(self) -> bool {
        matches!(self, Phase::Verified | Phase::Stopped | Phase::Failed)
    }

    fn can_transition_to(self, next: Phase) -> bool {
        use Phase::*;
        matches!(
            (self, next),
            (Bonded, Registry)
                | (Bonded, Failed)
                | (Registry, Configuring)
                | (Registry, Failed)
                | (Configuring, Activating)
                | (Configuring, Failed)
                | (Activating, ActivationInput)
                | (Activating, Activated)
                | (Activating, Failed)
                | (ActivationInput, Activating)
                | (ActivationInput, Failed)
                | (Activated, Syncing)
                | (Activated, Failed)
                | (Syncing, WaitingForWatch)
                | (Syncing, Failed)
                | (WaitingForWatch, VerifyingReconnect)
                | (WaitingForWatch, Verified)
                | (WaitingForWatch, Failed)
                | (VerifyingReconnect, Verified)
                | (VerifyingReconnect, Failed)
        )
    }
}

/// How completion was established. These are not interchangeable.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CompletionBasis {
    /// Clock visibility plus a controlled reconnect: the strict native barrier.
    StrictNative,
    /// Explicit owner confirmation after a clean stop and pair re-validation.
    OwnerConfirmed,
}

/// Outcome of the Wi-Fi step. Unsupported networks are skipped, not failures.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WifiOutcome {
    Accepted,
    SkippedUnsupported,
    DiscardedExpired,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SetupError {
    LocaleMissing,
    RegistryIncomplete,
    ActivationRejected,
    SyncTimeout,
    SyncStale,
    CompletionUnconfirmed,
    StopIncomplete,
    LateEvent,
    InvalidPhase(&'static str),
    Checkpoint(&'static str),
}

impl fmt::Display for SetupError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::LocaleMissing => write!(f, "SETUP_LOCALE_MISSING"),
            Self::RegistryIncomplete => write!(f, "SETUP_REGISTRY_INCOMPLETE"),
            Self::ActivationRejected => write!(f, "SETUP_ACTIVATION_REJECTED"),
            Self::SyncTimeout => write!(f, "SETUP_SYNC_TIMEOUT"),
            Self::SyncStale => write!(f, "SETUP_SYNC_STALE"),
            Self::CompletionUnconfirmed => write!(f, "SETUP_COMPLETION_UNCONFIRMED"),
            Self::StopIncomplete => write!(f, "SETUP_STOP_INCOMPLETE"),
            Self::LateEvent => write!(f, "SETUP_LATE_EVENT"),
            Self::InvalidPhase(what) => write!(f, "SETUP_INVALID_PHASE: {what}"),
            Self::Checkpoint(what) => write!(f, "SETUP_CHECKPOINT: {what}"),
        }
    }
}

impl std::error::Error for SetupError {}

/// One durable provisioning run.
#[derive(Debug)]
pub struct Provisioning {
    pair_epoch: u64,
    session: u64,
    phase: Phase,
    checkpoints: Vec<u32>,
    completion: Option<CompletionBasis>,
    failure: Option<SetupError>,
}

impl Provisioning {
    pub fn new(pair_epoch: u64, session: u64) -> Self {
        Self {
            pair_epoch,
            session,
            phase: Phase::Bonded,
            checkpoints: Vec::new(),
            completion: None,
            failure: None,
        }
    }

    pub fn pair_epoch(&self) -> u64 {
        self.pair_epoch
    }

    pub fn session(&self) -> u64 {
        self.session
    }

    pub fn phase(&self) -> Phase {
        self.phase
    }

    pub fn completion(&self) -> Option<CompletionBasis> {
        self.completion
    }

    pub fn failure(&self) -> Option<&SetupError> {
        self.failure.as_ref()
    }

    pub fn checkpoints(&self) -> &[u32] {
        &self.checkpoints
    }

    /// True only after one of the two completion routes. A phase called
    /// "activated" is not enough.
    pub fn operational_eligible(&self) -> bool {
        self.completion.is_some() && self.phase == Phase::Verified
    }

    /// Records an observed durable checkpoint. Ordinal 1 must come first and
    /// ordinals must strictly increase. Gaps are allowed because a step can be
    /// skipped by policy (for example Wi-Fi on an unsupported network).
    pub fn checkpoint(&mut self, ordinal: u32) -> Result<(), SetupError> {
        if !(FIRST_CHECKPOINT..=LAST_CHECKPOINT).contains(&ordinal) {
            return Err(SetupError::Checkpoint("ordinal outside 1..=18"));
        }
        if let Some(last) = self.checkpoints.last() {
            if ordinal <= *last {
                return Err(SetupError::Checkpoint("ordinal must increase"));
            }
        } else if ordinal != 1 {
            return Err(SetupError::Checkpoint("first ordinal must be 1"));
        }
        self.checkpoints.push(ordinal);
        Ok(())
    }

    fn last_checkpoint(&self) -> u32 {
        self.checkpoints.last().copied().unwrap_or(0)
    }

    fn transition(&mut self, next: Phase) -> Result<(), SetupError> {
        if self.phase.is_terminal() {
            return Err(SetupError::InvalidPhase("terminal phase"));
        }
        if !self.phase.can_transition_to(next) {
            return Err(SetupError::InvalidPhase("edge not allowed"));
        }
        self.phase = next;
        Ok(())
    }

    pub fn registry_started(&mut self) -> Result<(), SetupError> {
        self.transition(Phase::Registry)
    }

    /// Missing registry properties stop the run; no invented defaults.
    pub fn registry_complete(&mut self, complete: bool) -> Result<(), SetupError> {
        if self.phase != Phase::Registry {
            return Err(SetupError::InvalidPhase("registry_complete"));
        }
        if !complete {
            self.fail(SetupError::RegistryIncomplete);
            return Err(SetupError::RegistryIncomplete);
        }
        self.transition(Phase::Configuring)
    }

    /// The Watch's authenticated locale. Passing the phone's locale here is a
    /// caller bug that this signature cannot detect, but a missing value is a
    /// hard stop before activation.
    pub fn locale_seen(&mut self, locale: Option<&str>) -> Result<(), SetupError> {
        if !matches!(self.phase, Phase::Registry | Phase::Configuring) {
            return Err(SetupError::InvalidPhase("locale_seen"));
        }
        match locale {
            Some(value) if !value.trim().is_empty() => Ok(()),
            _ => {
                self.fail(SetupError::LocaleMissing);
                Err(SetupError::LocaleMissing)
            }
        }
    }

    pub fn activate(&mut self) -> Result<(), SetupError> {
        self.transition(Phase::Activating)
    }

    pub fn activation_input_required(&mut self) -> Result<(), SetupError> {
        self.transition(Phase::ActivationInput)
    }

    pub fn resume_activation(&mut self) -> Result<(), SetupError> {
        self.transition(Phase::Activating)
    }

    /// Activation confirmed by an activation result. Requires the paired commit
    /// checkpoint (12) and records checkpoint 13.
    pub fn activation_confirmed(&mut self) -> Result<(), SetupError> {
        if self.phase != Phase::Activating {
            return Err(SetupError::InvalidPhase("activation_confirmed"));
        }
        if self.last_checkpoint() < 12 {
            return Err(SetupError::Checkpoint("activation before paired commit"));
        }
        self.transition(Phase::Activated)?;
        self.checkpoint(13)?;
        Ok(())
    }

    pub fn activation_rejected(&mut self) -> SetupError {
        self.fail(SetupError::ActivationRejected);
        SetupError::ActivationRejected
    }

    pub fn sync_started(&mut self) -> Result<(), SetupError> {
        self.transition(Phase::Syncing)
    }

    /// A correlated terminal sync state advances to WaitingForWatch and records
    /// checkpoint 15. An uncorrelated response is ignored and changes nothing.
    pub fn sync_terminal(&mut self, correlated: bool) -> Result<(), SetupError> {
        if self.phase != Phase::Syncing {
            return Err(SetupError::InvalidPhase("sync_terminal"));
        }
        if !correlated {
            return Err(SetupError::SyncStale);
        }
        if self.last_checkpoint() < 13 {
            return Err(SetupError::Checkpoint("sync before activation"));
        }
        self.transition(Phase::WaitingForWatch)?;
        self.checkpoint(15)?;
        Ok(())
    }

    /// A timeout is reported; it never synthesises the terminal sync state.
    pub fn sync_timed_out(&mut self) -> Result<(), SetupError> {
        if self.phase != Phase::Syncing {
            return Err(SetupError::InvalidPhase("sync_timed_out"));
        }
        self.phase = Phase::WaitingForWatch;
        Err(SetupError::SyncTimeout)
    }

    /// One observation for the strict native barrier. Either half alone leaves
    /// the run in VerifyingReconnect and reports unconfirmed.
    pub fn verify_strict(
        &mut self,
        clock_visible: bool,
        reconnect_observed: bool,
    ) -> Result<(), SetupError> {
        if !matches!(
            self.phase,
            Phase::WaitingForWatch | Phase::VerifyingReconnect
        ) {
            return Err(SetupError::InvalidPhase("verify_strict"));
        }
        if !(clock_visible && reconnect_observed) {
            if self.phase == Phase::WaitingForWatch {
                self.phase = Phase::VerifyingReconnect;
            }
            return Err(SetupError::CompletionUnconfirmed);
        }
        if self.last_checkpoint() < 15 {
            return Err(SetupError::Checkpoint("completion before PairedSync"));
        }
        self.phase = Phase::Verified;
        self.completion = Some(CompletionBasis::StrictNative);
        self.checkpoint(17)?;
        self.checkpoint(18)?;
        Ok(())
    }

    /// Owner confirmation is a distinct route. It requires a clean stop and a
    /// re-read, re-validated pair; an app ACK is not confirmation.
    pub fn owner_confirm(
        &mut self,
        clean_stop: bool,
        pair_revalidated: bool,
    ) -> Result<(), SetupError> {
        if !matches!(
            self.phase,
            Phase::WaitingForWatch | Phase::VerifyingReconnect
        ) {
            return Err(SetupError::InvalidPhase("owner_confirm"));
        }
        if !clean_stop {
            self.fail(SetupError::StopIncomplete);
            return Err(SetupError::StopIncomplete);
        }
        if !pair_revalidated {
            return Err(SetupError::CompletionUnconfirmed);
        }
        if self.last_checkpoint() < 15 {
            return Err(SetupError::Checkpoint("completion before PairedSync"));
        }
        self.phase = Phase::Verified;
        self.completion = Some(CompletionBasis::OwnerConfirmed);
        self.checkpoint(16)?;
        Ok(())
    }

    /// Wi-Fi is conditional. Unsupported networks are skipped, not failed.
    pub fn wifi_step(&self, supported: bool, session_matches: bool) -> WifiOutcome {
        if !supported {
            WifiOutcome::SkippedUnsupported
        } else if !session_matches {
            WifiOutcome::DiscardedExpired
        } else {
            WifiOutcome::Accepted
        }
    }

    /// Any run can be stopped. A dirty stop is terminal and not eligible.
    pub fn stop(&mut self, clean: bool) {
        if self.phase.is_terminal() {
            return;
        }
        if clean {
            self.phase = Phase::Stopped;
        } else {
            self.fail(SetupError::StopIncomplete);
        }
    }

    /// Rejects an event carrying another run's identity.
    pub fn accept_event(&self, event_session: u64) -> Result<(), SetupError> {
        if event_session != self.session {
            return Err(SetupError::LateEvent);
        }
        Ok(())
    }

    fn fail(&mut self, error: SetupError) {
        self.phase = Phase::Failed;
        self.failure = Some(error);
        self.completion = None;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn to_activating() -> Provisioning {
        let mut p = Provisioning::new(7, 11);
        p.checkpoint(1).unwrap();
        p.registry_started().unwrap();
        for ordinal in 2..=9 {
            p.checkpoint(ordinal).unwrap();
        }
        p.checkpoint(10).unwrap();
        p.registry_complete(true).unwrap();
        p.locale_seen(Some("en-GB")).unwrap();
        p.checkpoint(11).unwrap();
        p.checkpoint(12).unwrap();
        p.activate().unwrap();
        p
    }

    #[test]
    fn happy_path_requires_real_completion_evidence() {
        let mut p = to_activating();
        p.activation_confirmed().unwrap();
        p.sync_started().unwrap();
        p.sync_terminal(true).unwrap();
        assert!(!p.operational_eligible());
        p.verify_strict(true, true).unwrap();
        assert_eq!(p.phase(), Phase::Verified);
        assert_eq!(p.completion(), Some(CompletionBasis::StrictNative));
        assert!(p.operational_eligible());
    }

    #[test]
    fn activation_requires_paired_commit() {
        let mut p = Provisioning::new(7, 11);
        p.registry_started().unwrap();
        p.registry_complete(true).unwrap();
        p.activate().unwrap();
        assert!(matches!(
            p.activation_confirmed(),
            Err(SetupError::Checkpoint(_))
        ));
        assert_eq!(p.phase(), Phase::Activating);
    }

    #[test]
    fn missing_locale_stops_before_activation() {
        let mut p = Provisioning::new(7, 11);
        p.checkpoint(1).unwrap();
        p.registry_started().unwrap();
        assert!(matches!(
            p.locale_seen(None),
            Err(SetupError::LocaleMissing)
        ));
        assert_eq!(p.phase(), Phase::Failed);
        assert!(matches!(p.activate(), Err(SetupError::InvalidPhase(_))));
    }

    #[test]
    fn ack_and_unrelated_sync_do_not_complete() {
        let mut p = to_activating();
        p.activation_confirmed().unwrap();
        p.sync_started().unwrap();
        assert!(matches!(p.sync_terminal(false), Err(SetupError::SyncStale)));
        assert_eq!(p.phase(), Phase::Syncing);
        assert!(!p.operational_eligible());
    }

    #[test]
    fn strict_verification_needs_both_observations() {
        let mut p = to_activating();
        p.activation_confirmed().unwrap();
        p.sync_started().unwrap();
        p.sync_terminal(true).unwrap();
        assert!(matches!(
            p.verify_strict(true, false),
            Err(SetupError::CompletionUnconfirmed)
        ));
        assert_eq!(p.phase(), Phase::VerifyingReconnect);
        assert!(!p.operational_eligible());
        assert!(p.verify_strict(true, true).is_ok());
    }

    #[test]
    fn owner_confirmation_requires_clean_stop_and_revalidation() {
        let mut p = to_activating();
        p.activation_confirmed().unwrap();
        p.sync_started().unwrap();
        p.sync_terminal(true).unwrap();
        assert!(matches!(
            p.owner_confirm(false, true),
            Err(SetupError::StopIncomplete)
        ));
        assert_eq!(p.phase(), Phase::Failed);

        let mut q = to_activating();
        q.activation_confirmed().unwrap();
        q.sync_started().unwrap();
        q.sync_terminal(true).unwrap();
        assert!(matches!(
            q.owner_confirm(true, false),
            Err(SetupError::CompletionUnconfirmed)
        ));
        assert!(q.owner_confirm(true, true).is_ok());
        assert_eq!(q.completion(), Some(CompletionBasis::OwnerConfirmed));
        assert!(q.operational_eligible());
    }

    #[test]
    fn no_phase_regression_or_skipping() {
        let mut p = Provisioning::new(7, 11);
        assert!(matches!(p.activate(), Err(SetupError::InvalidPhase(_))));
        p.registry_started().unwrap();
        assert!(matches!(p.activate(), Err(SetupError::InvalidPhase(_))));
    }

    #[test]
    fn checkpoints_are_ordered_and_start_at_one() {
        let mut p = Provisioning::new(7, 11);
        assert!(matches!(p.checkpoint(2), Err(SetupError::Checkpoint(_))));
        p.checkpoint(1).unwrap();
        assert!(matches!(p.checkpoint(1), Err(SetupError::Checkpoint(_))));
        p.checkpoint(3).unwrap();
        assert_eq!(p.checkpoints(), &[1, 3]);
    }

    #[test]
    fn wifi_is_conditional_not_fatal() {
        let p = Provisioning::new(7, 11);
        assert_eq!(p.wifi_step(false, true), WifiOutcome::SkippedUnsupported);
        assert_eq!(p.wifi_step(true, false), WifiOutcome::DiscardedExpired);
        assert_eq!(p.wifi_step(true, true), WifiOutcome::Accepted);
    }

    #[test]
    fn late_events_are_rejected() {
        let p = Provisioning::new(7, 11);
        assert!(p.accept_event(11).is_ok());
        assert!(matches!(p.accept_event(12), Err(SetupError::LateEvent)));
    }

    #[test]
    fn dirty_stop_is_terminal_and_ineligible() {
        let mut p = to_activating();
        p.stop(false);
        assert_eq!(p.phase(), Phase::Failed);
        assert_eq!(p.failure(), Some(&SetupError::StopIncomplete));
        assert!(!p.operational_eligible());
    }

    #[test]
    fn sync_timeout_never_synthesises_terminal_state() {
        let mut p = to_activating();
        p.activation_confirmed().unwrap();
        p.sync_started().unwrap();
        assert!(matches!(p.sync_timed_out(), Err(SetupError::SyncTimeout)));
        assert_eq!(p.phase(), Phase::WaitingForWatch);
        assert!(!p.checkpoints().contains(&15));
        assert!(!p.operational_eligible());
    }
}
