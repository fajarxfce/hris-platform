import io
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest

from database_recovery import RecoveryError
from recovery_archive import capture_file, inspect_archive, restore_archive


class RecoveryArchiveTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.archive = self.directory / "archive.tar"

    def archive_with(self, entries):
        with tarfile.open(self.archive, "w") as archive:
            for name, kind, body in entries:
                entry = tarfile.TarInfo(name)
                entry.type = kind
                entry.size = len(body)
                if kind in (tarfile.SYMTYPE, tarfile.LNKTYPE):
                    entry.linkname = "../outside"
                archive.addfile(entry, io.BytesIO(body))

    def test_restore_preserves_nested_bytes_and_empty_files_without_archived_ownership(self):
        self.archive_with([(".", tarfile.DIRTYPE, b""), ("nested/value", tarfile.REGTYPE, b"private bytes"), ("empty", tarfile.REGTYPE, b"")])
        target = self.directory / "restored"
        restore_archive(self.archive, target, limit=1024)
        self.assertEqual(b"private bytes", (target / "nested/value").read_bytes())
        self.assertEqual(b"", (target / "empty").read_bytes())
        self.assertEqual(0o600, (target / "nested/value").stat().st_mode & 0o777)
        self.assertEqual(0o700, target.stat().st_mode & 0o777)

    def test_links_devices_traversal_duplicate_and_oversized_entries_fail_before_extraction(self):
        cases = [
            [("../outside", tarfile.REGTYPE, b"x")], [("/outside", tarfile.REGTYPE, b"x")],
            [("link", tarfile.SYMTYPE, b"")], [("link", tarfile.LNKTYPE, b"")],
            [("device", tarfile.CHRTYPE, b"")], [("large", tarfile.REGTYPE, b"x" * 1025)],
            [("item", tarfile.REGTYPE, b"x"), ("./item", tarfile.REGTYPE, b"x")],
        ]
        for entries in cases:
            with self.subTest(entries=entries[:1]):
                self.archive_with(entries)
                target = self.directory / "must-not-exist"
                with self.assertRaises(RecoveryError):
                    restore_archive(self.archive, target, limit=1024)
                self.assertFalse(target.exists())

    def test_large_pax_header_and_truncated_regular_payload_are_rejected(self):
        entry = tarfile.TarInfo("extension")
        entry.type = tarfile.XHDTYPE
        entry.size = 1024 * 1024
        self.archive.write_bytes(entry.tobuf())
        with self.assertRaises(RecoveryError):
            inspect_archive(self.archive, limit=2 * 1024 * 1024)
        entry.type = tarfile.REGTYPE
        self.archive.write_bytes(entry.tobuf())
        with self.assertRaises(RecoveryError):
            inspect_archive(self.archive, limit=2 * 1024 * 1024)

    def test_stream_limit_and_deadline_remove_partial_files_and_join_the_child(self):
        for program, size, timeout in [
            ("import sys; sys.stdout.buffer.write(b'x'*10000)", 5, 5),
            ("import time; time.sleep(30)", 100, 0.05),
            ("raise SystemExit(3)", 100, 5),
        ]:
            with self.subTest(program=program), (self.directory / "log").open("wb") as log:
                destination = self.directory / "stream"
                with self.assertRaises(RecoveryError):
                    capture_file([sys.executable, "-c", program], destination, log, limit=size, timeout=timeout)
                self.assertFalse(destination.exists())

    def test_existing_stream_output_is_never_removed_or_overwritten(self):
        destination = self.directory / "stream"
        destination.write_bytes(b"original")
        with (self.directory / "log").open("wb") as log:
            with self.assertRaises(RecoveryError):
                capture_file([sys.executable, "-c", "print('replacement')"], destination, log, limit=100, timeout=5)
        self.assertEqual(b"original", destination.read_bytes())
