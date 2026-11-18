# Open Crown — documentation index

| Doc | What it answers |
| --- | --- |
| [00 — Charter](00-charter.md) | Why the project exists; goals, non-goals, success criteria |
| [01 — Feasibility](01-feasibility.md) | What is possible, what is not, and what would kill the project |
| [02 — Architecture](02-architecture.md) | Components, trust boundaries, bootstrap and session flows |
| [03 — Clean room](03-clean-room.md) | What "clean room" permits here, and how it is audited |
| [04 — Payload system](04-payload-system.md) | The per-boot exploit catalog, signing, targeting, safety |
| [05 — Stack](05-stack.md) | Kotlin, Rust, UniFFI, auth stack, build and CI |
| [06 — Roadmap](06-roadmap.md) | Phases, gates, kill criteria, effort |
| [07 — Protocol research](07-protocol-research.md) | What to reverse engineer and how |
| [08 — Testing](08-testing.md) | Evidence standards, hardware matrix, exploit safety tests |
| [09 — Legal and ethics](09-legal-ethics.md) | Ownership, Apple ID handling, distribution, privacy |
| [10 — Risks](10-risks.md) | Risk register and the three that actually matter |
| [11 — Glossary](11-glossary.md) | Terms and abbreviations |
| [ADR 0001](adr/0001-kotlin-rust-uniffi.md) | Kotlin UI + Rust core + UniFFI |
| [ADR 0002](adr/0002-per-boot-borrowed-root.md) | Per-boot borrowed root, not persistent root |
| [ADR 0003](adr/0003-apple-id-in-app.md) | In-app Apple ID sign-in |
| [ADR 0004](adr/0004-payload-catalog-v1.md) | Payload catalog ships in v1 |
| [ADR 0005](adr/0005-no-watch-exploitation.md) | No watch-side exploitation |
| [Provenance](provenance.md) | Clean-room record, filled in per feature |

## Status

Planning. No code yet. The next action is Phase 0 in the roadmap, followed by
the Phase 2 experiment that decides whether the product can exist as specified.
