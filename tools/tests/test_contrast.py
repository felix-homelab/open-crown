import importlib.util
import pathlib
import unittest

SPEC = importlib.util.spec_from_file_location(
    "contrast", pathlib.Path(__file__).resolve().parents[2] / "tools/design/contrast.py"
)
contrast = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(contrast)


class ContrastTests(unittest.TestCase):
    def test_black_on_white_is_max_contrast(self):
        self.assertAlmostEqual(contrast.ratio("000000", "FFFFFF"), 21.0, places=1)

    def test_identical_colors_have_no_contrast(self):
        self.assertEqual(contrast.ratio("777777", "777777"), 1.0)

    def test_theme_parses_light_and_dark(self):
        light, dark = contrast.parse()
        self.assertEqual(light["label"], "1D1D1F")
        self.assertEqual(dark["label"], "F5F5F7")
        self.assertEqual(light["cardBackground"], "FFFFFF")
        self.assertEqual(dark["cardBackground"], "1C1C1E")


if __name__ == "__main__":
    unittest.main()
