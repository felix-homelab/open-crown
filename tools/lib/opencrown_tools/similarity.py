"""Token-shingle similarity audit for the clean-room boundary.

This is a Lane C tool. It does not prove originality; it produces reproducible
evidence that no product file contains a long run identical to a reference file,
and surfaces the shared identifiers a human must review.
"""

from __future__ import annotations

import pathlib
import re

TOKEN = re.compile(r"[A-Za-z_][A-Za-z0-9_]*|\d+|\S")
STRING = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')
C_COMMENT = re.compile(r"/\*.*?\*/|//[^\n]*", re.DOTALL)

KEYWORDS = {
    "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
    "in", "interface", "is", "null", "object", "package", "private", "public",
    "return", "true", "try", "typealias", "val", "var", "when", "while", "as",
    "async", "await", "const", "crate", "enum", "extern", "fn", "impl", "let",
    "loop", "match", "mod", "move", "mut", "pub", "ref", "self", "static",
    "struct", "super", "trait", "unsafe", "use", "where", "import", "final",
    "new", "void", "int", "long", "boolean", "this", "extends", "implements",
}
SHINGLE = 10
MIN_TOKENS = 25
FLAG_CONTAINMENT = 0.20


def strip_comments(text: str) -> str:
    return C_COMMENT.sub(" ", text)


def tokens(text: str) -> list[str]:
    cleaned = strip_comments(text)
    out: list[str] = []
    position = 0
    for match in STRING.finditer(cleaned):
        out.extend(TOKEN.findall(cleaned[position:match.start()]))
        out.append("STR:" + match.group(0))
        position = match.end()
    out.extend(TOKEN.findall(cleaned[position:]))
    return [token.lower() for token in out]


def shingles(values: list[str], size: int = SHINGLE) -> set[tuple[str, ...]]:
    if len(values) < size:
        return set()
    return {tuple(values[index:index + size]) for index in range(len(values) - size + 1)}


def containment(product: set[tuple[str, ...]], reference: set[tuple[str, ...]]) -> float:
    if not product:
        return 0.0
    return len(product & reference) / len(product)


def identifiers(values: list[str]) -> set[str]:
    return {
        value for value in values
        if value not in KEYWORDS and len(value) > 2 and value[0].isalpha()
    }


def source_files(
    root: pathlib.Path,
    suffixes: set[str],
    skip_reference: bool = True,
) -> list[pathlib.Path]:
    skipped = {
        "build", "target", "generated", ".git", ".gradle", ".gradle-home",
        ".toolchain", ".android-sdk", ".venv", "node_modules", "__pycache__",
        ".kotlin", ".cxx",
    }
    if skip_reference:
        skipped.add("reference")
    files: list[pathlib.Path] = []
    for path in root.rglob("*"):
        if not path.is_file() or path.suffix not in suffixes:
            continue
        if any(part in skipped for part in path.parts):
            continue
        files.append(path)
    return files


def fingerprint(path: pathlib.Path) -> dict:
    values = tokens(path.read_text(encoding="utf-8", errors="replace"))
    return {
        "tokens": values,
        "shingles": shingles(values),
        "identifiers": identifiers(values),
    }


def compare_fingerprints(product: dict, reference: dict) -> dict:
    return {
        "containment": containment(product["shingles"], reference["shingles"]),
        "product_tokens": len(product["tokens"]),
        "shared_identifiers": product["identifiers"] & reference["identifiers"],
    }


def compare(product: pathlib.Path, reference: pathlib.Path) -> dict:
    return compare_fingerprints(fingerprint(product), fingerprint(reference))