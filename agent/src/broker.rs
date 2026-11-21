//! Request dispatch.
//!
//! The broker validates the envelope, checks the capability policy, calls the
//! controller, and records one audit event per request. The controller is a
//! trait so this logic is testable without a phone or a radio.

use crate::audit::{AuditLog, Outcome};
use crate::policy::{self, Action, CapabilitySet};
use crate::protocol::{
    self, Envelope, Failure, Ok as OkResponse, Request, Response,
    response::Result as ResponseResult,
};

pub trait Controller {
    fn scan(&mut self, setup_only: bool, timeout_ms: u32) -> Result<String, String>;
    fn connect(&mut self, address: &[u8]) -> Result<u32, String>;
    fn open_channel(&mut self, psm: u32, mode: i32) -> Result<u32, String>;
    fn send(&mut self, handle: u32, data: &[u8]) -> Result<(), String>;
    fn close(&mut self, handle: u32) -> Result<(), String>;
}

pub struct Broker<C: Controller> {
    capabilities: CapabilitySet,
    controller: C,
    audit: AuditLog,
}

impl<C: Controller> Broker<C> {
    pub fn new(capabilities: CapabilitySet, controller: C) -> Self {
        Self {
            capabilities,
            controller,
            audit: AuditLog::new(),
        }
    }

    pub fn audit(&self) -> &AuditLog {
        &self.audit
    }

    pub fn controller(&mut self) -> &mut C {
        &mut self.controller
    }

    pub fn handle(&mut self, envelope: Envelope) -> Response {
        let boot_id = envelope.boot_id.clone();

        if let Err(error) = protocol::validate(&envelope) {
            self.audit.record(&boot_id, "reject", Outcome::Denied);
            return failure("invalid", &error.to_string());
        }

        let Some(request) = envelope.request else {
            self.audit.record(&boot_id, "reject", Outcome::Denied);
            return failure("invalid", "missing request");
        };

        let action = action_for(&request);
        if let Err(deny) = policy::check(self.capabilities, action) {
            self.audit.record(&boot_id, action.name(), Outcome::Denied);
            return failure("denied", &deny.to_string());
        }

        let result: Result<OkResponse, String> = match request {
            Request::Scan(scan) => self
                .controller
                .scan(scan.setup_only, scan.timeout_ms)
                .map(|detail| OkResponse { detail, handle: 0 }),
            Request::Connect(connect) => {
                self.controller
                    .connect(&connect.address)
                    .map(|handle| OkResponse {
                        detail: "connected".to_string(),
                        handle,
                    })
            }
            Request::OpenChannel(open) => {
                self.controller
                    .open_channel(open.psm, open.mode)
                    .map(|handle| OkResponse {
                        detail: "channel open".to_string(),
                        handle,
                    })
            }
            Request::Send(send) => {
                self.controller
                    .send(send.handle, &send.data)
                    .map(|()| OkResponse {
                        detail: "sent".to_string(),
                        handle: send.handle,
                    })
            }
            Request::Close(close) => self.controller.close(close.handle).map(|()| OkResponse {
                detail: "closed".to_string(),
                handle: close.handle,
            }),
            Request::Diag(_) => Ok(OkResponse {
                detail: "diag".to_string(),
                handle: 0,
            }),
        };

        match result {
            Ok(ok) => {
                self.audit.record(&boot_id, action.name(), Outcome::Ok);
                Response {
                    version: protocol::VERSION,
                    result: Some(ResponseResult::Ok(ok)),
                }
            }
            Err(message) => {
                self.audit.record(&boot_id, action.name(), Outcome::Failed);
                failure("controller", &message)
            }
        }
    }
}

fn action_for(request: &Request) -> Action {
    match request {
        Request::Scan(_) => Action::Scan,
        Request::Connect(_) => Action::Connect,
        Request::OpenChannel(_) => Action::OpenChannel,
        Request::Send(_) => Action::Send,
        Request::Close(_) => Action::Close,
        Request::Diag(_) => Action::Diag,
    }
}

fn failure(code: &str, message: &str) -> Response {
    Response {
        version: protocol::VERSION,
        result: Some(ResponseResult::Failure(Failure {
            code: code.to_string(),
            message: message.to_string(),
        })),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::protocol::{
        ConnectRequest, DiagRequest, Envelope, OpenChannelRequest, ScanRequest, SendRequest,
    };

    #[derive(Default)]
    struct MockController {
        scans: u32,
        connects: u32,
        fail: bool,
    }

    impl Controller for MockController {
        fn scan(&mut self, _setup_only: bool, _timeout_ms: u32) -> Result<String, String> {
            self.scans += 1;
            if self.fail {
                return Err("radio busy".to_string());
            }
            Ok("scanned".to_string())
        }

        fn connect(&mut self, _address: &[u8]) -> Result<u32, String> {
            self.connects += 1;
            Ok(7)
        }

        fn open_channel(&mut self, _psm: u32, _mode: i32) -> Result<u32, String> {
            Ok(9)
        }

        fn send(&mut self, _handle: u32, _data: &[u8]) -> Result<(), String> {
            Ok(())
        }

        fn close(&mut self, _handle: u32) -> Result<(), String> {
            Ok(())
        }
    }

    fn envelope(request: Request) -> Envelope {
        Envelope {
            version: protocol::VERSION,
            boot_id: "boot-1".to_string(),
            token: "a".repeat(64),
            request: Some(request),
        }
    }

    fn controller_caps() -> CapabilitySet {
        CapabilitySet::from_names(&[
            "root".to_string(),
            "domain".to_string(),
            "controller".to_string(),
        ])
        .unwrap()
    }

    fn ok_of(response: &Response) -> &OkResponse {
        match response.result.as_ref().unwrap() {
            ResponseResult::Ok(ok) => ok,
            other => panic!("expected ok, got {other:?}"),
        }
    }

    fn failure_of(response: &Response) -> &Failure {
        match response.result.as_ref().unwrap() {
            ResponseResult::Failure(failure) => failure,
            other => panic!("expected failure, got {other:?}"),
        }
    }

    #[test]
    fn scan_is_dispatched_and_audited() {
        let mut broker = Broker::new(controller_caps(), MockController::default());
        let response = broker.handle(envelope(Request::Scan(ScanRequest {
            setup_only: true,
            timeout_ms: 1000,
        })));
        assert_eq!(ok_of(&response).detail, "scanned");
        assert_eq!(broker.controller().scans, 1);
        assert_eq!(broker.audit().len(), 1);
        assert_eq!(broker.audit().events()[0].outcome, Outcome::Ok);
    }

    #[test]
    fn missing_capability_is_denied_before_the_controller() {
        let mut broker = Broker::new(CapabilitySet::empty(), MockController::default());
        let response = broker.handle(envelope(Request::Scan(ScanRequest {
            setup_only: false,
            timeout_ms: 1000,
        })));
        assert_eq!(failure_of(&response).code, "denied");
        assert_eq!(broker.controller().scans, 0);
        assert_eq!(broker.audit().events()[0].outcome, Outcome::Denied);
    }

    #[test]
    fn invalid_envelope_is_rejected_before_policy() {
        let mut broker = Broker::new(controller_caps(), MockController::default());
        let mut value = envelope(Request::Scan(ScanRequest {
            setup_only: false,
            timeout_ms: 1000,
        }));
        value.token = "short".to_string();
        let response = broker.handle(value);
        assert_eq!(failure_of(&response).code, "invalid");
        assert_eq!(broker.controller().scans, 0);
    }

    #[test]
    fn controller_failure_becomes_a_failure_response() {
        let mut broker = Broker::new(
            controller_caps(),
            MockController {
                fail: true,
                ..Default::default()
            },
        );
        let response = broker.handle(envelope(Request::Scan(ScanRequest {
            setup_only: false,
            timeout_ms: 1000,
        })));
        assert_eq!(failure_of(&response).code, "controller");
        assert_eq!(broker.audit().events()[0].outcome, Outcome::Failed);
    }

    #[test]
    fn connect_returns_a_handle() {
        let mut broker = Broker::new(controller_caps(), MockController::default());
        let response = broker.handle(envelope(Request::Connect(ConnectRequest {
            address: vec![1, 2, 3, 4, 5, 6],
        })));
        assert_eq!(ok_of(&response).handle, 7);
        assert_eq!(broker.controller().connects, 1);
    }

    #[test]
    fn open_channel_returns_a_handle() {
        let mut broker = Broker::new(controller_caps(), MockController::default());
        let response = broker.handle(envelope(Request::OpenChannel(OpenChannelRequest {
            psm: 0x1001,
            mode: crate::protocol::ChannelMode::Ertm as i32,
        })));
        assert_eq!(ok_of(&response).handle, 9);
    }

    #[test]
    fn diag_needs_its_own_capability() {
        let mut broker = Broker::new(controller_caps(), MockController::default());
        let denied = broker.handle(envelope(Request::Diag(DiagRequest {})));
        assert_eq!(failure_of(&denied).code, "denied");

        let mut broker = Broker::new(
            CapabilitySet::from_names(&["diag".to_string()]).unwrap(),
            MockController::default(),
        );
        let allowed = broker.handle(envelope(Request::Diag(DiagRequest {})));
        assert_eq!(ok_of(&allowed).detail, "diag");
    }

    #[test]
    fn send_is_dispatched() {
        let mut broker = Broker::new(controller_caps(), MockController::default());
        let response = broker.handle(envelope(Request::Send(SendRequest {
            handle: 3,
            data: vec![1, 2],
        })));
        assert_eq!(ok_of(&response).detail, "sent");
    }
}
