import tempfile
import unittest
from pathlib import Path

from generate_altstore_source import BUNDLE_ID, source


class GenerateAltStoreSourceTest(unittest.TestCase):
    def test_points_at_the_tagged_release_ipa(self):
        with tempfile.TemporaryDirectory() as directory:
            ipa = Path(directory) / "MuSicX.ipa"
            ipa.write_bytes(b"x" * 42)
            source_data = source("ufoptg/MuSicX", "15.0.0", ipa, "2026-11-14")
            self.assertEqual("MuSicX", source_data["name"])
            app = source_data["apps"][0]
            self.assertEqual("MuSicX", app["name"])
            self.assertEqual(BUNDLE_ID, app["bundleIdentifier"])
            self.assertEqual(
                {
                    "version": "15.0.0",
                    "date": "2026-11-14",
                    "downloadURL": "https://github.com/ufoptg/MuSicX/releases/download/v15.0.0/MuSicX.ipa",
                    "size": 42,
                    "minOSVersion": "17.0",
                },
                app["versions"][0],
            )


if __name__ == "__main__":
    unittest.main()
