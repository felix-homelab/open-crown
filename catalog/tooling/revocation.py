#!/usr/bin/env python3
"""Build and check the payload revocation list.

The runtime refuses a revoked id+version before staging
(docs/04-payload-system.md section 7). This tool writes the file it reads.
"""

import argparse
import json
import sys
from pathlib import Path

SCHEMA = 1


def load(path: str | Path) -> dict:
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    if data.get("schema") != SCHEMA:
        raise ValueError(f"unsupported revocation schema: {data.get('schema')!r}")
    return data


def empty() -> dict:
    return {"schema": SCHEMA, "revoked": []}


def revoke(path: str | Path, entry_id: str, version: str, reason: str | None = None) -> dict:
    path = Path(path)
    data = load(path) if path.exists() else empty()
    for entry in data["revoked"]:
        if entry["id"] == entry_id and entry["version"] == version:
            if reason:
                entry["reason"] = reason
            break
    else:
        entry = {"id": entry_id, "version": version}
        if reason:
            entry["reason"] = reason
        data["revoked"].append(entry)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    return data


def is_revoked(path: str | Path, entry_id: str, version: str) -> bool:
    if not Path(path).exists():
        return False
    return any(
        entry["id"] == entry_id and entry["version"] == version
        for entry in load(path)["revoked"]
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    add = sub.add_parser("add", help="revoke a payload")
    add.add_argument("--list", required=True)
    add.add_argument("--id", required=True)
    add.add_argument("--version", required=True)
    add.add_argument("--reason")

    check = sub.add_parser("check", help="ask whether a payload is revoked")
    check.add_argument("--list", required=True)
    check.add_argument("--id", required=True)
    check.add_argument("--version", required=True)

    args = parser.parse_args()
    if args.command == "add":
        revoke(args.list, args.id, args.version, args.reason)
        print(f"revoked {args.id} {args.version}")
        return 0

    if is_revoked(args.list, args.id, args.version):
        print("revoked")
        return 0
    print("not revoked")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
