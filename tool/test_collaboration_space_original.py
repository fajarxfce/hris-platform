"""Preserve original data on failure; reject changed processes and unverified SDK artifacts."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch
import zipfile

from collaboration_native_device import PACKAGE
from collaboration_space_original import OriginalDeliveryExperiment, verified_artifact, checkpoint, delivery_diagnostic, lost_receipt_checkpoint


class OriginalDeliveryOwnershipTest(unittest.TestCase):
    def experiment(self, installed=True):
        experiment = object.__new__(OriginalDeliveryExperiment)
        experiment.process = None
        experiment.result = {"passed": False}
        experiment.native = Mock(installed=installed, port=None, lock=Mock())
        return experiment

    def test_failed_experiment_preserves_package_and_releases_ownership(self):
        experiment = self.experiment()
        self.assertTrue(experiment.cleanup())
        experiment.native.command.assert_called_once_with("shell", "am", "force-stop", PACKAGE)
        experiment.native.cleanup.assert_not_called()
        experiment.native.lock.close.assert_called_once()
        self.assertTrue(experiment.result["retainedProofPackage"])

    def test_failed_stop_still_disconnects_and_releases_without_uninstalling(self):
        experiment = self.experiment()
        experiment.native.port = 45678
        experiment.native.command.side_effect = subprocess.TimeoutExpired("owned", 15)
        with patch("collaboration_space_original.disconnect_owned_gateway") as disconnect:
            self.assertFalse(experiment.cleanup())
            disconnect.assert_called_once_with(experiment.native)
        experiment.native.lock.close.assert_called_once()
        experiment.native.cleanup.assert_not_called()

    def test_unowned_package_is_never_stopped_or_removed(self):
        experiment = self.experiment(installed=False)
        self.assertTrue(experiment.cleanup())
        experiment.native.command.assert_not_called()
        experiment.native.cleanup.assert_not_called()
        self.assertFalse(experiment.result["retainedProofPackage"])

    def test_process_replacement_is_not_killed(self):
        experiment = self.experiment()
        experiment.native.process_id.return_value = 122
        with self.assertRaises(RuntimeError):
            experiment.kill_owned({"processId": 121})
        experiment.native.command.assert_not_called()

    def test_checkpoint_published_between_poll_and_completion_is_read_before_failure(self):
        experiment = self.experiment()
        checkpoint = {"processId": 121}
        experiment.app_json = Mock(side_effect=[None, {"complete": True}, checkpoint])
        self.assertEqual(checkpoint, experiment.wait_app("staged"))
        self.assertEqual([("original-staged",), ("instrumentation",), ("original-staged",)],
                         [call.args for call in experiment.app_json.call_args_list])

    def test_delivery_diagnostic_rejects_payload_fields_and_invalid_counts(self):
        value = {"reason": "UNRESOLVED", "confirmed": 1, "unresolved": 1,
                 "receiptCount": 1, "states": ["SENT", "STAGED"], "elapsedMillis": 15}
        self.assertEqual(value, delivery_diagnostic(value))
        self.assertIsNot(value["states"], delivery_diagnostic(value)["states"])
        for changes in ({"text": "never expose"}, {"confirmed": True}, {"unresolved": 3},
                        {"reason": "unsafe"}, {"reason": []}, {"states": [{}, "SENT"]}, {"states": ["SENT"]}, {"elapsedMillis": -1}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                delivery_diagnostic(value | changes)

    def test_failed_recovery_retains_only_validated_outcome_and_still_fails(self):
        experiment = self.experiment()
        value = {"reason": "STOPPED", "confirmed": 1, "unresolved": 1,
                 "receiptCount": 1, "states": ["SENT", "STAGED"], "elapsedMillis": 16}
        experiment.app_json = Mock(side_effect=[None, {"failureCategory": "IllegalStateException"}, None, value])
        with self.assertRaises(RuntimeError):
            experiment.wait_app("recovered")
        self.assertEqual(value, experiment.result["delivery"])
        self.assertFalse(experiment.result["passed"])

    def test_boolean_and_out_of_range_pids_are_rejected(self):
        for pid in (True, False, 0, -1, 2147483648, "121"):
            with self.subTest(pid=pid), self.assertRaises(ValueError):
                checkpoint({"processId": pid})

    def test_lost_callback_requires_missing_receipt_and_empty_sdk_before_death(self):
        value = {"processId": 121, "receiptCount": 1, "queuedOriginalCount": 0,
                 "interruptedAfterSdkRemoval": True, "journalDurable": True,
                 "projectionNotWritten": True}
        self.assertEqual(value, lost_receipt_checkpoint(value))
        for changed in ({"receiptCount": 2}, {"receiptCount": True}, {"queuedOriginalCount": 1},
                        {"queuedOriginalCount": False}, {"interruptedAfterSdkRemoval": False},
                        {"journalDurable": False}, {"projectionNotWritten": False}):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                lost_receipt_checkpoint(value | changed)

    def test_original_interruption_releases_first_ack_then_requires_lost_second_callback(self):
        with tempfile.TemporaryDirectory() as directory:
            experiment = self.experiment()
            experiment.directory = Path(directory)
            first = {"processId": 121, "receiptCount": 1}
            ack = first | {"queuedOriginalCount": 0, "interruptedAfterSdkRemoval": True,
                           "journalDurable": True, "projectionNotWritten": True}
            experiment.wait_app = Mock(side_effect=[first, ack])
            self.assertEqual(ack, experiment.await_interruption_checkpoint())
            self.assertEqual(["first-ack", "ack"], [c.args[0] for c in experiment.wait_app.call_args_list])
            self.assertTrue((Path(directory) / "first-ack.json").is_file())
            self.assertTrue(experiment.result["lostCallbackAfterSdkRemoval"])

    def test_wrong_missing_duplicate_or_truncated_native_library_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "proof.apk"
            for names in ([], ["lib/arm64-v8a/libmatrix_sdk_ffi.so"],
                          ["lib/x86_64/libmatrix_sdk_ffi.so"],
                          ["lib/x86_64/libmatrix_sdk_ffi.so", "lib/x86/libmatrix_sdk_ffi.so"]):
                with self.subTest(names=names):
                    with zipfile.ZipFile(path, "w") as archive:
                        archive.writestr("AndroidManifest.xml", b"fictional")
                        for name in names:
                            archive.writestr(name, b"unverified")
                    with self.assertRaises(ValueError):
                        verified_artifact(path)


if __name__ == "__main__":
    unittest.main()
