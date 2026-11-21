//! Unix socket plumbing: peer credentials, framing, one request per connection.
//!
//! The wire format lives in `protocol.rs` (protobuf v1, ADR 0013). This module
//! owns the socket, the peer-credential check and the read limit; it does not
//! decide what a request means.
