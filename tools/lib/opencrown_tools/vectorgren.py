"""Turn a fixture slice into a golden vector.

A vector stores the bytes that went in and the bytes that came back, plus the
capture hash that justifies them. Redaction is explicit and recorded.
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Iterable

from .fixtures import Fixture

HEX = re.compile(r"[0-9a-fA-F]*")


class VectorError(ValueError):
    pass


def build_vector(
    fixture: Fixture,
    *,
    name: str,
    start: int = 0,
    count: int | None = None,
    request_direction: str = "host_to_controller",
    response_direction: str = "controller_to_host",
    redactions: Iterable[tuple[bytes, bytes]] = (),
) -> dict:
    if start < 0:
        raise VectorError("start must be >= 0")
    end = len(fixture.packets) if count is None else start + count
    if end > len(fixture.packets):
        raise VectorError("selection runs past the end of the fixture")

    selection = fixture.packets[start:end]
    request = b"".join(p.data for p in selection if p.direction == request_direction)
    response = b"".join(p.data for p in selection if p.direction == response_direction)
    if not request:
        raise VectorError(f"no packets in direction {request_direction!r}")
    if not response:
        raise VectorError(f"no packets in direction {response_direction!r}")

    applied: list[str] = []
    for find, replace in redactions:
        if find in request:
            request = request.replace(find, replace)
            applied.append(f"{find.hex()}={replace.hex()}")
        if find in response:
            response = response.replace(find, replace)
            applied.append(f"{find.hex()}={replace.hex()}")

    return {
        "schema": 1,
        "name": name,
        "capture_sha256": fixture.source_sha256,
        "request_direction": request_direction,
        "response_direction": response_direction,
        "redactions": applied,
        "input": {"direction": request_direction, "hex": request.hex()},
        "expected": {"direction": response_direction, "hex": response.hex()},
        "meta": {
            "watch_model": fixture.meta.get("watch_model"),
            "watchos": fixture.meta.get("watchos"),
            "phone_build": fixture.meta.get("phone_build"),
            "captured_at": fixture.captured_at,
        },
    }


def parse_redaction(spec: str) -> tuple[bytes, bytes]:
    """Parse findhex=replacehex; the replacement defaults to one zero byte."""
    if "=" in spec:
        find_hex, replace_hex = spec.split("=", 1)
    else:
        find_hex, replace_hex = spec, "00"
    if not find_hex or not HEX.fullmatch(find_hex) or len(find_hex) % 2:
        raise VectorError(f"bad redaction find pattern: {find_hex!r}")
    if not HEX.fullmatch(replace_hex) or len(replace_hex) % 2:
        raise VectorError(f"bad redaction replace pattern: {replace_hex!r}")
    if len(replace_hex) > len(find_hex):
        raise VectorError("replacement may not be longer than the match")
    return bytes.fromhex(find_hex), bytes.fromhex(replace_hex)


def write_vector(vector: dict, path: str | Path) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(vector, indent=2) + "\n", encoding="utf-8")
