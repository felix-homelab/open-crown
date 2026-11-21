//! End-to-end: run the agent binary, talk to its socket, read the response.

#![cfg(unix)]

use std::io::{Read, Write};
use std::os::unix::net::UnixStream;
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::time::{Duration, Instant};

use opencrown_agent::protocol::{
    self, Envelope, Request, ScanRequest, response::Result as ResponseResult,
};

fn socket_path(tag: &str) -> PathBuf {
    let mut path = std::env::temp_dir();
    path.push(format!(
        "oc-cli-{tag}-{}-{}.sock",
        std::process::id(),
        Instant::now().elapsed().as_nanos()
    ));
    path
}

fn current_uid() -> u32 {
    unsafe { libc::getuid() }
}

fn start(path: &Path, token: &str, boot_id: &str) -> Child {
    Command::new(env!("CARGO_BIN_EXE_crown-agent"))
        .arg("--socket")
        .arg(path)
        .arg("--app-uid")
        .arg(current_uid().to_string())
        .arg("--capabilities")
        .arg("controller")
        .arg("--token")
        .arg(token)
        .arg("--boot-id")
        .arg(boot_id)
        .arg("--once")
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
        .expect("spawn crown-agent")
}

fn wait_for_socket(path: &Path) {
    let deadline = Instant::now() + Duration::from_secs(3);
    while Instant::now() < deadline {
        if path.exists() {
            return;
        }
        std::thread::sleep(Duration::from_millis(20));
    }
    panic!("socket was not created: {}", path.display());
}

fn request(path: &Path, envelope: &Envelope) -> protocol::Response {
    let mut stream = UnixStream::connect(path).expect("connect");
    let frame = protocol::encode(envelope);
    stream
        .write_all(&(frame.len() as u32).to_be_bytes())
        .expect("length");
    stream.write_all(&frame).expect("frame");

    let mut length = [0u8; 4];
    stream.read_exact(&mut length).expect("response length");
    let mut body = vec![0u8; u32::from_be_bytes(length) as usize];
    stream.read_exact(&mut body).expect("response body");
    protocol::decode_response(&body).expect("decode response")
}

fn envelope(token: &str) -> Envelope {
    Envelope {
        version: protocol::VERSION,
        boot_id: "boot-test".to_string(),
        token: token.to_string(),
        request: Some(Request::Scan(ScanRequest {
            setup_only: false,
            timeout_ms: 1000,
        })),
    }
}

#[test]
fn serves_one_request_then_exits() {
    let path = socket_path("serve");
    let token = "a".repeat(64);
    let mut child = start(&path, &token, "boot-test");
    wait_for_socket(&path);

    let response = request(&path, &envelope(&token));
    match response.result.expect("result") {
        ResponseResult::Failure(failure) => assert_eq!(failure.code, "controller"),
        other => panic!("expected failure, got {other:?}"),
    }

    assert!(child.wait().expect("wait").success());
}

#[test]
fn wrong_token_is_rejected() {
    let path = socket_path("token");
    let token = "a".repeat(64);
    let mut child = start(&path, &token, "boot-test");
    wait_for_socket(&path);

    let response = request(&path, &envelope(&"b".repeat(64)));
    match response.result.expect("result") {
        ResponseResult::Failure(failure) => assert_eq!(failure.code, "invalid"),
        other => panic!("expected failure, got {other:?}"),
    }

    assert!(child.wait().expect("wait").success());
}
