"""Payload manifest checks shared by tooling and, later, the runtime.

The schema is catalog/schema/payload-manifest.schema.json. This module is the
executable version of the same rules so the release process and the runtime do
not drift apart.
"""

from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
from typing import Any

CAPABILITIES = {"root", "domain", "controller", "shim", "diag"}
FAMILIES = {"page-cache", "futex-uaf", "future"}
HEX64 = re.compile(r"[0-9a-f]{64}")
SEMVER = re.compile(r"[0-9]+[.][0-9]+[.][0-9]+")
ID_PATTERN = re.compile(r"[a-z0-9][a-z0-9._-]{2,127}")
ANDROID_PATTERN = re.compile(r"(>=|<=|=)[0-9]{2}")

REQUIRED = (
    "schema",
    "id",
    "version",
    "min_agent",
    "family",
    "targets",
    "capabilities",
    "entry",
    "agent",
    "persistence",
    "reversible",
    "artifacts",
)


class ManifestError(ValueError):
    pass


def canonical_bytes(obj: Any) -> bytes:
    return json.dumps(
        obj, sort_keys=True, separators=(",", ":"), ensure_ascii=False
    ).encode("utf-8")


def digest(obj: Any) -> str:
    return hashlib.sha256(canonical_bytes(obj)).hexdigest()


def signing_input(obj: Any) -> bytes:
    """The bytes the catalog signature covers: the SHA-256 digest, per docs/04."""
    return bytes.fromhex(digest(obj))


def load(path: str | Path) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def _inside_files(value: Any) -> bool:
    if not isinstance(value, str) or not value.startswith("files/"):
        return False
    parts = value.split("/")
    return ".." not in parts and "" not in parts and len(parts) > 1


def validate(obj: dict) -> list[str]:
    issues: list[str] = []

    if not isinstance(obj, dict):
        return ["manifest is not an object"]
    missing = [key for key in REQUIRED if key not in obj]
    if missing:
        return ["missing key: " + key for key in missing]

    if obj.get("schema") != 1:
        issues.append(f"unsupported schema: {obj.get('schema')!r}")
    if not ID_PATTERN.fullmatch(str(obj.get("id", ""))):
        issues.append("id must match [a-z0-9][a-z0-9._-]{2,127}")
    for key in ("version", "min_agent"):
        if not SEMVER.fullmatch(str(obj.get(key, ""))):
            issues.append(f"{key} must be semver")
    if obj.get("family") not in FAMILIES:
        issues.append(f"unknown family: {obj.get('family')!r}")

    targets = obj.get("targets")
    if not isinstance(targets, list) or not targets:
        issues.append("targets must be a non-empty list")
    else:
        for index, target in enumerate(targets):
            where = f"targets[{index}]"
            if not isinstance(target, dict):
                issues.append(f"{where} is not an object")
                continue
            if not target.get("model"):
                issues.append(f"{where}.model is required")
            if not target.get("kernel_build"):
                issues.append(f"{where}.kernel_build is required")
            if target.get("abi") != "arm64-v8a":
                issues.append(f"{where}.abi must be arm64-v8a in v1")
            if not ANDROID_PATTERN.fullmatch(str(target.get("android", ""))):
                issues.append(f"{where}.android must look like >=14")

    capabilities = obj.get("capabilities")
    if not isinstance(capabilities, list) or not capabilities:
        issues.append("capabilities must be a non-empty list")
    else:
        unknown = sorted(set(capabilities) - CAPABILITIES)
        if unknown:
            issues.append("unknown capabilities: " + ", ".join(unknown))
        if len(set(capabilities)) != len(capabilities):
            issues.append("capabilities must be unique")

    for key in ("entry", "agent"):
        if not _inside_files(obj.get(key)):
            issues.append(f"{key} must resolve inside files/ with no traversal")

    if obj.get("persistence") != "boot":
        issues.append("persistence must be boot in v1")
    if obj.get("reversible") != "reboot":
        issues.append("reversible must be reboot in v1")

    artifacts = obj.get("artifacts")
    if not isinstance(artifacts, list) or not artifacts:
        issues.append("artifacts must be a non-empty list")
    else:
        for index, artifact in enumerate(artifacts):
            where = f"artifacts[{index}]"
            if not isinstance(artifact, dict):
                issues.append(f"{where} is not an object")
                continue
            if not _inside_files(artifact.get("path")):
                issues.append(f"{where}.path must resolve inside files/")
            if not HEX64.fullmatch(str(artifact.get("sha256", ""))):
                issues.append(f"{where}.sha256 must be 64 lowercase hex chars")
            size = artifact.get("size")
            if not isinstance(size, int) or isinstance(size, bool) or size < 1:
                issues.append(f"{where}.size must be a positive integer")

    if "notes" in obj and not isinstance(obj["notes"], str):
        issues.append("notes must be a string")

    return issues


def verify_signature(obj: dict, signature: bytes, public_key: bytes) -> None:
    """Verify an Ed25519 signature over the manifest digest.

    Raises ManifestError when the dependency is missing, and lets the
    underlying InvalidSignature propagate when the signature is wrong.
    """
    try:
        from cryptography.exceptions import InvalidSignature
        from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
    except ImportError as exc:  # pragma: no cover - depends on the environment
        raise ManifestError(
            "cryptography is required for signature verification: pip install cryptography"
        ) from exc

    key = Ed25519PublicKey.from_public_bytes(public_key)
    try:
        key.verify(signature, signing_input(obj))
    except InvalidSignature as exc:
        raise ManifestError("signature does not match the manifest digest") from exc
