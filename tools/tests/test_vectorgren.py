import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "lib"))

from opencrown_tools.fixtures import Fixture, Packet
from opencrown_tools.vectorgren import VectorError, build_vector, parse_redaction


def fixture() -> Fixture:
    return Fixture(
        name="x",
        meta={"phone_build": "p", "watch_model": "w", "watchos": "o"},
        source_sha256="b" * 64,
        captured_at="2026-11-18T20:00:00+00:00",
        packets=[
            Packet(0, 1, "host_to_controller", 2, b"\xde\xad"),
            Packet(1, 2, "controller_to_host", 4, b"\xbe\xef"),
        ],
    )


class VectorGenTests(unittest.TestCase):
    def test_build(self) -> None:
        vector = build_vector(fixture(), name="pairing/hello")
        self.assertEqual(vector["input"]["hex"], "dead")
        self.assertEqual(vector["expected"]["hex"], "beef")
        self.assertEqual(vector["capture_sha256"], "b" * 64)

    def test_redaction_is_recorded(self) -> None:
        vector = build_vector(
            fixture(), name="x", redactions=[(b"\xde\xad", b"\x00\x00")]
        )
        self.assertEqual(vector["input"]["hex"], "0000")
        self.assertEqual(vector["redactions"], ["dead=0000"])

    def test_missing_direction(self) -> None:
        with self.assertRaises(VectorError):
            build_vector(fixture(), name="x", response_direction="air")

    def test_selection_out_of_range(self) -> None:
        with self.assertRaises(VectorError):
            build_vector(fixture(), name="x", start=5)

    def test_parse_redaction(self) -> None:
        self.assertEqual(parse_redaction("dead"), (b"\xde\xad", b"\x00"))
        self.assertEqual(parse_redaction("dead=beef"), (b"\xde\xad", b"\xbe\xef"))
        with self.assertRaises(VectorError):
            parse_redaction("abc")


if __name__ == "__main__":
    unittest.main()
