//! Crown Agent: the resident privileged helper.
//!
//! It owns the Bluetooth controller for one boot, validates every envelope from
//! the app, and records every privileged action. It never parses Watch protocol
//! messages; it moves bytes (docs/02-architecture.md section 2.3).
//!
//! Status: skeleton. The socket, the policy, and the controller backend land in
//! Phase 0 (harness) and Phase 4 (product).

mod audit;
mod broker;
mod ipc;
mod policy;

fn main() {
    eprintln!("crown-agent: skeleton; see docs/02-architecture.md section 2.3");
    std::process::exit(70);
}
