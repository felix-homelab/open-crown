import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[2] / "catalog" / "tooling"))

from payload_index import load, targets_for, validate


def data() -> dict:
    return {
        "schema": 1,
        "updated": "2026-11-21",
        "sources": [
            {
                "id": "example-source",
                "url": "https://example.invalid/payload",
                "license": "MIT",
                "retrieved": "2026-11-21",
            }
        ],
        "payloads": [
            {
                "id": "org.opencrown.payload.example",
                "name": "Example",
                "family": "page-cache",
                "status": "available",
                "source": "example-source",
                "cves": ["CVE-2026-0001"],
                "android": ">=31",
                "abi": "arm64-v8a",
                "targets": [
                    {
                        "model": "SM-A528B",
                        "kernel_build": "5.4.254-example",
                        "kernel_version": "5.4.254",
                    },
                    {
                        "model": "SM-A528B",
                        "kernel_build": None,
                        "kernel_version": "5.4.254",
                        "build": "A528BEXAMPLE",
                    },
                ],
            }
        ],
    }


class PayloadIndexTests(unittest.TestCase):
    def test_valid_index(self) -> None:
        self.assertEqual(validate(data()), [])

    def test_bad_schema(self) -> None:
        broken = data()
        broken["schema"] = 2
        self.assertTrue(any("schema" in issue for issue in validate(broken)))

    def test_duplicate_payload(self) -> None:
        broken = data()
        broken["payloads"].append(dict(broken["payloads"][0]))
        self.assertTrue(any("duplicates" in issue for issue in validate(broken)))

    def test_unknown_source(self) -> None:
        broken = data()
        broken["payloads"][0]["source"] = "missing"
        self.assertTrue(any("source" in issue for issue in validate(broken)))

    def test_bad_status(self) -> None:
        broken = data()
        broken["payloads"][0]["status"] = "probably"
        self.assertTrue(any("status" in issue for issue in validate(broken)))

    def test_bad_cve(self) -> None:
        broken = data()
        broken["payloads"][0]["cves"] = ["43284"]
        self.assertTrue(any("cves" in issue for issue in validate(broken)))

    def test_null_kernel_needs_published_version(self) -> None:
        broken = data()
        del broken["payloads"][0]["targets"][1]["kernel_version"]
        self.assertTrue(any("kernel_version" in issue for issue in validate(broken)))

    def test_payload_without_targets_is_listed(self) -> None:
        listed = data()
        listed["payloads"][0]["targets"] = []
        self.assertEqual(validate(listed), [])

    def test_committed_index_is_valid(self) -> None:
        repo = pathlib.Path(__file__).resolve().parents[2]
        committed = load(repo / "catalog" / "payload-index.json")
        self.assertEqual(validate(committed), [])
        self.assertEqual(
            targets_for(
                committed,
                "SM-A528B",
                "5.4.254-qgki-28575149-abA528BXXSBGYI3",
            ),
            [],
            "the A52 must not be given a fabricated payload target",
        )

    def test_exact_lookup_ignores_null_targets(self) -> None:
        matches = targets_for(data(), "SM-A528B", "5.4.254-example")
        self.assertEqual(len(matches), 1)
        self.assertEqual(matches[0]["payload"]["id"], "org.opencrown.payload.example")
        self.assertEqual(targets_for(data(), "SM-A528B", "5.4.254-other"), [])


if __name__ == "__main__":
    unittest.main()
