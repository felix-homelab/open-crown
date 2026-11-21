#!/usr/bin/env python3
"""Append-only transparency log for payload digests.

Each line is one entry. hash = SHA-256(seq || payload_digest || prev), with the
fields canonicalised, so removing or editing an entry breaks every later hash.
"""

import argparse
import hashlib
import json
from pathlib import Path

GENESIS = "0" * 64


def _canonical(value: dict) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")


def _entry_hash(seq: int, payload_digest: str, prev: str) -> str:
    return hashlib.sha256(
        _canonical({"seq": seq, "payload_digest": payload_digest, "prev": prev})
    ).hexdigest()


def read(path: str | Path) -> list[dict]:
    path = Path(path)
    if not path.exists():
        return []
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def append(path: str | Path, payload_digest: str) -> dict:
    if len(payload_digest) != 64:
        raise ValueError("payload digest must be 64 hex characters")
    path = Path(path)
    entries = read(path)
    prev = entries[-1]["hash"] if entries else GENESIS
    entry = {
        "seq": len(entries) + 1,
        "payload_digest": payload_digest,
        "prev": prev,
    }
    entry["hash"] = _entry_hash(entry["seq"], entry["payload_digest"], entry["prev"])
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(entry, sort_keys=True) + "\n")
    return entry


def verify(path: str | Path) -> list[str]:
    issues: list[str] = []
    prev = GENESIS
    for index, entry in enumerate(read(path), start=1):
        if entry.get("seq") != index:
            issues.append(f"line {index}: seq is {entry.get('seq')!r}")
        if entry.get("prev") != prev:
            issues.append(f"line {index}: prev does not match")
        expected = _entry_hash(
            entry.get("seq", 0), entry.get("payload_digest", ""), entry.get("prev", "")
        )
        if entry.get("hash") != expected:
            issues.append(f"line {index}: hash does not match")
        prev = entry.get("hash", "")
    return issues


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    add = sub.add_parser("append", help="append a payload digest")
    add.add_argument("--log", required=True)
    add.add_argument("--digest", required=True)

    check = sub.add_parser("verify", help="verify the whole chain")
    check.add_argument("--log", required=True)

    args = parser.parse_args()
    if args.command == "append":
        entry = append(args.log, args.digest)
        print(f"appended seq={entry['seq']} hash={entry['hash']}")
        return 0

    issues = verify(args.log)
    for issue in issues:
        print(issue)
    print("ok" if not issues else f"{len(issues)} issue(s)")
    return 0 if not issues else 1


if __name__ == "__main__":
    raise SystemExit(main())
