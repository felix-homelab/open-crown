import importlib.util
import json
import pathlib
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]


def load_module():
    spec = importlib.util.spec_from_file_location(
        "apk_report", ROOT / "tools" / "release" / "apk-report.py"
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ApkReportTests(unittest.TestCase):
    def test_report(self) -> None:
        module = load_module()

        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            apk = root / "app-debug.apk"
            apk.write_bytes(b"apk-bytes")
            (root / "output-metadata.json").write_text(
                json.dumps({"version": 1}), encoding="utf-8"
            )

            data = module.report(apk)
            self.assertEqual(data["apk"], "app-debug.apk")
            self.assertEqual(data["size"], 9)
            self.assertEqual(len(data["sha256"]), 64)
            self.assertEqual(data["metadata"], {"version": 1})

    def test_missing_apk(self) -> None:
        module = load_module()
        with self.assertRaises(FileNotFoundError):
            module.report("/nonexistent/app.apk")


if __name__ == "__main__":
    unittest.main()
