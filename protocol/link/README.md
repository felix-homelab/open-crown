# Link

**Status:** not started. No spec, no vectors.

## Purpose

ERTM, NetworkRelay, Class-C/D, encryption parameters, reconnect.

## Questions to answer

- Which parameters must persist across reboots?
- What must be renegotiated after a Watch reset?

## Notes

Must survive reboots.

## Files

- `spec.md` — written from captures, following [TEMPLATE.md](../TEMPLATE.md)
- `vectors/` — one JSON file per case: input bytes, expected output, capture hash
