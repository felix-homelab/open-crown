"""Spec lint: required sections and at least one usable vector.

A spec without a vector is a hypothesis (docs/03-clean-room.md).
"""

from __future__ import annotations

import json
import re
from pathlib import Path

REQUIRED_SECTIONS = (
    "purpose",
    "actors and preconditions",
    "sequence",
    "messages",
    "state machine and errors",
    "version and feature detection",
    "open questions",
    "vectors",
    "acceptance",
)

VALID_STATUS = {"draft", "verified", "stale"}

_HEADING = re.compile(r"^#{2,4}\s+(.+?)\s*$", re.MULTILINE)
_STATUS = re.compile(r"\*\*Status:\*\*\s*([A-Za-z]+)")
_HEX64 = re.compile(r"[0-9a-f]{64}")


def lint_spec(path: str | Path) -> list[str]:
    path = Path(path)
    text = path.read_text(encoding="utf-8")
    issues: list[str] = []

    headings = {m.strip().lower().rstrip(":") for m in _HEADING.findall(text)}
    for section in REQUIRED_SECTIONS:
        if section not in headings:
            issues.append(f"missing section: {section}")

    status = _STATUS.search(text)
    if status is None:
        issues.append("missing **Status:** line")
    elif status.group(1).lower() not in VALID_STATUS:
        issues.append(f"invalid status: {status.group(1)}")

    vector_dir = path.parent / "vectors"
    vectors = sorted(vector_dir.glob("*.json"))
    if not vectors:
        issues.append("no vectors: a spec without a vector is a hypothesis")
    for vector in vectors:
        issues.extend(_lint_vector(vector))

    return [f"{path}: {issue}" for issue in issues]


def _lint_vector(path: Path) -> list[str]:
    issues: list[str] = []
    try:
        obj = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        return [f"{path.name}: invalid JSON ({exc})"]

    if obj.get("schema") != 1:
        issues.append(f"{path.name}: unsupported vector schema")
    if not _HEX64.fullmatch(str(obj.get("capture_sha256", ""))):
        issues.append(f"{path.name}: capture_sha256 must be 64 lowercase hex chars")

    for field in ("input", "expected"):
        value = obj.get(field)
        hexed = value.get("hex", "") if isinstance(value, dict) else ""
        if not hexed:
            issues.append(f"{path.name}: missing {field}.hex")
            continue
        try:
            bytes.fromhex(hexed)
        except ValueError:
            issues.append(f"{path.name}: {field}.hex is not valid hex")

    return issues
