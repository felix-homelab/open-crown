#!/usr/bin/env python3
"""Verify a payload manifest: shape, digest, and optional Ed25519 signature."""

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools" / "lib"))

from opencrown_tools.manifest import (
    ManifestError,
    digest,
    load,
    validate,
    verify_signature,
)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest")
    parser.add_argument("--sig", help="raw Ed25519 signature file")
    parser.add_argument("--pubkey", help="raw 32-byte Ed25519 public key")
    args = parser.parse_args()

    manifest = load(args.manifest)
    issues = validate(manifest)

    print(f"id:      {manifest.get('id')}")
    print(f"version: {manifest.get('version')}")
    print(f"digest:  {digest(manifest)}")

    if issues:
        for issue in issues:
            print(f"invalid: {issue}")
        return 1

    if args.sig or args.pubkey:
        if not (args.sig and args.pubkey):
            print("--sig and --pubkey must be given together")
            return 2
        try:
            verify_signature(
                manifest,
                Path(args.sig).read_bytes(),
                Path(args.pubkey).read_bytes(),
            )
        except ManifestError as exc:
            print(f"invalid: {exc}")
            return 1
        print("signature: valid")

    print("ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
