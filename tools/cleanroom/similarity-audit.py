#!/usr/bin/env python3
"""Lane C similarity audit: product source versus the quarantined reference."""

from __future__ import annotations

import argparse
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "lib"))

from opencrown_tools.similarity import (
    FLAG_CONTAINMENT,
    compare_fingerprints,
    fingerprint,
    identifiers,
    source_files,
    tokens,
)

SUFFIXES = {".kt", ".rs", ".java", ".dart", ".c", ".h"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--product", default=".")
    parser.add_argument("--reference", default="reference/apple_watch_connector")
    args = parser.parse_args()
    product_root = pathlib.Path(args.product).resolve()
    reference_root = pathlib.Path(args.reference).resolve()
    if not reference_root.exists():
        print("similarity: reference tree is absent; nothing to compare", file=sys.stderr)
        return 2
    product_files = [
        path for path in source_files(product_root, SUFFIXES)
        if not any(part in {"docs", "tools"} for part in path.parts)
    ]
    reference_files = source_files(reference_root, SUFFIXES, skip_reference=False)
    findings = 0
    max_seen = 0.0
    reference_fingerprints = {path: fingerprint(path) for path in reference_files}
    for product in product_files:
        candidates = [
            reference for reference in reference_files
            if reference.suffix == product.suffix
        ]
        if not candidates:
            continue
        product_fingerprint = fingerprint(product)
        results = sorted(
            ((compare_fingerprints(product_fingerprint, reference_fingerprints[reference]), reference) for reference in candidates),
            key=lambda item: item[0]["containment"],
            reverse=True,
        )
        best, best_reference = results[0]
        score = best["containment"]
        max_seen = max(max_seen, score)
        if score >= FLAG_CONTAINMENT:
            findings += 1
            print(
                f"FINDING {product.relative_to(product_root)} -> "
                f"{best_reference.relative_to(reference_root)} containment={score:.3f} "
                f"shared-identifiers={len(best['shared_identifiers'])}"
            )
            print("  shared: " + ", ".join(sorted(best["shared_identifiers"])[:12]))
        elif score >= 0.08:
            print(
                f"review  {product.relative_to(product_root)} -> "
                f"{best_reference.relative_to(reference_root)} containment={score:.3f}"
            )
    product_identifiers = set()
    for product in product_files:
        product_identifiers |= identifiers(tokens(product.read_text(encoding="utf-8", errors="replace")))
    reference_identifiers = set()
    for reference in reference_files:
        reference_identifiers |= identifiers(tokens(reference.read_text(encoding="utf-8", errors="replace")))
    shared = sorted(product_identifiers & reference_identifiers)
    print(f"product_files={len(product_files)} reference_files={len(reference_files)}")
    print(f"max_containment={max_seen:.3f} findings={findings}")
    print(f"shared_identifiers={len(shared)}")
    print("shared_sample=" + ", ".join(shared[:40]))
    return 1 if findings else 0


if __name__ == "__main__":
    raise SystemExit(main())