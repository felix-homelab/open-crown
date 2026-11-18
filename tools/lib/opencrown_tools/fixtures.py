"""Fixture model: the normalised form of a capture.

A fixture is what every other tool consumes. It keeps packets as raw bytes
plus the metadata a spec needs to be auditable. See docs/07 section 4.1.
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

SCHEMA = 1

REQUIRED_META = ("phone_build", "watch_model", "watchos")

DIRECTIONS = ("host_to_controller", "controller_to_host", "air", "unknown")


class FixtureError(ValueError):
    """A fixture is missing something that makes it auditable."""


@dataclass
class Packet:
    index: int
    ts_us: int | None
    direction: str
    channel: int | None
    data: bytes

    def to_json(self) -> dict[str, Any]:
        return {
            "index": self.index,
            "ts_us": self.ts_us,
            "direction": self.direction,
            "channel": self.channel,
            "data_hex": self.data.hex(),
        }

    @classmethod
    def from_json(cls, obj: dict[str, Any]) -> "Packet":
        return cls(
            index=int(obj["index"]),
            ts_us=obj.get("ts_us"),
            direction=obj["direction"],
            channel=obj.get("channel"),
            data=bytes.fromhex(obj["data_hex"]),
        )


@dataclass
class Fixture:
    name: str
    meta: dict[str, Any] = field(default_factory=dict)
    source_kind: str = "unknown"
    source_sha256: str = ""
    packets: list[Packet] = field(default_factory=list)
    captured_at: str | None = None

    def validate(self) -> None:
        missing = [key for key in REQUIRED_META if not self.meta.get(key)]
        if missing:
            raise FixtureError("missing metadata: " + ", ".join(missing))
        if not self.captured_at:
            raise FixtureError("missing captured_at")
        if not self.source_sha256:
            raise FixtureError("missing source_sha256")
        for packet in self.packets:
            if packet.direction not in DIRECTIONS:
                raise FixtureError(
                    f"packet {packet.index}: unknown direction {packet.direction!r}"
                )

    def to_json(self) -> dict[str, Any]:
        return {
            "schema": SCHEMA,
            "name": self.name,
            "captured_at": self.captured_at,
            "meta": self.meta,
            "source": {"kind": self.source_kind, "sha256": self.source_sha256},
            "packets": [packet.to_json() for packet in self.packets],
        }

    @classmethod
    def from_json(cls, obj: dict[str, Any]) -> "Fixture":
        if obj.get("schema") != SCHEMA:
            raise FixtureError(f"unsupported fixture schema: {obj.get('schema')!r}")
        source = obj.get("source", {})
        return cls(
            name=obj["name"],
            meta=obj.get("meta", {}),
            source_kind=source.get("kind", "unknown"),
            source_sha256=source.get("sha256", ""),
            packets=[Packet.from_json(packet) for packet in obj.get("packets", [])],
            captured_at=obj.get("captured_at"),
        )


def load(path: str | Path) -> Fixture:
    return Fixture.from_json(json.loads(Path(path).read_text(encoding="utf-8")))


def save(fixture: Fixture, path: str | Path) -> None:
    fixture.validate()
    Path(path).write_text(json.dumps(fixture.to_json(), indent=2) + "\n", encoding="utf-8")


def sha256_file(path: str | Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()
