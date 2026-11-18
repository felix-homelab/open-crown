# fixture-diff

**Status:** not implemented.

## Purpose

Compare two fixtures and highlight changed messages.

## Interface

```text
fixture-diff <a.json> <b.json> [--ignore timing]
```

## Rules

- Message-level diff, not a byte diff.
- Timing is noise; carry it separately.
- Exit non-zero when the diff is empty, so CI can assert a change.
