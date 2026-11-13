import unittest
from check_repository import problem

class RepositoryPolicyTest(unittest.TestCase):
    def test_private_files_are_rejected(self):
        for path in ('.runtime/local.json', '.env', '.env.production', '.tools/cache/file', 'frontend/dist/app.js', 'automation/target/report.xml'):
            with self.subTest(path=path):
                self.assertIsNotNone(problem(path))

    def test_templates_sources_and_selected_screenshots_are_allowed(self):
        for path in ('.env.example', 'backend/src/main/java/App.java', 'frontend/src/main.tsx', 'docs/screenshots/purchase-desktop.png', '.github/workflows/quality.yml'):
            with self.subTest(path=path):
                self.assertIsNone(problem(path))

    def test_windows_paths_and_key_archives_are_rejected(self):
        for path in (r'frontend\node_modules\lib.js', 'private.pfx', 'server.key', 'tools.zip', 'database.dump', 'export.backup', 'sources.tar'):
            with self.subTest(path=path):
                self.assertIsNotNone(problem(path))

if __name__ == '__main__':
    unittest.main()
