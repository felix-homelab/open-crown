# ADR 0012 — Catalog key custody

**Status:** Accepted
**Date:** 2026-11-21

## Context

A signed catalog is the trust root for code that yields root. The private key is
therefore the most valuable secret in the project, and the repository must never
contain it. Signing must still be possible on a machine that is usually offline.

## Decision

- **Root key offline.** Generated on an air-gapped machine, stored on removable
  media in two locations, never on a networked host.
- **Delegated keys** sign payloads. They are scoped to an id prefix, have a short
  validity window, and can be revoked without touching the root.
- **Revocation list** is published with the catalog and checked by the runtime
  before staging (implemented in `runtime/src/revocation.rs`).
- **Transparency log**: every digest is appended to a hash-chained log
  (`catalog/tooling/transparency.py`); a split view is detectable by comparing
  the head.
- **No key material in the repository.** `catalog/examples/signed/` is signed by
  a throwaway key whose private half was discarded; it exists only to prove the
  Python signer and the Rust verifier agree.
- **Rotation** uses an overlap window: the new key signs first, the old key stays
  trusted until its validity ends, and the app's pinned key set accepts both.

## Consequences

**Positive:** a compromised build machine cannot mint payloads beyond the
delegated key's scope and lifetime; revocation is immediate; the log makes a
silent replacement visible.

**Negative:** signing is a manual ceremony; losing the root key means a new
trust root and an app update; delegated keys add a second thing to protect.

## Alternatives considered

- **One long-lived online key** — rejected: a single compromise forges the whole
  catalog.
- **No signing, hashes only** — rejected: the hash would ship with the bundle and
  prove nothing.
- **App-store style review** — not available; distribution is direct.
