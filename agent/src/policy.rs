//! Capability policy.
//!
//! The app is untrusted. The agent grants only what the manifest declared, and
//! controller operations require the controller capability, not merely uid 0
//! (docs/adr/0007-uid0-plus-domain.md).

use std::fmt;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Capability {
    Root,
    Domain,
    Controller,
    Shim,
    Diag,
}

impl Capability {
    fn from_name(name: &str) -> Option<Self> {
        match name {
            "root" => Some(Self::Root),
            "domain" => Some(Self::Domain),
            "controller" => Some(Self::Controller),
            "shim" => Some(Self::Shim),
            "diag" => Some(Self::Diag),
            _ => None,
        }
    }

    pub fn name(self) -> &'static str {
        match self {
            Self::Root => "root",
            Self::Domain => "domain",
            Self::Controller => "controller",
            Self::Shim => "shim",
            Self::Diag => "diag",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Action {
    Scan,
    Connect,
    OpenChannel,
    Send,
    Close,
    Diag,
}

impl Action {
    pub fn name(self) -> &'static str {
        match self {
            Self::Scan => "scan",
            Self::Connect => "connect",
            Self::OpenChannel => "open_channel",
            Self::Send => "send",
            Self::Close => "close",
            Self::Diag => "diag",
        }
    }

    fn required(self) -> Capability {
        match self {
            Self::Scan | Self::Connect | Self::OpenChannel | Self::Send | Self::Close => {
                Capability::Controller
            }
            Self::Diag => Capability::Diag,
        }
    }
}

#[derive(Debug, PartialEq, Eq)]
pub enum Deny {
    MissingCapability(Capability),
}

impl fmt::Display for Deny {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::MissingCapability(capability) => {
                write!(f, "missing capability: {}", capability.name())
            }
        }
    }
}

impl std::error::Error for Deny {}

#[derive(Debug, PartialEq, Eq)]
pub enum PolicyError {
    UnknownCapability(String),
}

impl fmt::Display for PolicyError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::UnknownCapability(name) => write!(f, "unknown capability: {name}"),
        }
    }
}

impl std::error::Error for PolicyError {}

/// The capability set a manifest granted for this boot.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct CapabilitySet {
    bits: u8,
}

impl CapabilitySet {
    pub fn empty() -> Self {
        Self::default()
    }

    pub fn from_names(names: &[String]) -> Result<Self, PolicyError> {
        let mut set = Self::empty();
        for name in names {
            let capability = Capability::from_name(name)
                .ok_or_else(|| PolicyError::UnknownCapability(name.clone()))?;
            set.insert(capability);
        }
        Ok(set)
    }

    pub fn insert(&mut self, capability: Capability) {
        self.bits |= 1 << capability as u8;
    }

    pub fn contains(self, capability: Capability) -> bool {
        self.bits & (1 << capability as u8) != 0
    }

    pub fn is_empty(self) -> bool {
        self.bits == 0
    }
}

pub fn check(capabilities: CapabilitySet, action: Action) -> Result<(), Deny> {
    let required = action.required();
    if capabilities.contains(required) {
        Ok(())
    } else {
        Err(Deny::MissingCapability(required))
    }
}

/// A small budget so one compromised app cannot flood the controller.
#[derive(Debug)]
pub struct AttemptBudget {
    remaining: u32,
}

impl AttemptBudget {
    pub fn new(max: u32) -> Self {
        Self { remaining: max }
    }

    pub fn try_take(&mut self) -> bool {
        if self.remaining == 0 {
            return false;
        }
        self.remaining -= 1;
        true
    }

    pub fn remaining(&self) -> u32 {
        self.remaining
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn controller() -> CapabilitySet {
        let mut set = CapabilitySet::empty();
        set.insert(Capability::Controller);
        set
    }

    #[test]
    fn controller_operations_need_the_controller_capability() {
        for action in [
            Action::Scan,
            Action::Connect,
            Action::OpenChannel,
            Action::Send,
            Action::Close,
        ] {
            assert!(check(controller(), action).is_ok());
            assert_eq!(
                check(CapabilitySet::empty(), action).unwrap_err(),
                Deny::MissingCapability(Capability::Controller)
            );
        }
    }

    #[test]
    fn root_alone_does_not_grant_controller_operations() {
        let mut root = CapabilitySet::empty();
        root.insert(Capability::Root);
        root.insert(Capability::Domain);
        assert!(check(root, Action::Send).is_err());
    }

    #[test]
    fn diag_needs_its_own_capability() {
        assert!(check(controller(), Action::Diag).is_err());
        let mut diag = CapabilitySet::empty();
        diag.insert(Capability::Diag);
        assert!(check(diag, Action::Diag).is_ok());
    }

    #[test]
    fn rejects_unknown_capability() {
        let names = vec!["root".to_string(), "telepathy".to_string()];
        assert_eq!(
            CapabilitySet::from_names(&names).unwrap_err(),
            PolicyError::UnknownCapability("telepathy".to_string())
        );
    }

    #[test]
    fn budget_is_finite() {
        let mut budget = AttemptBudget::new(2);
        assert!(budget.try_take());
        assert!(budget.try_take());
        assert!(!budget.try_take());
        assert_eq!(budget.remaining(), 0);
    }
}
