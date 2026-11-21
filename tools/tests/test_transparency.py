import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[2] / "catalog" / "tooling"))

from transparency import append, read, verify


class TransparencyTests(unittest.TestCase):
    def test_chain_verifies(self) -> None:
        import tempfile

        with tempfile.TemporaryDirectory() as tmp:
            log = pathlib.Path(tmp) / "transparency.jsonl"
            append(log, "a" * 64)
            append(log, "b" * 64)
            append(log, "c" * 64)
            self.assertEqual(len(read(log)), 3)
            self.assertEqual(verify(log), [])

    def test_tampering_breaks_the_chain(self) -> None:
        import tempfile

        with tempfile.TemporaryDirectory() as tmp:
            log = pathlib.Path(tmp) / "transparency.jsonl"
            append(log, "a" * 64)
            append(log, "b" * 64)
            lines = log.read_text(encoding="utf-8").splitlines()
            log.write_text(lines[0] + "\n" + lines[1].replace("b" * 64, "c" * 64) + "\n", encoding="utf-8")
            self.assertTrue(verify(log))

    def test_bad_digest_is_rejected(self) -> None:
        import tempfile

        with tempfile.TemporaryDirectory() as tmp:
            log = pathlib.Path(tmp) / "log.jsonl"
            with self.assertRaises(ValueError):
                append(log, "short")

    def test_empty_log_is_valid(self) -> None:
        import tempfile

        with tempfile.TemporaryDirectory() as tmp:
            log = pathlib.Path(tmp) / "log.jsonl"
            self.assertEqual(verify(log), [])


if __name__ == "__main__":
    unittest.main()
