# ADR 0015 — External payload quarantine (private development)

**Status:** Accepted for private development; public distribution blocked
**Date:** 2026-11-21

## Context

The project owner directed that payloads be extracted from the supplied public
sources and integrated into this repository under descriptive Open Crown names,
and decided that licensing would be resolved later while the repository stays
private.

The sources are **not drop-in bundles**:

- DirtyFrag is an APK with JNI and kernel modules.
- Root My Galaxy is a JNI/preload shared object driven by its own app.
- Root My S24 is a Shizuku `UserService` runner around `libtimer`/`uhexec`/`ksud`.

Copying a file and renaming it does not make it an Open Crown payload, and it
does not transfer authorship. The upstream licences also differ from this
project's MIT licence (ADR 0006): GPL-2.0-only, GPL-3.0 and Apache-2.0.

## Decision

- Extracted, **unmodified** upstream artifacts live under
  `payloads/external/<catalog-id>/`, never mixed into `runtime/`, `app/` or
  `core/`.
- Each bundle carries `PROVENANCE.md` (source URL, commit, upstream path,
  licence, hash, exact target, upstream claim) and the upstream licence text.
- Open Crown catalog names are labels, not authorship claims. The upstream
  copyright and licence remain in force.
- The Open Crown MIT grant applies only to Open Crown-authored code. The
  external tree is **not** MIT-licensed and must not be distributed as part of
  an MIT release.
- No external payload is run on the A52 or any other device in this phase. No
  readiness result is a hardware-verification claim.
- Before any public distribution, the external tree must be removed, replaced
  by separately licensed repositories, or the combined distribution must be
  relicensed and notices satisfied. This ADR does not resolve that question.

## Why not port the code into `runtime/` under a new name

The source apps contain their own privileged runners and device glue. A
faithful port is a new exploit-development project, not a rename, and it would
put third-party code under the MIT tree. If a first-party payload is wanted, it
needs its own target, build, evidence and licence decision.

## Consequences

**Positive:** the payload source and its exact target are recorded; the
catalog/readiness layer can see the bundle; the licence boundary is explicit;
no false authorship or hardware claim.

**Negative:** the repository contains third-party GPL/Apache code and a large
binary; the public-release question is deferred; the A52 still cannot execute
any of it; root-only payloads do not satisfy the controller contract.

## Alternatives considered

- **Copy/port into MIT `runtime/`** — rejected: licence conflict, false
  provenance, and the code depends on its own privileged runner.
- **Fetch at build time and never commit** — kept as the cleaner long-term
  adapter, but the owner asked for a direct private integration now.
- **Separate GPL payload repository** — still the best public-release shape;
  deferred with the licence decision.
