#!/usr/bin/env python3
"""Turn a fixture slice into a golden vector."""

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lib"))

from opencrown_tools.fixtures import load
from opencrown_tools.vectorgren import build_vector, parse_redaction, write_vector


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fixture")
    parser.add_argument("--name", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--start", type=int, default=0)
    parser.add_argument("--count", type=int, default=None)
    parser.add_argument("--request-direction", default="host_to_controller")
    parser.add_argument("--response-direction", default="controller_to_host")
    parser.add_argument(
        "--redact",
        action="append",
        default=[],
        help="hex to mask, as findhex or findhex=replacehex",
    )
    args = parser.parse_args()

    redactions = [parse_redaction(spec) for spec in args.redact]
    vector = build_vector(
        load(args.fixture),
        name=args.name,
        start=args.start,
        count=args.count,
        request_direction=args.request_direction,
        response_direction=args.response_direction,
        redactions=redactions,
    )
    write_vector(vector, args.out)
    print(
        f"wrote {args.out}: {len(vector['input']['hex']) // 2} input bytes, "
        f"{len(vector['expected']['hex']) // 2} expected bytes, "
        f"{len(vector['redactions'])} redaction(s)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
