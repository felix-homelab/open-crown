# IDS

**Status:** not started. No spec, no vectors.

## Purpose

IDS framing, NWSC, request/response correlation, versioning.

## Questions to answer

- How are control and application streams opened?
- How is a response correlated to a request?
- How are unknown services rejected?

## Notes

Foundation for every service.

## Files

- `spec.md` — written from captures, following [TEMPLATE.md](../TEMPLATE.md)
- `vectors/` — one JSON file per case: input bytes, expected output, capture hash
