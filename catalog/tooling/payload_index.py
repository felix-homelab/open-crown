#!/usr/bin/env python3
"""Validate the payload index.

The index is source-level (ADR 0014): a payload may publish several device
targets, and a target may carry only a kernel version when the upstream source
did not publish an exact ``uname -r`` string. A null ``kernel_build`` is valid
but can never match a device; it is not silently expanded into a guess.

Run:
    python catalog/tooling/payload_index.py validate --index catalog/payload-index.json
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

SCHEMA = 1
STATUSES = {"available", "pending", "retired"}
FAMILIES = {"page-cache", "futex-uaf", "future"}
ANDROID = re.compile(r"(>=|<=|=)[0-9]{2}")
DATE = re.compile(r"[0-9]{4}-[0-9]{2}-[0-9]{2}")
ID = re.compile(r"[a-z0-9][a-z0-9._-]{2,127}")
SOURCE_ID = re.compile(r"[a-z0-9][a-z0-9._-]{2,63}")
CVE = re.compile(r"CVE-[0-9]{4}-[0-9]+")


def load(path: str | Path) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def validate(data: dict) -> list[str]:
    issues: list[str] = []
    if not isinstance(data, dict):
        return ["index is not an object"]
    if data.get("schema") != SCHEMA:
        issues.append(f"unsupported schema: {data.get('schema')!r}")
    if not DATE.fullmatch(str(data.get("updated", ""))):
        issues.append("updated must be YYYY-MM-DD")

    sources = data.get("sources")
    source_ids: set[str] = set()
    if not isinstance(sources, list) or not sources:
        issues.append("sources must be a non-empty list")
    else:
        for index, source in enumerate(sources):
            where = f"sources[{index}]"
            if not isinstance(source, dict):
                issues.append(f"{where} is not an object")
                continue
            source_id = source.get("id")
            if not SOURCE_ID.fullmatch(str(source_id or "")):
                issues.append(f"{where}.id is not a valid source id")
            elif source_id in source_ids:
                issues.append(f"{where}.id duplicates {source_id}")
            else:
                source_ids.add(source_id)
            if not str(source.get("url", "")).startswith("https://"):
                issues.append(f"{where}.url must start with https://")
            if not source.get("license"):
                issues.append(f"{where}.license is required")
            if "retrieved" in source and not DATE.fullmatch(str(source["retrieved"])):
                issues.append(f"{where}.retrieved must be YYYY-MM-DD")

    payloads = data.get("payloads")
    payload_ids: set[str] = set()
    if not isinstance(payloads, list):
        return issues + ["payloads must be a list"]

    for index, payload in enumerate(payloads):
        where = f"payloads[{index}]"
        if not isinstance(payload, dict):
            issues.append(f"{where} is not an object")
            continue

        payload_id = payload.get("id")
        if not ID.fullmatch(str(payload_id or "")):
            issues.append(f"{where}.id is not valid")
        elif payload_id in payload_ids:
            issues.append(f"{where}.id duplicates {payload_id}")
        else:
            payload_ids.add(payload_id)
        if not payload.get("name"):
            issues.append(f"{where}.name is required")
        if payload.get("family") not in FAMILIES:
            issues.append(f"{where}.family is not valid")
        if payload.get("status") not in STATUSES:
            issues.append(f"{where}.status is not valid")
        if payload.get("source") not in source_ids:
            issues.append(f"{where}.source is not a declared source")
        if not ANDROID.fullmatch(str(payload.get("android", ""))):
            issues.append(f"{where}.android must look like >=31")
        if payload.get("abi") != "arm64-v8a":
            issues.append(f"{where}.abi must be arm64-v8a")
        if "cves" in payload:
            cves = payload["cves"]
            if not isinstance(cves, list):
                issues.append(f"{where}.cves must be a list")
            else:
                for cve in cves:
                    if not CVE.fullmatch(str(cve)):
                        issues.append(f"{where}.cves has invalid {cve!r}")
                if len(set(cves)) != len(cves):
                    issues.append(f"{where}.cves must be unique")

        targets = payload.get("targets")
        if not isinstance(targets, list):
            issues.append(f"{where}.targets must be a list")
            continue
        seen: set[tuple[str, str | None, str | None]] = set()
        for target_index, target in enumerate(targets):
            target_where = f"{where}.targets[{target_index}]"
            if not isinstance(target, dict):
                issues.append(f"{target_where} is not an object")
                continue
            model = target.get("model")
            if not model:
                issues.append(f"{target_where}.model is required")
            kernel_build = target.get("kernel_build")
            kernel_version = target.get("kernel_version")
            if kernel_build is not None and not kernel_build:
                issues.append(f"{target_where}.kernel_build must be a string or null")
            if kernel_build is None and not kernel_version:
                issues.append(
                    f"{target_where} needs kernel_build or kernel_version; "
                    "null kernel_build must preserve the published kernel_version"
                )
            if "android" in target and not ANDROID.fullmatch(str(target["android"])):
                issues.append(f"{target_where}.android must look like >=31")
            if "abi" in target and target["abi"] != "arm64-v8a":
                issues.append(f"{target_where}.abi must be arm64-v8a")
            if "status" in target and target["status"] not in STATUSES:
                issues.append(f"{target_where}.status is not valid")
            key = (str(model), kernel_build, target.get("build"))
            if key in seen:
                issues.append(f"{target_where} duplicates {model} / {kernel_build}")
            seen.add(key)

    return issues


def targets_for(data: dict, model: str, kernel_build: str) -> list[dict]:
    """Return exact matching targets across all payloads."""
    matches: list[dict] = []
    for payload in data.get("payloads", []):
        for target in payload.get("targets", []):
            if (
                target.get("model") == model
                and target.get("kernel_build") == kernel_build
            ):
                matches.append({"payload": payload, "target": target})
    return matches


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    check_valid = sub.add_parser("validate", help="check the whole file")
    check_valid.add_argument("--index", required=True)

    check_target = sub.add_parser("check", help="look up an exact model + kernel build")
    check_target.add_argument("--index", required=True)
    check_target.add_argument("--model", required=True)
    check_target.add_argument("--kernel", required=True)

    list_payloads = sub.add_parser("list", help="print payload ids and target counts")
    list_payloads.add_argument("--index", required=True)

    args = parser.parse_args()
    data = load(args.index)
    issues = validate(data)
    if args.command == "validate":
        for issue in issues:
            print(issue)
        print("ok" if not issues else f"{len(issues)} issue(s)")
        return 0 if not issues else 1
    if issues:
        for issue in issues:
            print(issue)
        return 1
    if args.command == "list":
        for payload in data["payloads"]:
            print(
                f"{payload['id']}\t{payload['status']}\t"
                f"{len(payload['targets'])} target(s)"
            )
        return 0

    matches = targets_for(data, args.model, args.kernel)
    for match in matches:
        payload = match["payload"]
        target = match["target"]
        print(f"{payload['id']}\t{target.get('status', payload['status'])}")
    if not matches:
        print("no exact target")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
