# Open Crown — documentation index

| Doc | What it answers |
| --- | --- |
| [00 — Charter](00-charter.md) | Why the project exists; goals, non-goals, success criteria |
| [01 — Feasibility](01-feasibility.md) | What the evidence proves, what it does not, what would kill the project |
| [02 — Architecture](02-architecture.md) | Components, trust boundaries, the two transport shapes |
| [03 — Provenance](03-clean-room.md) | Prior art, clean-room discipline, licences |
| [04 — Payload system](04-payload-system.md) | Privilege contract, supply, resident agent, safety |
| [05 — Stack](05-stack.md) | Kotlin, Rust, UniFFI, auth, build and CI |
| [06 — Roadmap](06-roadmap.md) | K2a first, phases, gates, effort |
| [07 — Protocol research](07-protocol-research.md) | What is already mapped, what we verify, how |
| [08 — Testing](08-testing.md) | Evidence standards, hardware matrix, privilege tests |
| [09 — Legal and ethics](09-legal-ethics.md) | Accepted risk, ownership, distribution, privacy |
| [10 — Risks](10-risks.md) | Risk register and the four that actually matter |
| [11 — Glossary](11-glossary.md) | Terms and the real protocol vocabulary |
| [12 — Decisions](12-decisions.md) | The decision log this revision came from |
| [Provenance log](provenance.md) | Per-feature clean-room record |

## ADRs

| ADR | Decision | Status |
| --- | --- | --- |
| [0001](adr/0001-kotlin-rust-uniffi.md) | Kotlin UI + Rust core + UniFFI | Accepted |
| [0002](adr/0002-per-boot-borrowed-root.md) | Per-boot borrowed root, not persistent root | Under test (K2a/K2b) |
| [0003](adr/0003-apple-id-in-app.md) | In-app Apple ID sign-in | Proposed — which side holds the session is open |
| [0004](adr/0004-payload-catalog-v1.md) | Payload catalog ships in v1 | Accepted, with the ADR 0010 order |
| [0005](adr/0005-no-watch-exploitation.md) | No watch-side exploitation | Accepted |
| [0006](adr/0006-license-mit.md) | MIT licence; distribution risk accepted | Accepted |
| [0007](adr/0007-uid0-plus-domain.md) | Borrowed privilege is `uid 0 + SELinux domain` | Accepted (to be verified by K2a) |
| [0008](adr/0008-resident-agent.md) | Resident agent for the life of the boot | Accepted |
| [0009](adr/0009-payload-supply.md) | Public n-days and a supported-device list | Accepted |
| [0010](adr/0010-ship-order.md) | Portfolio order: research, companion, catalog | Accepted |
| [0011](adr/0011-no-harness-root.md) | No harness root; the payload is the only path | Accepted |
| [0012](adr/0012-catalog-key-custody.md) | Offline root, delegated signing keys, revocation, log | Accepted |
| [0013](adr/0013-agent-ipc-envelope.md) | Protobuf envelope v1 with explicit limits | Accepted |

## Status

Planning. Build skeleton only — no protocol logic. Two things gate everything:

1. **K2a** — can a payload take the controller under enforcing SELinux?
2. **Activation** — can the phone hold the Apple ID session, or must the Watch?

Both are experiments, not assumptions. See [06 — Roadmap](06-roadmap.md).
