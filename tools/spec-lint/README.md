# spec-lint

**Status:** implemented - cli.py on top of tools/lib/opencrown_tools.

## Purpose

Check that a spec has the required sections and at least one vector.

## Interface

```text
spec-lint protocol/**/spec.md
```

## Rules

- Required sections come from protocol/TEMPLATE.md.
- A spec without vectors fails.
- A vector without a capture hash fails.
