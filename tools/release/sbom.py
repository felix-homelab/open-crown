#!/usr/bin/env python3
"""Generate a CycloneDX 1.5 SBOM for an Open Crown release APK.

Includes the APK hash, Android runtime libraries, Rust packages from cargo
metadata, and every native file in the APK with its SHA-256. It is a generated
inventory, not a signed attestation or a licence-text bundle.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import uuid
import zipfile
from datetime import datetime, timezone

ANDROID_COMPONENTS = [
    ("org.jetbrains.kotlin", "kotlin-stdlib", "2.2.20", "Apache-2.0"),
    ("androidx.core", "core-ktx", "1.17.0", "Apache-2.0"),
    ("androidx.activity", "activity-compose", "1.11.0", "Apache-2.0"),
    ("androidx.compose.foundation", "foundation", "1.9.1", "Apache-2.0"),
    ("androidx.compose.material3", "material3", "1.3.2", "Apache-2.0"),
    ("androidx.compose.ui", "ui", "1.9.1", "Apache-2.0"),
    ("net.java.dev.jna", "jna", "5.17.0", "LGPL-2.1-or-later OR Apache-2.0"),
]
EXPRESSION = re.compile(r"^[A-Za-z0-9.+():\- ]+( (AND|OR|WITH) [A-Za-z0-9.+():\- ]+)*$")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def license_entry(value: str | None) -> dict:
    if not value:
        return {"license": {"name": "NOASSERTION"}}
    normalized = value.replace("MIT/Apache-2.0", "MIT OR Apache-2.0")
    if EXPRESSION.match(normalized):
        return {"expression": normalized}
    return {"license": {"name": value}}


def cargo_packages(metadata_path: pathlib.Path | None) -> list[dict]:
    if metadata_path is not None:
        data = json.loads(metadata_path.read_text(encoding="utf-8"))
    else:
        result = subprocess.run(
            ["cargo", "metadata", "--format-version", "1"],
            check=True,
            capture_output=True,
            text=True,
        )
        data = json.loads(result.stdout)
    packages = []
    for package in sorted(data["packages"], key=lambda item: (item["name"], item["version"])):
        packages.append(
            {
                "type": "library",
                "bom-ref": f"pkg:cargo/{package['name']}@{package['version']}",
                "name": package["name"],
                "version": package["version"],
                "licenses": [license_entry(package.get("license"))],
            }
        )
    return packages


def android_packages() -> list[dict]:
    return [
        {
            "type": "library",
            "bom-ref": f"pkg:maven/{group}/{artifact}@{version}",
            "group": group,
            "name": artifact,
            "version": version,
            "licenses": [license_entry(license_name)],
        }
        for group, artifact, version, license_name in ANDROID_COMPONENTS
    ]


def native_components(apk: pathlib.Path) -> list[dict]:
    components = []
    with zipfile.ZipFile(apk) as archive:
        for name in sorted(archive.namelist()):
            if not name.startswith("lib/"):
                continue
            digest = sha256(archive.read(name))
            components.append(
                {
                    "type": "file",
                    "bom-ref": f"file:{name}",
                    "name": name,
                    "hashes": [{"alg": "SHA-256", "content": digest}],
                }
            )
    return components


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=pathlib.Path)
    parser.add_argument("--out", required=True, type=pathlib.Path)
    parser.add_argument("--cargo-metadata", type=pathlib.Path)
    parser.add_argument("--timestamp", help="ISO-8601 UTC timestamp for a reproducible SBOM")
    args = parser.parse_args()

    apk_bytes = args.apk.read_bytes()
    apk_hash = sha256(apk_bytes)
    timestamp = args.timestamp or datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    components = cargo_packages(args.cargo_metadata) + android_packages() + native_components(args.apk)
    deduped = {component["bom-ref"]: component for component in components}
    ordered = [deduped[key] for key in sorted(deduped)]

    document = {
        "bomFormat": "CycloneDX",
        "specVersion": "1.5",
        "serialNumber": "urn:uuid:" + str(uuid.uuid5(uuid.NAMESPACE_URL, f"opencrown:{apk_hash}:{timestamp}")),
        "version": 1,
        "metadata": {
            "timestamp": timestamp,
            "tools": {
                "components": [
                    {"type": "application", "name": "opencrown-sbom", "version": "1"}
                ]
            },
            "component": {
                "type": "application",
                "bom-ref": "pkg:apk/org.opencrown.app@0.1.0",
                "name": "org.opencrown.app",
                "version": "0.1.0",
                "hashes": [{"alg": "SHA-256", "content": apk_hash}],
            },
        },
        "components": ordered,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
    print(f"sbom: {len(ordered)} components -> {args.out}")
    print(f"apk: {apk_hash}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
