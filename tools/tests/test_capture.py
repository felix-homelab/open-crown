import pathlib
import struct
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "lib"))

from opencrown_tools.capture import (
    CaptureError,
    parse_btsnoop,
    parse_pcap,
    parse_pcapng,
)

OFFSET = 62_135_596_800_000_000


def make_btsnoop() -> bytes:
    header = b"btsnoop\x00" + struct.pack(">II", 1, 1002)
    records = b""
    for payload, flags, ts in (
        (b"\x02\xaa\xbb", 0, OFFSET + 1000),
        (b"\x04\xcc", 1, OFFSET + 2000),
    ):
        records += struct.pack(">IIIIQ", len(payload), len(payload), flags, 0, ts)
        records += payload
    return header + records


def make_pcap() -> bytes:
    header = struct.pack("<IHHiIII", 0xA1B2C3D4, 2, 4, 0, 0, 65535, 187)
    frame = b"\x02\x11\x22"
    record = struct.pack("<IIII", 1_700_000_000, 250, len(frame), len(frame)) + frame
    return header + record


def block(block_type: int, body: bytes, endian: str = "<") -> bytes:
    total = 12 + len(body)
    return (
        struct.pack(endian + "II", block_type, total)
        + body
        + struct.pack(endian + "I", total)
    )


def make_pcapng() -> bytes:
    shb = block(0x0A0D0D0A, struct.pack("<IHHq", 0x1A2B3C4D, 1, 0, -1))
    idb = block(1, struct.pack("<HHI", 187, 0, 65535) + struct.pack("<HH", 0, 0))
    frame = b"\x02\x01\x02\x03"
    ts = 1_700_000_000_000_000
    epb = block(
        6,
        struct.pack("<IIIII", 0, ts >> 32, ts & 0xFFFFFFFF, len(frame), len(frame))
        + frame,
    )
    return shb + idb + epb


class CaptureTests(unittest.TestCase):
    def test_btsnoop_directions_and_timestamps(self) -> None:
        packets = parse_btsnoop(make_btsnoop())
        self.assertEqual(len(packets), 2)
        self.assertEqual(packets[0].direction, "host_to_controller")
        self.assertEqual(packets[0].channel, 2)
        self.assertEqual(packets[0].data, b"\xaa\xbb")
        self.assertEqual(packets[0].ts_us, 1000)
        self.assertEqual(packets[1].direction, "controller_to_host")
        self.assertEqual(packets[1].channel, 4)

    def test_btsnoop_rejects_garbage(self) -> None:
        with self.assertRaises(CaptureError):
            parse_btsnoop(b"nope")

    def test_pcap_linktype_187(self) -> None:
        packets = parse_pcap(make_pcap())
        self.assertEqual(len(packets), 1)
        self.assertEqual(packets[0].channel, 2)
        self.assertEqual(packets[0].data, b"\x11\x22")
        self.assertEqual(packets[0].ts_us, 1_700_000_000_000_250)

    def test_pcapng_epb(self) -> None:
        packets = parse_pcapng(make_pcapng())
        self.assertEqual(len(packets), 1)
        self.assertEqual(packets[0].ts_us, 1_700_000_000_000_000)
        self.assertEqual(packets[0].data, b"\x01\x02\x03")

    def test_pcapng_rejects_truncated(self) -> None:
        with self.assertRaises(CaptureError):
            parse_pcapng(block(0x0A0D0D0A, struct.pack("<IHHq", 0x1A2B3C4D, 1, 0, -1))[:-4])


if __name__ == "__main__":
    unittest.main()
