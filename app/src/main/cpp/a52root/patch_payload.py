#!/usr/bin/env python3
"""Patch DirtyInit's payload_576.bin for the A52s SM-A528B.

Input: assets/payload_576.bin from the public DirtyInit APK.
The four patches avoid the A52 init panic paths:
  0x40 : NOP the openat-error branch
  0x60 : skip clone; force w0=0 so the init thread serves directly
  0x64 : NOP the clone svc
  0x1ec: retry accept on relay read error instead of exit
  0x220: return safely through RETAA for non-root libbase users
"""
from pathlib import Path
import sys

src = Path(sys.argv[1]).read_bytes()
b = bytearray(src)
assert len(b) == 576, len(b)
b[0x40:0x44] = (0xd503201f).to_bytes(4, "little")  # nop
b[0x60:0x64] = (0x52800000).to_bytes(4, "little")  # mov w0, #0
b[0x64:0x68] = (0xd503201f).to_bytes(4, "little")  # nop
b[0x1ec:0x1f0] = (0x17ffffbb).to_bytes(4, "little") # b 0xd8 (accept)
b[0x220:0x224] = (0xd65f0bff).to_bytes(4, "little") # retaa
Path("payload_576_a52.bin").write_bytes(b)
print("wrote payload_576_a52.bin")
