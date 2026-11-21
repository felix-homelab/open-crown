import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[2] / "catalog" / "tooling"))

from devices import find, validate


def data() -> dict:
    return {
        "schema": 1,
        "updated": "2026-11-21",
        "devices": [
            {
                "model": "EXAMPLE-1",
                "kernel_build": "example-6.1.0",
                "abi": "arm64-v8a",
                "android": ">=31",
                "status": "example",
            },
            {
                "model": "PHONE-2",
                "kernel_build": "6.1.0-android14",
                "abi": "arm64-v8a",
                "android": ">=34",
                "status": "supported",
                "note": "Tested on the bench.",
            },
        ],
    }


class DeviceTests(unittest.TestCase):
    def test_valid_list(self) -> None:
        self.assertEqual(validate(data()), [])

    def test_duplicate_is_rejected(self) -> None:
        broken = data()
        broken["devices"].append(dict(broken["devices"][0]))
        self.assertTrue(any("duplicates" in issue for issue in validate(broken)))

    def test_bad_status(self) -> None:
        broken = data()
        broken["devices"][0]["status"] = "probably"
        self.assertTrue(any("status" in issue for issue in validate(broken)))

    def test_bad_android_range(self) -> None:
        broken = data()
        broken["devices"][0]["android"] = "14"
        self.assertTrue(any("android" in issue for issue in validate(broken)))

    def test_bad_schema(self) -> None:
        broken = data()
        broken["schema"] = 2
        self.assertTrue(any("schema" in issue for issue in validate(broken)))

    def test_find_exact(self) -> None:
        self.assertEqual(find(data(), "PHONE-2", "6.1.0-android14")["status"], "supported")
        self.assertIsNone(find(data(), "PHONE-2", "6.1.1-android14"))


if __name__ == "__main__":
    unittest.main()
