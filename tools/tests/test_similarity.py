import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "lib"))

from opencrown_tools.similarity import compare, shingles, tokens


class SimilarityTests(unittest.TestCase):
    def test_comments_do_not_create_shingles(self) -> None:
        left = tokens("// same comment\nval a = 1")
        right = tokens("val a = 1")
        self.assertEqual(left, right)

    def test_strings_are_compared_as_single_tokens(self) -> None:
        self.assertIn('str:"hello"', tokens('val x = "hello"'))

    def test_identical_files_have_full_containment(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            source = "fn example() { let value = 1; }\n" * 8
            (root / "a.rs").write_text(source, encoding="utf-8")
            (root / "b.rs").write_text(source, encoding="utf-8")
            result = compare(root / "a.rs", root / "b.rs")
            self.assertGreater(result["containment"], 0.9)

    def test_short_files_have_no_shingles(self) -> None:
        self.assertEqual(shingles(tokens("val a = 1")), set())


if __name__ == "__main__":
    unittest.main()
