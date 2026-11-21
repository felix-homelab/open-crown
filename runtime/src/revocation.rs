//! Revocation list.
//!
//! A revoked payload never runs, even if its signature is valid. The list is
//! cached with the catalog and checked before staging
//! (docs/04-payload-system.md section 7).

use serde::Deserialize;

use crate::error::RuntimeError;

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct RevocationFile {
    schema: u32,
    revoked: Vec<Entry>,
}

#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Entry {
    pub id: String,
    pub version: String,
    #[serde(default)]
    pub reason: Option<String>,
}

#[derive(Debug, Default, Clone)]
pub struct RevocationList {
    entries: Vec<Entry>,
}

impl RevocationList {
    pub fn empty() -> Self {
        Self::default()
    }

    pub fn parse(bytes: &[u8]) -> Result<Self, RuntimeError> {
        let file: RevocationFile = serde_json::from_slice(bytes)?;
        if file.schema != 1 {
            return Err(RuntimeError::Invalid(format!(
                "unsupported revocation schema {}",
                file.schema
            )));
        }
        Ok(Self {
            entries: file.revoked,
        })
    }

    pub fn is_revoked(&self, id: &str, version: &str) -> bool {
        self.entries
            .iter()
            .any(|entry| entry.id == id && entry.version == version)
    }

    pub fn entries(&self) -> &[Entry] {
        &self.entries
    }

    pub fn len(&self) -> usize {
        self.entries.len()
    }

    pub fn is_empty(&self) -> bool {
        self.entries.is_empty()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const LIST: &[u8] = br#"{
        "schema": 1,
        "revoked": [
            {"id": "org.opencrown.payload.example", "version": "1.0.0", "reason": "panics"},
            {"id": "org.opencrown.payload.other", "version": "2.0.0"}
        ]
    }"#;

    #[test]
    fn parses_and_matches_exact_version() {
        let list = RevocationList::parse(LIST).unwrap();
        assert_eq!(list.len(), 2);
        assert!(list.is_revoked("org.opencrown.payload.example", "1.0.0"));
        assert!(!list.is_revoked("org.opencrown.payload.example", "1.0.1"));
        assert!(!list.is_revoked("org.opencrown.payload.other", "1.0.0"));
    }

    #[test]
    fn rejects_unknown_schema() {
        let list = br#"{"schema": 2, "revoked": []}"#;
        assert!(RevocationList::parse(list).is_err());
    }

    #[test]
    fn rejects_unknown_field() {
        let list = br#"{"schema": 1, "revoked": [], "extra": true}"#;
        assert!(RevocationList::parse(list).is_err());
    }

    #[test]
    fn empty_list_matches_nothing() {
        assert!(!RevocationList::empty().is_revoked("anything", "1.0.0"));
    }
}
