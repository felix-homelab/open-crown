#!/usr/bin/env python3
"""Apply the Open Crown init-relay calibration to the 576-byte payload.

Usage:
    python3 patch_payload.py input_payload.bin payloads/org.opencrown.payload.pagecache-relay/payload.bin

The four calibration changes keep non-root libbase users returning safely and
make the init thread serve the relay directly on the verified A52 profile.
"""
from pathlib import Path
import sys

if len(sys.argv) != 3:
    raise SystemExit(__doc__)
src = Path(sys.argv[1]).read_bytes()
b = bytearray(src)
assert len(b) == 576, len(b)
b[0x40:0x44] = (0xd503201f).to_bytes(4, "little")   # nop
b[0x60:0x64] = (0x52800000).to_bytes(4, "little")   # mov w0, #0
b[0x64:0x68] = (0xd503201f).to_bytes(4, "little")   # nop
b[0x1ec:0x1f0] = (0x17ffffbb).to_bytes(4, "little") # b accept
b[0x220:0x224] = (0xd65f0bff).to_bytes(4, "little") # retaa
Path(sys.argv[2]).write_bytes(b)
print(f"wrote {sys.argv[2]}")
