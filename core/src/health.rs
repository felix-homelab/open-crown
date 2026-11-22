//! Health defaults mirror.
//!
//! The Watch's Health encryption, anchors and native acceptance are **UNKNOWN**
//! (docs/cleanroom/spec/07-settings-wifi-health.md section 12). This module
//! implements only the established mirror rules: required category/key/
//! timestamp, missing domain becomes the empty string, newer replaces, equal or
//! older and tombstones keep the prior date, a missing value is a tombstone
//! rather than zero, and storage is bounded and transactional.

use std::collections::BTreeMap;
use std::fmt;

/// Mirror bounds from the spec.
pub const MAX_ROWS: usize = 4096;
pub const MAX_BYTES: usize = 16 * 1024 * 1024;
/// Bound on one string/data value before the aggregate bound applies.
pub const MAX_VALUE_BYTES: usize = 1024 * 1024;

#[derive(Debug, Clone, PartialEq)]
pub enum HealthValue {
    Double(f64),
    Int64(i64),
    Text(String),
    Data(Vec<u8>),
    /// The source had no value. This is a tombstone, never zero or empty text.
    Tombstone,
}

impl HealthValue {
    fn bytes(&self) -> usize {
        match self {
            Self::Text(value) => value.len(),
            Self::Data(value) => value.len(),
            _ => 0,
        }
    }
}

#[derive(Debug, Clone, PartialEq)]
pub struct HealthSample {
    pub category: String,
    pub domain: Option<String>,
    pub key: String,
    /// NSDate reference seconds (2001-01-01), not Unix seconds or millis.
    pub timestamp: i64,
    pub value: HealthValue,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum MergeOutcome {
    Inserted,
    Replaced,
    Ignored,
    Tombstoned,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum HealthError {
    MissingCategory,
    MissingKey,
    InvalidTimestamp,
    ValueTooLarge,
    RowsFull,
    BytesFull,
    BatchFailed,
}

impl fmt::Display for HealthError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let code = match self {
            Self::MissingCategory => "HEALTH_CATEGORY",
            Self::MissingKey => "HEALTH_KEY",
            Self::InvalidTimestamp => "HEALTH_TIMESTAMP",
            Self::ValueTooLarge => "HEALTH_VALUE_SIZE",
            Self::RowsFull => "HEALTH_ROWS_FULL",
            Self::BytesFull => "HEALTH_BYTES_FULL",
            Self::BatchFailed => "HEALTH_BATCH_FAILED",
        };
        write!(f, "{code}")
    }
}

impl std::error::Error for HealthError {}

#[derive(Debug, Clone, PartialEq)]
struct Stored {
    domain: String,
    timestamp: i64,
    value: HealthValue,
}

/// Bounded health mirror. A failed batch leaves the prior state untouched.
#[derive(Debug, Clone, Default)]
pub struct HealthMirror {
    rows: BTreeMap<(String, String), Stored>,
    bytes: usize,
}

impl HealthMirror {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn len(&self) -> usize {
        self.rows.len()
    }

    pub fn is_empty(&self) -> bool {
        self.rows.is_empty()
    }

    pub fn bytes(&self) -> usize {
        self.bytes
    }

    pub fn get(&self, category: &str, key: &str) -> Option<&HealthValue> {
        self.rows
            .get(&(category.to_string(), key.to_string()))
            .map(|stored| &stored.value)
    }

    /// Applies one sample. Equal or older timestamps and tombstones keep the
    /// prior date; only a strictly newer value replaces it.
    pub fn insert(&mut self, sample: HealthSample) -> Result<MergeOutcome, HealthError> {
        if sample.category.trim().is_empty() {
            return Err(HealthError::MissingCategory);
        }
        if sample.key.trim().is_empty() {
            return Err(HealthError::MissingKey);
        }
        if sample.timestamp == i64::MIN {
            return Err(HealthError::InvalidTimestamp);
        }
        if sample.value.bytes() > MAX_VALUE_BYTES {
            return Err(HealthError::ValueTooLarge);
        }
        let key = (sample.category.clone(), sample.key.clone());
        let domain = sample.domain.unwrap_or_default();
        match self.rows.get_mut(&key) {
            Some(existing) => {
                if sample.timestamp <= existing.timestamp {
                    return Ok(MergeOutcome::Ignored);
                }
                let old_bytes = existing.value.bytes();
                match sample.value {
                    HealthValue::Tombstone => {
                        existing.value = HealthValue::Tombstone;
                        // Keep the prior date: a later stale value must not
                        // resurrect the row, and the tombstone itself does not
                        // pretend to be an observation date.
                        Ok(MergeOutcome::Tombstoned)
                    }
                    value => {
                        let new_bytes = value.bytes();
                        let next = self
                            .bytes
                            .saturating_sub(old_bytes)
                            .saturating_add(new_bytes);
                        if next > MAX_BYTES {
                            return Err(HealthError::BytesFull);
                        }
                        self.bytes = next;
                        existing.domain = domain;
                        existing.timestamp = sample.timestamp;
                        existing.value = value;
                        Ok(MergeOutcome::Replaced)
                    }
                }
            }
            None => {
                if self.rows.len() >= MAX_ROWS {
                    return Err(HealthError::RowsFull);
                }
                if sample.value == HealthValue::Tombstone {
                    return Ok(MergeOutcome::Ignored);
                }
                let value_bytes = sample.value.bytes();
                if self.bytes.saturating_add(value_bytes) > MAX_BYTES {
                    return Err(HealthError::BytesFull);
                }
                self.bytes += value_bytes;
                self.rows.insert(
                    key,
                    Stored {
                        domain,
                        timestamp: sample.timestamp,
                        value: sample.value,
                    },
                );
                Ok(MergeOutcome::Inserted)
            }
        }
    }

    /// Full transaction: either every sample applies or none does. A storage
    /// failure therefore never becomes an empty mirror.
    pub fn apply_batch(
        &mut self,
        samples: Vec<HealthSample>,
    ) -> Result<Vec<MergeOutcome>, HealthError> {
        let mut candidate = self.clone();
        let mut outcomes = Vec::with_capacity(samples.len());
        for sample in samples {
            outcomes.push(
                candidate
                    .insert(sample)
                    .map_err(|_| HealthError::BatchFailed)?,
            );
        }
        *self = candidate;
        Ok(outcomes)
    }

    pub fn clear(&mut self) {
        self.rows.clear();
        self.bytes = 0;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample(category: &str, key: &str, timestamp: i64, value: HealthValue) -> HealthSample {
        HealthSample {
            category: category.into(),
            domain: None,
            key: key.into(),
            timestamp,
            value,
        }
    }

    #[test]
    fn newer_replaces_and_older_or_equal_is_ignored() {
        let mut mirror = HealthMirror::new();
        mirror
            .insert(sample("heart", "hr", 100, HealthValue::Double(60.0)))
            .unwrap();
        assert_eq!(
            mirror
                .insert(sample("heart", "hr", 90, HealthValue::Double(55.0)))
                .unwrap(),
            MergeOutcome::Ignored
        );
        assert_eq!(
            mirror
                .insert(sample("heart", "hr", 100, HealthValue::Double(70.0)))
                .unwrap(),
            MergeOutcome::Ignored
        );
        assert_eq!(mirror.get("heart", "hr"), Some(&HealthValue::Double(60.0)));
        assert_eq!(
            mirror
                .insert(sample("heart", "hr", 101, HealthValue::Double(72.0)))
                .unwrap(),
            MergeOutcome::Replaced
        );
        assert_eq!(mirror.get("heart", "hr"), Some(&HealthValue::Double(72.0)));
    }

    #[test]
    fn missing_domain_becomes_the_empty_string() {
        let mut mirror = HealthMirror::new();
        mirror
            .insert(sample(
                "sleep",
                "stage",
                1,
                HealthValue::Text("deep".into()),
            ))
            .unwrap();
        assert_eq!(
            mirror
                .rows
                .get(&("sleep".into(), "stage".into()))
                .unwrap()
                .domain,
            ""
        );
    }

    #[test]
    fn missing_value_is_a_tombstone_not_zero() {
        let mut mirror = HealthMirror::new();
        mirror
            .insert(sample("heart", "hr", 100, HealthValue::Double(60.0)))
            .unwrap();
        assert_eq!(
            mirror
                .insert(sample("heart", "hr", 101, HealthValue::Tombstone))
                .unwrap(),
            MergeOutcome::Tombstoned
        );
        assert_eq!(mirror.get("heart", "hr"), Some(&HealthValue::Tombstone));
        assert_ne!(mirror.get("heart", "hr"), Some(&HealthValue::Double(0.0)));

        // A tombstone keeps the prior date: a stale value cannot resurrect it.
        assert_eq!(
            mirror
                .insert(sample("heart", "hr", 100, HealthValue::Double(50.0)))
                .unwrap(),
            MergeOutcome::Ignored
        );
        assert_eq!(mirror.get("heart", "hr"), Some(&HealthValue::Tombstone));

        // A newer value can replace the tombstone.
        assert_eq!(
            mirror
                .insert(sample("heart", "hr", 102, HealthValue::Double(65.0)))
                .unwrap(),
            MergeOutcome::Replaced
        );
        assert_eq!(mirror.get("heart", "hr"), Some(&HealthValue::Double(65.0)));
    }

    #[test]
    fn required_fields_are_enforced() {
        let mut mirror = HealthMirror::new();
        assert!(matches!(
            mirror.insert(sample("", "k", 1, HealthValue::Int64(1))),
            Err(HealthError::MissingCategory)
        ));
        assert!(matches!(
            mirror.insert(sample("c", "", 1, HealthValue::Int64(1))),
            Err(HealthError::MissingKey)
        ));
        assert!(matches!(
            mirror.insert(sample(
                "c",
                "k",
                1,
                HealthValue::Data(vec![0; MAX_VALUE_BYTES + 1])
            )),
            Err(HealthError::ValueTooLarge)
        ));
    }

    #[test]
    fn row_and_byte_bounds_are_enforced() {
        let mut mirror = HealthMirror::new();
        for index in 0..MAX_ROWS {
            mirror
                .insert(sample("c", &format!("k{index}"), 1, HealthValue::Int64(1)))
                .unwrap();
        }
        assert!(matches!(
            mirror.insert(sample("c", "overflow", 1, HealthValue::Int64(1))),
            Err(HealthError::RowsFull)
        ));

        let mut bytes = HealthMirror::new();
        let value = HealthValue::Data(vec![1; MAX_VALUE_BYTES]);
        let mut inserted = 0;
        loop {
            let result = bytes.insert(sample("c", &format!("k{inserted}"), 1, value.clone()));
            if matches!(result, Err(HealthError::BytesFull)) {
                break;
            }
            result.unwrap();
            inserted += 1;
            if inserted > 32 {
                panic!("expected the byte bound");
            }
        }
        assert!(inserted > 0);
    }

    #[test]
    fn a_failed_batch_leaves_the_prior_mirror_untouched() {
        let mut mirror = HealthMirror::new();
        mirror
            .insert(sample("heart", "hr", 1, HealthValue::Double(60.0)))
            .unwrap();
        let before = mirror.len();
        let result = mirror.apply_batch(vec![
            sample("heart", "hr", 2, HealthValue::Double(61.0)),
            sample("", "bad", 2, HealthValue::Double(0.0)),
        ]);
        assert_eq!(result, Err(HealthError::BatchFailed));
        assert_eq!(mirror.len(), before);
        assert_eq!(mirror.get("heart", "hr"), Some(&HealthValue::Double(60.0)));
    }

    #[test]
    fn successful_batch_is_atomic_and_ordered() {
        let mut mirror = HealthMirror::new();
        let outcomes = mirror
            .apply_batch(vec![
                sample("heart", "hr", 1, HealthValue::Double(60.0)),
                sample("heart", "hr", 2, HealthValue::Double(61.0)),
                sample("heart", "hr", 2, HealthValue::Double(70.0)),
            ])
            .unwrap();
        assert_eq!(
            outcomes,
            vec![
                MergeOutcome::Inserted,
                MergeOutcome::Replaced,
                MergeOutcome::Ignored
            ]
        );
        assert_eq!(mirror.get("heart", "hr"), Some(&HealthValue::Double(61.0)));
    }

    #[test]
    fn a_missing_row_is_none_not_zero() {
        let mirror = HealthMirror::new();
        assert_eq!(mirror.get("heart", "hr"), None);
    }
}
