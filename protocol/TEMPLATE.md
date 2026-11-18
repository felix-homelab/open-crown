# <Area> — <Feature> spec

**Status:** draft | verified | stale
**Supported watchOS:** <range>
**Last verified:** <date>, <hardware>
**Vectors:** `vectors/*.json`

## Purpose

One paragraph. What problem does this feature solve for the user?

## Actors and preconditions

Who is involved, and what must already be true. Include the privilege level and
the transport shape (A or B, see docs/02-architecture.md section 3).

## Sequence

A diagram and a numbered walkthrough. Mark server round-trips explicitly.

## Messages

| Name | Direction | Fields | Type | Endianness | Optional |
| --- | --- | --- | --- | --- | --- |
| | | | | | |

## State machine and errors

States, transitions, and what happens on each error. A protocol mismatch is a
first-class error, not a parse crash.

## Version and feature detection

What differs across watchOS versions, and how it is detected before use.

## Open questions

Marked, not guessed.

## Vectors

| Vector | Capture | Covers |
| --- | --- | --- |
| `vectors/...json` | `sha256:...` | |

## Acceptance

```text
Given a factory-reset Watch on <watchOS>, when <action>, then <observable>.
Evidence: capture <hash>, vector <path>, hardware note <date>.
Known limits: <what is not covered>.
```
