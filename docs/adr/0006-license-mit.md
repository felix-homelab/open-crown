# ADR 0006 — MIT licence, distribution risk accepted

**Status:** Accepted
**Date:** 2026-11-18

## Context

The project had no licence, which meant "all rights reserved" while the README
described an open, auditable tool. It also distributes signed root payloads,
which is dual-use and cannot be made safe by a licence.

## Decision

- Licence the project under **MIT**.
- State the distribution risk as **accepted**, in the README and in
  [09 — Legal and ethics](../09-legal-ethics.md).
- Treat legal advice as **advisory**. If advice is obtained, publish it with the
  release; if it is not, say so in the release notes.
- Do not use geographic restriction as a substitute for a distribution decision.

## Consequences

**Positive:** the code is readable, buildable, and forkable; the provenance
audit means something; users can verify the binaries they run.

**Negative:** MIT grants no patent licence and does not stop someone building a
hostile fork. The payload artifact is extractable and we cannot prevent that;
the documents now say so instead of claiming otherwise.

## Alternatives considered

- **Apache-2.0** — a patent grant and a clearer contribution model, at the cost
  of more ceremony. Revisit if the project acquires contributors or patents
  become a concern.
- **GPL-3.0** — discourages proprietary forks, but complicates linking and
  redistribution for an app with native components. Rejected for now.
- **No licence** — rejected: it contradicts the auditability claim.
