#!/usr/bin/env python3
"""Fail if the quarantined reference or Apple-origin assets leak into the product.

Checks:
1. No tracked file lives under reference/.
2. reference/ is ignored.
3. No build or CI file references the reference tree.
4. No product source mentions the reference project.
5. No tracked Apple-rendered asset types (watchface, SF fonts, CoreML models).
6. third_party/apple/ entries, if any, carry provenance and licence files.

Exit code 0 = clear, 1 = finding. Run from anywhere inside the repository.
"""
from __future__ import annotations

import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
PRODUCT_DIRS = ["app", "core", "agent", "runtime", "ffi", "payloads"]
BUILD_FILES = [
    "settings.gradle.kts",
    "build.gradle.kts",
    "app/build.gradle.kts",
    "Cargo.toml",
    "app/src/main/cpp/CMakeLists.txt",
]
FORBIDDEN_TOKENS = ["reference/apple_watch_connector", "apple_watch_connector"]
APPLE_ASSET_SUFFIXES = [".watchface", ".mlmodel", ".mlmodelc", ".sf", ".otf", ".ttf", ".woff", ".woff2"]


def git(*args: str) -> str:
    try:
        return subprocess.run(
            ["git", *args], cwd=ROOT, check=True, capture_output=True, text=True
        ).stdout
    except (subprocess.CalledProcessError, FileNotFoundError):
        return ""


def main() -> int:
    findings: list[str] = []

    tracked_reference = [
        line for line in git("ls-files", "reference").splitlines() if line.strip()
    ]
    if tracked_reference:
        findings.append("tracked files under reference/: " + ", ".join(tracked_reference[:5]))

    ignore = (ROOT / ".gitignore").read_text(encoding="utf-8") if (ROOT / ".gitignore").exists() else ""
    if "reference/" not in ignore:
        findings.append(".gitignore does not ignore reference/")

    for rel in BUILD_FILES:
        path = ROOT / rel
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for token in FORBIDDEN_TOKENS:
            if token in text:
                findings.append(f"{rel} references {token}")

    workflow_dir = ROOT / ".github" / "workflows"
    if workflow_dir.exists():
        for path in workflow_dir.rglob("*.yml"):
            text = path.read_text(encoding="utf-8", errors="replace")
            for token in FORBIDDEN_TOKENS:
                if token in text:
                    findings.append(f"{path.relative_to(ROOT)} references {token}")

    for d in PRODUCT_DIRS:
        base = ROOT / d
        if not base.exists():
            continue
        for path in base.rglob("*"):
            if not path.is_file():
                continue
            if path.suffix.lower() in APPLE_ASSET_SUFFIXES:
                findings.append(f"forbidden asset type in product tree: {path.relative_to(ROOT)}")
            if path.suffix.lower() in {".kt", ".java", ".rs", ".c", ".h", ".kts", ".toml", ".json", ".xml"}:
                text = path.read_text(encoding="utf-8", errors="replace")
                for token in FORBIDDEN_TOKENS:
                    if token in text:
                        findings.append(f"{path.relative_to(ROOT)} references {token}")

    apple_dir = ROOT / "third_party" / "apple"
    if apple_dir.exists():
        for entry in apple_dir.iterdir():
            if not entry.is_dir():
                continue
            for required in ("SOURCE.md", "LICENSE", "SHA256SUMS", "PURPOSE.md"):
                if not (entry / required).exists():
                    findings.append(f"third_party/apple/{entry.name} missing {required}")

    if findings:
        print("cleanroom boundary: FAILED")
        for finding in findings:
            print("  - " + finding)
        return 1
    print("cleanroom boundary: clear")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
