//! Crown Agent: the resident privileged helper.
//!
//! It owns the Bluetooth controller for one boot, validates every envelope from
//! the app, and records every privileged action. It never parses Watch protocol
//! messages; it moves bytes (docs/02-architecture.md section 2.3).
//!
//! This build serves the socket with a stub controller so the transport, policy
//! and audit paths can be exercised without hardware. The HCI/HAL controller
//! lands with Phase 0/4.

use std::path::PathBuf;
use std::process::ExitCode;

use opencrown_agent::audit::AuditFile;
use opencrown_agent::broker::{Broker, Controller};
use opencrown_agent::ipc::Server;
use opencrown_agent::policy::CapabilitySet;

struct Args {
    socket: PathBuf,
    app_uid: u32,
    capabilities: CapabilitySet,
    token: String,
    boot_id: String,
    audit: Option<PathBuf>,
    once: bool,
}

impl Args {
    fn parse(args: impl Iterator<Item = String>) -> Result<Self, String> {
        let mut socket = None;
        let mut app_uid = None;
        let mut names = "controller".to_string();
        let mut token = std::env::var("CROWN_TOKEN").ok();
        let mut boot_id = std::env::var("CROWN_BOOT_ID").ok();
        let mut audit = None;
        let mut once = false;
        let mut args = args.peekable();

        while let Some(arg) = args.next() {
            let mut value = |name: &str| args.next().ok_or_else(|| format!("{name} needs a value"));
            match arg.as_str() {
                "--socket" => socket = Some(PathBuf::from(value("--socket")?)),
                "--app-uid" => {
                    app_uid = Some(
                        value("--app-uid")?
                            .parse()
                            .map_err(|_| "--app-uid must be a number".to_string())?,
                    )
                }
                "--capabilities" => names = value("--capabilities")?,
                "--token" => token = Some(value("--token")?),
                "--boot-id" => boot_id = Some(value("--boot-id")?),
                "--audit" => audit = Some(PathBuf::from(value("--audit")?)),
                "--once" => once = true,
                "--help" => {
                    return Err("usage: crown-agent --socket PATH --app-uid UID \
[--capabilities controller,diag] [--token HEX] [--boot-id ID] [--audit PATH] [--once]"
                        .to_string());
                }
                other => return Err(format!("unknown argument: {other}")),
            }
        }

        let socket = socket.ok_or("--socket is required")?;
        let app_uid = app_uid.ok_or("--app-uid is required")?;
        let token = token.ok_or("pass --token or set CROWN_TOKEN")?;
        let boot_id = boot_id.ok_or("pass --boot-id or set CROWN_BOOT_ID")?;
        let capability_names: Vec<String> = names
            .split(',')
            .filter(|name| !name.is_empty())
            .map(str::to_string)
            .collect();
        let capabilities =
            CapabilitySet::from_names(&capability_names).map_err(|err| err.to_string())?;

        Ok(Self {
            socket,
            app_uid,
            capabilities,
            token,
            boot_id,
            audit,
            once,
        })
    }
}

struct StubController;

impl Controller for StubController {
    fn scan(&mut self, _setup_only: bool, _timeout_ms: u32) -> Result<String, String> {
        Err("controller is not implemented in this build".to_string())
    }
    fn connect(&mut self, _address: &[u8]) -> Result<u32, String> {
        Err("controller is not implemented in this build".to_string())
    }
    fn open_channel(&mut self, _psm: u32, _mode: i32) -> Result<u32, String> {
        Err("controller is not implemented in this build".to_string())
    }
    fn send(&mut self, _handle: u32, _data: &[u8]) -> Result<(), String> {
        Err("controller is not implemented in this build".to_string())
    }
    fn close(&mut self, _handle: u32) -> Result<(), String> {
        Err("controller is not implemented in this build".to_string())
    }
}

fn main() -> ExitCode {
    let args = match Args::parse(std::env::args().skip(1)) {
        Ok(args) => args,
        Err(message) => {
            eprintln!("crown-agent: {message}");
            return ExitCode::from(2);
        }
    };

    match serve(args) {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => {
            eprintln!("crown-agent: {error}");
            ExitCode::FAILURE
        }
    }
}

fn serve(args: Args) -> Result<(), Box<dyn std::error::Error>> {
    let server = Server::bind(&args.socket)?;
    let mut broker = Broker::new(
        args.capabilities,
        StubController,
        args.token.clone(),
        args.boot_id.clone(),
    );
    let mut audit = match &args.audit {
        Some(path) => Some(AuditFile::create(path)?),
        None => None,
    };
    let mut written = 0;

    loop {
        server.accept_and_serve(&mut broker, args.app_uid)?;

        let events = broker.audit().events();
        let total = events.len();
        for event in &events[written..] {
            if let Some(file) = audit.as_mut() {
                file.record(&event.boot_id, &event.action, event.outcome)?;
            }
            eprintln!(
                "audit boot={} action={} outcome={}",
                event.boot_id,
                event.action,
                event.outcome.name()
            );
        }
        written = total;

        if args.once {
            break;
        }
    }

    Ok(())
}
