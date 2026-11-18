# 03 — Clean room

## 1. What "clean room" means here

You asked for a clean room but allowed the **ideas and principles** of prior
work to be carried over. That is a pragmatic, not a strict, clean room. This
document defines exactly what that permits, so the position is defensible and
auditable.

**Allowed:** concepts, architecture patterns, protocol facts, behavioural
observations, lessons learned, and public documentation.

**Not allowed:** source code, comments, identifiers, string tables, assets,
watch-face files, binaries, or internal APIs copied from another project.

The line is: **re-implement the idea; never copy the expression.**

## 2. Why it matters

- Prior companion projects in this space are often unlicensed (no LICENSE means
  all rights reserved). Copying creates a derivative-work risk that is hard to
  unwind later.
- A from-scratch implementation forces a written specification and gives us
  golden vectors that a copied implementation never has.
- Clean provenance is a distribution requirement: users are being asked to run
  root-capable code, and "where did this come from" must have an answer.

## 3. Rules

### Allowed

- Reading prior design documents and public write-ups for ideas.
- Observing traffic between a phone and a Watch you own.
- Decoding captures and writing prose, diagrams, and tables about them.
- Reading public specifications, patents, and vendor documentation.
- Writing independent code from a spec.
- Reusing protocol constants that are visible in a capture.

### Not allowed

- Copying source, comments, identifiers, string tables, or assets.
- Porting a function by "translating" it line by line.
- Lifting binaries, resources, watch-face assets, or keystores.
- Using decompiler output as a code template.
- Mirroring another project's file layout, class names, or internal APIs.

### Grey zone — note it in the provenance log

- Reading a prior design doc that describes a sequence.
- Comparing behaviour against another running implementation (black-box only).
- Reusing an idea whose name is also a protocol term (e.g. "IDS", "checkpoint").

## 4. Artefacts per feature

Every protocol feature lands with:

1. **Spec** — `protocol/<area>/<feature>.md`: purpose, sequence, fields,
   endianness, error cases, open questions.
2. **Vectors** — `protocol/<area>/vectors/*.json`: input bytes, expected output,
   and the capture that produced them.
3. **Code + test** — an implementation that consumes the vectors.

A spec without a vector is a hypothesis. A vector without a capture is a guess.
Both may exist, labelled as such.

## 5. Information barrier in practice

One person holds prior knowledge, so the barrier is made of artefacts and time:

- Specs are written from captures **before** the corresponding code, and
  committed with a timestamp.
- Implementation PRs link to the spec, never to another repository.
- The reviewer checks code against the spec and vectors, not against memory.
- Any file that "feels familiar" is rewritten from the spec.

This is not a perfect barrier. It is a documented, auditable effort.

## 6. Provenance log

`docs/provenance.md` (created with the first code commit) records, per feature:

```text
feature:        discovery advertisement decode
spec:           protocol/discovery/advertisement.md @ <commit>
vectors:        protocol/discovery/vectors/*.json
sources:        own captures (2026-11-18), public BLE advertising format
prior ideas:    setup-state advertisement concept (public write-ups)
code copied:    none
reviewer:       <name>, <date>
```

The `prior ideas` line is the honest record of what §1 permits.

## 7. Third-party code

- Every dependency is pinned and recorded in an SBOM.
- Licenses are checked in CI; copyleft in the app process requires review.
- No Apple binaries, Apple SDKs, or internal entitlements are vendored.
- Generated code (UniFFI, protobuf) is marked generated and excluded from
  provenance review.

## 8. Naming

The project uses its own vocabulary: Crown, Agent, Catalog, Link, Channel,
Session, Checkpoint, Borrow. Shared identifiers are the easiest way to create a
derivative, so if a name matches another project's internal type, rename it.

## 9. Exit criteria for the clean-room audit

Before v1.0:

- `docs/provenance.md` has an entry for every protocol feature.
- CI runs a similarity scan against a list of known prior projects.
- A reviewer reads the spec and the code and signs off that the code follows
  the spec.
- No release binary lacks a reproducible build path.
- No asset in the repository is traceable to another project.
