# hci-echo

**Status:** not implemented.

## Purpose

Phase 0 harness: own the controller and echo bytes.

## Interface

```text
hci-echo --shape a|b --target <addr> --payload 'ping'
```

## Rules

- This is the K2a experiment, not product code.
- It runs with temporary root, and it must record the SELinux context and denials.
- It is allowed to be ugly; it is not allowed to lie.
