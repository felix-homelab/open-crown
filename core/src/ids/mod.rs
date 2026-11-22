//! IDS route and session rules.
//!
//! The IDS envelope encoding, control Hello contents and service-map layout are
//! **UNKNOWN** (docs/cleanroom/spec/04-ids-session.md section 9). This module
//! implements the parts the spec does establish: the route catalogue with its
//! protection class, epoch-scoped correlation, the receipt-versus-effect
//! distinction, and the reconnect policy. It does not pretend to parse a wire
//! message it has never seen.

use std::collections::BTreeMap;
use std::fmt;
use std::time::Duration;

/// Bound on outstanding requests. The reference does not publish one; this is
/// an Open Crown resource limit.
pub const MAX_PENDING_REQUESTS: usize = 64;

/// Product reconnect policy established by the reference and adopted here.
pub const RECONNECT_BACKOFF_SECS: [u64; 3] = [15, 30, 60];

/// Protection class of an IDS route. The wire encoding is UNKNOWN.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ProtectionClass {
    ClassC,
    ClassD,
}

/// One named application route.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Route {
    pub topic: &'static str,
    pub class: ProtectionClass,
    pub purpose: &'static str,
}

/// The route catalogue from the Lane A spec. Being on this list says nothing
/// about whether the corresponding feature is implemented.
pub const ROUTES: &[Route] = &[
    Route {
        topic: "com.apple.private.alloy.bluetoothregistry",
        class: ProtectionClass::ClassD,
        purpose: "registry lifecycle",
    },
    Route {
        topic: "com.apple.private.alloy.bluetoothregistryclassc",
        class: ProtectionClass::ClassC,
        purpose: "class-C registry exchange",
    },
    Route {
        topic: "com.apple.private.alloy.pbbridge",
        class: ProtectionClass::ClassC,
        purpose: "provisioning, activation, Buddy",
    },
    Route {
        topic: "com.apple.private.alloy.preferencessync.pairedsync",
        class: ProtectionClass::ClassC,
        purpose: "PairedSync preferences",
    },
    Route {
        topic: "com.apple.private.alloy.preferencessync",
        class: ProtectionClass::ClassD,
        purpose: "ordinary preferences (NPS)",
    },
    Route {
        topic: "com.apple.private.alloy.bulletindistributor",
        class: ProtectionClass::ClassC,
        purpose: "notifications and actions",
    },
    Route {
        topic: "com.apple.private.alloy.bulletindistributor.settings",
        class: ProtectionClass::ClassD,
        purpose: "notification policy",
    },
    Route {
        topic: "com.apple.private.alloy.findmylocaldevice",
        class: ProtectionClass::ClassD,
        purpose: "find watch or phone",
    },
    Route {
        topic: "com.apple.private.alloy.health.sync.classc",
        class: ProtectionClass::ClassC,
        purpose: "health synchronisation",
    },
    Route {
        topic: "com.apple.private.alloy.telephony",
        class: ProtectionClass::ClassC,
        purpose: "telephony codecs",
    },
    Route {
        topic: "com.apple.private.alloy.timesync",
        class: ProtectionClass::ClassD,
        purpose: "time exchange",
    },
    Route {
        topic: "com.apple.private.alloy.timezonesync",
        class: ProtectionClass::ClassD,
        purpose: "time zone exchange",
    },
    Route {
        topic: "com.apple.private.alloy.clockface.sync",
        class: ProtectionClass::ClassD,
        purpose: "face collection and delta",
    },
    Route {
        topic: "com.apple.private.alloy.systemsettings",
        class: ProtectionClass::ClassD,
        purpose: "about and diagnostics",
    },
    Route {
        topic: "com.apple.private.alloy.wifi.networksync",
        class: ProtectionClass::ClassC,
        purpose: "Wi-Fi archive",
    },
    Route {
        topic: "com.apple.private.alloy.sysdiagnose",
        class: ProtectionClass::ClassC,
        purpose: "diagnostic archives",
    },
    Route {
        topic: "com.apple.private.alloy.idscredentials",
        class: ProtectionClass::ClassD,
        purpose: "device credentials",
    },
];

/// Expands the documented short form. Returns None for a foreign topic.
pub fn canonical_topic(topic: &str) -> Option<String> {
    if let Some(rest) = topic.strip_prefix("alloy.") {
        if rest.is_empty() {
            return None;
        }
        Some(format!("com.apple.private.alloy.{rest}"))
    } else if topic.starts_with("com.apple.private.alloy.") {
        Some(topic.to_string())
    } else {
        None
    }
}

/// Resolves a topic against the catalogue. Unknown topics are rejected; there
/// is no generic forwarding fallback.
pub fn route(topic: &str) -> Option<Route> {
    let canonical = canonical_topic(topic)?;
    ROUTES
        .iter()
        .copied()
        .find(|route| route.topic == canonical)
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SessionState {
    Closed,
    ControlHello,
    ServiceMap,
    Ready,
    Ineligible,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum IneligibleReason {
    PolicyRefused,
    IncompleteCommit,
}

/// An ACK proves receipt at the transport layer, not that an effect happened.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Acknowledgement {
    Receipt,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum IdsError {
    UnknownTopic,
    ClassMismatch,
    CorrelationMiss,
    LateEpoch,
    InvalidState(&'static str),
    QueueFull,
    PolicyRefused,
    Ineligible,
}

impl fmt::Display for IdsError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::UnknownTopic => write!(f, "IDS_UNKNOWN_TOPIC"),
            Self::ClassMismatch => write!(f, "IDS_CLASS_MISMATCH"),
            Self::CorrelationMiss => write!(f, "IDS_CORRELATION_MISS"),
            Self::LateEpoch => write!(f, "IDS_LATE_EPOCH"),
            Self::InvalidState(what) => write!(f, "IDS_INVALID_STATE: {what}"),
            Self::QueueFull => write!(f, "IDS_QUEUE_FULL"),
            Self::PolicyRefused => write!(f, "IDS_POLICY_REFUSED"),
            Self::Ineligible => write!(f, "IDS_INELIGIBLE"),
        }
    }
}

impl std::error::Error for IdsError {}

#[derive(Debug, Clone)]
struct Pending {
    topic: String,
    class: ProtectionClass,
}

/// Epoch-scoped IDS session.
///
/// An epoch changes on every link loss. Any reply from an older epoch is
/// dropped, so a late answer cannot resurrect state for a link that is gone.
#[derive(Debug)]
pub struct IdsSession {
    epoch: u64,
    state: SessionState,
    pending: BTreeMap<[u8; 16], Pending>,
    reconnect_attempts: u8,
    ineligible: Option<IneligibleReason>,
}

impl IdsSession {
    pub fn new(epoch: u64) -> Self {
        Self {
            epoch,
            state: SessionState::Closed,
            pending: BTreeMap::new(),
            reconnect_attempts: 0,
            ineligible: None,
        }
    }

    pub fn epoch(&self) -> u64 {
        self.epoch
    }

    pub fn state(&self) -> SessionState {
        self.state
    }

    pub fn ineligible_reason(&self) -> Option<IneligibleReason> {
        self.ineligible
    }

    pub fn pending_len(&self) -> usize {
        self.pending.len()
    }

    /// Enters the control-Hello phase of a new link.
    pub fn begin_control(&mut self, epoch: u64) -> Result<(), IdsError> {
        if self.state == SessionState::Ineligible {
            return Err(IdsError::Ineligible);
        }
        if epoch != self.epoch {
            return Err(IdsError::LateEpoch);
        }
        if self.state != SessionState::Closed && self.state != SessionState::ControlHello {
            return Err(IdsError::InvalidState("begin_control"));
        }
        self.state = SessionState::ControlHello;
        Ok(())
    }

    pub fn control_hello(&mut self, epoch: u64) -> Result<(), IdsError> {
        self.check_epoch(epoch)?;
        if self.state != SessionState::ControlHello {
            return Err(IdsError::InvalidState("control_hello"));
        }
        self.state = SessionState::ServiceMap;
        Ok(())
    }

    pub fn service_map(&mut self, epoch: u64) -> Result<(), IdsError> {
        self.check_epoch(epoch)?;
        if self.state != SessionState::ServiceMap {
            return Err(IdsError::InvalidState("service_map"));
        }
        self.state = SessionState::Ready;
        self.reconnect_attempts = 0;
        Ok(())
    }

    /// Registers a request. The caller receives the route so it uses the class
    /// the catalogue assigned, never one chosen by the app.
    pub fn register_request(
        &mut self,
        epoch: u64,
        topic: &str,
        message_id: [u8; 16],
    ) -> Result<Route, IdsError> {
        self.check_epoch(epoch)?;
        if self.state != SessionState::Ready {
            return Err(IdsError::InvalidState("request before IDS ready"));
        }
        let route = route(topic).ok_or(IdsError::UnknownTopic)?;
        if self.pending.contains_key(&message_id) {
            return Err(IdsError::CorrelationMiss);
        }
        if self.pending.len() >= MAX_PENDING_REQUESTS {
            return Err(IdsError::QueueFull);
        }
        self.pending.insert(
            message_id,
            Pending {
                topic: route.topic.to_string(),
                class: route.class,
            },
        );
        Ok(route)
    }

    /// A transport ACK. It is a receipt; the pending response is still awaited.
    pub fn accept_ack(
        &self,
        epoch: u64,
        message_id: [u8; 16],
    ) -> Result<Acknowledgement, IdsError> {
        self.check_epoch(epoch)?;
        if !self.pending.contains_key(&message_id) {
            return Err(IdsError::CorrelationMiss);
        }
        Ok(Acknowledgement::Receipt)
    }

    /// A semantic response. It must match the pending topic, class and epoch.
    pub fn accept_reply(
        &mut self,
        epoch: u64,
        message_id: [u8; 16],
        topic: &str,
        class: ProtectionClass,
    ) -> Result<Route, IdsError> {
        self.check_epoch(epoch)?;
        let pending = self
            .pending
            .get(&message_id)
            .ok_or(IdsError::CorrelationMiss)?;
        let canonical = canonical_topic(topic).ok_or(IdsError::CorrelationMiss)?;
        if pending.topic != canonical {
            return Err(IdsError::CorrelationMiss);
        }
        if pending.class != class {
            return Err(IdsError::ClassMismatch);
        }
        let route = ROUTES
            .iter()
            .copied()
            .find(|route| route.topic == canonical)
            .ok_or(IdsError::UnknownTopic)?;
        self.pending.remove(&message_id);
        Ok(route)
    }

    /// Link loss invalidates the epoch and every outstanding request.
    pub fn link_lost(&mut self) {
        if self.state == SessionState::Ineligible {
            return;
        }
        self.epoch = self.epoch.wrapping_add(1);
        self.state = SessionState::Closed;
        self.pending.clear();
    }

    /// Returns the next bounded reconnect delay. A policy refusal is terminal.
    pub fn next_backoff(&mut self, policy_allows: bool) -> Result<Duration, IdsError> {
        if let Some(reason) = self.ineligible {
            return Err(match reason {
                IneligibleReason::PolicyRefused => IdsError::PolicyRefused,
                IneligibleReason::IncompleteCommit => IdsError::Ineligible,
            });
        }
        if !policy_allows {
            self.ineligible = Some(IneligibleReason::PolicyRefused);
            self.state = SessionState::Ineligible;
            return Err(IdsError::PolicyRefused);
        }
        if self.state != SessionState::Closed {
            return Err(IdsError::InvalidState("backoff outside Closed"));
        }
        let index = usize::from(self.reconnect_attempts.min(2));
        self.reconnect_attempts = self.reconnect_attempts.saturating_add(1);
        Ok(Duration::from_secs(RECONNECT_BACKOFF_SECS[index]))
    }

    /// After the delay, a new control exchange starts on the already-advanced
    /// epoch.
    pub fn reconnected(&mut self) -> Result<(), IdsError> {
        if self.state == SessionState::Ineligible {
            return Err(IdsError::Ineligible);
        }
        if self.state != SessionState::Closed {
            return Err(IdsError::InvalidState("reconnected outside Closed"));
        }
        self.state = SessionState::ControlHello;
        self.pending.clear();
        Ok(())
    }

    pub fn refuse_policy(&mut self) {
        self.ineligible = Some(IneligibleReason::PolicyRefused);
        self.state = SessionState::Ineligible;
        self.pending.clear();
    }

    pub fn mark_incomplete_commit(&mut self) {
        self.ineligible = Some(IneligibleReason::IncompleteCommit);
        self.state = SessionState::Ineligible;
        self.pending.clear();
    }

    fn check_epoch(&self, epoch: u64) -> Result<(), IdsError> {
        if epoch != self.epoch {
            return Err(IdsError::LateEpoch);
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const ID: [u8; 16] = [7; 16];
    const OTHER: [u8; 16] = [8; 16];

    fn ready_session() -> IdsSession {
        let mut session = IdsSession::new(1);
        session.begin_control(1).unwrap();
        session.control_hello(1).unwrap();
        session.service_map(1).unwrap();
        session
    }

    #[test]
    fn route_classes_match_the_spec() {
        assert_eq!(
            route("alloy.preferencessync.pairedsync").unwrap().class,
            ProtectionClass::ClassC
        );
        assert_eq!(
            route("com.apple.private.alloy.preferencessync")
                .unwrap()
                .class,
            ProtectionClass::ClassD
        );
        assert_eq!(
            route("alloy.clockface.sync").unwrap().topic,
            "com.apple.private.alloy.clockface.sync"
        );
    }

    #[test]
    fn unknown_and_foreign_topics_are_rejected() {
        assert!(route("com.example.private.alloy.other").is_none());
        assert!(route("alloy.").is_none());
        assert!(route("preferencessync").is_none());
    }

    #[test]
    fn happy_path_reaches_ready() {
        let session = ready_session();
        assert_eq!(session.state(), SessionState::Ready);
        assert_eq!(session.epoch(), 1);
    }

    #[test]
    fn requests_before_ready_are_rejected() {
        let mut session = IdsSession::new(1);
        session.begin_control(1).unwrap();
        assert!(matches!(
            session.register_request(1, "alloy.pbbridge", ID),
            Err(IdsError::InvalidState(_))
        ));
    }

    #[test]
    fn ack_is_receipt_only_and_reply_consumes_the_request() {
        let mut session = ready_session();
        session.register_request(1, "alloy.pbbridge", ID).unwrap();
        assert_eq!(session.accept_ack(1, ID).unwrap(), Acknowledgement::Receipt);
        assert_eq!(session.pending_len(), 1);
        let route = session
            .accept_reply(1, ID, "alloy.pbbridge", ProtectionClass::ClassC)
            .unwrap();
        assert_eq!(route.class, ProtectionClass::ClassC);
        assert_eq!(session.pending_len(), 0);
    }

    #[test]
    fn class_mismatch_and_topic_mismatch_are_dropped() {
        let mut session = ready_session();
        session.register_request(1, "alloy.pbbridge", ID).unwrap();
        assert!(matches!(
            session.accept_reply(1, ID, "alloy.pbbridge", ProtectionClass::ClassD),
            Err(IdsError::ClassMismatch)
        ));
        assert!(matches!(
            session.accept_reply(1, ID, "alloy.preferencessync", ProtectionClass::ClassD),
            Err(IdsError::CorrelationMiss)
        ));
        assert_eq!(session.pending_len(), 1);
        assert!(matches!(
            session.accept_ack(1, OTHER),
            Err(IdsError::CorrelationMiss)
        ));
    }

    #[test]
    fn link_loss_advances_epoch_and_drops_late_replies() {
        let mut session = ready_session();
        session.register_request(1, "alloy.pbbridge", ID).unwrap();
        session.link_lost();
        assert_eq!(session.epoch(), 2);
        assert_eq!(session.pending_len(), 0);
        assert!(matches!(
            session.accept_reply(1, ID, "alloy.pbbridge", ProtectionClass::ClassC),
            Err(IdsError::LateEpoch)
        ));
    }

    #[test]
    fn backoff_is_15_30_60_and_capped() {
        let mut session = ready_session();
        session.link_lost();
        assert_eq!(session.next_backoff(true).unwrap(), Duration::from_secs(15));
        assert_eq!(session.next_backoff(true).unwrap(), Duration::from_secs(30));
        assert_eq!(session.next_backoff(true).unwrap(), Duration::from_secs(60));
        assert_eq!(session.next_backoff(true).unwrap(), Duration::from_secs(60));
    }

    #[test]
    fn policy_refusal_is_terminal() {
        let mut session = ready_session();
        session.link_lost();
        assert!(matches!(
            session.next_backoff(false),
            Err(IdsError::PolicyRefused)
        ));
        assert_eq!(
            session.ineligible_reason(),
            Some(IneligibleReason::PolicyRefused)
        );
        assert!(matches!(
            session.next_backoff(true),
            Err(IdsError::PolicyRefused)
        ));
    }

    #[test]
    fn incomplete_commit_prevents_reconnect() {
        let mut session = ready_session();
        session.link_lost();
        session.mark_incomplete_commit();
        assert!(matches!(
            session.next_backoff(true),
            Err(IdsError::Ineligible)
        ));
        assert!(matches!(session.reconnected(), Err(IdsError::Ineligible)));
    }

    #[test]
    fn queue_is_bounded() {
        let mut session = ready_session();
        for i in 0..MAX_PENDING_REQUESTS {
            let mut id = [0u8; 16];
            id[0] = i as u8;
            session.register_request(1, "alloy.pbbridge", id).unwrap();
        }
        assert!(matches!(
            session.register_request(1, "alloy.pbbridge", OTHER),
            Err(IdsError::QueueFull)
        ));
    }

    #[test]
    fn duplicate_message_id_is_rejected() {
        let mut session = ready_session();
        session.register_request(1, "alloy.pbbridge", ID).unwrap();
        assert!(matches!(
            session.register_request(1, "alloy.pbbridge", ID),
            Err(IdsError::CorrelationMiss)
        ));
    }
}
