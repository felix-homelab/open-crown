import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "lib"))

from opencrown_tools.secrets import scan_paths


class SecretScanTests(unittest.TestCase):
    def test_private_key_block_is_found_without_echoing_it(self) -> None:
        with __import__("tempfile").TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            marker = "-----BEGIN " + "PRIVATE KEY-----"
            (root / "leak.md").write_text("prefix " + marker + " suffix", encoding="utf-8")
            findings = scan_paths([root / "leak.md"], root)
            self.assertTrue(any("private-key-block" in item for item in findings))
            self.assertFalse(any(marker in item for item in findings))

    def test_clean_file_passes(self) -> None:
        with __import__("tempfile").TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            (root / "ok.md").write_text("nothing sensitive here", encoding="utf-8")
            self.assertEqual(scan_paths([root / "ok.md"], root), [])

    def test_forbidden_suffix_is_found(self) -> None:
        with __import__("tempfile").TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            (root / "signing.key").write_text("irrelevant", encoding="utf-8")
            findings = scan_paths([root / "signing.key"], root)
            self.assertTrue(any("forbidden signing-material" in item for item in findings))


if __name__ == "__main__":
    unittest.main()
