//! Unix socket server: one request per connection, framed and bounded.
//!
//! The wire format is `protocol.rs` (protobuf v1, ADR 0013). This module owns
//! the socket: bind with 0600 permissions, accept one peer, check its uid, read
//! one length-prefixed frame, hand it to the broker, write one response.

use std::fs;
use std::io::{self, Read, Write};
use std::os::unix::fs::{FileTypeExt, PermissionsExt};
use std::os::unix::net::{UnixListener, UnixStream};
use std::path::{Path, PathBuf};
use std::time::Duration;

use crate::broker::{Broker, Controller};
use crate::protocol::{self, Failure, Response, response::Result as ResponseResult};

pub const MAX_FRAME: usize = protocol::MAX_ENVELOPE;
pub const IO_TIMEOUT: Duration = Duration::from_secs(5);

#[derive(Debug)]
pub enum ServeError {
    Io(io::Error),
    FrameTooLarge(usize),
    PeerUid { expected: u32, found: u32 },
}

impl std::fmt::Display for ServeError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Io(error) => write!(f, "io: {error}"),
            Self::FrameTooLarge(size) => write!(f, "frame too large: {size}"),
            Self::PeerUid { expected, found } => {
                write!(f, "peer uid {found}, expected {expected}")
            }
        }
    }
}

impl std::error::Error for ServeError {}

impl From<io::Error> for ServeError {
    fn from(error: io::Error) -> Self {
        Self::Io(error)
    }
}

pub struct Server {
    listener: UnixListener,
    path: PathBuf,
}

impl Server {
    /// Binds a socket, replacing a stale one, and restricts it to the owner.
    pub fn bind(path: &Path) -> io::Result<Self> {
        if let Ok(metadata) = fs::symlink_metadata(path) {
            if metadata.file_type().is_socket() {
                fs::remove_file(path)?;
            }
        }
        let listener = UnixListener::bind(path)?;
        fs::set_permissions(path, fs::Permissions::from_mode(0o600))?;
        Ok(Self {
            listener,
            path: path.to_path_buf(),
        })
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    /// Accepts one peer, serves one request, and closes the connection.
    pub fn accept_and_serve<C: Controller>(
        &self,
        broker: &mut Broker<C>,
        expected_uid: u32,
    ) -> Result<(), ServeError> {
        let (mut stream, _address) = self.listener.accept()?;
        stream.set_read_timeout(Some(IO_TIMEOUT))?;
        stream.set_write_timeout(Some(IO_TIMEOUT))?;

        let uid = peer_uid(&stream)?;
        if uid != expected_uid {
            return Err(ServeError::PeerUid {
                expected: expected_uid,
                found: uid,
            });
        }

        let frame = read_frame(&mut stream)?;
        let response = match protocol::decode(&frame) {
            Ok(envelope) => broker.handle(envelope),
            Err(error) => failure_response("invalid", &error.to_string()),
        };
        let bytes = protocol::encode(&response);
        write_frame(&mut stream, &bytes)?;
        Ok(())
    }
}

impl Drop for Server {
    fn drop(&mut self) {
        let _ = fs::remove_file(&self.path);
    }
}

#[cfg(target_os = "macos")]
fn peer_uid(stream: &UnixStream) -> io::Result<u32> {
    use std::os::unix::io::AsRawFd;

    let mut uid: libc::uid_t = 0;
    let mut gid: libc::gid_t = 0;
    let rc = unsafe { libc::getpeereid(stream.as_raw_fd(), &mut uid, &mut gid) };
    if rc != 0 {
        return Err(io::Error::last_os_error());
    }
    Ok(uid as u32)
}

#[cfg(target_os = "linux")]
fn peer_uid(stream: &UnixStream) -> io::Result<u32> {
    use std::os::unix::io::AsRawFd;

    let mut credentials = libc::ucred {
        pid: 0,
        uid: 0,
        gid: 0,
    };
    let mut length = std::mem::size_of::<libc::ucred>() as libc::socklen_t;
    let rc = unsafe {
        libc::getsockopt(
            stream.as_raw_fd(),
            libc::SOL_SOCKET,
            libc::SO_PEERCRED,
            &mut credentials as *mut libc::ucred as *mut libc::c_void,
            &mut length,
        )
    };
    if rc != 0 {
        return Err(io::Error::last_os_error());
    }
    Ok(credentials.uid as u32)
}

#[cfg(not(any(target_os = "macos", target_os = "linux")))]
fn peer_uid(_stream: &UnixStream) -> io::Result<u32> {
    Err(io::Error::new(
        io::ErrorKind::Unsupported,
        "peer credentials are not implemented on this platform",
    ))
}

fn read_frame(stream: &mut UnixStream) -> Result<Vec<u8>, ServeError> {
    let mut length = [0u8; 4];
    stream.read_exact(&mut length)?;
    let length = u32::from_be_bytes(length) as usize;
    if length > MAX_FRAME {
        return Err(ServeError::FrameTooLarge(length));
    }
    let mut body = vec![0u8; length];
    stream.read_exact(&mut body)?;
    Ok(body)
}

fn write_frame(stream: &mut UnixStream, bytes: &[u8]) -> io::Result<()> {
    stream.write_all(&(bytes.len() as u32).to_be_bytes())?;
    stream.write_all(bytes)?;
    stream.flush()
}

fn failure_response(code: &str, message: &str) -> Response {
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
    use crate::policy::CapabilitySet;
    use crate::protocol::{Envelope, Request, ScanRequest};
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::thread;

    static COUNTER: AtomicUsize = AtomicUsize::new(0);

    #[derive(Default)]
    struct MockController;

    impl Controller for MockController {
        fn scan(&mut self, _setup_only: bool, _timeout_ms: u32) -> Result<String, String> {
            Ok("scanned".to_string())
        }
        fn connect(&mut self, _address: &[u8]) -> Result<u32, String> {
            Ok(1)
        }
        fn open_channel(&mut self, _psm: u32, _mode: i32) -> Result<u32, String> {
            Ok(2)
        }
        fn send(&mut self, _handle: u32, _data: &[u8]) -> Result<(), String> {
            Ok(())
        }
        fn close(&mut self, _handle: u32) -> Result<(), String> {
            Ok(())
        }
    }

    fn socket_path(tag: &str) -> PathBuf {
        let mut path = std::env::temp_dir();
        path.push(format!(
            "oc-{tag}-{}-{}.sock",
            std::process::id(),
            COUNTER.fetch_add(1, Ordering::SeqCst)
        ));
        path
    }

    fn caps() -> CapabilitySet {
        CapabilitySet::from_names(&["controller".to_string()]).unwrap()
    }

    fn envelope() -> Envelope {
        Envelope {
            version: protocol::VERSION,
            boot_id: "boot-1".to_string(),
            token: "a".repeat(64),
            request: Some(Request::Scan(ScanRequest {
                setup_only: false,
                timeout_ms: 1000,
            })),
        }
    }

    fn current_uid() -> u32 {
        unsafe { libc::getuid() }
    }

    fn client(path: PathBuf, frame: Vec<u8>) -> thread::JoinHandle<Vec<u8>> {
        thread::spawn(move || {
            let mut stream = UnixStream::connect(path).unwrap();
            stream
                .write_all(&(frame.len() as u32).to_be_bytes())
                .unwrap();
            stream.write_all(&frame).unwrap();
            let mut length = [0u8; 4];
            stream.read_exact(&mut length).unwrap();
            let mut body = vec![0u8; u32::from_be_bytes(length) as usize];
            stream.read_exact(&mut body).unwrap();
            body
        })
    }

    #[test]
    fn serves_one_request() {
        let path = socket_path("serve");
        let server = Server::bind(&path).unwrap();
        let mut broker = Broker::new(caps(), MockController, "a".repeat(64), "boot-1");
        let handle = client(path.clone(), protocol::encode(&envelope()));

        server.accept_and_serve(&mut broker, current_uid()).unwrap();
        let response = protocol::decode_response(&handle.join().unwrap()).unwrap();

        match response.result.unwrap() {
            ResponseResult::Ok(ok) => assert_eq!(ok.detail, "scanned"),
            other => panic!("expected ok, got {other:?}"),
        }
        assert_eq!(broker.audit().len(), 1);
    }

    #[test]
    fn rejects_a_different_uid() {
        let path = socket_path("uid");
        let server = Server::bind(&path).unwrap();
        let mut broker = Broker::new(caps(), MockController, "a".repeat(64), "boot-1");
        let handle = client(path.clone(), protocol::encode(&envelope()));

        let error = server
            .accept_and_serve(&mut broker, current_uid() + 1)
            .unwrap_err();
        assert!(matches!(error, ServeError::PeerUid { .. }));
        let _ = handle.join();
        assert!(broker.audit().is_empty());
    }

    #[test]
    fn rejects_an_oversized_frame() {
        let path = socket_path("large");
        let server = Server::bind(&path).unwrap();
        let mut broker = Broker::new(caps(), MockController, "a".repeat(64), "boot-1");

        let handle = thread::spawn({
            let path = path.clone();
            move || {
                let mut stream = UnixStream::connect(path).unwrap();
                let _ = stream.write_all(&((MAX_FRAME as u32) + 1).to_be_bytes());
                let _ = stream.read(&mut [0u8; 1]);
            }
        });

        let error = server
            .accept_and_serve(&mut broker, current_uid())
            .unwrap_err();
        assert!(matches!(error, ServeError::FrameTooLarge(_)));
        let _ = handle.join();
    }

    #[test]
    fn malformed_frame_gets_an_invalid_response() {
        let path = socket_path("garbage");
        let server = Server::bind(&path).unwrap();
        let mut broker = Broker::new(caps(), MockController, "a".repeat(64), "boot-1");
        let handle = client(path.clone(), vec![0xff; 8]);

        server.accept_and_serve(&mut broker, current_uid()).unwrap();
        let response = protocol::decode_response(&handle.join().unwrap()).unwrap();
        match response.result.unwrap() {
            ResponseResult::Failure(failure) => assert_eq!(failure.code, "invalid"),
            other => panic!("expected failure, got {other:?}"),
        }
    }
}
