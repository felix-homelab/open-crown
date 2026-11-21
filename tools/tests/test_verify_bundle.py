import hashlib
import json
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[2] / "catalog" / "tooling"))
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "lib"))

try:
    import cryptography  # noqa: F401

    HAVE_CRYPTO = True
except ImportError:
    HAVE_CRYPTO = False


@unittest.skipUnless(HAVE_CRYPTO, "cryptography is not installed")
class VerifyBundleTests(unittest.TestCase):
    def make_bundle(self, root: pathlib.Path) -> pathlib.Path:
        from sign import generate_keypair, sign_manifest

        bundle = root / "bundle"
        (bundle / "files").mkdir(parents=True)
        payload = b"payload"
        agent = b"agent"
        (bundle / "files" / "payload.bin").write_bytes(payload)
        (bundle / "files" / "agent").write_bytes(agent)

        manifest = {
            "schema": 1,
            "id": "org.opencrown.payload.example",
            "version": "1.0.0",
            "min_agent": "0.1.0",
            "family": "page-cache",
            "targets": [
                {
                    "model": "EXAMPLE-1",
                    "kernel_build": "example-6.1.0",
                    "abi": "arm64-v8a",
                    "android": ">=31",
                }
            ],
            "capabilities": ["root", "domain", "controller"],
            "entry": "files/payload.bin",
            "agent": "files/agent",
            "persistence": "boot",
            "reversible": "reboot",
            "artifacts": [
                {
                    "path": "files/payload.bin",
                    "sha256": hashlib.sha256(payload).hexdigest(),
                    "size": len(payload),
                },
                {
                    "path": "files/agent",
                    "sha256": hashlib.sha256(agent).hexdigest(),
                    "size": len(agent),
                },
            ],
        }
        (bundle / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        private_hex, public_hex = generate_keypair()
        (bundle / "manifest.sig").write_bytes(sign_manifest(manifest, private_hex))
        (bundle / "public_key.hex").write_text(public_hex + "\n", encoding="utf-8")
        return bundle

    def test_valid_bundle(self) -> None:
        from verify import verify_bundle

        with tempfile.TemporaryDirectory() as tmp:
            bundle = self.make_bundle(pathlib.Path(tmp))
            self.assertEqual(verify_bundle(bundle), [])

    def test_tampered_artifact(self) -> None:
        from verify import verify_bundle

        with tempfile.TemporaryDirectory() as tmp:
            bundle = self.make_bundle(pathlib.Path(tmp))
            (bundle / "files" / "payload.bin").write_bytes(b"evil")
            issues = verify_bundle(bundle)
            self.assertTrue(any("mismatch" in issue for issue in issues))

    def test_tampered_manifest_breaks_signature(self) -> None:
        from verify import verify_bundle

        with tempfile.TemporaryDirectory() as tmp:
            bundle = self.make_bundle(pathlib.Path(tmp))
            manifest = json.loads((bundle / "manifest.json").read_text(encoding="utf-8"))
            manifest["version"] = "9.9.9"
            (bundle / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            issues = verify_bundle(bundle)
            self.assertTrue(any("signature" in issue for issue in issues))

    def test_revoked_bundle(self) -> None:
        from verify import verify_bundle

        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            bundle = self.make_bundle(root)
            revoked = root / "revoked.json"
            revoked.write_text(
                '{"schema":1,"revoked":[{"id":"org.opencrown.payload.example","version":"1.0.0"}]}',
                encoding="utf-8",
            )
            issues = verify_bundle(bundle, revoked)
            self.assertIn("payload is revoked", issues)

    def test_missing_artifact(self) -> None:
        from verify import verify_bundle

        with tempfile.TemporaryDirectory() as tmp:
            bundle = self.make_bundle(pathlib.Path(tmp))
            (bundle / "files" / "agent").unlink()
            issues = verify_bundle(bundle)
            self.assertTrue(any("missing artifact" in issue for issue in issues))


if __name__ == "__main__":
    unittest.main()
