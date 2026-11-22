//! Watch faces: inventory, delta transactions and read-back confirmation.
//!
//! START/BATCH/END payload encoding is **UNKNOWN**
//! (docs/cleanroom/spec/05-faces.md section 10). This module implements the
//! behaviour the spec does establish: a complete baseline, validated
//! operations, a staged transaction, and the rule that only a matching
//! read-back may produce APPLIED. A timeout or disconnect produces UNKNOWN.

use std::fmt;

pub type FaceId = [u8; 16];
pub type Hash32 = [u8; 32];

/// Read-back budget from the spec.
pub const READBACK_TIMEOUT_MS: u64 = 180_000;
/// Archive size ceiling.
pub const MAX_ARCHIVE_BYTES: usize = 16 * 1024 * 1024;
/// Inline add ceiling.
pub const MAX_INLINE_BYTES: usize = 128 * 1024;
/// One upload/download chunk.
pub const MAX_CHUNK_BYTES: usize = 64 * 1024;
/// Transfer lifetime.
pub const TRANSFER_TTL_MS: u64 = 30 * 60 * 1000;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum FaceError {
    NoInventory,
    TargetUnknown,
    LastFace,
    BaselineStale,
    PackageInvalid,
    PackageTooLarge,
    TransferExpired,
    TransferOffset,
    DeltaRefused,
    DeltaPartial,
    ReadbackTimeout,
    ReadbackMismatch,
    InvalidState(&'static str),
}

impl fmt::Display for FaceError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let code = match self {
            Self::NoInventory => "FACE_NO_INVENTORY",
            Self::TargetUnknown => "FACE_TARGET_UNKNOWN",
            Self::LastFace => "FACE_LAST_FACE",
            Self::BaselineStale => "FACE_BASELINE_STALE",
            Self::PackageInvalid => "FACE_PACKAGE_INVALID",
            Self::PackageTooLarge => "FACE_PACKAGE_TOO_LARGE",
            Self::TransferExpired => "FACE_TRANSFER_EXPIRED",
            Self::TransferOffset => "FACE_TRANSFER_OFFSET",
            Self::DeltaRefused => "FACE_DELTA_REFUSED",
            Self::DeltaPartial => "FACE_DELTA_PARTIAL",
            Self::ReadbackTimeout => "FACE_READBACK_TIMEOUT",
            Self::ReadbackMismatch => "FACE_READBACK_MISMATCH",
            Self::InvalidState(_) => "FACE_INVALID_STATE",
        };
        write!(f, "{code}")
    }
}

impl std::error::Error for FaceError {}

/// One installed face as observed from the Watch.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Face {
    pub id: FaceId,
    pub config_hash: Hash32,
}

/// A complete observation of the native collection.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Inventory {
    generation: u64,
    faces: Vec<Face>,
    selected: Option<FaceId>,
    complete: bool,
}

impl Inventory {
    /// A partial multipart read must be constructed with complete=false and is
    /// never usable as a mutation baseline.
    pub fn new(
        generation: u64,
        faces: Vec<Face>,
        selected: Option<FaceId>,
        complete: bool,
    ) -> Result<Self, FaceError> {
        if let Some(id) = selected {
            if !faces.iter().any(|face| face.id == id) {
                return Err(FaceError::TargetUnknown);
            }
        }
        Ok(Self {
            generation,
            faces,
            selected,
            complete,
        })
    }

    pub fn generation(&self) -> u64 {
        self.generation
    }

    pub fn faces(&self) -> &[Face] {
        &self.faces
    }

    pub fn selected(&self) -> Option<FaceId> {
        self.selected
    }

    pub fn is_complete(&self) -> bool {
        self.complete
    }

    pub fn order(&self) -> Vec<FaceId> {
        self.faces.iter().map(|face| face.id).collect()
    }

    fn face(&self, id: FaceId) -> Option<&Face> {
        self.faces.iter().find(|face| face.id == id)
    }

    fn contains(&self, id: FaceId) -> bool {
        self.face(id).is_some()
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Mutation {
    Select(FaceId),
    Duplicate {
        source: FaceId,
        new_id: FaceId,
    },
    Remove(FaceId),
    Reorder(Vec<FaceId>),
    UpdateConfig {
        face: FaceId,
        config_hash: Hash32,
    },
    Add {
        id: FaceId,
        config_hash: Hash32,
    },
    ReplaceResources {
        face: FaceId,
        baseline: Hash32,
        resources_hash: Hash32,
    },
}

/// What a complete read-back must show for the transaction to be APPLIED.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Expected {
    Selected(FaceId),
    Absent(FaceId),
    Order(Vec<FaceId>),
    Config { id: FaceId, hash: Hash32 },
    Present { id: FaceId, hash: Hash32 },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Readback {
    pub generation: u64,
    pub complete: bool,
    pub faces: Vec<Face>,
    pub selected: Option<FaceId>,
}

impl Readback {
    fn matches(&self, expected: &Expected) -> bool {
        if !self.complete {
            return false;
        }
        match expected {
            Expected::Selected(id) => self.selected == Some(*id) && self.has(*id),
            Expected::Absent(id) => !self.has(*id),
            Expected::Order(order) => {
                self.faces.iter().map(|face| face.id).collect::<Vec<_>>() == *order
            }
            Expected::Config { id, hash } => self
                .faces
                .iter()
                .find(|face| face.id == *id)
                .is_some_and(|face| face.config_hash == *hash),
            Expected::Present { id, hash } => self
                .faces
                .iter()
                .find(|face| face.id == *id)
                .is_some_and(|face| face.config_hash == *hash),
        }
    }

    fn has(&self, id: FaceId) -> bool {
        self.faces.iter().any(|face| face.id == id)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum State {
    Planned,
    StartWait,
    BatchReady,
    EndReady,
    EndWait,
    ReadbackWait,
    Applied,
    Rejected,
    Unknown,
}

impl State {
    pub fn is_terminal(self) -> bool {
        matches!(self, State::Applied | State::Rejected | State::Unknown)
    }
}

/// A staged mutation. Terminal states never retry; a new observation is needed
/// before another mutation can be planned.
#[derive(Debug)]
pub struct DeltaTransaction {
    expected: Expected,
    baseline_generation: u64,
    state: State,
    deadline_ms: u64,
}

impl DeltaTransaction {
    /// Validates a mutation against a complete baseline. No message is sent.
    pub fn plan(inventory: &Inventory, mutation: Mutation, now_ms: u64) -> Result<Self, FaceError> {
        if !inventory.is_complete() {
            return Err(FaceError::NoInventory);
        }
        let expected = match mutation {
            Mutation::Select(id) => {
                if !inventory.contains(id) {
                    return Err(FaceError::TargetUnknown);
                }
                Expected::Selected(id)
            }
            Mutation::Duplicate { source, new_id } => {
                let source_face = inventory.face(source).ok_or(FaceError::TargetUnknown)?;
                if inventory.contains(new_id) {
                    return Err(FaceError::TargetUnknown);
                }
                Expected::Present {
                    id: new_id,
                    hash: source_face.config_hash,
                }
            }
            Mutation::Remove(id) => {
                if !inventory.contains(id) {
                    return Err(FaceError::TargetUnknown);
                }
                if inventory.faces.len() <= 1 {
                    return Err(FaceError::LastFace);
                }
                Expected::Absent(id)
            }
            Mutation::Reorder(order) => {
                if order.len() != inventory.faces.len() {
                    return Err(FaceError::TargetUnknown);
                }
                let mut seen = order.clone();
                seen.sort_unstable();
                seen.dedup();
                if seen.len() != order.len() || !order.iter().all(|id| inventory.contains(*id)) {
                    return Err(FaceError::TargetUnknown);
                }
                Expected::Order(order)
            }
            Mutation::UpdateConfig { face, config_hash } => {
                if !inventory.contains(face) {
                    return Err(FaceError::TargetUnknown);
                }
                Expected::Config {
                    id: face,
                    hash: config_hash,
                }
            }
            Mutation::Add { id, config_hash } => {
                if inventory.contains(id) {
                    return Err(FaceError::TargetUnknown);
                }
                Expected::Present {
                    id,
                    hash: config_hash,
                }
            }
            Mutation::ReplaceResources {
                face,
                baseline,
                resources_hash,
            } => {
                let current = inventory.face(face).ok_or(FaceError::TargetUnknown)?;
                if current.config_hash != baseline {
                    return Err(FaceError::BaselineStale);
                }
                Expected::Present {
                    id: face,
                    hash: resources_hash,
                }
            }
        };
        Ok(Self {
            expected,
            baseline_generation: inventory.generation,
            state: State::Planned,
            deadline_ms: now_ms.saturating_add(READBACK_TIMEOUT_MS),
        })
    }

    pub fn state(&self) -> State {
        self.state
    }

    pub fn expected(&self) -> &Expected {
        &self.expected
    }

    pub fn baseline_generation(&self) -> u64 {
        self.baseline_generation
    }

    pub fn deadline_ms(&self) -> u64 {
        self.deadline_ms
    }

    pub fn start_sent(&mut self) -> Result<(), FaceError> {
        self.expect(State::Planned, "start_sent")?;
        self.state = State::StartWait;
        Ok(())
    }

    pub fn start_accepted(&mut self) -> Result<(), FaceError> {
        self.expect(State::StartWait, "start_accepted")?;
        self.state = State::BatchReady;
        Ok(())
    }

    pub fn start_refused(&mut self) -> Result<(), FaceError> {
        self.expect(State::StartWait, "start_refused")?;
        self.state = State::Rejected;
        Ok(())
    }

    pub fn batches_sent(&mut self) -> Result<(), FaceError> {
        self.expect(State::BatchReady, "batches_sent")?;
        self.state = State::EndReady;
        Ok(())
    }

    pub fn end_sent(&mut self) -> Result<(), FaceError> {
        self.expect(State::EndReady, "end_sent")?;
        self.state = State::EndWait;
        Ok(())
    }

    pub fn end_accepted(&mut self) -> Result<(), FaceError> {
        self.expect(State::EndWait, "end_accepted")?;
        self.state = State::ReadbackWait;
        Ok(())
    }

    pub fn end_refused(&mut self) -> Result<(), FaceError> {
        self.expect(State::EndWait, "end_refused")?;
        self.state = State::Rejected;
        Ok(())
    }

    /// A disconnect while waiting for a read-back is UNKNOWN, never APPLIED.
    pub fn disconnected(&mut self) -> Result<(), FaceError> {
        if self.state.is_terminal() {
            return Err(FaceError::InvalidState("transaction is terminal"));
        }
        self.state = State::Unknown;
        Ok(())
    }

    /// Applies a complete read-back. Only a match produces APPLIED.
    pub fn readback(&mut self, readback: &Readback, now_ms: u64) -> Result<State, FaceError> {
        self.expect(State::ReadbackWait, "readback")?;
        if now_ms > self.deadline_ms {
            self.state = State::Unknown;
            return Err(FaceError::ReadbackTimeout);
        }
        if !readback.complete {
            self.state = State::Unknown;
            return Err(FaceError::DeltaPartial);
        }
        if readback.matches(&self.expected) {
            self.state = State::Applied;
            Ok(State::Applied)
        } else {
            self.state = State::Rejected;
            Err(FaceError::ReadbackMismatch)
        }
    }

    /// Deadline enforcement. The caller may poll this; it never applies work.
    pub fn check_deadline(&mut self, now_ms: u64) -> Result<(), FaceError> {
        if self.state.is_terminal() {
            return Ok(());
        }
        if now_ms > self.deadline_ms {
            self.state = State::Unknown;
            return Err(FaceError::ReadbackTimeout);
        }
        Ok(())
    }

    fn expect(&self, expected: State, operation: &'static str) -> Result<(), FaceError> {
        if self.state != expected {
            return Err(FaceError::InvalidState(operation));
        }
        Ok(())
    }
}

/// Structural package limits. Hashing is the caller's job; this rejects sizes
/// before any transfer begins.
pub fn validate_archive(size: usize) -> Result<(), FaceError> {
    if size == 0 {
        return Err(FaceError::PackageInvalid);
    }
    if size > MAX_ARCHIVE_BYTES {
        return Err(FaceError::PackageTooLarge);
    }
    Ok(())
}

pub fn is_inline(size: usize) -> bool {
    size <= MAX_INLINE_BYTES
}

/// Validates a chunk against the declared total and the sequential offset.
pub fn validate_chunk(total: usize, offset: usize, len: usize) -> Result<(), FaceError> {
    if len == 0 || len > MAX_CHUNK_BYTES || offset.checked_add(len).is_none() {
        return Err(FaceError::TransferOffset);
    }
    if offset + len > total || total > MAX_ARCHIVE_BYTES {
        return Err(FaceError::TransferOffset);
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn id(byte: u8) -> FaceId {
        [byte; 16]
    }

    fn hash(byte: u8) -> Hash32 {
        [byte; 32]
    }

    fn inventory() -> Inventory {
        Inventory::new(
            4,
            vec![
                Face {
                    id: id(1),
                    config_hash: hash(1),
                },
                Face {
                    id: id(2),
                    config_hash: hash(2),
                },
                Face {
                    id: id(3),
                    config_hash: hash(3),
                },
            ],
            Some(id(1)),
            true,
        )
        .unwrap()
    }

    fn to_readback(mut t: DeltaTransaction) -> DeltaTransaction {
        t.start_sent().unwrap();
        t.start_accepted().unwrap();
        t.batches_sent().unwrap();
        t.end_sent().unwrap();
        t.end_accepted().unwrap();
        t
    }

    fn readback(faces: Vec<Face>, selected: Option<FaceId>) -> Readback {
        Readback {
            generation: 5,
            complete: true,
            faces,
            selected,
        }
    }

    #[test]
    fn select_becomes_applied_only_on_matching_readback() {
        let inv = inventory();
        let t = DeltaTransaction::plan(&inv, Mutation::Select(id(2)), 0).unwrap();
        let mut t = to_readback(t);
        let wrong = readback(inv.faces().to_vec(), Some(id(1)));
        t.readback(&wrong, 10).unwrap_err();
        assert_eq!(t.state(), State::Rejected);

        let inv = inventory();
        let t = DeltaTransaction::plan(&inv, Mutation::Select(id(2)), 0).unwrap();
        let mut t = to_readback(t);
        let right = readback(inv.faces().to_vec(), Some(id(2)));
        assert_eq!(t.readback(&right, 10).unwrap(), State::Applied);
    }

    #[test]
    fn only_readback_can_apply() {
        let inv = inventory();
        let mut t = DeltaTransaction::plan(&inv, Mutation::Select(id(2)), 0).unwrap();
        t.start_sent().unwrap();
        t.start_accepted().unwrap();
        t.batches_sent().unwrap();
        t.end_sent().unwrap();
        t.end_accepted().unwrap();
        assert_eq!(t.state(), State::ReadbackWait);
        assert!(!t.state().is_terminal());
    }

    #[test]
    fn last_face_cannot_be_removed() {
        let one = Inventory::new(
            1,
            vec![Face {
                id: id(1),
                config_hash: hash(1),
            }],
            Some(id(1)),
            true,
        )
        .unwrap();
        assert!(matches!(
            DeltaTransaction::plan(&one, Mutation::Remove(id(1)), 0),
            Err(FaceError::LastFace)
        ));
    }

    #[test]
    fn partial_inventory_cannot_plan() {
        let partial = Inventory::new(1, vec![], None, false).unwrap();
        assert!(matches!(
            DeltaTransaction::plan(&partial, Mutation::Select(id(1)), 0),
            Err(FaceError::NoInventory)
        ));
    }

    #[test]
    fn reorder_requires_an_exact_permutation() {
        let inv = inventory();
        assert!(
            DeltaTransaction::plan(&inv, Mutation::Reorder(vec![id(3), id(1), id(2)]), 0).is_ok()
        );
        assert!(matches!(
            DeltaTransaction::plan(&inv, Mutation::Reorder(vec![id(1), id(2)]), 0),
            Err(FaceError::TargetUnknown)
        ));
        assert!(matches!(
            DeltaTransaction::plan(&inv, Mutation::Reorder(vec![id(1), id(1), id(2)]), 0),
            Err(FaceError::TargetUnknown)
        ));
    }

    #[test]
    fn stale_baseline_is_refused() {
        let inv = inventory();
        assert!(matches!(
            DeltaTransaction::plan(
                &inv,
                Mutation::ReplaceResources {
                    face: id(1),
                    baseline: hash(9),
                    resources_hash: hash(4)
                },
                0
            ),
            Err(FaceError::BaselineStale)
        ));
    }

    #[test]
    fn timeout_and_disconnect_are_unknown_not_applied() {
        let inv = inventory();
        let mut t = DeltaTransaction::plan(&inv, Mutation::Select(id(2)), 0).unwrap();
        t.start_sent().unwrap();
        t.start_accepted().unwrap();
        t.batches_sent().unwrap();
        t.end_sent().unwrap();
        t.end_accepted().unwrap();
        assert!(matches!(
            t.check_deadline(READBACK_TIMEOUT_MS + 1),
            Err(FaceError::ReadbackTimeout)
        ));
        assert_eq!(t.state(), State::Unknown);

        let inv = inventory();
        let mut u = DeltaTransaction::plan(&inv, Mutation::Select(id(2)), 0).unwrap();
        u.start_sent().unwrap();
        u.disconnected().unwrap();
        assert_eq!(u.state(), State::Unknown);
    }

    #[test]
    fn incomplete_readback_is_unknown() {
        let inv = inventory();
        let t = DeltaTransaction::plan(&inv, Mutation::Select(id(2)), 0).unwrap();
        let mut t = to_readback(t);
        let partial = Readback {
            generation: 5,
            complete: false,
            faces: inv.faces().to_vec(),
            selected: Some(id(2)),
        };
        assert!(matches!(
            t.readback(&partial, 1),
            Err(FaceError::DeltaPartial)
        ));
        assert_eq!(t.state(), State::Unknown);
    }

    #[test]
    fn package_limits_are_enforced() {
        assert!(matches!(
            validate_archive(0),
            Err(FaceError::PackageInvalid)
        ));
        assert!(matches!(
            validate_archive(MAX_ARCHIVE_BYTES + 1),
            Err(FaceError::PackageTooLarge)
        ));
        assert!(validate_archive(MAX_ARCHIVE_BYTES).is_ok());
        assert!(is_inline(MAX_INLINE_BYTES));
        assert!(!is_inline(MAX_INLINE_BYTES + 1));
        assert!(validate_chunk(100_000, 0, MAX_CHUNK_BYTES).is_ok());
        assert!(matches!(
            validate_chunk(100_000, 0, MAX_CHUNK_BYTES + 1),
            Err(FaceError::TransferOffset)
        ));
        assert!(matches!(
            validate_chunk(10_000, 9_000, 2_000),
            Err(FaceError::TransferOffset)
        ));
    }

    #[test]
    fn terminal_transactions_never_retry() {
        let inv = inventory();
        let mut t = DeltaTransaction::plan(&inv, Mutation::Select(id(2)), 0).unwrap();
        t.start_sent().unwrap();
        t.start_refused().unwrap();
        assert_eq!(t.state(), State::Rejected);
        assert!(matches!(t.start_sent(), Err(FaceError::InvalidState(_))));
    }
}
