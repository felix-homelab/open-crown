#!/usr/bin/env python3
"""Report an APK: SHA-256, size, and the Gradle output metadata if present."""

import argparse
import hashlib
import json
from pathlib import Path


def digest(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def report(apk: str | Path, metadata: str | Path | None = None) -> dict:
    apk = Path(apk)
    if not apk.is_file():
        raise FileNotFoundError(apk)
    data = {
        "apk": apk.name,
        "sha256": digest(apk),
        "size": apk.stat().st_size,
    }
    if metadata is None:
        metadata = apk.parent / "output-metadata.json"
    metadata = Path(metadata)
    if metadata.is_file():
        data["metadata"] = json.loads(metadata.read_text(encoding="utf-8"))
    return data


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk")
    parser.add_argument("--metadata", help="defaults to output-metadata.json next to the APK")
    parser.add_argument("--out", help="write the report here instead of stdout")
    args = parser.parse_args()

    data = report(args.apk, args.metadata)
    text = json.dumps(data, indent=2) + "\n"
    if args.out:
        Path(args.out).write_text(text, encoding="utf-8")
        print(f"wrote {args.out}")
    else:
        print(text, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
