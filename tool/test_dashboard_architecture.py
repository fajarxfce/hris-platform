"""Regression coverage for imports that resemble identifiers in module paths."""

from pathlib import Path
import tempfile
import unittest

from check_dashboard_architecture import check_dashboard


class DashboardArchitectureTest(unittest.TestCase):
    def check(self, text: str) -> list[str]:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "apps/dashboard/src/features/people/domain/entities/example.ts"
            source.parent.mkdir(parents=True)
            source.write_text(text)
            return check_dashboard(root)

    def test_import_in_file_names_and_permission_literals_is_not_a_statement(self):
        self.assertEqual([], self.check('''
import type { EmployeeImport } from "./employee-import";
import type { EmployeeImportRow } from "./employee-import-row";
export const permissions = ["people.import", "people.manage"];
'''))

    def test_side_effect_imports_still_obey_domain_boundaries(self):
        for statement in ('import "react";', 'import"react";'):
            with self.subTest(statement=statement):
                self.assertEqual(1, len(self.check(statement)))

    def test_dynamic_imports_still_obey_domain_boundaries(self):
        self.assertEqual(1, len(self.check('const loaded = import("../../data/client");')))

    def test_named_imports_still_obey_domain_boundaries(self):
        self.assertEqual(1, len(self.check('import { client } from "../../data/client";')))


if __name__ == "__main__":
    unittest.main()
