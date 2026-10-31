"""Protect owned cleanup and secret-safe diagnostics in the real-provider driver."""
import json
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import check_collaboration_providers as proof


class ProviderDriverTest(unittest.TestCase):
    def test_unowned_or_unknown_native_sync_mode_is_rejected_before_any_fixture_allocation(self):
        with patch.object(proof.Path, "mkdir") as mkdir:
            for mode in ("classic", "unknown"):
                with self.assertRaises(ValueError):
                    proof.ProviderProof(native_sync_mode=mode)
            mkdir.assert_not_called()

    def fixture(self, directory):
        run = object.__new__(proof.ProviderProof)
        run.run_id = "owned"
        run.directory = Path(directory)
        run.containers = ["first", "second"]
        run.network = "owned-network"
        run.record = {}
        return run

    def test_private_fixture_does_not_inherit_world_readable_mode(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.json"
            proof.write_private(path, {"token": "fictional-sensitive-value"})
            self.assertEqual(0o600, stat.S_IMODE(path.stat().st_mode))
            self.assertEqual({"token": "fictional-sensitive-value"}, json.loads(path.read_text()))

    def test_failed_command_does_not_publish_arguments_or_provider_stderr(self):
        result = subprocess.CompletedProcess([], 1, "", "credential=private-fixture")
        with patch.object(proof.subprocess, "run", return_value=result), patch.object(proof, "DIAGNOSTICS", None):
            with self.assertRaises(RuntimeError) as failure:
                proof.command("docker", "run", "--env", "SECRET=private-fixture")
        self.assertNotIn("private-fixture", str(failure.exception))
        self.assertNotIn("SECRET", str(failure.exception))

    def test_sudo_applies_only_to_docker_and_keeps_workspace_tools_unprivileged(self):
        result = subprocess.CompletedProcess([], 0, "ok", "")
        with patch.object(proof, "DOCKER_COMMAND", ["sudo", "-n", "docker"]), patch.object(proof.subprocess, "run", return_value=result) as run:
            proof.command("docker", "inspect", "owned")
            self.assertEqual(("sudo", "-n", "docker", "inspect", "owned"), run.call_args.args[0])
            proof.command("npm", "test")
            self.assertEqual(("npm", "test"), run.call_args.args[0])

    def test_cleanup_never_removes_mismatched_or_unlabelled_resources(self):
        with tempfile.TemporaryDirectory() as directory:
            fixture = self.fixture(directory)
            def inspect(*args, **kwargs):
                if args[:2] == ("docker", "inspect"):
                    return json.dumps([{"Config": {"Labels": None if args[2] == "first" else {"hris.collaboration-proof": "someone-else"}}}])
                if args[:3] == ("docker", "network", "inspect"):
                    return json.dumps([{"Labels": {"hris.collaboration-proof": "someone-else"}}])
                self.fail("Cleanup attempted a mutation of unowned resources")
            with patch.object(proof, "command", side_effect=inspect):
                fixture.cleanup()
            self.assertFalse(fixture.record["cleanup"])

    def test_log_failure_does_not_prevent_removal_of_owned_containers(self):
        with tempfile.TemporaryDirectory() as directory:
            fixture = self.fixture(directory)
            mutations = []
            def command(*args, **kwargs):
                if args[:2] == ("docker", "inspect"):
                    return json.dumps([{"Config": {"Labels": {"hris.collaboration-proof": "owned"}}}])
                if args[:3] == ("docker", "network", "inspect"):
                    return json.dumps([{"Labels": {"hris.collaboration-proof": "owned"}}])
                mutations.append(args)
                return ""
            with patch.object(proof, "command", side_effect=command), patch.object(proof.subprocess, "run", side_effect=OSError("log unavailable")):
                fixture.cleanup()
            self.assertTrue(fixture.record["cleanup"])
            self.assertEqual([
                ("docker", "rm", "--force", "second"),
                ("docker", "rm", "--force", "first"),
                ("docker", "network", "rm", "owned-network"),
            ], mutations)

    def test_one_removal_failure_still_attempts_other_owned_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            fixture = self.fixture(directory)
            mutations = []
            def command(*args, **kwargs):
                if args[:2] == ("docker", "inspect"):
                    return json.dumps([{"Config": {"Labels": {"hris.collaboration-proof": "owned"}}}])
                if args[:3] == ("docker", "network", "inspect"):
                    return json.dumps([{"Labels": {"hris.collaboration-proof": "owned"}}])
                mutations.append(args)
                if args[-1] == "second":
                    raise RuntimeError("fixture removal failed")
                return ""
            with patch.object(proof, "command", side_effect=command), patch.object(proof.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, "", "")):
                fixture.cleanup()
            self.assertFalse(fixture.record["cleanup"])
            self.assertIn(("docker", "rm", "--force", "first"), mutations)
            self.assertIn(("docker", "network", "rm", "owned-network"), mutations)


if __name__ == "__main__":
    unittest.main()
