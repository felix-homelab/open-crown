#!/usr/bin/env python3
"""Compare two fixtures. Exits 1 when they are identical, so CI can assert change."""

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lib"))

from opencrown_tools.fixturediff import diff
from opencrown_tools.fixtures import load


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("left")
    parser.add_argument("right")
    parser.add_argument(
        "--strict-timing",
        action="store_true",
        help="also report timestamp differences (ignored by default)",
    )
    args = parser.parse_args()

    changes = diff(load(args.left), load(args.right), ignore_timing=not args.strict_timing)
    if not changes:
        print("no differences")
        return 1

    for change in changes:
        print(f"[{change.index}] {change.field}: {change.left!r} -> {change.right!r}")
    print(f"{len(changes)} difference(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
