"""Owned HRIS SDK driver: bounded evidence, cancellation and cleanup after failures."""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import MagicMock, patch

from collaboration_hris_native import private_json, proof_evidence, read_json, run, stop_process


class HrisNativeDriverTest(unittest.TestCase):
    def evidence(self):
        return {"encryptedBothDirections": True, "logicalIdsPreserved": True, "syncMode": "classic",
                "protectedStoreReopened": True, "missingRecordPreserved": True,
                "wrongPassphrasePreserved": True, "exclusiveOwner": True,
                "senderEventId": "$first", "recipientEventId": "$second",
                "senderTransaction": "first-transaction", "recipientTransaction": "second-transaction"}

    def test_receipts_are_bounded_and_unknown_payloads_are_not_retained(self):
        proof = self.evidence()
        self.assertEqual(proof, proof_evidence(proof | {"body": "private-fixture", "token": "private-fixture"}))
        for name, value in (("senderEventId", "$bad\n"), ("senderEventId", "$" * 256),
                            ("senderTransaction", "raw body"), ("syncMode", "native"),
                            ("recipientEventId", "$first"), ("logicalIdsPreserved", 1),
                            ("protectedStoreReopened", 1), ("missingRecordPreserved", False),
                            ("wrongPassphrasePreserved", None), ("exclusiveOwner", "true")):
            with self.subTest(name=name, value=value), self.assertRaises(ValueError):
                proof_evidence(proof | {name: value})

    def test_private_ipc_is_atomic_private_and_never_overwrites_old_evidence(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "result.json"
            private_json(path, {"passed": True})
            self.assertEqual(0o600, path.stat().st_mode & 0o777)
            with self.assertRaises(FileExistsError):
                private_json(path, {"passed": False})
            self.assertEqual({"passed": True}, read_json(path))
            self.assertFalse(path.with_suffix(".json.next").exists())
            path.write_bytes(b"x" * 65537)
            with self.assertRaises(ValueError):
                read_json(path)

    def fixture(self, root, base="http://127.0.0.1:43210"):
        private_json(root / "fixture.json", {"baseUrl": base})
        native = MagicMock(adb="owned-adb", serial="emulator-5560")
        native.cleanup.return_value = True
        native.bind.return_value = {"apkSha256": "fictional"}
        process = MagicMock()
        process.poll.return_value = None
        return native, process

    def execute(self, root, native, process, **patches):
        with patch("collaboration_hris_native.NativeDevice", return_value=native) as factory, \
                patch("collaboration_hris_native.subprocess.Popen", return_value=process), \
                patch("collaboration_hris_native.subprocess.run", return_value=subprocess.CompletedProcess([], 0)), \
                patch("collaboration_hris_native.time.sleep"), \
                patch("collaboration_hris_native.time.monotonic", side_effect=patches.get("clock", [0, 1, 116])):
            value = run("owned.apk", "sdk", root)
            factory.assert_called_once_with("owned.apk", "emulator-5560", "sdk", root)
            native.cleanup.assert_called_once()
            return value, read_json(root / "result.json")

    def test_non_loopback_servers_are_rejected_before_emulator_access(self):
        for base in ("https://127.0.0.1:43210", "http://10.77.77.3:5555", "http://127.0.0.1:80",
                     "http://user@127.0.0.1:43210", "http://127.0.0.1:43210/path", "http://127.0.0.1:43210?token=value"):
            with self.subTest(base=base), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                self.fixture(root, base)
                with patch("collaboration_hris_native.NativeDevice") as native, self.assertRaises(ValueError):
                    run("owned.apk", "sdk", root)
                native.assert_not_called()

    def test_prepare_and_mapping_failure_still_release_owned_resources(self):
        for stage, error in (("prepare", RuntimeError("private-fixture")),
                             ("prepare", subprocess.CalledProcessError(1, "private-fixture")),
                             ("bind", InterruptedError("private-fixture"))):
            with self.subTest(stage=stage, category=type(error)), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                native, process = self.fixture(root)
                getattr(native, stage).side_effect = error
                value, result = self.execute(root, native, process)
                self.assertFalse(value)
                self.assertTrue(result["cleanup"])
                self.assertNotIn("private-fixture", json.dumps(result))
                self.assertEqual(type(error).__name__, result["failureCategory"])

    def test_success_requires_valid_receipts_and_completed_cleanup(self):
        for cleaned in (True, False):
            with self.subTest(cleaned=cleaned), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                native, process = self.fixture(root)
                native.cleanup.return_value = cleaned
                native.command.side_effect = [json.dumps({"complete": True}), json.dumps(self.evidence())]
                value, result = self.execute(root, native, process)
                self.assertEqual(cleaned, value)
                self.assertEqual(cleaned, result["cleanup"])
                self.assertTrue(result["passed"])
                self.assertEqual(self.evidence(), result["proof"])
                process.terminate.assert_called_once()

    def test_instrumentation_failure_and_cancellation_preserve_safe_evidence(self):
        for replies in ([json.dumps({"complete": False, "failureCategory": "IllegalStateException", "message": "private-fixture"})],
                        [InterruptedError("private-fixture")],
                        [json.dumps({"complete": True}), json.dumps({"body": "private-fixture"})]):
            with self.subTest(replies=replies), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                native, process = self.fixture(root)
                native.command.side_effect = replies
                value, result = self.execute(root, native, process)
                self.assertFalse(value)
                self.assertTrue(result["cleanup"])
                self.assertNotIn("private-fixture", json.dumps(result))

    def test_partial_ipc_and_poll_timeout_terminate_without_unbounded_retry(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            native, process = self.fixture(root)
            native.command.return_value = "{"
            value, result = self.execute(root, native, process)
            self.assertFalse(value)
            self.assertEqual("TimeoutError", result["failureCategory"])
            self.assertTrue(result["cleanup"])
            self.assertEqual(2, native.command.call_count)

    def test_unkillable_child_does_not_skip_package_and_reverse_cleanup(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            native, process = self.fixture(root)
            native.command.side_effect = [json.dumps({"complete": True}), json.dumps(self.evidence())]
            process.wait.side_effect = subprocess.TimeoutExpired("owned-adb", 5)
            value, result = self.execute(root, native, process)
            self.assertFalse(value)
            self.assertFalse(result["cleanup"])
            process.kill.assert_called_once()
            self.assertEqual(2, process.wait.call_count)

    def test_stop_without_child_is_complete(self):
        self.assertTrue(stop_process(None))


if __name__ == "__main__":
    unittest.main()
