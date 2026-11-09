from pathlib import Path
import tempfile
import unittest
from migration_versions import ordered_migrations


class MigrationVersionsTest(unittest.TestCase):
    def ordered(self, names):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in names:
                (root / name).write_text("-- fixture\n")
            return [path.name for path in ordered_migrations(root)]

    def test_inserted_version_precedes_unshipped_group_range(self):
        expected = ["V9__a.sql", "V10__b.sql", "V116__c.sql", "V116_1__d.sql", "V116_2__g.sql", "V116_3__h.sql", "V116_4__i.sql", "V117__e.sql", "V137__f.sql"]
        self.assertEqual(expected, self.ordered(reversed(expected)))

    def test_numeric_components_and_both_flyway_separators(self):
        self.assertEqual(["V116.2__b.sql", "V116_10__a.sql"], self.ordered(["V116_10__a.sql", "V116.2__b.sql"]))

    def test_duplicate_flyway_versions_fail_before_schema_generation(self):
        for names in (["V116__a.sql", "V116_0__b.sql"], ["V116_1__a.sql", "V116.01.0__b.sql"]):
            with self.subTest(names=names), self.assertRaisesRegex(ValueError, "Duplicate"):
                self.ordered(names)

    def test_invalid_numbered_migration_is_not_silently_skipped(self):
        with self.assertRaisesRegex(ValueError, "Invalid"):
            self.ordered(["V116_bad__a.sql"])

    def test_unnumbered_resources_are_not_migrations(self):
        self.assertEqual(["V1__a.sql"], self.ordered(["V1__a.sql", "README.md", "notes.sql"]))


if __name__ == "__main__":
    unittest.main()
