import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[2] / "catalog" / "tooling"))

from revocation import empty, is_revoked, load, revoke


class RevocationTests(unittest.TestCase):
    def test_add_and_check(self) -> None:
        import tempfile

        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "revoked.json"
            revoke(path, "org.opencrown.payload.example", "1.0.0", "panics")
            self.assertTrue(is_revoked(path, "org.opencrown.payload.example", "1.0.0"))
            self.assertFalse(is_revoked(path, "org.opencrown.payload.example", "1.0.1"))
            self.assertEqual(load(path)["revoked"][0]["reason"], "panics")

    def test_duplicate_is_updated_not_appended(self) -> None:
        import tempfile

        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "revoked.json"
            revoke(path, "a", "1.0.0")
            revoke(path, "a", "1.0.0", "still bad")
            data = load(path)
            self.assertEqual(len(data["revoked"]), 1)
            self.assertEqual(data["revoked"][0]["reason"], "still bad")

    def test_schema_is_checked(self) -> None:
        import tempfile

        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "revoked.json"
            path.write_text('{"schema": 2, "revoked": []}', encoding="utf-8")
            with self.assertRaises(ValueError):
                load(path)

    def test_empty(self) -> None:
        self.assertEqual(empty(), {"schema": 1, "revoked": []})


if __name__ == "__main__":
    unittest.main()
