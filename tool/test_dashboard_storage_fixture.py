import json
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from dashboard_storage_fixture import DashboardStorageFixture


class DockerFixture:
    """Controlled process failures; the browser suite exercises actual Garage I/O."""
    def __init__(self):
        self.owned = set()
        self.commands = []
        self.creation_failure = None
        self.alive = True
        self.binding = "127.0.0.1:19000"
        self.removal_failure = False

    def run(self, arguments, **options):
        assert 0 < options["timeout"] <= 60
        self.commands.append(arguments)
        operation = arguments[1]
        result = SimpleNamespace(returncode=0, stdout="", stderr="")
        if operation == "run":
            self.owned.add(arguments[arguments.index("--name") + 1])
            if self.creation_failure:
                raise self.creation_failure
            result.stdout = "a" * 64
        elif operation == "exec":
            result.returncode = 0 if self.alive else 1
            result.stdout = "hris-browser-exports" if self.alive else ""
        elif operation == "inspect":
            result.stdout = "true" if self.alive else "false"
        elif operation == "port":
            result.stdout = self.binding
        elif operation == "rm":
            if self.removal_failure:
                result.returncode, result.stderr = 1, "Fixture daemon unavailable"
            else:
                self.owned.discard(arguments[-1])
        else:
            raise AssertionError("Unexpected process operation")
        return result


class StorageFixtureTest(unittest.TestCase):
    def test_owns_private_credentials_and_disposes_once(self):
        docker = DockerFixture()
        with tempfile.TemporaryDirectory() as directory, patch(
            "dashboard_storage_fixture.subprocess.run", side_effect=docker.run
        ):
            folder = Path(directory)
            fixture = DashboardStorageFixture(folder, folder, ["docker"])
            self.assertEqual(1, len(docker.owned))
            self.assertEqual(0o600, fixture.environment_file.stat().st_mode & 0o777)
            self.assertEqual({"endpoint": "http://127.0.0.1:19000", "bucket": fixture.bucket},
                             json.loads(fixture.metadata_file.read_text()))
            fixture.close()
            fixture.close()
            self.assertEqual(set(), docker.owned)
            self.assertFalse(fixture.environment_file.exists())
            self.assertFalse(fixture.metadata_file.exists())
            self.assertEqual(1, sum(command[1] == "rm" for command in docker.commands))

    def test_lost_creation_response_and_interruption_still_remove_owned_container(self):
        for failure in (subprocess.TimeoutExpired(["docker", "run"], 60), KeyboardInterrupt()):
            with self.subTest(failure=type(failure).__name__), tempfile.TemporaryDirectory() as directory:
                folder = Path(directory)
                docker = DockerFixture()
                docker.creation_failure = failure
                with patch("dashboard_storage_fixture.subprocess.run", side_effect=docker.run):
                    with self.assertRaises(type(failure)):
                        DashboardStorageFixture(folder, folder, ["docker"])
                self.assertEqual(set(), docker.owned)
                self.assertEqual([], list(folder.iterdir()))

    def test_exited_container_and_non_loopback_binding_cannot_publish_metadata(self):
        for alive, binding in ((False, "127.0.0.1:19000"), (True, "0.0.0.0:19000")):
            with self.subTest(alive=alive, binding=binding), tempfile.TemporaryDirectory() as directory:
                docker = DockerFixture()
                docker.alive, docker.binding = alive, binding
                folder = Path(directory)
                with patch("dashboard_storage_fixture.subprocess.run", side_effect=docker.run):
                    with self.assertRaises(RuntimeError):
                        DashboardStorageFixture(folder, folder, ["docker"])
                self.assertEqual(set(), docker.owned)
                self.assertEqual([], list(folder.iterdir()))

    def test_startup_deadline_cleans_resources(self):
        docker = DockerFixture()
        with tempfile.TemporaryDirectory() as directory, patch(
            "dashboard_storage_fixture.subprocess.run", side_effect=docker.run
        ), patch("dashboard_storage_fixture.time.monotonic", side_effect=[0, 61]):
            folder = Path(directory)
            with self.assertRaisesRegex(RuntimeError, "startup timed out"):
                DashboardStorageFixture(folder, folder, ["docker"])
            self.assertEqual(set(), docker.owned)
            self.assertEqual([], list(folder.iterdir()))

    def test_failed_removal_erases_credentials_and_retains_owner_for_retry(self):
        docker = DockerFixture()
        with tempfile.TemporaryDirectory() as directory, patch(
            "dashboard_storage_fixture.subprocess.run", side_effect=docker.run
        ):
            folder = Path(directory)
            fixture = DashboardStorageFixture(folder, folder, ["docker"])
            docker.removal_failure = True
            with self.assertRaisesRegex(RuntimeError, "cleanup failed"):
                fixture.close()
            self.assertEqual({fixture.container}, docker.owned)
            self.assertEqual([], list(folder.iterdir()))
            docker.removal_failure = False
            fixture.close()
            self.assertEqual(set(), docker.owned)


if __name__ == "__main__":
    unittest.main()
