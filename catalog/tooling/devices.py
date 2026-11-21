#!/usr/bin/env python3
"""Validate the supported-device list.

The list is evidence, not intention (docs/04-payload-system.md section 8):
every entry names an exact model and kernel build. "example" entries exist so
the format can be tested before real hardware is added.
"""

import argparse
import json
import re
from pathlib import Path

SCHEMA = 1
STATUSES = {"supported", "example", "retired"}
ANDROID = re.compile(r"(>=|<=|=)[0-9]{2}")
DATE = re.compile(r"[0-9]{4}-[0-9]{2}-[0-9]{2}")


def load(path: str | Path) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def validate(data: dict) -> list[str]:
    issues: list[str] = []
    if data.get("schema") != SCHEMA:
        issues.append(f"unsupported schema: {data.get('schema')!r}")
    if not DATE.fullmatch(str(data.get("updated", ""))):
        issues.append("updated must be YYYY-MM-DD")
    devices = data.get("devices")
    if not isinstance(devices, list):
        return issues + ["devices must be a list"]

    seen = set()
    for index, device in enumerate(devices):
        where = f"devices[{index}]"
        if not isinstance(device, dict):
            issues.append(f"{where} is not an object")
            continue
        for key in ("model", "kernel_build"):
            if not device.get(key):
                issues.append(f"{where}.{key} is required")
        if device.get("abi") != "arm64-v8a":
            issues.append(f"{where}.abi must be arm64-v8a")
        if not ANDROID.fullmatch(str(device.get("android", ""))):
            issues.append(f"{where}.android must look like >=31")
        if device.get("status") not in STATUSES:
            issues.append(f"{where}.status is not valid")
        if "note" in device and not isinstance(device["note"], str):
            issues.append(f"{where}.note must be a string")
        key = (device.get("model"), device.get("kernel_build"))
        if key in seen:
            issues.append(f"{where} duplicates {key[0]} / {key[1]}")
        seen.add(key)
    return issues


def find(data: dict, model: str, kernel: str) -> dict | None:
    for device in data.get("devices", []):
        if device.get("model") == model and device.get("kernel_build") == kernel:
            return device
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    check_valid = sub.add_parser("validate", help="check the whole file")
    check_valid.add_argument("--list", required=True)

    check_device = sub.add_parser("check", help="look one device up")
    check_device.add_argument("--list", required=True)
    check_device.add_argument("--model", required=True)
    check_device.add_argument("--kernel", required=True)

    args = parser.parse_args()
    data = load(args.list)

    if args.command == "validate":
        issues = validate(data)
        for issue in issues:
            print(issue)
        print("ok" if not issues else f"{len(issues)} issue(s)")
        return 0 if not issues else 1

    issues = validate(data)
    if issues:
        for issue in issues:
            print(issue)
        return 1
    device = find(data, args.model, args.kernel)
    if device is None:
        print("unknown")
        return 1
    print(device["status"])
    return 0 if device["status"] == "supported" else 2


if __name__ == "__main__":
    raise SystemExit(main())
