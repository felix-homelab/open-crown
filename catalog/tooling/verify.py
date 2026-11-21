#!/usr/bin/env python3
"""Verify a payload bundle end to end.

Checks the manifest rules, the signature, the revocation list, and every
artifact's size and SHA-256. This is the release-side check that mirrors what
the runtime does before staging.
"""

import argparse
import hashlib
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools" / "lib"))

from opencrown_tools.manifest import digest, load, validate
from revocation import is_revoked
from sign import verify_manifest


def _file_hash(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def verify_bundle(bundle_dir: str | Path, revocations: str | Path | None = None) -> list[str]:
    bundle = Path(bundle_dir)
    manifest = load(bundle / "manifest.json")
    issues = validate(manifest)
    if issues:
        return issues

    if revocations is not None and is_revoked(
        revocations, manifest["id"], manifest["version"]
    ):
        issues.append("payload is revoked")

    signature_path = bundle / "manifest.sig"
    public_path = bundle / "public_key.hex"
    if not signature_path.is_file():
        issues.append("missing manifest.sig")
    elif not public_path.is_file():
        issues.append("missing public_key.hex")
    else:
        try:
            verify_manifest(
                manifest,
                signature_path.read_bytes(),
                public_path.read_text(encoding="utf-8").strip(),
            )
        except ValueError as exc:
            issues.append(str(exc))

    for artifact in manifest["artifacts"]:
        path = bundle / artifact["path"]
        if not path.is_file():
            issues.append("missing artifact: " + artifact["path"])
            continue
        if path.stat().st_size != artifact["size"]:
            issues.append("size mismatch: " + artifact["path"])
        if _file_hash(path) != artifact["sha256"]:
            issues.append("hash mismatch: " + artifact["path"])

    return issues


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", nargs="?", help="bundle directory to verify")
    parser.add_argument("--manifest", help="check only the manifest shape")
    parser.add_argument("--revocations", help="revocation list to consult")
    args = parser.parse_args()

    if args.manifest:
        manifest = load(args.manifest)
        issues = validate(manifest)
        print(f"id:      {manifest.get('id')}")
        print(f"version: {manifest.get('version')}")
        print(f"digest:  {digest(manifest)}")
        for issue in issues:
            print(f"invalid: {issue}")
        print("ok" if not issues else f"{len(issues)} issue(s)")
        return 0 if not issues else 1

    if not args.bundle:
        parser.error("give a bundle directory or --manifest")

    issues = verify_bundle(args.bundle, args.revocations)
    for issue in issues:
        print(f"invalid: {issue}")
    print("ok" if not issues else f"{len(issues)} issue(s)")
    return 0 if not issues else 1


if __name__ == "__main__":
    raise SystemExit(main())
