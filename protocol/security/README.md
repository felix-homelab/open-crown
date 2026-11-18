# Security

**Status:** not started. No spec, no vectors.

## Purpose

uIKE, SPAKE2+/PPK, PIN and optical proofs, bond derivation.

## Questions to answer

- What exactly is the long-term secret?
- What does the Watch require from the host during the handshake?
- How is the bond derived and stored?

## Notes

The crux of the protocol work.

## Files

- `spec.md` — written from captures, following [TEMPLATE.md](../TEMPLATE.md)
- `vectors/` — one JSON file per case: input bytes, expected output, capture hash
