# Setup

**Status:** not started. No spec, no vectors.

## Purpose

PBBridge ordering, initial properties, language, PairedSync/NPS, observers.

## Questions to answer

- Which completion observer is authoritative?
- Which steps are order-sensitive?
- What does activation actually confirm?

## Notes

Ordering matters more than any single message.

## Files

- `spec.md` — written from captures, following [TEMPLATE.md](../TEMPLATE.md)
- `vectors/` — one JSON file per case: input bytes, expected output, capture hash
