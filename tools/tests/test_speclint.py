import json
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "lib"))

from opencrown_tools.speclint import lint_spec

SECTIONS = [
    "Purpose",
    "Actors and preconditions",
    "Sequence",
    "Messages",
    "State machine and errors",
    "Version and feature detection",
    "Open questions",
    "Vectors",
    "Acceptance",
]


def write_spec(root: pathlib.Path, sections: list[str] | None = None) -> pathlib.Path:
    body = "# Discovery - advertisement spec\n\n**Status:** draft\n\n"
    for section in sections if sections is not None else SECTIONS:
        body += f"## {section}\n\ntext\n\n"
    spec = root / "spec.md"
    spec.write_text(body, encoding="utf-8")
    return spec


def write_vector(root: pathlib.Path, **overrides) -> None:
    vector = {
        "schema": 1,
        "capture_sha256": "a" * 64,
        "input": {"hex": "00"},
        "expected": {"hex": "01"},
    }
    vector.update(overrides)
    vectors = root / "vectors"
    vectors.mkdir(exist_ok=True)
    (vectors / "case.json").write_text(json.dumps(vector), encoding="utf-8")


class SpecLintTests(unittest.TestCase):
    def test_valid_spec_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            spec = write_spec(root)
            write_vector(root)
            self.assertEqual(lint_spec(spec), [])

    def test_missing_section_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            spec = write_spec(root, [section for section in SECTIONS if section != "Messages"])
            write_vector(root)
            issues = lint_spec(spec)
        self.assertTrue(any("missing section: messages" in issue for issue in issues))

    def test_missing_vector_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            spec = write_spec(root)
            issues = lint_spec(spec)
        self.assertTrue(any("no vectors" in issue for issue in issues))

    def test_bad_vector_hash_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            spec = write_spec(root)
            write_vector(root, capture_sha256="short")
            issues = lint_spec(spec)
        self.assertTrue(any("capture_sha256" in issue for issue in issues))

    def test_bad_status_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            spec = root / "spec.md"
            spec.write_text("# X\n\n**Status:** probably\n", encoding="utf-8")
            issues = lint_spec(spec)
        self.assertTrue(any("invalid status" in issue for issue in issues))


if __name__ == "__main__":
    unittest.main()
