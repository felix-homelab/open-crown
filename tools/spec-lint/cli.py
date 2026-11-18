#!/usr/bin/env python3
"""Lint protocol specs: required sections and at least one usable vector."""

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lib"))

from opencrown_tools.speclint import lint_spec


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="+", help="spec files or directories")
    args = parser.parse_args()

    targets = []
    for raw in args.paths:
        path = Path(raw)
        if path.is_dir():
            targets.extend(sorted(path.rglob("spec.md")))
        else:
            targets.append(path)

    if not targets:
        print("no specs found", file=sys.stderr)
        return 2

    issues = []
    for target in targets:
        issues.extend(lint_spec(target))

    for issue in issues:
        print(issue)
    print(f"{len(targets)} spec(s), {len(issues)} issue(s)")
    return 1 if issues else 0


if __name__ == "__main__":
    raise SystemExit(main())
