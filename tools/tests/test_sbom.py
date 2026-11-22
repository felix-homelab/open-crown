import importlib.util
import json
import pathlib
import tempfile
import unittest
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("sbom", ROOT / "tools/release/sbom.py")
sbom = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(sbom)


class SbomTests(unittest.TestCase):
    def test_license_expression_normalization(self):
        self.assertEqual(
            sbom.license_entry("MIT/Apache-2.0"),
            {"expression": "MIT OR Apache-2.0"},
        )
        self.assertEqual(sbom.license_entry(None)["license"]["name"], "NOASSERTION")

    def test_native_components_hash_entries(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = pathlib.Path(tmp) / "app.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("lib/arm64-v8a/libx.so", b"abc")
                archive.writestr("classes.dex", b"ignored")
            components = sbom.native_components(apk)
            self.assertEqual(len(components), 1)
            self.assertEqual(components[0]["name"], "lib/arm64-v8a/libx.so")
            self.assertEqual(components[0]["hashes"][0]["alg"], "SHA-256")


if __name__ == "__main__":
    unittest.main()