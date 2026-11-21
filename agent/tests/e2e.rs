//! End to end across crates: runtime prepares the committed bundle, then launches
//! the agent binary with the minted token and boot id, and the agent accepts the
//! request they authorise.

#![cfg(unix)]

use std::io::{Read, Write};
use std::os::unix::net::UnixStream;
use std::path::{Path, PathBuf};
use std::process::Child;
use std::time::{Duration, Instant};

use opencrown_agent::protocol::{
    self, Envelope, Request, ScanRequest, response::Result as ResponseResult,
};
use opencrown_runtime::launch::launch;
use opencrown_runtime::matcher::DeviceFingerprint;
use opencrown_runtime::revocation::RevocationList;
use opencrown_runtime::{hex, prepare};

fn bundle() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../catalog/examples/signed")
}

fn device() -> DeviceFingerprint {
    DeviceFingerprint {
        model: "EXAMPLE-1".to_string(),
        kernel_release: "example-6.1.0".to_string(),
        abi: "arm64-v8a".to_string(),
        android_sdk: 36,
        build_id: "EXAMPLE".to_string(),
    }
}

fn socket_path(tag: &str) -> PathBuf {
    let mut path = std::env::temp_dir();
    path.push(format!(
        "oc-e2e-{tag}-{}-{}.sock",
        std::process::id(),
        Instant::now().elapsed().as_nanos()
    ));
    path
}

fn current_uid() -> u32 {
    unsafe { libc::getuid() }
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

fn request(path: &Path, token: &str, boot_id: &str) -> protocol::Response {
    let envelope = Envelope {
        version: protocol::VERSION,
        boot_id: boot_id.to_string(),
        token: token.to_string(),
        request: Some(Request::Scan(ScanRequest {
            setup_only: false,
            timeout_ms: 1000,
        })),
    };
    let mut stream = UnixStream::connect(path).expect("connect");
    let frame = protocol::encode(&envelope);
    stream
        .write_all(&(frame.len() as u32).to_be_bytes())
        .expect("length");
    stream.write_all(&frame).expect("frame");

    let mut length = [0u8; 4];
    stream.read_exact(&mut length).expect("response length");
    let mut body = vec![0u8; u32::from_be_bytes(length) as usize];
    stream.read_exact(&mut body).expect("response body");
    protocol::decode_response(&body).expect("decode")
}

#[test]
fn runtime_identity_is_accepted_by_the_agent() {
    let bundle = bundle();
    let signature = std::fs::read(bundle.join("manifest.sig")).unwrap();
    let public_hex = std::fs::read_to_string(bundle.join("public_key.hex")).unwrap();
    let public = hex::decode(public_hex.trim()).unwrap();
    let work = std::env::temp_dir().join(format!("oc-e2e-work-{}", std::process::id()));
    let prepared = prepare(
        &bundle,
        &work,
        &device(),
        &signature,
        &public,
        &RevocationList::empty(),
    )
    .unwrap();

    let socket = socket_path("run");
    let args = vec![
        "--socket".to_string(),
        socket.to_string_lossy().into_owned(),
        "--app-uid".to_string(),
        current_uid().to_string(),
        "--capabilities".to_string(),
        "controller".to_string(),
        "--token".to_string(),
        prepared.token.expose().to_string(),
        "--boot-id".to_string(),
        prepared.boot_id.expose().to_string(),
        "--once".to_string(),
    ];
    let mut child: Child = launch(
        Path::new(env!("CARGO_BIN_EXE_crown-agent")),
        &args,
        &prepared.token,
        &prepared.boot_id,
    )
    .unwrap();
    wait_for_socket(&socket);

    let response = request(&socket, prepared.token.expose(), prepared.boot_id.expose());
    match response.result.expect("result") {
        ResponseResult::Failure(failure) => assert_eq!(failure.code, "controller"),
        other => panic!("expected failure, got {other:?}"),
    }

    assert!(child.wait().unwrap().success());
    let _ = std::fs::remove_dir_all(&work);
}
