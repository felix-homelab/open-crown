"""Markdown link checker.

Relative file links must exist. Anchors are checked against heading slugs when
they point at another Markdown file. External links are not fetched: CI should
not depend on the network.
"""

import re
from pathlib import Path

LINK = re.compile(r"\[[^\]]*\]\(([^)]+)\)")
HEADING = re.compile(r"^#{1,6}[ \t]+(.+?)[ \t]*$", re.MULTILINE)
SKIP_DIRS = {
    ".claude",
    ".git",
    ".toolchain",
    ".android-sdk",
    ".gradle-home",
    ".gradle",
    ".venv",
    "__pycache__",
    "build",
    "target",
    "node_modules",
    "reference",
}


def slug(text: str) -> str:
    text = text.strip().lower()
    text = re.sub(r"[^\w\s-]", "", text)
    return re.sub(r"\s", "-", text)


def headings(path: Path) -> set[str]:
    text = path.read_text(encoding="utf-8")
    return {slug(heading) for heading in HEADING.findall(text)}


def _walk(root: Path):
    for path in root.rglob("*.md"):
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        yield path


def check(root: str | Path) -> list[str]:
    root = Path(root).resolve()
    issues: list[str] = []
    cache: dict[Path, set[str]] = {}

    for path in _walk(root):
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for raw in LINK.findall(text):
            target = raw.strip()
            if not target or target.startswith(("http://", "https://", "mailto:")):
                continue
            if target.startswith("#"):
                if target[1:] not in _headings(path, cache):
                    issues.append(f"{path}: missing anchor {target}")
                continue

            file_part, _, fragment = target.partition("#")
            destination = (path.parent / file_part).resolve()
            if not destination.exists():
                issues.append(f"{path}: missing link target {file_part}")
                continue
            if fragment and destination.suffix == ".md":
                if fragment not in _headings(destination, cache):
                    issues.append(f"{path}: missing anchor #{fragment} in {file_part}")
    return [str(issue) for issue in issues]


def _headings(path: Path, cache: dict[Path, set[str]]) -> set[str]:
    if path not in cache:
        cache[path] = headings(path)
    return cache[path]
