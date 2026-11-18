# vector-gen

**Status:** not implemented.

## Purpose

Turn a capture into a golden vector with redaction.

## Interface

```text
vector-gen <fixture> --select <n> --redact ids,tokens --out vectors/<case>.json
```

## Rules

- Redaction is explicit and recorded in the vector.
- The vector stores input and expected output, not a whole capture.
- Never ship unredacted identifiers.
