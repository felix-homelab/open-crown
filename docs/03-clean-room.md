# 03 — Provenance and clean-room discipline

## 1. The prior art, named

The protocol layer was reconstructed in public by
[apple_watch_connector](https://github.com/LordixDemon/apple_watch_connector)
(Java, Flutter, Rust; Android via Magisk, Linux and Windows via native HCI).

Open Crown is an independent reimplementation. We use **ideas, protocol facts,
and behavioural observations** from that work. We do not copy source, comments,
identifiers, string tables, assets, watch-face files, or binaries.

Naming it is a requirement, not a courtesy. The previous revision of this
document referred to "prior companion projects" without naming one, which made
the provenance claim unauditable.

## 2. What the licence position actually is

- The prior repository has **no licence file**, so its code is all rights
  reserved.
- If Open Crown and the prior project share an author, self-reimplementation is
  a **choice**, not a legal necessity; the author could relicense their own code.
  The rewrite is justified as a maintainability decision in
  [ADR 0001](adr/0001-kotlin-rust-uniffi.md), not as a legal shield.
- If they do not share an author, copying is infringement; reading ideas is not.
  Ideas and functionality are not protected by copyright, though patents can
  still apply.
- The real provenance risks are the **third-party components** in that tree and
  in ours (Flutter, Java libraries, Rust crates), Apple's terms, and patents —
  not the author's memory.

Accordingly: no code is copied, and this document does not claim the legal
benefits of a two-team clean room. It claims provenance discipline, which is what
a single maintainer can actually deliver.

## 3. What is allowed

- Concepts, architecture patterns, protocol facts, behavioural observations,
  lessons learned, and public documentation.
- Reading prior design documents and public write-ups for ideas.
- Observing traffic between a phone and a Watch you own.
- Decoding captures and writing prose, diagrams, and tables about them.
- Reading public specifications, patents, and vendor documentation.
- Writing independent code from a spec.
- Reusing protocol constants visible in a capture.

## 4. What is not allowed

- Copying source, comments, identifiers, string tables, or assets.
- Porting a function by "translating" it line by line.
- Lifting binaries, resources, watch-face assets, or keystores.
- Using decompiler output as a code template.
- Mirroring another project's file layout, class names, or internal APIs.
- Reusing a prior project's binary or generated code as a starting point.

## 5. Artefacts per feature

Every protocol feature lands with:

1. **Spec** — `protocol/<area>/<feature>.md`: purpose, sequence, fields,
   endianness, error cases, open questions.
2. **Vectors** — `protocol/<area>/vectors/*.json`: input bytes, expected
   output, and the capture that produced them.
3. **Code + test** — an implementation that consumes the vectors.

A spec without a vector is a hypothesis. A vector without a capture is a guess.
Both may exist, labelled as such.

## 6. Naming

The project uses its own vocabulary: Crown, Agent, Catalog, Link, Channel,
Session, Checkpoint, Borrow, Domain. Shared identifiers are the easiest way to
create a derivative, so if a name matches another project's internal type,
rename it.

The opposite rule also applies: **do not invent synonyms for the prior art's
public protocol names to hide the relationship.** SPAKE2+, uIKE, NetworkRelay,
IDS, NWSC, PBBridge, PairedSync and NPS stay as they are, because they are
observable protocol terms, not copied identifiers.

## 7. Provenance log

[docs/provenance.md](provenance.md) records, per feature:

```text
feature:        discovery advertisement decode
spec:           protocol/discovery/advertisement.md @ <commit>
vectors:        protocol/discovery/vectors/*.json
sources:        own captures (2026-11-18), prior art README (October 2026)
prior ideas:    apple_watch_connector — setup-state advertisement concept
code copied:    none
reviewer:       <name>, <date>
```

The `prior ideas` line must name the project, not gesture at it.

## 8. Third-party code

- Every dependency is pinned and recorded in an SBOM.
- Licences are checked in CI; copyleft in the app process requires review.
- No Apple binaries, Apple SDKs, or internal entitlements are vendored.
- Generated code (UniFFI, protobuf) is marked generated and excluded from
  provenance review.
- The prior art's `NOTICE`-bearing components are not copied; if one is ever
  needed, it enters through the SBOM like any other dependency.

## 9. Exit criteria for the audit

Before v1.0:

- `docs/provenance.md` has an entry for every protocol feature.
- CI runs a similarity scan against a named list of prior projects, including
  the one above.
- A reviewer reads the spec and the code and signs off that the code follows the
  spec.
- No release binary lacks a reproducible build path.
- No asset in the repository is traceable to another project.
- The prior art is named in the README and in the provenance log.
