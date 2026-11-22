#!/usr/bin/env python3
"""Audit CrownColors contrast against the design-system requirement.

Required (fail the command): label and secondaryLabel on both page and card
backgrounds at >= 4.5:1 in light and dark.
Informational: tertiary and accent tones, which the design spec does not require
for essential information.
"""

from __future__ import annotations

import pathlib
import re
import sys

COLOR_FILE = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/kotlin/org/opencrown/app/ui/theme/CrownColors.kt"
COLOR = re.compile(r"\b(\w+)\s*(?::\s*Color\s*)?=\s*Color\(0xFF([0-9A-Fa-f]{6})\)")
REQUIRED = 4.5


def luminance(hex_value: str) -> float:
    channels = [int(hex_value[index:index + 2], 16) / 255 for index in (0, 2, 4)]
    linear = [
        value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4
        for value in channels
    ]
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]


def ratio(foreground: str, background: str) -> float:
    high, low = sorted((luminance(foreground), luminance(background)), reverse=True)
    return (high + 0.05) / (low + 0.05)


def parse(path: pathlib.Path = COLOR_FILE) -> tuple[dict[str, str], dict[str, str]]:
    text = path.read_text(encoding="utf-8")
    light_source, dark_source = text.split("val Dark", 1)
    light = dict(COLOR.findall(light_source))
    dark = dict(COLOR.findall(dark_source))
    return light, dark


def main() -> int:
    light, dark = parse()
    failures = 0
    rows = []
    for theme_name, palette in (("light", light), ("dark", dark)):
        for foreground in ("label", "secondaryLabel", "destructiveText", "tertiaryLabel", "tint", "onTint", "destructive", "success", "warning"):
            for background in ("groupedBackground", "cardBackground", "tint"):
                if background == "tint" and foreground != "onTint":
                    continue
                if background != "tint" and foreground == "onTint":
                    continue
                value = ratio(palette[foreground], palette[background])
                required = foreground in {"label", "secondaryLabel", "destructiveText"} and background != "tint"
                status = "required" if required else "info"
                if required and value < REQUIRED:
                    failures += 1
                    status = "FAIL"
                rows.append((theme_name, foreground, background, value, status))
    print("theme  foreground        background        ratio  status")
    for theme_name, foreground, background, value, status in rows:
        print(f"{theme_name:5}  {foreground:15} {background:15} {value:5.2f}  {status}")
    print(f"required failures: {failures}")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())