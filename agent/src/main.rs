//! Crown Agent: the resident privileged helper.
//!
//! It owns the Bluetooth controller for one boot, validates every envelope from
//! the app, and records every privileged action. It never parses Watch protocol
//! messages; it moves bytes (docs/02-architecture.md section 2.3).
//!
//! Status: skeleton. The socket, the controller backend and the probe launch
//! land in Phase 0/4.

use opencrown_agent::policy::{self, Action, CapabilitySet};

fn main() {
    // Nothing is granted before a manifest is verified. Prove the default.
    if let Err(deny) = policy::check(CapabilitySet::empty(), Action::Diag) {
        eprintln!("crown-agent: {deny}; see docs/02-architecture.md section 2.3");
    }
    std::process::exit(70);
}
