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
| --- | --- | --- | --- | --- | --- | --- |
| _(none yet)_ | | | | | | |

## Attestation

Every contributor signs off in the PR:

```text
I confirm that this contribution was written from the linked specification
and my own captures, and that no source, asset, or binary from another
project was copied into it.
```
