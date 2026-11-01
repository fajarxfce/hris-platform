"""Owned HRIS SDK driver: bounded evidence, cancellation and cleanup after failures."""
import json
from pathlib import Path
import subprocess
import tempfile
import time
import unittest
from unittest.mock import MagicMock, patch

from collaboration_hris_native import app_write_script, checkpoint_evidence, private_json, proof_evidence, read_json, run, stop_process


class HrisNativeDriverTest(unittest.TestCase):
    def evidence(self):
        return {"encryptedBothDirections": True, "logicalIdsPreserved": True, "syncMode": "classic",
                "protectedStoreReopened": True, "missingRecordPreserved": True,
                "wrongPassphrasePreserved": True, "exclusiveOwner": True,
                "queueRestored": True, "transactionReused": True, "identityAfterRestart": True,
                "messageIntentRestored": True, "queueMappingRecovered": True, "queueWriterExclusive": True, "perRoomDeliveryIsolated": True, "heldQueueRestored": True, "nativeDeliveryOwned": True, "commonSenderOwned": True, "receiptJournalRecovered": True, "projectionInterrupted": True,
                "originalHistoryWindowOwned": True, "historyBodiesRestored": True,
                "commonHistoryAcquisitionOwned": True,
                "latestHistoryHeadOwned": True,
                "historyWindow": {"originals": 2, "unavailable": 0, "extractionNanos": 100, "modelCharacters": 100, "originalCharacters": 100},
                "senderEventId": "$first", "recipientEventId": "$second",
                "senderTransaction": "first-transaction", "recipientTransaction": "second-transaction"}

    def test_receipts_are_bounded_and_unknown_payloads_are_not_retained(self):
        proof = self.evidence()
        self.assertEqual(proof, proof_evidence(proof | {"body": "private-fixture", "token": "private-fixture"}))
        for name, value in (("senderEventId", "$bad\n"), ("senderEventId", "$" * 256),
                            ("senderTransaction", "raw body"), ("syncMode", "native"),
                            ("recipientEventId", "$first"), ("logicalIdsPreserved", 1),
                            ("protectedStoreReopened", 1), ("missingRecordPreserved", False),
                            ("wrongPassphrasePreserved", None), ("exclusiveOwner", "true"),
                            ("messageIntentRestored", False), ("queueMappingRecovered", 1), ("queueWriterExclusive", None), ("perRoomDeliveryIsolated", False),
                            ("heldQueueRestored", 1), ("nativeDeliveryOwned", False),
                            ("commonSenderOwned", None), ("receiptJournalRecovered", False), ("projectionInterrupted", 1),
                            ("originalHistoryWindowOwned", False), ("historyBodiesRestored", 1), ("historyWindow", None),
                            ("commonHistoryAcquisitionOwned", 1), ("latestHistoryHeadOwned", 1)):
            with self.subTest(name=name, value=value), self.assertRaises(ValueError):
                proof_evidence(proof | {name: value})

    def test_history_metrics_reject_wrong_types_and_bounds_and_drop_unknown_payloads(self):
        proof = self.evidence()
        metrics = proof["historyWindow"]
        self.assertEqual(proof, proof_evidence(proof | {"historyWindow": metrics | {"body": "private-fixture"}}))
        for name, value in (("originals", 1), ("originals", True), ("unavailable", 129),
                            ("extractionNanos", -1), ("extractionNanos", 5_000_000_001),
                            ("modelCharacters", 4_194_305), ("originalCharacters", 0)):
            with self.subTest(name=name), self.assertRaises(ValueError):
                proof_evidence(proof | {"historyWindow": metrics | {name: value}})

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

    def test_app_signal_is_invisible_until_input_finishes_and_cannot_replace_a_signal(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            directory = root / "files/protocol-proof"
            directory.mkdir(parents=True)
            target = directory / "gateway-send.json"
            temporary = target.with_suffix(".json.next")
            process = subprocess.Popen(["sh", "-c", app_write_script("gateway-send")],
                                       cwd=root, stdin=subprocess.PIPE,
                                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            try:
                process.stdin.write(b'{"ready":')
                process.stdin.flush()
                until = time.monotonic() + 5
                while not temporary.exists() or temporary.stat().st_size == 0:
                    self.assertLess(time.monotonic(), until)
                    time.sleep(0.01)
                self.assertFalse(target.exists())
                process.stdin.write(b'true}')
                process.stdin.close()
                self.assertEqual(0, process.wait(timeout=5))
                self.assertEqual({"ready": True}, json.loads(target.read_text()))
                self.assertEqual(0o600, target.stat().st_mode & 0o777)
                self.assertFalse(temporary.exists())
                repeated = subprocess.run(["sh", "-c", app_write_script("gateway-send")],
                                          cwd=root, input=b'{"ready":false}',
                                          capture_output=True, timeout=5)
                self.assertNotEqual(0, repeated.returncode)
                self.assertEqual({"ready": True}, json.loads(target.read_text()))
            finally:
                if process.stdin and not process.stdin.closed:
                    process.stdin.close()
                stop_process(process)

    def test_app_signal_names_cannot_change_the_owned_shell_command(self):
        for name in ("../signal", "signal;id", "signal\n", "$(id)", "x" * 65, None):
            with self.subTest(name=name), self.assertRaises(ValueError):
                app_write_script(name)

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
                patch("collaboration_hris_native.time.monotonic", side_effect=patches.get("clock", [0, 1, 2, 231])):
            value = run("owned.apk", "sdk", root)
            factory.assert_called_once_with("owned.apk", "emulator-5560", "sdk", root)
            native.cleanup.assert_called_once()
            return value, read_json(root / "result.json")

    def handshake(self, root, native, restored=None):
        private_json(root / "send.json", {"ready": True})
        private_json(root / "resume.json", {"ready": True})
        native.process_id.side_effect = [101, None, 202]
        reads = 0
        def command(*arguments):
            nonlocal reads
            if arguments[:3] == ("shell", "am", "force-stop"):
                return ""
            name = Path(arguments[-1]).stem
            if name == "instrumentation":
                reads += 1
                if reads == 1:
                    raise RuntimeError("No checkpoint yet")
                return json.dumps({"complete": True})
            if name == "gateway-ready":
                return json.dumps({"ready": True})
            if name == "gateway-staged":
                return json.dumps(self.evidence() | {"transactionId": "first-transaction", "processId": 101,
                    "senderKeyFingerprint": "a" * 64, "recipientKeyFingerprint": "b" * 64})
            if name == "gateway-restored":
                return json.dumps(restored or {"transactionId": "first-transaction", "processId": 202})
            if name == "gateway-result":
                return json.dumps(self.evidence() | {"processId": 202})
            raise AssertionError("Unexpected owned IPC read")
        native.command.side_effect = command

    def test_queue_checkpoints_reject_ambiguous_processes_and_discard_unknown_payloads(self):
        value = self.evidence() | {"transactionId": "retained", "processId": 123,
                                  "senderKeyFingerprint": "a" * 64, "recipientKeyFingerprint": "b" * 64}
        self.assertEqual({"transactionId": "retained", "processId": 123},
                         checkpoint_evidence(value | {"token": "private-fixture"}, staged=True))
        for name, wrong in (("processId", True), ("processId", 0), ("processId", 2147483648),
                            ("transactionId", "x" * 129), ("protectedStoreReopened", 1),
                            ("senderKeyFingerprint", "private-fixture")):
            with self.subTest(name=name), self.assertRaises(ValueError):
                checkpoint_evidence(value | {name: wrong}, staged=True)

    def test_reused_process_or_changed_transaction_cannot_release_delivery(self):
        for restored in ({"transactionId": "first-transaction", "processId": 101},
                         {"transactionId": "replacement-transaction", "processId": 202}):
            with self.subTest(restored=restored), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                native, process = self.fixture(root)
                self.handshake(root, native, restored=restored)
                value, result = self.execute(root, native, process)
                self.assertFalse(value)
                self.assertTrue(result["cleanup"])
                self.assertFalse((root / "native-restored.json").exists())

    def test_early_completion_cannot_claim_a_process_restart(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            native, process = self.fixture(root)
            native.command.return_value = json.dumps({"complete": True})
            value, result = self.execute(root, native, process)
            self.assertFalse(value)
            self.assertTrue(result["cleanup"])
            native.process_id.assert_not_called()

    def test_a_still_running_old_process_cannot_be_replaced_by_a_second_instrumentation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            native, process = self.fixture(root)
            self.handshake(root, native)
            native.process_id.side_effect = [101, 101]
            value, result = self.execute(root, native, process)
            self.assertFalse(value)
            self.assertTrue(result["cleanup"])
            self.assertFalse((root / "native-staged.json").exists())

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
                self.handshake(root, native)
                value, result = self.execute(root, native, process)
                self.assertEqual(cleaned, value)
                self.assertEqual(cleaned, result["cleanup"])
                self.assertTrue(result["passed"])
                self.assertEqual(self.evidence() | {"processRestarted": True}, result["proof"])
                self.assertEqual(3, native.process_id.call_count)
                self.assertEqual(2, process.terminate.call_count)

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
            value, result = self.execute(root, native, process, clock=[0, 1, 231])
            self.assertFalse(value)
            self.assertEqual("TimeoutError", result["failureCategory"])
            self.assertTrue(result["cleanup"])
            self.assertEqual(2, native.command.call_count)

    def test_unkillable_child_does_not_skip_package_and_reverse_cleanup(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            native, process = self.fixture(root)
            self.handshake(root, native)
            process.wait.side_effect = subprocess.TimeoutExpired("owned-adb", 5)
            value, result = self.execute(root, native, process)
            self.assertFalse(value)
            self.assertFalse(result["cleanup"])
            process.kill.assert_called()

    def test_stop_without_child_is_complete(self):
        self.assertTrue(stop_process(None))


if __name__ == "__main__":
    unittest.main()
