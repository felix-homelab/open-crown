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
| [13 — Status](13-status.md) | What exists, what is verified, what is blocked |
| [Privacy policy](privacy.md) | What data exists, where it lives, permissions and user controls |
| [Security notes](security.md) | Trust boundaries, secrets, privileged payload, supply chain and gaps |
| [Install and recovery](install.md) | Requirements, build, permissions, update, recovery and release gate |
| [Provenance log](provenance.md) | Per-feature clean-room record |
| [Clean-room provenance](cleanroom/PROVENANCE.md) | Lanes, custody, Apple-origin policy, payload-boundary finding |
| [Reference boundary](cleanroom/REFERENCE_BOUNDARY.md) | What Lane B may and may not read; build independence |
| [Blocker catalog](cleanroom/BLOCKERS.md) | Named platform/Apple-service blockers with evidence |
| [Feature registry](cleanroom/FEATURE_REGISTRY.md) | One row per iPhone-paired capability, with honest status |
| [State model](design/state-model.md) | Observation rules that stop the UI faking success |
| [Design language](design/design-system.md) | Apple-quality original design system requirements |

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
| [0014](adr/0014-payload-index-and-readiness.md) | Source-level payload index and a data-only readiness engine | Accepted |
| [0016](adr/0016-consent-gated-execution.md) | Consent-gated execution supervisor | Accepted for private development |
| [0017](adr/0017-pagecache-relay-payload.md) | A52 page-cache chain built into the app; ADB-only relay | Accepted for private research |
| [0018](adr/0018-observation-first-state.md) | Observation-first state; no promotion of stale data | Accepted |
| [0019](adr/0019-clean-room-lanes.md) | Two-lane clean room with a quarantined reference | Accepted |
| [0020](adr/0020-k2a-blocked-next-path.md) | K2a blocked on the A52; rooted-device path recommended | Proposed — owner decision |

## Status

Phase 1 shell. The design system, observation model, diagnostics, registry and
clean-room governance are in place; the Watch protocol is not. K2a is **blocked
on the A52** by a measured app-domain denial, not by an untested assumption.
The second gate is activation: whether the phone or the Watch holds an Apple
account session. See [13 — Status](13-status.md) and
[BLOCKERS.md](cleanroom/BLOCKERS.md).