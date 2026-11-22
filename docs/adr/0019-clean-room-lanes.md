# ADR 0019 — Two-lane clean room with a quarantined reference

**Status:** Accepted
**Date:** 2026-11-22

## Context

The protocol has no public specification. The only large public body of
knowledge is a reference project with no license, plus captures and physical
observation. A single-maintainer project cannot staff a formal two-team clean
room, but it can enforce a documented separation between the lane that reads
the reference and the lane that writes product code. Without that, provenance
claims are unauditable and the reference's structure leaks into the app.

## Decision

Three lanes:

- **A — Spec** reads reference prose, public material, captures, and physical
  observations, and writes `docs/cleanroom/spec/`.
- **B — Build** receives Lane A specs and the design system only. It may not
  read `reference/`. It writes product code.
- **C — Review** audits provenance, licenses, and similarity, and verifies that
  the app builds without the reference tree.

The reference is materialised only from local material, stored under
`reference/apple_watch_connector/`, gitignored, and never added to Gradle,
Cargo, CMake, Python, or CI. Apple-origin assets are not vendored. The
pre-existing page-cache chain is explicitly treated as quarantined payload code
with an open provenance review, not as clean-room product code.

Details: [docs/cleanroom/PROVENANCE.md](../cleanroom/PROVENANCE.md) and
[docs/cleanroom/REFERENCE_BOUNDARY.md](../cleanroom/REFERENCE_BOUNDARY.md).

## Consequences

**Positive:** Lane B has a reproducible rule; Lane C has concrete checks;
release notes can distinguish clean-room code from the quarantined chain.

**Negative:** spec writing is slower than porting, and some facts may stay
UNKNOWN until a capture exists. That is the intended cost.

**Rejected alternatives:**

- **Read the reference and "rewrite carefully"** — no enforceable boundary and
  no defensible audit.
- **Ignore the reference entirely** — loses the only inventory of what to
  verify and makes the project rediscover known dead ends.
- **Vendor the reference under its (absent) license** — not permitted.
