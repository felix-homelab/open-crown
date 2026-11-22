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
| Discovery inventory and selection rules | [spec/01-discovery.md](cleanroom/spec/01-discovery.md) | none (FE25 layout UNKNOWN; no capture) | Lane A spec; reference behavioural prose as an inventory; own product policy for TTL | bounded inventory, token selection, freshness recheck | none | lead, 2026-11-22 |
| IDS route and session rules | [spec/04-ids-session.md](cleanroom/spec/04-ids-session.md) | none (envelope UNKNOWN; no capture) | Lane A spec; public route names observable in the reference inventory | route catalogue, epoch and receipt concepts | none | lead, 2026-11-22 |
| Provisioning rules | [spec/03-activation-setup.md](cleanroom/spec/03-activation-setup.md) | none (payloads UNKNOWN; no capture) | Lane A spec; reference phase/checkpoint vocabulary as an inventory | phase projection, checkpoint ordinals, dual completion routes | none | lead, 2026-11-22 |
| Face delta and read-back rules | [spec/05-faces.md](cleanroom/spec/05-faces.md) | none (encoding UNKNOWN; no capture) | Lane A spec; reference operation/limit inventory | staged delta, read-back confirmation, package bounds | none | lead, 2026-11-22 |
| Notification claim rules | [spec/06-notifications.md](cleanroom/spec/06-notifications.md) | none (bulletin encoding UNKNOWN; no capture) | Lane A spec; reference claim-rule inventory | revision binding, commit-before-effect, at-most-once | none | lead, 2026-11-22 |
| Write confirmation | [docs/design/state-model.md](design/state-model.md) | none (no wire change; policy model) | own observation rules; Android/agent lifecycle | command lifecycle and no-promotion rule | none | lead, 2026-11-22 |
| Pairing rules | [spec/02-pairing-security.md](cleanroom/spec/02-pairing-security.md) | none (uIKE/SPAKE/SMP UNKNOWN; no capture) | Lane A spec; reference phase/branch inventory | branch exclusivity, bond ordering, replacement safety | none | lead, 2026-11-22 |
| Health mirror rules | [spec/07-settings-wifi-health.md](cleanroom/spec/07-settings-wifi-health.md) | none (encryption/anchors UNKNOWN; no capture) | Lane A spec; reference merge-rule inventory | newer-wins, tombstones, bounds, transactionality | none | lead, 2026-11-22 |

## Attestation

Every contributor signs off in the PR:

```text
I confirm that this contribution was written from the linked specification
and my own captures, and that no source, asset, or binary from another
project was copied into it.
```