//! Byte-level codecs.
//!
//! Every parser here consumes untrusted input and must be fuzzable
//! (docs/08-testing.md section 7). No allocation on the hot path without a
//! measured reason.
