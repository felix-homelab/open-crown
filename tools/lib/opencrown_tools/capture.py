"""Capture parsers: btsnoop (Android HCI snoop) and pcap/pcapng.

Only the subset we need: packet boundaries, direction, timestamp, raw bytes.
Anything the parser does not understand is preserved as raw bytes rather than
guessed at (docs/07 section 4.2).
"""

from __future__ import annotations

import struct
from pathlib import Path

from .fixtures import Packet


class CaptureError(ValueError):
    pass


BTSNOOP_MAGIC = b"btsnoop\x00"
# btsnoop timestamps count microseconds from year 0; 1970-01-01 is this many.
BTSNOOP_EPOCH_OFFSET_US = 62_135_596_800_000_000

PCAP_MAGIC = {
    b"\xd4\xc3\xb2\xa1": ("<", False),
    b"\xa1\xb2\xc3\xd4": (">", False),
    b"\x4d\x3c\xb2\xa1": ("<", True),
    b"\xa1\xb2\x3c\x4d": (">", True),
}

PCAPNG_SHB = b"\x0a\x0d\x0d\x0a"


def parse_btsnoop(data: bytes) -> list[Packet]:
    if len(data) < 16 or data[:8] != BTSNOOP_MAGIC:
        raise CaptureError("not a btsnoop file")
    version, datalink = struct.unpack(">II", data[8:16])
    if version != 1:
        raise CaptureError(f"unsupported btsnoop version: {version}")

    packets: list[Packet] = []
    offset = 16
    while offset < len(data):
        if offset + 24 > len(data):
            raise CaptureError("truncated btsnoop record header")
        _orig_len, incl_len, flags, _drops, ts = struct.unpack(
            ">IIIIQ", data[offset : offset + 24]
        )
        offset += 24
        if offset + incl_len > len(data):
            raise CaptureError("truncated btsnoop record body")
        payload = data[offset : offset + incl_len]
        offset += incl_len

        direction = "controller_to_host" if flags & 1 else "host_to_controller"
        channel = None
        body = payload
        if datalink in (1001, 1002) and payload:
            channel = payload[0]
            body = payload[1:]

        ts_us = ts - BTSNOOP_EPOCH_OFFSET_US if ts >= BTSNOOP_EPOCH_OFFSET_US else None
        packets.append(
            Packet(
                index=len(packets),
                ts_us=ts_us,
                direction=direction,
                channel=channel,
                data=body,
            )
        )
    return packets


def parse_pcap(data: bytes) -> list[Packet]:
    if len(data) < 24 or data[:4] not in PCAP_MAGIC:
        raise CaptureError("not a pcap file")
    endian, nanosecond = PCAP_MAGIC[data[:4]]
    _major, _minor, _zone, _sigfigs, _snaplen, linktype = struct.unpack(
        endian + "HHiIII", data[4:24]
    )

    packets: list[Packet] = []
    offset = 24
    while offset + 16 <= len(data):
        ts_sec, ts_frac, incl_len, _orig_len = struct.unpack(
            endian + "IIII", data[offset : offset + 16]
        )
        offset += 16
        if offset + incl_len > len(data):
            raise CaptureError("truncated pcap record body")
        frame = data[offset : offset + incl_len]
        offset += incl_len

        ts_us = ts_sec * 1_000_000 + (ts_frac // 1000 if nanosecond else ts_frac)
        direction, channel, body = _decode_linktype(linktype, frame)
        packets.append(
            Packet(
                index=len(packets),
                ts_us=ts_us,
                direction=direction,
                channel=channel,
                data=body,
            )
        )
    return packets


def _decode_linktype(linktype: int, frame: bytes) -> tuple[str, int | None, bytes]:
    if linktype == 201 and len(frame) >= 5:  # HCI H4 with pseudo-header
        direction = {1: "host_to_controller", 2: "controller_to_host"}.get(
            frame[0], "unknown"
        )
        return direction, frame[4], frame[5:]
    if linktype in (187, 1001, 1002) and frame:  # HCI H4
        return "unknown", frame[0], frame[1:]
    if linktype == 251:  # LE link-layer
        return "air", None, frame
    return "unknown", None, frame


def parse_pcapng(data: bytes) -> list[Packet]:
    if len(data) < 12 or data[:4] != PCAPNG_SHB:
        raise CaptureError("not a pcapng file")

    packets: list[Packet] = []
    interfaces: list[dict[str, int]] = []
    endian = "<"
    offset = 0

    while offset + 12 <= len(data):
        if data[offset : offset + 4] == PCAPNG_SHB:
            bom = data[offset + 8 : offset + 12]
            if bom == b"\x1a\x2b\x3c\x4d":
                endian = ">"
            elif bom == b"\x4d\x3c\x2b\x1a":
                endian = "<"
            else:
                raise CaptureError("bad pcapng byte-order magic")

        block_type, block_len = struct.unpack(endian + "II", data[offset : offset + 8])
        if block_len < 12 or offset + block_len > len(data):
            raise CaptureError("truncated pcapng block")
        body = data[offset + 8 : offset + block_len - 4]

        if block_type == 0x0A0D0D0A:
            interfaces = []
        elif block_type == 1 and len(body) >= 8:
            linktype, _reserved, _snaplen = struct.unpack(endian + "HHI", body[:8])
            interfaces.append(
                {"linktype": linktype, "tsresol": _tsresol(body[8:], endian)}
            )
        elif block_type == 6 and len(body) >= 20:
            iface, ts_high, ts_low, caplen, _origlen = struct.unpack(
                endian + "IIIII", body[:20]
            )
            frame = body[20 : 20 + caplen]
            ts = (ts_high << 32) | ts_low
            resolution = interfaces[iface]["tsresol"] if iface < len(interfaces) else 6
            ts_us = _to_microseconds(ts, resolution)
            linktype = interfaces[iface]["linktype"] if iface < len(interfaces) else -1
            direction, channel, payload = _decode_linktype(linktype, frame)
            packets.append(
                Packet(
                    index=len(packets),
                    ts_us=ts_us,
                    direction=direction,
                    channel=channel,
                    data=payload,
                )
            )
        elif block_type == 3 and len(body) >= 4:
            orig_len = struct.unpack(endian + "I", body[:4])[0]
            frame = body[4 : 4 + orig_len]
            packets.append(
                Packet(
                    index=len(packets),
                    ts_us=None,
                    direction="air",
                    channel=None,
                    data=frame,
                )
            )

        offset += block_len

    return packets


def _tsresol(options: bytes, endian: str) -> int:
    offset = 0
    while offset + 4 <= len(options):
        code, length = struct.unpack(endian + "HH", options[offset : offset + 4])
        offset += 4
        if code == 0:
            break
        value = options[offset : offset + length]
        offset += length + (-length % 4)
        if code == 9 and len(value) == 1 and not value[0] & 0x80:
            return value[0]
    return 6


def _to_microseconds(ts: int, resolution: int) -> int:
    if resolution >= 6:
        return ts // (10 ** (resolution - 6))
    return ts * (10 ** (6 - resolution))


def parse_file(path: str | Path) -> list[Packet]:
    data = Path(path).read_bytes()
    if data[:8] == BTSNOOP_MAGIC:
        return parse_btsnoop(data)
    if data[:4] in PCAP_MAGIC:
        return parse_pcap(data)
    if data[:4] == PCAPNG_SHB:
        return parse_pcapng(data)
    raise CaptureError(f"unrecognised capture format: {path}")
