# capture-import

**Status:** not implemented.

## Purpose

Normalise an Android HCI snoop log or a pcap into the fixture format.

## Interface

```text
capture-import <capture> --out fixtures/<name>.json --meta <json>
```

## Rules

- Preserve every byte; never rewrite a capture.
- Record the capture hash in the fixture.
- Refuse to emit a fixture without date, phone build, watch model and watchOS.
