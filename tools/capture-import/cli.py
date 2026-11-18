#!/usr/bin/env python3
"""Normalise an HCI snoop log or pcap into a fixture."""

import argparse
import datetime
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lib"))

from opencrown_tools.capture import (
    BTSNOOP_MAGIC,
    PCAP_MAGIC,
    PCAPNG_SHB,
    parse_file,
)
from opencrown_tools.fixtures import Fixture, save, sha256_file


def _source_kind(path: Path) -> str:
    head = path.read_bytes()[:8]
    if head.startswith(BTSNOOP_MAGIC):
        return "btsnoop"
    if head[:4] in PCAP_MAGIC:
        return "pcap"
    if head[:4] == PCAPNG_SHB:
        return "pcapng"
    return "unknown"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture")
    parser.add_argument("--out", required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--phone-build", required=True)
    parser.add_argument("--watch-model", required=True)
    parser.add_argument("--watchos", required=True)
    parser.add_argument("--captured-at", default=None)
    parser.add_argument("--notes", default="")
    args = parser.parse_args()

    path = Path(args.capture)
    packets = parse_file(path)
    captured_at = args.captured_at or (
        datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat()
    )
    fixture = Fixture(
        name=args.name,
        meta={
            "phone_build": args.phone_build,
            "watch_model": args.watch_model,
            "watchos": args.watchos,
            "notes": args.notes,
        },
        source_kind=_source_kind(path),
        source_sha256=sha256_file(path),
        packets=packets,
        captured_at=captured_at,
    )
    save(fixture, args.out)

    counts: dict[str, int] = {}
    for packet in packets:
        counts[packet.direction] = counts.get(packet.direction, 0) + 1
    summary = ", ".join(f"{name}={count}" for name, count in sorted(counts.items()))
    print(f"wrote {args.out}: {len(packets)} packet(s) [{summary}]")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
