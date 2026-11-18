import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "lib"))

from opencrown_tools.fixturediff import diff
from opencrown_tools.fixtures import Fixture, Packet


def fixture(packets: list[Packet]) -> Fixture:
    return Fixture(
        name="x",
        meta={"phone_build": "p", "watch_model": "w", "watchos": "o"},
        source_sha256="a" * 64,
        captured_at="2026-11-18T20:00:00+00:00",
        packets=packets,
    )


class FixtureDiffTests(unittest.TestCase):
    def test_identical_is_empty(self) -> None:
        packets = [Packet(0, 1, "host_to_controller", 2, b"\x01")]
        self.assertEqual(diff(fixture(packets), fixture(packets)), [])

    def test_data_change(self) -> None:
        left = fixture([Packet(0, 1, "host_to_controller", 2, b"\x01")])
        right = fixture([Packet(0, 1, "host_to_controller", 2, b"\x02")])
        changes = diff(left, right)
        self.assertEqual(len(changes), 1)
        self.assertEqual(changes[0].field, "data")

    def test_timing_ignored_by_default(self) -> None:
        left = fixture([Packet(0, 1, "host_to_controller", 2, b"\x01")])
        right = fixture([Packet(0, 2, "host_to_controller", 2, b"\x01")])
        self.assertEqual(diff(left, right), [])
        self.assertEqual(diff(left, right, ignore_timing=False)[0].field, "ts_us")

    def test_length_change(self) -> None:
        left = fixture([Packet(0, 1, "host_to_controller", 2, b"\x01")])
        right = fixture([])
        self.assertEqual(diff(left, right)[0].field, "packet")


if __name__ == "__main__":
    unittest.main()
