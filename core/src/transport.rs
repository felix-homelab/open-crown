//! The only way the core reaches a radio.
//!
//! The core never knows whether it is talking to the resident agent, the
//! desktop harness, or a recorded trace (docs/02-architecture.md section 9).
//!
//! The design sketch uses a `Stream`; a callback keeps this crate dependency
//! free until the first backend lands.

use crate::error::Error;

/// A peer address. The address type travels with it; see docs/01-feasibility.md.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct PeerId(pub [u8; 6]);

/// Which advertisements to surface.
#[derive(Debug, Clone, Copy, Default)]
pub struct ScanFilter {
    /// Only devices that advertise a setup state.
    pub setup_only: bool,
}

/// One advertisement, exactly as received.
#[derive(Debug, Clone)]
pub struct Advertisement {
    pub peer: PeerId,
    pub rssi: i16,
    pub data: Vec<u8>,
}

/// An open link to a peer.
#[derive(Debug, Clone, Copy)]
pub struct Link {
    pub peer: PeerId,
}

/// L2CAP mode. The Watch uses enhanced retransmission for its data channels.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ChannelMode {
    Basic,
    EnhancedRetransmission,
}

/// An open channel on a link.
#[derive(Debug, Clone, Copy)]
pub struct Channel {
    pub psm: u16,
    pub mode: ChannelMode,
}

pub trait Transport {
    fn scan(&self, filter: ScanFilter, sink: &mut dyn FnMut(Advertisement)) -> Result<(), Error>;
    fn connect(&self, peer: PeerId) -> Result<Link, Error>;
    fn open_channel(&self, link: &Link, psm: u16, mode: ChannelMode) -> Result<Channel, Error>;
    fn send(&self, channel: &Channel, data: &[u8]) -> Result<(), Error>;
    fn close(&self, channel: Channel) -> Result<(), Error>;
}
