"""Secret and signing-material scan.

The scanner never prints the matched value, only the file, line and rule, so a
finding cannot leak the secret into CI logs or a pasted report.
"""

from __future__ import annotations

import pathlib
import re

TEXT_SUFFIXES = {
    ".kt", ".kts", ".java", ".rs", ".c", ".h", ".py", ".md", ".json",
    ".yml", ".yaml", ".toml", ".xml", ".sh", ".properties", ".gradle",
    ".txt", ".cfg", ".ini", ".conf",
}
SKIP_DIRS = {"reference", ".git", "build", "target", ".gradle", ".gradle-home", ".toolchain", ".android-sdk", "__pycache__"}
FORBIDDEN_SUFFIXES = {".pem", ".key", ".p12", ".pfx", ".jks", ".keystore"}

PATTERNS = [
    ("private-key-block", re.compile(r"-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----")),
    ("github-token", re.compile(r"\b(?:ghp_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{60,})\b")),
    ("aws-access-key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("google-api-key", re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b")),
    ("slack-token", re.compile(r"\bxox[baprs]-[0-9A-Za-z-]{10,}\b")),
]


def scan_file(path: pathlib.Path, root: pathlib.Path) -> list[str]:
    findings: list[str] = []
    rel = path.relative_to(root)
    if path.suffix.lower() in FORBIDDEN_SUFFIXES:
        findings.append(f"{rel}: forbidden signing-material suffix")
        return findings
    if path.suffix.lower() not in TEXT_SUFFIXES and path.name not in {"gradlew", "gradlew.bat"}:
        return findings
    try:
        text = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        return findings
    for line_number, line in enumerate(text.splitlines(), start=1):
        for name, pattern in PATTERNS:
            if pattern.search(line):
                findings.append(f"{rel}:{line_number}: {name}")
    return findings


def scan_paths(paths: list[pathlib.Path], root: pathlib.Path) -> list[str]:
    findings: list[str] = []
    for path in paths:
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        if path.is_file():
            findings.extend(scan_file(path, root))
    return findings
