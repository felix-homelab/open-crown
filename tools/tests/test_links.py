import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "lib"))

from opencrown_tools.links import check, slug


class LinkTests(unittest.TestCase):
    def test_slug(self) -> None:
        self.assertEqual(slug("8. Evidence format"), "8-evidence-format")
        self.assertEqual(slug("Phase 0 — First payload (6–10 weeks)"), "phase-0--first-payload-610-weeks")

    def test_valid_links_and_anchors(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            (root / "b.md").write_text("# Section one\n\n## Deep part\n", encoding="utf-8")
            (root / "a.md").write_text("[b](b.md) [deep](b.md#deep-part) [here](#top)\n\n# Top\n", encoding="utf-8")
            self.assertEqual(check(root), [])

    def test_missing_file(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            (root / "a.md").write_text("[gone](gone.md)\n", encoding="utf-8")
            self.assertTrue(any("missing link target" in issue for issue in check(root)))

    def test_missing_anchor(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            (root / "b.md").write_text("# Present\n", encoding="utf-8")
            (root / "a.md").write_text("[b](b.md#absent)\n", encoding="utf-8")
            self.assertTrue(any("missing anchor" in issue for issue in check(root)))

    def test_external_links_are_ignored(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            (root / "a.md").write_text("[x](https://example.com) [y](mailto:a@b.c)\n", encoding="utf-8")
            self.assertEqual(check(root), [])


if __name__ == "__main__":
    unittest.main()
