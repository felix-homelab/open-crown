//! Bounded reader for untrusted input.
//!
//! Every read is checked against the remaining length. A truncated or
//! over-long input is a Malformed error, never a panic, because this code runs
//! against bytes that come off the air.

use crate::error::Error;

#[derive(Debug, Clone)]
pub struct Reader<'a> {
    data: &'a [u8],
    offset: usize,
}

impl<'a> Reader<'a> {
    pub fn new(data: &'a [u8]) -> Self {
        Self { data, offset: 0 }
    }

    pub fn position(&self) -> usize {
        self.offset
    }

    pub fn remaining(&self) -> usize {
        self.data.len() - self.offset
    }

    pub fn is_empty(&self) -> bool {
        self.remaining() == 0
    }

    pub fn take(&mut self, len: usize) -> Result<&'a [u8], Error> {
        if self.remaining() < len {
            return Err(Error::Malformed("truncated input"));
        }
        let start = self.offset;
        self.offset += len;
        Ok(&self.data[start..self.offset])
    }

    pub fn skip(&mut self, len: usize) -> Result<(), Error> {
        self.take(len).map(|_| ())
    }

    pub fn u8(&mut self) -> Result<u8, Error> {
        Ok(self.take(1)?[0])
    }

    pub fn u16_le(&mut self) -> Result<u16, Error> {
        let bytes = self.take(2)?;
        Ok(u16::from_le_bytes([bytes[0], bytes[1]]))
    }

    pub fn u16_be(&mut self) -> Result<u16, Error> {
        let bytes = self.take(2)?;
        Ok(u16::from_be_bytes([bytes[0], bytes[1]]))
    }

    pub fn u32_le(&mut self) -> Result<u32, Error> {
        let bytes = self.take(4)?;
        Ok(u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]))
    }

    pub fn u32_be(&mut self) -> Result<u32, Error> {
        let bytes = self.take(4)?;
        Ok(u32::from_be_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]))
    }

    /// Unsigned LEB128, bounded to 64 bits.
    pub fn varint(&mut self) -> Result<u64, Error> {
        let mut value = 0u64;
        let mut shift = 0u32;
        loop {
            if shift > 63 {
                return Err(Error::Malformed("varint overflow"));
            }
            let byte = self.u8()?;
            let payload = u64::from(byte & 0x7f);
            if shift == 63 && payload > 1 {
                return Err(Error::Malformed("varint overflow"));
            }
            value |= payload << shift;
            if byte & 0x80 == 0 {
                return Ok(value);
            }
            shift += 7;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_little_endian() {
        let mut reader = Reader::new(&[0x34, 0x12, 0x78, 0x56, 0x34, 0x12]);
        assert_eq!(reader.u16_le().unwrap(), 0x1234);
        assert_eq!(reader.u32_le().unwrap(), 0x1234_5678);
        assert!(reader.is_empty());
    }

    #[test]
    fn reads_big_endian() {
        let mut reader = Reader::new(&[0x12, 0x34]);
        assert_eq!(reader.u16_be().unwrap(), 0x1234);
    }

    #[test]
    fn truncation_is_an_error_not_a_panic() {
        let mut reader = Reader::new(&[0x01]);
        assert!(matches!(reader.u16_le(), Err(Error::Malformed(_))));
        assert_eq!(reader.position(), 0);
    }

    #[test]
    fn skips() {
        let mut reader = Reader::new(&[0x00, 0x01, 0x02]);
        reader.skip(2).unwrap();
        assert_eq!(reader.u8().unwrap(), 0x02);
    }

    #[test]
    fn varint() {
        let mut reader = Reader::new(&[0xac, 0x02]);
        assert_eq!(reader.varint().unwrap(), 300);
    }

    #[test]
    fn varint_overflow() {
        let data = [0xffu8; 12];
        let mut reader = Reader::new(&data);
        assert!(reader.varint().is_err());
    }
}
