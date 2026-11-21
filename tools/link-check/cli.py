#!/usr/bin/env python3
"""Check relative Markdown links and anchors."""

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lib"))

from opencrown_tools.links import check


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("roots", nargs="*", default=["."])
    args = parser.parse_args()

    issues = []
    for root in args.roots:
        issues.extend(check(root))

    for issue in issues:
        print(issue)
    print("ok" if not issues else f"{len(issues)} issue(s)")
    return 0 if not issues else 1


if __name__ == "__main__":
    raise SystemExit(main())
