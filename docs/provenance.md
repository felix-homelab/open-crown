# Provenance log

This file records, per feature, what was consulted and what was copied. The
policy is in [03 — Provenance](03-clean-room.md): ideas and protocol facts
allowed; source, comments, identifiers, assets, and binaries are not.

## Prior art

| Project | Relationship | What we take | What we do not take |
| --- | --- | --- | --- |
| [apple_watch_connector](https://github.com/LordixDemon/apple_watch_connector) (Java, Flutter, Rust) | Independent reimplementation of the same protocol; unlicensed | Protocol facts, layer names, behavioural observations, lessons | Source, identifiers, assets, binaries, generated code |

The prior project is named here and in the README. The previous revision used
the phrase "prior companion projects" without naming one, which made the record
unauditable.

## Entries

| Feature | Spec | Vectors | Sources consulted | Prior ideas used | Code copied | Reviewer |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Observation-first shell | [docs/design/state-model.md](design/state-model.md) | none (UI state) | own product requirements; Android lifecycle/Compose documentation | none | none | lead, 2026-11-22 |
| Design system | [docs/design/design-system.md](design/design-system.md) | none (visual) | own design brief; public Compose primitives | none | none; original icons and crown mark | lead, 2026-11-22 |
| Feature registry | [docs/cleanroom/FEATURE_REGISTRY.md](cleanroom/FEATURE_REGISTRY.md) | none | [reference feature inventory](cleanroom/inventory/reference-features.md) as an inventory of what to verify; Apple public support pages for blocker evidence | capability list only; no implementation detail | none | lead, 2026-11-22 |
| BLE advertisement observation | [spec/01-discovery.md](cleanroom/spec/01-discovery.md) | pending | Android BluetoothLeScanner public API; own scan implementation | discovery concept | none | lead, 2026-11-22 |
| Pairing UI state machine | [spec/02-pairing-security.md](cleanroom/spec/02-pairing-security.md) | none (no protocol yet) | Lane A spec; own state requirements | pairing phases as a concept | none | lead, 2026-11-22 |
| App-to-agent broker probe | [docs/evidence/broker-probe-a52.md](evidence/broker-probe-a52.md) | probe source in tools/broker-probe | own physical observation on the A52; Android SELinux/unix-socket public behaviour | none | none; probe is original | lead + broker-probe, 2026-11-22 |
| Clean-room boundary guard | [docs/cleanroom/REFERENCE_BOUNDARY.md](cleanroom/REFERENCE_BOUNDARY.md) | none | own audit requirements | none | none | lead, 2026-11-22 |

## Attestation

Every contributor signs off in the PR:

```text
I confirm that this contribution was written from the linked specification
and my own captures, and that no source, asset, or binary from another
project was copied into it.
```