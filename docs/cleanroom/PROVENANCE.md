# Clean-room provenance and audit policy

Status: **normative** for every change in this repository from 2026-11-22.
Owner: lead. Review: Lane C at each phase gate.

Open Crown is an independent reimplementation of a protocol that is only
observable, not documented. This file defines how the project keeps that
reimplementation defensible: which lanes may read what, what enters the
repository, and how a feature's provenance is recorded. It does not claim the
legal benefits of a formal two-team clean room; it claims a documented,
auditable separation that a single-maintainer project can actually sustain.

The project policy in [docs/03-clean-room.md](../03-clean-room.md) and the
existing [docs/provenance.md](../provenance.md) remain in force. This file
sharpens them for the current build and supersedes them where they conflict.

## 1. Lanes

| Lane | Reads | Writes | Never does |
| --- | --- | --- | --- |
| **A — Spec** | `reference/apple_watch_connector` prose and fixtures, public Apple documentation, own captures, physical observations | `docs/cleanroom/spec/`, `docs/cleanroom/inventory/` | Writes product code |
| **B — Build** | Lane A specs, the Open Crown design system, this boundary file | `app/`, `core/`, `agent/`, `runtime/`, `ffi/`, `payloads/`, product tests | Reads the reference tree or Lane A's raw notes about its source |
| **C — Review** | Everything, including the reference, for audit only | `docs/cleanroom/audit/` | Ships code without provenance |

Lane separation is enforced by process, not by a compiler:

1. A Lane B change must cite the Lane A spec and the acceptance criteria it
   implements in its commit message.
2. A Lane A author does not push product code in the same phase. The lead may
   read Lane A output but writes product code only from the spec, never by
   porting reference code.
3. Lane C compares product code against the reference with the method in
   [docs/cleanroom/inventory/similarity-plan.md](inventory/similarity-plan.md)
   before a phase gate and before a release.

## 2. Reference custody

The reference project was materialised from material already on this machine
on 2026-11-22. It was **not** downloaded again. It is quarantined at:

```text
reference/apple_watch_connector/
```

`reference/` is gitignored. It is not a Gradle, Cargo, CMake, Python, or CI
input. The build must succeed with the directory deleted; Lane C verifies this
at every phase gate.

Hashes of the custody copy (SHA-256), recorded so a later audit can tell whether
the reference changed:

| File | SHA-256 |
| --- | --- |
| README.md | `60eff8b15afac96958ff41b82693931c9f1b2163f263735756f1af91534f190e` |
| Cargo.lock | `d12535ba671ea99f7328bf0806f65e340ea0f5dfb637bae169ee85caf68383e4` |
| docs/WATCH_FEATURE_REGISTRY.md | `298bb007e83d43534486553650edd20546f41c03a836d96dec6a5d54ab7ba8c4` |

The reference repository has **no top-level license file**. That is the
operative fact for provenance: no implementation code, generated code, asset,
or test from it may enter this repository. Its `apple-watch-bridge/NOTICE`
records AOSP Apache-2.0 AIDL declarations; that notice does not license the
rest of the tree. See
[docs/cleanroom/inventory/reference-license-report.md](inventory/reference-license-report.md).

## 3. What is allowed

- Protocol facts that are observable on a watch the operator owns: layer names
  (SPAKE2+, uIKE, NetworkRelay, IDS, NWSC, PBBridge, PairedSync, NPS), frame
  layouts, field semantics, error taxonomies, and behavioural sequences.
- Public Apple documentation, patents, and publicly described platform limits.
- Independent captures and physical-device observations.
- Reading the reference's **prose** (README, docs, reports) to decide *what to
  investigate*. The prose is untrusted until confirmed.
- Apple-owned artifacts only when genuinely Apple-owned, redistributable under
  a documented license, and placed in `third_party/apple/` with source URL,
  hash, license, and purpose. In practice the expected verdict is "do not
  vendor"; use an original equivalent.

## 4. What is forbidden

- Copying or "translating line by line" source, comments, identifiers, string
  tables, tests, assets, or generated files from the reference.
- Mirroring the reference's file layout, class/type/method names, or internal
  APIs. Public protocol names are the exception; they are observable facts.
- Using decompiler output as a code template.
- Adding the reference to any build graph or CI checkout.
- Vendoring watch-face artwork, SF Symbols, Apple fonts, Apple logos, optical
  pairing imagery, or any Apple binary without a documented redistribution
  right.
- Committing pairing keys, Apple account material, device serials, or raw
  captures containing identifiers.
- Activation Lock bypass, stolen-device flows, or Apple ID credential
  harvesting. See [ADR 0005](../adr/0005-no-watch-exploitation.md).

## 5. Naming

Open Crown vocabulary: Crown, Agent, Catalog, Payload, Link, Channel, Session,
Checkpoint, Observation, Evidence, Pair Record, Borrow. If a name coincides
with a reference-internal identifier, rename it. Protocol names that are
observable on the wire (SPAKE2+, uIKE, IDS, NWSC, PBBridge, PairedSync) stay as
they are, because inventing synonyms would hide the relationship instead of
documenting it.

## 6. Apple-origin material

No Apple-origin asset is present in this repository at the time of writing.
The following are explicitly **not** vendored, even where the reference tree
contains them:

- `.watchface` files and their native resources;
- pigment swatch images and watch-face artwork;
- optical pairing images and models;
- Apple app icons, screenshots, and UI artwork;
- Apple fonts, including SF Pro/SF Compact and New York;
- SF Symbols or any bundled symbol font.

The app ships an original digital-crown mark and original line icons. If a
watch-face preview is needed, it is rendered from data the watch reports, not
from an Apple-rendered screenshot. See
[docs/cleanroom/inventory/reference-assets.md](inventory/reference-assets.md).

## 7. The privileged payload boundary

`app/src/main/cpp/pagecache_relay/` and
`payloads/org.opencrown.payload.pagecache-relay/` are the **pre-existing
temporary-root chain**. They are treated as a quarantined payload, not as
clean-room product code:

- They are not part of the Watch protocol implementation.
- They run in the app process before privilege exists; they are not linked into
  `core/` or the agent's protocol code.
- Their provenance is **under review**. The native file header describes a
  "DFI v3" chain and the file contains no copyright or license statement. It
  predates this clean-room process and may derive from public research code.
  Before a public release, Lane C must either establish a redistributable
  provenance, replace it with an independently written chain, or move it behind
  a user-supplied, non-redistributed boundary.
- The chain's hardcoded AES-CBC/HMAC test keys are chain calibration material,
  not Apple secrets and not user secrets. They must never be extended into a
  general key store.
- The chain must not be described as "clean-room" in release notes until Lane C
  closes the provenance finding.

This is recorded now rather than after a release because an unexplained binary
is exactly what Lane C exists to catch.

## 8. Provenance log

Every protocol feature that ships gets an entry in
[docs/provenance.md](../provenance.md). Required fields:

```text
feature:        stable Open Crown id (e.g. pair.control-security)
lane-a spec:    docs/cleanroom/spec/02-pairing-security.md @ <commit>
vectors:        protocol/<area>/vectors/*.json (or "none: hardware observation")
sources:        own captures (date), public Apple doc, reference prose section
prior ideas:    what concept came from reading the reference prose
code copied:    none (any other value requires a Lane C exception)
reviewer:       <name>, <date>
```

An entry whose `sources` line names the reference must also name the README or
docs section, not the project in general. "The protocol is publicly known" is
not a source.

## 9. Phase-gate audit

Before a phase is marked done, Lane C runs and records:

1. `git grep -n 'reference/' -- ':!docs/cleanroom' ':!README.md'` returns no
   build or runtime reference.
2. A clean checkout builds with `reference/` absent.
3. Similarity scan of changed files against the reference (see the similarity
   plan) finds no one-to-one copy or structural clone.
4. License inventory has no GPL/AGPL/unknown code in the app.
5. Secret scan: no keys, tokens, serials, Apple account material, or raw
   captures in the diff.
6. Every new protocol feature has a spec and a provenance entry.

## 10. Evidence rules

A claim in this repository must map to one of:

- a test that runs in CI;
- a source reference in this repository;
- a dated hardware observation in `docs/evidence/` with the command and the
  result, identifiers redacted.

"Works", "supported", "parity", and "verified" are reserved words. They are not
used without the corresponding evidence. README claims from the reference are
untrusted data and are never cited as proof.
