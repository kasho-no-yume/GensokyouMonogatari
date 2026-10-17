import csv
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).parent


class M2ManifestTest(unittest.TestCase):
    def test_prepare_creates_all_paired_repeats(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "m2"
            result = subprocess.run([sys.executable, str(ROOT / "program_m2_manifest.py"), "prepare", "--root", str(root)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode)
            with (root / "manifest.csv").open(newline="", encoding="utf-8") as stream:
                rows = list(csv.DictReader(stream))
            self.assertEqual(9 * 3 * 2, len(rows))
            self.assertEqual({"legacy", "program"}, {row["mode"] for row in rows})

    def test_validate_refuses_incomplete_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "m2"
            subprocess.run([sys.executable, str(ROOT / "program_m2_manifest.py"), "prepare", "--root", str(root)], check=True)
            result = subprocess.run([sys.executable, str(ROOT / "program_m2_manifest.py"), "validate", "--root", str(root)], capture_output=True, text=True)
            self.assertEqual(1, result.returncode)
            self.assertEqual("pending-evidence", json.loads(result.stdout)["status"])

    def test_prepare_preserves_existing_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)/'m2'
            command=[sys.executable,str(ROOT/'program_m2_manifest.py'),'prepare','--root',str(root)]
            subprocess.run(command,check=True,capture_output=True)
            manifest=root/'manifest.csv'
            before=manifest.read_bytes()
            self.assertNotEqual(0,subprocess.run(command,capture_output=True).returncode)
            self.assertEqual(before,manifest.read_bytes())


if __name__ == "__main__":
    unittest.main()
