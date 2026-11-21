# ADR 0013 — Agent IPC envelope v1

**Status:** Accepted
**Date:** 2026-11-21

## Context

The stack document said "Unix socket + protobuf" but no schema existed, so the
agent's attack surface was undefined. The agent runs as root and the app is
untrusted; the envelope is the first thing an attacker with app-level access
will try to forge.

## Decision

- **Protobuf**, generated with prost from `agent/proto/bridge.proto`. One
  envelope per connection, version 1.
- **Identity before capability.** The envelope carries a 64-character lowercase
  hex token and a boot id. Both are validated before any controller operation.
- **Explicit limits**, enforced in Rust and never trusted from the wire:

  | Field | Limit |
  | --- | --- |
  | envelope | 4096 bytes |
  | send payload | 512 bytes |
  | L2CAP PSM | 0x1001–0xFFFF |
  | handle | 1–255 |
  | scan timeout | 30 000 ms |
  | boot id | 1–128 safe characters |

- **One request per envelope**, as a `oneof`: scan, connect, open channel,
  send, close, diag. Unknown or missing requests are rejected.
- **Responses** carry `ok` or `failure { code, message }`.
- **Reproducible generation:** protoc is vendored (`protoc-bin-vendored`), so
  the build does not depend on a system install.

## Consequences

**Positive:** one schema, generated types, and tests for every limit. Field
numbers give forward compatibility without accepting unknown requests. The
agent never parses JSON.

**Negative:** prost and a build-time protoc add to the build; proto3 ignores
unknown fields, so a future request type could be silently ignored by an old
agent — the version check is what prevents that.

## Alternatives considered

- **JSON + serde** — rejected: the stack decision was protobuf, and JSON length
  and type checks are easier to get wrong on untrusted input.
- **Hand-rolled binary** — rejected: needless parsing risk.
- **gRPC** — rejected: a socket with one request per connection is enough, and
  gRPC adds a runtime the agent does not need.
