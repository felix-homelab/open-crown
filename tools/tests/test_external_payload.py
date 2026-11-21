import hashlib
import json
import pathlib
import sys
import unittest

REPO = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "catalog" / "tooling"))

from payload_index import load, validate
from verify import verify_bundle

BUNDLE = REPO / "payloads" / "external" / "org.opencrown.payload.futex-timer"


class ExternalPayloadTests(unittest.TestCase):
    def test_bundle_verifies(self) -> None:
        self.assertEqual(verify_bundle(BUNDLE), [])

    def test_artifact_matches_manifest(self) -> None:
        manifest = json.loads((BUNDLE / "manifest.json").read_text())
        artifact = manifest["artifacts"][0]
        data = (BUNDLE / artifact["path"]).read_bytes()
        self.assertEqual(len(data), artifact["size"])
        self.assertEqual(hashlib.sha256(data).hexdigest(), artifact["sha256"])
        self.assertEqual(manifest["entry"], manifest["agent"])
        self.assertEqual(manifest["capabilities"], ["root", "domain"])

    def test_index_declares_exact_target(self) -> None:
        index = load(REPO / "catalog" / "payload-index.json")
        self.assertEqual(validate(index), [])
        entry = next(
            payload
            for payload in index["payloads"]
            if payload["id"] == "org.opencrown.payload.futex-timer"
        )
        self.assertEqual(entry["source"], "root-my-s24")
        self.assertEqual(entry["family"], "futex-uaf")
        self.assertEqual(len(entry["targets"]), 1)
        target = entry["targets"][0]
        self.assertEqual(target["model"], "SM-S9280")
        self.assertEqual(
            target["kernel_build"],
            "6.1.145-android14-11-3254743-abS9280ZCS6DZJ3",
        )

    def test_provenance_names_the_upstream(self) -> None:
        text = (BUNDLE / "PROVENANCE.md").read_text()
        self.assertIn("NanoTurtle1145/root-my-s24", text)
        self.assertIn("GPL-3.0", text)
        self.assertIn("not built", text.lower())
        self.assertTrue((BUNDLE / "LICENSE").is_file())


if __name__ == "__main__":
    unittest.main()
