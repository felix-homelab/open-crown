#!/usr/bin/env python3
"""Fail when tracked files contain credentials or signing material."""

from __future__ import annotations

import argparse
import pathlib
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "lib"))

from opencrown_tools.secrets import scan_paths


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", default=".")
    args = parser.parse_args()
    root = pathlib.Path(args.root).resolve()
    try:
        output = subprocess.run(
            ["git", "ls-files", "-z"],
            cwd=root,
            check=True,
            capture_output=True,
        ).stdout
    except (subprocess.CalledProcessError, FileNotFoundError):
        print("secret scan: unable to list tracked files", file=sys.stderr)
        return 2
    paths = [root / item.decode() for item in output.split(b"\0") if item]
    findings = scan_paths(paths, root)
    if findings:
        print("secret scan: FAILED")
        for finding in findings:
            print("  - " + finding)
        return 1
    print(f"secret scan: clear ({len(paths)} tracked files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
