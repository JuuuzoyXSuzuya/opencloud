import tempfile
import unittest
from pathlib import Path

from opencloud import catalog


TSV = Path(__file__).resolve().parent.parent / "data" / "countries.tsv"


class CatalogTests(unittest.TestCase):
    def test_table_is_complete_and_unique(self):
        rows = catalog.load(TSV)
        self.assertEqual(66, len({row.iso for row in rows}))
        self.assertGreater(len(rows), 66)
        self.assertEqual(len(rows), len({row.numeric for row in rows}))
        self.assertIn("nl", {row.iso for row in rows})
        for row in rows:
            self.assertRegex(row.iso, r"^[a-z]{2}$")
            self.assertRegex(row.numeric, r"^[0-9]{5,6}$")
            self.assertTrue(row.name)
            self.assertTrue(row.carrier)

    def test_country_can_have_several_operators(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "multi.tsv"
            path.write_text(
                "nl\tNetherlands\t20408\tKPN\n"
                "nl\tNetherlands\t20404\tVodafone\n"
                "de\tGermany\t26201\tTelekom\n",
                encoding="utf-8")
            rows = catalog.load(path)
            self.assertEqual(3, len(rows))
            source = catalog.java_source(rows, "nl")
            self.assertIn("profilesFor", source)
            self.assertIn("BY_ISO", source)

    def test_java_source_has_default_and_row(self):
        rows = catalog.load(TSV)
        source = catalog.java_source(rows, "nl")
        self.assertIn('DEFAULT_ISO = "nl";', source)
        self.assertIn('{"nl", "Netherlands", "20408", "KPN"}', source)
        self.assertIn("LinkedHashMap<String, Profile>", source)

    def test_bad_table_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "bad.tsv"
            path.write_text("nl\tNetherlands\t20408\tKPN\nde\tGermany\t20408\tX\n",
                            encoding="utf-8")
            with self.assertRaises(catalog.CatalogError):
                catalog.load(path)

    def test_unknown_default_is_rejected(self):
        rows = catalog.load(TSV)
        with self.assertRaises(catalog.CatalogError):
            catalog.java_source(rows, "zz")

