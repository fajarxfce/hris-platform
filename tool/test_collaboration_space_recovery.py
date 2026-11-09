"""A process may be killed only after verified native-control, durable-wake and claim evidence."""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from collaboration_space_recovery import SpaceRecoveryExperiment
from collaboration_space_original import OriginalDeliveryExperiment


class SpaceRecoveryDriverTest(unittest.TestCase):
    def experiment(self, directory):
        value = object.__new__(SpaceRecoveryExperiment)
        value.directory = Path(directory)
        return value

    def test_valid_control_is_published_before_ack_becomes_eligible_for_interruption(self):
        with tempfile.TemporaryDirectory() as directory:
            experiment = self.experiment(directory)
            listening = {"processId": 42, "nativeSyncActive": True, "startupWakeCleared": True}
            control = {"processId": 42, "durableWake": True, "capturedReplayDeduplicated": True, "publicationRevision": 7}
            ack = {"processId": 42, "claimedBeforeDelivery": True}
            with patch.object(OriginalDeliveryExperiment, "wait_app", side_effect=[listening, control, ack]) as receive:
                self.assertEqual(ack, experiment.wait_app("ack"))
            self.assertEqual(["listening", "control", "ack"], [c.args[0] for c in receive.call_args_list])
            self.assertTrue((Path(directory) / "listening.json").is_file())
            self.assertTrue((Path(directory) / "control.json").is_file())

    def test_missing_startup_isolation_never_allows_ack_to_reach_kill_owner(self):
        with tempfile.TemporaryDirectory() as directory:
            experiment = self.experiment(directory)
            with patch.object(OriginalDeliveryExperiment, "wait_app", return_value={"nativeSyncActive": True}) as receive:
                with self.assertRaises(ValueError):
                    experiment.wait_app("ack")
            self.assertEqual(1, receive.call_count)
            self.assertFalse((Path(directory) / "listening.json").exists())

    def test_invalid_or_unacknowledged_control_never_allows_durable_ack_read(self):
        listening = {"nativeSyncActive": True, "startupWakeCleared": True}
        control = {"durableWake": True, "capturedReplayDeduplicated": True, "publicationRevision": 7}
        for replacement in ({"durableWake": False}, {"capturedReplayDeduplicated": False},
                            {"publicationRevision": True}, {"publicationRevision": 0},
                            {"publicationRevision": 2**63}):
            with self.subTest(replacement=replacement), tempfile.TemporaryDirectory() as directory:
                experiment = self.experiment(directory)
                with patch.object(OriginalDeliveryExperiment, "wait_app", side_effect=[listening, control | replacement]) as receive:
                    with self.assertRaises(ValueError):
                        experiment.wait_app("ack")
                self.assertEqual(2, receive.call_count)
                self.assertFalse((Path(directory) / "control.json").exists())

    def test_unclaimed_ack_and_incomplete_recovery_are_not_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            experiment = self.experiment(directory)
            with patch.object(OriginalDeliveryExperiment, "wait_app", side_effect=[
                {"nativeSyncActive": True, "startupWakeCleared": True},
                {"durableWake": True, "capturedReplayDeduplicated": True, "publicationRevision": 7},
                {"claimedBeforeDelivery": False},
            ]):
                with self.assertRaises(ValueError):
                    experiment.wait_app("ack")
            complete = {"groupTaskCompleted": True, "progressAndWakeCompleted": True, "recoveredWithoutNewControl": True}
            for key in complete:
                with self.subTest(key=key), patch.object(OriginalDeliveryExperiment, "wait_app", return_value=complete | {key: False}):
                    with self.assertRaises(ValueError):
                        experiment.wait_app("recovered")


if __name__ == "__main__":
    unittest.main()
