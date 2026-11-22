# Evidence

Every hardware test produces a note here. The format is in
[08 — Testing](../08-testing.md#8-evidence-format).

@@@text
date, operator, phone build, watch model, watchOS, payload id
what was done, what was observed, what was captured
result: pass / fail / partial
attachments: capture hash, screenshots, logs (redacted)
@@@

## Recent notes

| Session | Result | Note |
| --- | --- | --- |
| 2026-11-22 | pass (architecture) | [A52 UniFFI: Rust core loaded](a52-uniffi-core.md) |
| 2026-11-22 | pass (visual) | [A52 design: light appearance and 1.3x text](a52-design-system.md) |
| 2026-11-22 | pass (narrow) | [A52 discovery: ads observed, none selectable](a52-discovery.md) |
| 2026-11-22 | blocked | [A52 broker: real untrusted_app denial](a52-broker-untrusted-app.md) |
| 2026-11-22 | blocked | [Broker probe and relay protocol decode](broker-probe-a52.md) |
| 2026-11-22 | pass (partial) | [A52 chain in the app](a52-app-chain.md) |
| 2026-11-22 | blocked | [Phase 0 K2a](phase0-k2a.md) |

Rules:

- One file per test session, named @@YYYY-MM-DD-<topic>.md@@.
- Raw captures do not live here; they are referenced by hash and stored outside
  the repository (see @@.gitignore@@).
- Redact identifiers, tokens, and health values before committing.
- A claim without a note is reverted.