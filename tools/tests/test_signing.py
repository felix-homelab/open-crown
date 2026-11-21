import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "lib"))
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[2] / "catalog" / "tooling"))

try:
    import cryptography  # noqa: F401

    HAVE_CRYPTO = True
except ImportError:
    HAVE_CRYPTO = False


def manifest() -> dict:
    return {
        "schema": 1,
        "id": "org.opencrown.payload.example",
        "version": "1.0.0",
        "min_agent": "0.1.0",
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
            {"path": "files/payload.bin", "sha256": "a" * 64, "size": 10},
            {"path": "files/agent", "sha256": "b" * 64, "size": 20},
        ],
    }


@unittest.skipUnless(HAVE_CRYPTO, "cryptography is not installed")
class SigningTests(unittest.TestCase):
    def test_sign_and_verify(self) -> None:
        from sign import generate_keypair, sign_manifest, verify_manifest

        private_hex, public_hex = generate_keypair()
        signature = sign_manifest(manifest(), private_hex)
        self.assertEqual(len(signature), 64)
        verify_manifest(manifest(), signature, public_hex)

    def test_tampered_manifest_fails(self) -> None:
        from sign import generate_keypair, sign_manifest, verify_manifest

        private_hex, public_hex = generate_keypair()
        signature = sign_manifest(manifest(), private_hex)
        tampered = manifest()
        tampered["capabilities"] = ["root", "domain"]
        with self.assertRaises(ValueError):
            verify_manifest(tampered, signature, public_hex)

    def test_wrong_key_fails(self) -> None:
        from sign import generate_keypair, sign_manifest, verify_manifest

        private_hex, _ = generate_keypair()
        _, other_public = generate_keypair()
        signature = sign_manifest(manifest(), private_hex)
        with self.assertRaises(ValueError):
            verify_manifest(manifest(), signature, other_public)

    def test_invalid_manifest_is_rejected_before_signing(self) -> None:
        from sign import generate_keypair, sign_manifest

        private_hex, _ = generate_keypair()
        broken = manifest()
        broken["capabilities"] = ["telepathy"]
        with self.assertRaises(ValueError):
            sign_manifest(broken, private_hex)


if __name__ == "__main__":
    unittest.main()
