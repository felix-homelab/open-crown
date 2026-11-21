//! Append-only audit log.
//!
//! One line per privileged action. Fields are sanitised rather than escaped,
//! because every value is internal: a boot id, a known action name, and an
//! outcome. A character that would need escaping becomes an underscore.

use std::fs::OpenOptions;
use std::io::Write;
use std::path::Path;

/// Result of one attempted operation.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Outcome {
    Ok,
    Denied,
    Failed,
}

impl Outcome {
    pub fn name(self) -> &'static str {
        match self {
            Self::Ok => "ok",
            Self::Denied => "denied",
            Self::Failed => "failed",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Event {
    pub boot_id: String,
    pub action: String,
    pub outcome: Outcome,
}

#[derive(Debug, Default)]
pub struct AuditLog {
    events: Vec<Event>,
}

impl AuditLog {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn record(&mut self, boot_id: &str, action: &str, outcome: Outcome) {
        self.events.push(Event {
            boot_id: boot_id.to_string(),
            action: action.to_string(),
            outcome,
        });
    }

    pub fn events(&self) -> &[Event] {
        &self.events
    }

    pub fn len(&self) -> usize {
        self.events.len()
    }

    pub fn is_empty(&self) -> bool {
        self.events.is_empty()
    }

    /// Newline-delimited JSON, one object per event.
    pub fn to_jsonl(&self) -> String {
        let mut out = String::new();
        for event in &self.events {
            out.push_str(&line(&event.boot_id, &event.action, event.outcome));
        }
        out
    }
}

/// Appends events to a file with mode 0600. The agent owns this file.
pub struct AuditFile {
    file: std::fs::File,
}

impl AuditFile {
    pub fn create(path: &Path) -> std::io::Result<Self> {
        let mut options = OpenOptions::new();
        options.create(true).append(true);
        #[cfg(unix)]
        {
            use std::os::unix::fs::OpenOptionsExt;
            options.mode(0o600);
        }
        Ok(Self {
            file: options.open(path)?,
        })
    }

    pub fn record(&mut self, boot_id: &str, action: &str, outcome: Outcome) -> std::io::Result<()> {
        self.file
            .write_all(line(boot_id, action, outcome).as_bytes())?;
        self.file.flush()
    }
}

fn line(boot_id: &str, action: &str, outcome: Outcome) -> String {
    let mut out = String::new();
    out.push_str(r#"{"boot_id":""#);
    out.push_str(&sanitize(boot_id));
    out.push_str(r#"","action":""#);
    out.push_str(&sanitize(action));
    out.push_str(r#"","outcome":""#);
    out.push_str(outcome.name());
    out.push_str(r#""}"#);
    out.push('\n');
    out
}

fn sanitize(value: &str) -> String {
    value
        .chars()
        .map(|character| {
            let code = character as u32;
            let safe = code >= 0x20 && code != 0x22 && code != 0x5c;
            if safe { character } else { '_' }
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn records_events() {
        let mut log = AuditLog::new();
        log.record("boot-1", "scan", Outcome::Ok);
        log.record("boot-1", "send", Outcome::Denied);
        assert_eq!(log.len(), 2);
        let jsonl = log.to_jsonl();
        let lines: Vec<&str> = jsonl.lines().collect();
        assert_eq!(lines.len(), 2);
        assert!(lines[0].contains(r#""action":"scan""#));
        assert!(lines[1].contains(r#""outcome":"denied""#));
    }

    #[test]
    fn sanitizes_dangerous_characters() {
        let mut log = AuditLog::new();
        log.record("boot\"1", "act\\ion", Outcome::Ok);
        let jsonl = log.to_jsonl();
        assert!(!jsonl.contains('\\'));
        assert!(jsonl.contains("boot_1"));
        assert!(jsonl.contains("act_ion"));
    }

    #[test]
    fn empty_log_is_empty_jsonl() {
        assert_eq!(AuditLog::new().to_jsonl(), "");
        assert!(AuditLog::new().is_empty());
    }

    #[test]
    fn writes_to_a_file() {
        let path =
            std::env::temp_dir().join(format!("opencrown-audit-{}.jsonl", std::process::id()));
        let _ = std::fs::remove_file(&path);

        let mut file = AuditFile::create(&path).unwrap();
        file.record("boot-1", "scan", Outcome::Ok).unwrap();
        file.record("boot-1", "send", Outcome::Denied).unwrap();

        let text = std::fs::read_to_string(&path).unwrap();
        assert_eq!(text.lines().count(), 2);
        assert!(text.contains(r#""action":"scan""#));

        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let mode = std::fs::metadata(&path).unwrap().permissions().mode() & 0o777;
            assert_eq!(mode, 0o600);
        }

        let _ = std::fs::remove_file(&path);
    }
}
