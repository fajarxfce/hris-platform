import json
from pathlib import Path
import tempfile
import unittest

from dashboard_fcm_fixture import worker_fcm_environment


class NativeFcmFixtureTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / "service-account.json"
        self.value = {
            "type": "service_account", "project_id": "fixture-project",
            "token_uri": "https://oauth2.googleapis.com/token",
            "client_email": "fictional@example.invalid",
            "private_key": "PRIVATE_FIXTURE_ONLY", "private_key_id": "fixture-only",
        }
        self.write()
        self.environment = {
            "HRIS_TEST_WITH_FCM": "1", "HRIS_TEST_WITH_WORKER": "1",
            "HRIS_TEST_FCM_PROJECT_ID": "fixture-project",
            "HRIS_TEST_FCM_CREDENTIALS_FILE": str(self.path),
        }

    def write(self):
        self.path.write_text(json.dumps(self.value))
        self.path.chmod(0o600)

    def test_ordinary_fixture_ignores_inherited_production_opt_in_and_missing_file(self):
        self.path.unlink()
        self.assertEqual({}, worker_fcm_environment(self.environment | {
            "HRIS_TEST_WITH_FCM": "0", "HRIS_FCM_ENABLED": "true",
        }))

    def test_explicit_private_worker_configuration_returns_only_sender_settings(self):
        self.assertEqual({
            "HRIS_FCM_ENABLED": "true", "HRIS_FCM_PROJECT_ID": "fixture-project",
            "HRIS_FCM_CREDENTIALS_FILE": str(self.path),
        }, worker_fcm_environment(self.environment))

    def test_requires_worker_project_and_file(self):
        for key in ("HRIS_TEST_WITH_WORKER", "HRIS_TEST_FCM_PROJECT_ID", "HRIS_TEST_FCM_CREDENTIALS_FILE"):
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                worker_fcm_environment(self.environment | {key: ""})

    def test_rejects_mismatched_or_external_credentials_without_disclosing_contents(self):
        for key, value in (("project_id", "another-project"), ("type", "external_account"),
                           ("token_uri", "https://untrusted.invalid/token"), ("private_key", "")):
            before = dict(self.value)
            self.value[key] = value
            self.write()
            with self.subTest(key=key), self.assertRaises(RuntimeError) as caught:
                worker_fcm_environment(self.environment)
            self.assertNotIn("PRIVATE_FIXTURE_ONLY", str(caught.exception))
            self.assertNotIn(str(self.path), str(caught.exception))
            self.value = before

    def test_rejects_public_oversized_and_non_file_inputs(self):
        self.path.chmod(0o644)
        with self.assertRaises(RuntimeError):
            worker_fcm_environment(self.environment)
        self.path.chmod(0o600)
        self.path.write_bytes(b"x" * 32769)
        with self.assertRaises(RuntimeError):
            worker_fcm_environment(self.environment)
        with self.assertRaises(RuntimeError):
            worker_fcm_environment(self.environment | {"HRIS_TEST_FCM_CREDENTIALS_FILE": self.directory.name})


if __name__ == "__main__":
    unittest.main()
