import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "lib"))

from opencrown_tools.fixtures import Fixture, FixtureError, Packet, load, save


def sample() -> Fixture:
    return Fixture(
        name="sample",
        meta={"phone_build": "CPH2653", "watch_model": "Watch7,5", "watchos": "26.2"},
        source_kind="btsnoop",
        source_sha256="a" * 64,
        captured_at="2026-11-18T20:00:00+00:00",
        packets=[
            Packet(0, 100, "host_to_controller", 2, b"\x01"),
            Packet(1, 200, "controller_to_host", 4, b"\x02"),
        ],
    )


class FixtureTests(unittest.TestCase):
    def test_roundtrip(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "fixture.json"
            save(sample(), path)
            loaded = load(path)
        self.assertEqual(loaded.to_json(), sample().to_json())

    def test_missing_metadata_is_rejected(self) -> None:
        fixture = sample()
        fixture.meta.pop("watchos")
        with self.assertRaises(FixtureError):
            fixture.validate()

    def test_unknown_direction_is_rejected(self) -> None:
        fixture = sample()
        fixture.packets[0].direction = "sideways"
        with self.assertRaises(FixtureError):
            fixture.validate()


if __name__ == "__main__":
    unittest.main()
