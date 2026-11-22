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
| 2026-11-22 | pass (support) | [A52 diagnostics export: redacted report](a52-diagnostics-export.md) |
| 2026-11-22 | pass (local reset) | [A52 local app-data reset](a52-local-data-reset.md) |
| 2026-11-22 | pass (labels) | [A52 buttons and rows expose accessibility labels](a52-accessibility-labels.md) |
| 2026-11-22 | pass (contrast) | [A52 required contrast and status-pill fix](a52-contrast-audit.md) |
| 2026-11-22 | pass (accessibility) | [A52 Dynamic Type: 200% row reflow](a52-large-text-200.md) |
| 2026-11-22 | pass (licences) | [A52 open-source licence screen](a52-licences.md) |
| 2026-11-22 | pass (privacy) | [A52 privacy screen: local data and permissions](a52-privacy-screen.md) |
| 2026-11-22 | pass (update) | [A52 update path: state preserved](a52-update-path.md) |
| 2026-11-22 | pass (recovery) | [A52 recovery: app reinstall](a52-recovery-reinstall.md) |
| 2026-11-22 | pass (SBOM) | [A52 release SBOM: 144 components](a52-release-sbom.md) |
| 2026-11-22 | pass (signing gate) | [A52 release signing config and artifact gate](a52-release-signing-gate.md) |
| 2026-11-22 | pass (reproducibility) | [A52 release reproduces byte-for-byte](a52-release-reproducibility.md) |
| 2026-11-22 | pass (release) | [A52 release excludes the privileged relay](a52-release-no-relay.md) |
| 2026-11-22 | pass (release discovery) | [A52 release observes Apple advertisements only](a52-release-discovery.md) |
| 2026-11-22 | pass (build) | [A52 minified release with UniFFI](a52-release-build.md) |
| 2026-11-22 | pass (local policy) | [A52 notifications: per-app mute/unmute](a52-notification-policy.md) |
| 2026-11-22 | pass (local ingress) | [A52 notifications: captured locally, not delivered](a52-notification-ingress.md) |
| 2026-11-22 | pass (architecture) | [A52 UniFFI: Rust core loaded](a52-uniffi-core.md) |
| 2026-11-22 | pass (visual) | [A52 design: light appearance and 1.3x text](a52-design-system.md) |
| 2026-11-22 | pass (wiring) | [A52 discovery driven by the Rust core](a52-rust-discovery.md) |
| 2026-11-22 | pass (narrow) | [A52 discovery: ads observed, none selectable](a52-discovery.md) |
| 2026-11-22 | blocked | [A52 file broker: init cannot read app data](a52-file-broker-probe.md) |
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