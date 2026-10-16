import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import database_recovery as recovery

ROLE_SQL = """CREATE ROLE hris_migrator;
ALTER ROLE hris_migrator WITH SUPERUSER LOGIN;
CREATE ROLE hris_app;
ALTER ROLE hris_app WITH NOSUPERUSER NOBYPASSRLS;
CREATE ROLE hris_worker_capability;
CREATE ROLE hris_worker;
GRANT hris_worker_capability TO hris_worker WITH INHERIT TRUE GRANTED BY hris_migrator;
"""


def completed(stdout=b"", code=0):
    return subprocess.CompletedProcess([], code, stdout=stdout)


class BackupFilesTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name) / "backup"

    def make_backup(self):
        self.directory.mkdir(mode=0o700)
        for name, content in (("database.dump", b"PGDMP fixture"), ("roles.sql", ROLE_SQL.encode())):
            with recovery.create_file(self.directory / name) as output:
                output.write(content)
        manifest = {
            "format": 1, "database": "hris", "bootstrapRole": "hris_migrator",
            "postgresImage": "postgres:18.6-alpine", "rolePasswordsIncluded": False,
            "files": {name: recovery.file_record(self.directory / name) for name in ("database.dump", "roles.sql")},
        }
        recovery.write_json(self.directory / "manifest.json", manifest)
        return manifest

    def test_private_complete_backup_is_accepted_without_starting_docker(self):
        expected = self.make_backup()
        directory, actual = recovery.load_backup(self.directory)
        self.assertEqual(expected, actual)
        self.assertEqual(self.directory, directory)
        for path in self.directory.iterdir():
            self.assertEqual(0o600, path.stat().st_mode & 0o777)

    def test_tampered_archive_stops_before_any_docker_operation(self):
        self.make_backup()
        (self.directory / "database.dump").write_bytes(b"Changed fixture")
        with patch.object(recovery, "command") as command:
            with self.assertRaisesRegex(recovery.RecoveryError, "checksum"):
                recovery.verify_backup(self.directory)
            command.assert_not_called()

    def test_group_readable_directory_is_rejected(self):
        self.make_backup()
        self.directory.chmod(0o750)
        with self.assertRaisesRegex(recovery.RecoveryError, "0700"):
            recovery.load_backup(self.directory)

    def test_group_readable_archive_is_rejected(self):
        self.make_backup()
        (self.directory / "database.dump").chmod(0o640)
        with self.assertRaisesRegex(recovery.RecoveryError, "private backup file"):
            recovery.load_backup(self.directory)

    def test_symlink_directory_is_rejected(self):
        self.make_backup()
        link = self.directory.parent / "linked"
        link.symlink_to(self.directory, target_is_directory=True)
        with self.assertRaisesRegex(recovery.RecoveryError, "symbolic link"):
            recovery.load_backup(link)

    def test_symlink_archive_is_rejected(self):
        self.make_backup()
        archive = self.directory / "database.dump"
        archive.unlink()
        archive.symlink_to(self.directory / "roles.sql")
        with self.assertRaisesRegex(recovery.RecoveryError, "private backup file"):
            recovery.load_backup(self.directory)

    def test_backup_inside_repository_is_rejected_before_creation(self):
        with patch.object(recovery, "ROOT", Path(self.temporary.name)):
            with self.assertRaisesRegex(recovery.RecoveryError, "outside"):
                recovery.private_directory(self.directory, create=True)
        self.assertFalse(self.directory.exists())

    def test_existing_directory_is_never_overwritten(self):
        self.make_backup()
        before = (self.directory / "database.dump").read_bytes()
        with self.assertRaises(FileExistsError):
            recovery.backup_database(self.directory, [])
        self.assertEqual(before, (self.directory / "database.dump").read_bytes())

    def test_unknown_manifest_format_is_rejected(self):
        manifest = self.make_backup()
        for version in (True, 2, "1"):
            with self.subTest(version=version):
                manifest["format"] = version
                (self.directory / "manifest.json").write_text(json.dumps(manifest))
                with self.assertRaisesRegex(recovery.RecoveryError, "manifest"):
                    recovery.load_backup(self.directory)

    def test_unexpected_image_is_rejected(self):
        manifest = self.make_backup()
        manifest["postgresImage"] = "unrelated-image:latest"
        (self.directory / "manifest.json").write_text(json.dumps(manifest))
        with self.assertRaisesRegex(recovery.RecoveryError, "manifest"):
            recovery.load_backup(self.directory)

    def test_role_restore_retains_grantor_and_bootstrap_attributes(self):
        self.make_backup()
        sql = recovery.restored_role_sql(self.directory / "roles.sql")
        self.assertNotIn("CREATE ROLE hris_migrator;", sql)
        self.assertIn("ALTER ROLE hris_migrator WITH SUPERUSER LOGIN;", sql)
        self.assertIn("GRANTED BY hris_migrator;", sql)
        self.assertIn("CREATE ROLE hris_worker;", sql)

    def test_role_passwords_and_missing_or_duplicate_bootstrap_are_rejected(self):
        self.make_backup()
        for value in (ROLE_SQL + "ALTER ROLE hris_app PASSWORD 'fixture-only';",
                      ROLE_SQL.replace("CREATE ROLE hris_migrator;", ""),
                      ROLE_SQL + "CREATE ROLE hris_migrator;"):
            with self.subTest(value=value):
                (self.directory / "roles.sql").write_text(value)
                with self.assertRaisesRegex(recovery.RecoveryError, "role definitions"):
                    recovery.restored_role_sql(self.directory / "roles.sql")

    def test_cancelled_backup_removes_partial_payload_without_manifest(self):
        def interrupted(_args, _log, **kwargs):
            kwargs["stdout"].write(b"partial fixture")
            raise KeyboardInterrupt()

        with patch.object(recovery, "captured", side_effect=["a" * 64, "database", "postgres:18.6-alpine", "90"]):
            with patch.object(recovery, "command", side_effect=interrupted):
                with self.assertRaises(KeyboardInterrupt):
                    recovery.backup_database(self.directory, [Path("compose.yml")])
        self.assertFalse((self.directory / "database.partial").exists())
        self.assertFalse((self.directory / "manifest.json").exists())
        self.assertTrue((self.directory / "backup.log").exists())


class ResourceOwnershipTest(unittest.TestCase):
    def test_cleanup_removes_only_matching_labels_in_reverse_order(self):
        calls = []

        def command(args, _log, **_kwargs):
            calls.append(args)
            return completed(b"owned\n")

        with patch.object(recovery, "command", side_effect=command):
            recovery.cleanup_owned([("network", "network"), ("container", "database")], "owned", io.BytesIO())
        removals = [args for args in calls if args[2] == "rm"]
        self.assertEqual([["docker", "container", "rm", "--force", "database"],
                          ["docker", "network", "rm", "network"]], removals)

    def test_foreign_container_is_preserved_while_remaining_cleanup_runs(self):
        calls = []

        def command(args, _log, **_kwargs):
            calls.append(args)
            return completed(b"foreign" if args[-1] == "database" else b"owned")

        with patch.object(recovery, "command", side_effect=command):
            with self.assertRaisesRegex(recovery.RecoveryError, "another run"):
                recovery.cleanup_owned([("network", "network"), ("container", "database")], "owned", io.BytesIO())
        self.assertNotIn(["docker", "container", "rm", "--force", "database"], calls)
        self.assertIn(["docker", "network", "rm", "network"], calls)

    def test_failed_inspection_does_not_skip_other_resources(self):
        calls = []

        def command(args, _log, **_kwargs):
            calls.append(args)
            if args[-1] == "database":
                raise recovery.RecoveryError("unavailable")
            return completed(b"owned")

        with patch.object(recovery, "command", side_effect=command):
            with self.assertRaisesRegex(recovery.RecoveryError, "inspected"):
                recovery.cleanup_owned([("network", "network"), ("container", "database")], "owned", io.BytesIO())
        self.assertIn(["docker", "network", "rm", "network"], calls)

    def test_unavailable_daemon_is_not_reported_as_successful_cleanup(self):
        def command(args, _log, **_kwargs):
            if args[2] == "inspect":
                return completed(code=1)
            raise recovery.RecoveryError("daemon unavailable")

        with patch.object(recovery, "command", side_effect=command):
            with self.assertRaisesRegex(recovery.RecoveryError, "Could not confirm"):
                recovery.cleanup_owned([("container", "database")], "owned", io.BytesIO())

    def test_absent_resource_is_confirmed_without_removal(self):
        calls = []

        def command(args, _log, **_kwargs):
            calls.append(args)
            return completed(code=1 if args[2] == "inspect" else 0)

        with patch.object(recovery, "command", side_effect=command):
            recovery.cleanup_owned([("container", "database")], "owned", io.BytesIO())
        self.assertEqual(["inspect", "ls"], [args[2] for args in calls])

    def test_cancelled_owner_removes_resources_and_ephemeral_credentials(self):
        with tempfile.TemporaryDirectory() as directory:
            area = Path(directory)
            cleanup = []

            def removed(resources, run_id, _log):
                cleanup.extend(resources)
                self.assertTrue(all(name.endswith(run_id) for _kind, name in resources))

            with patch.object(recovery, "command", return_value=completed()):
                with patch.object(recovery, "captured", return_value="true"):
                    with patch.object(recovery, "cleanup_owned", side_effect=removed):
                        with self.assertRaises(KeyboardInterrupt):
                            with recovery.recovery_database(area, "postgres:18.6-alpine", io.BytesIO()) as fixture:
                                self.assertTrue(fixture[-1].exists())
                                self.assertEqual(0o600, fixture[-1].stat().st_mode & 0o777)
                                raise KeyboardInterrupt()
            self.assertEqual(["network", "container"], [kind for kind, _name in cleanup])
            self.assertEqual([], list(area.iterdir()))

    def test_ambiguous_creation_still_registers_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            cleanup = []

            def command(args, _log, **_kwargs):
                if args[1] == "run":
                    raise recovery.RecoveryError("creation response lost")
                return completed()

            with patch.object(recovery, "command", side_effect=command):
                with patch.object(recovery, "cleanup_owned", side_effect=lambda resources, *_: cleanup.extend(resources)):
                    with self.assertRaisesRegex(recovery.RecoveryError, "response lost"):
                        with recovery.recovery_database(Path(directory), "postgres:18.6-alpine", io.BytesIO()):
                            self.fail("The unconfirmed database must not be used")
            self.assertEqual(["network", "container"], [kind for kind, _name in cleanup])
            self.assertEqual([], list(Path(directory).iterdir()))

    def test_command_failure_does_not_expose_arguments_or_raw_error(self):
        args = ["tool", "fixture-private-value"]
        error = subprocess.TimeoutExpired(args, 30, stderr=b"raw private diagnostic")
        with patch.object(recovery.subprocess, "run", side_effect=error):
            with self.assertRaises(recovery.RecoveryError) as failure:
                recovery.command(args, io.BytesIO())
        self.assertNotIn("fixture-private", str(failure.exception))
        self.assertNotIn("raw private", str(failure.exception))


if __name__ == "__main__":
    unittest.main()
