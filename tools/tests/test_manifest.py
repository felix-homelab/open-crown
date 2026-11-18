import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "lib"))

from opencrown_tools import manifest as m


def good() -> dict:
    return {
        "schema": 1,
        "id": "org.opencrown.payload.example",
        "version": "1.0.0",
        "min_agent": "1.0.0",
        "family": "page-cache",
        "targets": [
            {
                "model": "CPH2653",
                "kernel_build": "6.1.1-android14",
                "abi": "arm64-v8a",
                "android": ">=14",
            }
        ],
        "capabilities": ["root", "domain", "controller"],
        "entry": "files/payload.bin",
        "agent": "files/agent",
        "persistence": "boot",
        "reversible": "reboot",
        "artifacts": [
            {"path": "files/payload.bin", "sha256": "a" * 64, "size": 10}
        ],
        "notes": "test",
    }


class ManifestTests(unittest.TestCase):
    def test_good_manifest(self) -> None:
        self.assertEqual(m.validate(good()), [])

    def test_unknown_capability(self) -> None:
        obj = good()
        obj["capabilities"] = ["root", "telepathy"]
        self.assertTrue(any("unknown capabilities" in issue for issue in m.validate(obj)))

    def test_traversal_rejected(self) -> None:
        obj = good()
        obj["entry"] = "files/../../etc/passwd"
        self.assertTrue(any("inside files/" in issue for issue in m.validate(obj)))

    def test_bad_sha(self) -> None:
        obj = good()
        obj["artifacts"][0]["sha256"] = "xyz"
        self.assertTrue(any("sha256" in issue for issue in m.validate(obj)))

    def test_persistence(self) -> None:
        obj = good()
        obj["persistence"] = "session"
        self.assertTrue(any("persistence" in issue for issue in m.validate(obj)))

    def test_digest_is_stable_and_order_independent(self) -> None:
        first = good()
        second = dict(reversed(list(good().items())))
        self.assertEqual(m.digest(first), m.digest(second))
        self.assertEqual(len(m.digest(first)), 64)

    def test_signature_roundtrip_when_cryptography_present(self) -> None:
        try:
            from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
        except ImportError:
            self.skipTest("cryptography is not installed")

        key = Ed25519PrivateKey.generate()
        signature = key.sign(m.signing_input(good()))
        m.verify_signature(good(), signature, key.public_key().public_bytes_raw())

        with self.assertRaises(m.ManifestError):
            m.verify_signature(good(), b"\x00" * 64, key.public_key().public_bytes_raw())


if __name__ == "__main__":
    unittest.main()
