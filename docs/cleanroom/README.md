# Clean-room workspace

This directory is the auditable boundary between what we learn and what we
ship.

| Path | Lane | What it contains |
| --- | --- | --- |
| [PROVENANCE.md](PROVENANCE.md) | all | Normative provenance policy, custody hashes, Apple-asset rules, payload-boundary finding |
| [REFERENCE_BOUNDARY.md](REFERENCE_BOUNDARY.md) | all | Who may read the reference; build independence; escalation |
| [BLOCKERS.md](BLOCKERS.md) | all | Named blockers with evidence; every blocked registry row cites one |
| [FEATURE_REGISTRY.md](FEATURE_REGISTRY.md) | all | Generated status of every capability row |
| [feature-registry.json](feature-registry.json) | all | Machine-readable registry consumed by the app |
| [inventory/](inventory/) | A | Reference assets, features, licence report, similarity plan |
| [spec/](spec/) | A | Behaviour specs derived from prose, captures and physical observation |
| [audit/](audit/) | C | Phase baselines, similarity findings, release audits |

## Rules in one line

The reference is an inventory of things to verify, never a source of code.
Lane B does not read it. Every Apple-origin asset stays out unless a written
redistribution right exists. A blocked feature is a product state, not a reason
to fake success.

## Verification

```sh
python3 tools/feature-registry/build.py --check
python3 tools/cleanroom/check-boundary.py
python3 tools/link-check/cli.py .
```

The boundary guard is part of CI. The similarity audit is run at every phase
gate with the method in [inventory/similarity-plan.md](inventory/similarity-plan.md).
The latest result is [audit/phase-1-similarity.md](audit/phase-1-similarity.md).
Registry coverage against the Lane A claims is tracked in
[audit/registry-coverage.md](audit/registry-coverage.md); it records open gaps
rather than claiming completeness.