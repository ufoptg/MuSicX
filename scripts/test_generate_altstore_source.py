import tempfile
import unittest
from pathlib import Path

from generate_altstore_source import BUNDLE_ID, source


class GenerateAltStoreSourceTest(unittest.TestCase):
    def test_points_at_the_tagged_release_ipa(self):
        with tempfile.TemporaryDirectory() as directory:
            ipa = Path(directory) / "MuSicX.ipa"
            ipa.write_bytes(b"x" * 42)
            app = source("MuSicXGroup/MuSicX", "14.0.0", ipa, "2026-11-14")["apps"][0]
            self.assertEqual(BUNDLE_ID, app["bundleIdentifier"])
            self.assertEqual(
                {
                    "version": "14.0.0",
                    "date": "2026-11-14",
                    "downloadURL": "https://github.com/MuSicXGroup/MuSicX/releases/download/v14.0.0/MuSicX.ipa",
                    "size": 42,
                    "minOSVersion": "17.0",
                },
                app["versions"][0],
            )


if __name__ == "__main__":
    unittest.main()
