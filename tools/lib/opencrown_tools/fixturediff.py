"""Fixture diff: message-level, not byte-level.

The documented CLI contract is to exit non-zero when the diff is empty, so CI
can assert that a change produced a difference.
"""

from __future__ import annotations

from dataclasses import dataclass

from .fixtures import Fixture


@dataclass
class Change:
    index: int
    field: str
    left: object
    right: object


def diff(left: Fixture, right: Fixture, ignore_timing: bool = True) -> list[Change]:
    changes: list[Change] = []
    total = max(len(left.packets), len(right.packets))

    for index in range(total):
        if index >= len(left.packets) or index >= len(right.packets):
            changes.append(
                Change(
                    index,
                    "packet",
                    "present" if index < len(left.packets) else "absent",
                    "present" if index < len(right.packets) else "absent",
                )
            )
            continue

        first, second = left.packets[index], right.packets[index]
        if first.data != second.data:
            changes.append(Change(index, "data", first.data.hex(), second.data.hex()))
        if first.direction != second.direction:
            changes.append(Change(index, "direction", first.direction, second.direction))
        if first.channel != second.channel:
            changes.append(Change(index, "channel", first.channel, second.channel))
        if not ignore_timing and first.ts_us != second.ts_us:
            changes.append(Change(index, "ts_us", first.ts_us, second.ts_us))

    return changes
