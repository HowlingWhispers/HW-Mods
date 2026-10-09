#!/usr/bin/env python3
"""Validate the materialized official BCCE reference; never claim it is a port."""
import json
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
REFERENCE = ROOT / "dist/buildcraft-reference/1.21.11-neoforge"
COMMIT = "23c6af379676ce5262c5c0cb6f1f331edc9b12c6"
MODULES = ("lib", "core", "energy", "transport", "factory", "silicon", "builders", "robotics")


class BCCEReferenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads((REFERENCE / "HOWL-SOURCE-MANIFEST.json").read_text("utf-8"))

    def test_exact_tag_is_the_baseline(self):
        self.assertEqual(self.manifest["version"], "8.0.23")
        self.assertEqual(self.manifest["release_tag"], "8.0.23")
        self.assertEqual(self.manifest["commit"], COMMIT)
        self.assertEqual(self.manifest["reference_target"], "1.21.11-neoforge")
        self.assertIn("NOT a H.O.W.L. binary", self.manifest["status"])

    def test_all_original_modules_have_source(self):
        java = REFERENCE / "src/main/java/buildcraft"
        for module in MODULES:
            with self.subTest(module=module):
                count = sum(1 for _ in (java / module).rglob("*.java"))
                self.assertGreater(count, 0)
                self.assertEqual(self.manifest["module_sources"][module], count)
        self.assertGreaterEqual(self.manifest["java_sources"], sum(self.manifest["module_sources"].values()))

    def test_original_critical_entrypoints_and_resources_exist(self):
        for relative in (
            "src/main/java/buildcraft/transport/BCTransportPipes.java",
            "src/main/java/buildcraft/transport/BCTransportRegistries.java",
            "src/main/java/buildcraft/energy/BCEnergyBlocks.java",
            "src/main/resources/assets",
            "META-INF/buildcraft-upstream/LICENSE.txt",
        ):
            with self.subTest(relative=relative):
                self.assertTrue((REFERENCE / relative).exists(), relative)
        self.assertIn(
            "Mozilla Public License Version 2.0",
            (REFERENCE / "META-INF/buildcraft-upstream/LICENSE.txt").read_text("utf-8"),
        )

    def test_asset_importer_uses_same_official_release(self):
        importer = (ROOT / "scripts/import-buildcraft-upstream-assets.sh").read_text("utf-8")
        self.assertIn(f'COMMIT="{COMMIT}"', importer)
        self.assertIn('RELEASE="8.0.23"', importer)


if __name__ == "__main__":
    unittest.main(verbosity=2)
