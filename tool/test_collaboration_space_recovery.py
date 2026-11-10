"""A process may be killed only after verified native-control, durable-wake and claim evidence."""
import tempfile
import unittest
import test_collaboration_space_original as original_cases
from pathlib import Path
from unittest.mock import patch

from collaboration_space_recovery import SpaceRecoveryExperiment, pending_space_original
from collaboration_space_original import OriginalDeliveryExperiment, space_original


class SpaceRecoveryDriverTest(unittest.TestCase):
    def experiment(self, directory):
        value = object.__new__(SpaceRecoveryExperiment)
        value.directory = Path(directory)
        value.expected = 2
        return value

    def test_valid_control_is_published_before_ack_becomes_eligible_for_interruption(self):
        with tempfile.TemporaryDirectory() as directory:
            experiment = self.experiment(directory)
            listening = {"processId": 42, "nativeSyncActive": True, "startupWakeCleared": True}
            control = {"processId": 42, "durableWake": True, "capturedReplayDeduplicated": True, "publicationRevision": 7}
            ack = {"processId": 42, "claimedBeforeDelivery": True}
            with patch.object(OriginalDeliveryExperiment, "wait_app", side_effect=[listening, control, ack]) as receive:
                self.assertEqual(ack, experiment.await_interruption_checkpoint())
            self.assertEqual(["listening", "control", "ack"], [c.args[0] for c in receive.call_args_list])
            self.assertTrue((Path(directory) / "listening.json").is_file())
            self.assertTrue((Path(directory) / "control.json").is_file())
            self.assertFalse((Path(directory) / "first-ack.json").exists())

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


class PendingSpaceRecoveryProofTest(unittest.TestCase):
    def experiment(self, directory, expected=3):
        experiment = object.__new__(SpaceRecoveryExperiment)
        experiment.directory = Path(directory)
        experiment.expected = expected
        return experiment

    def signals(self):
        return {
            'listening': {'processId': 121, 'nativeSyncActive': True, 'startupWakeCleared': True,
                          'queuedSpaceWithoutBinding': True},
            'control': {'processId': 121, 'durableWake': True,
                        'capturedReplayDeduplicated': True, 'publicationRevision': 5},
            'space-staged': {'processId': 121},
            'ack': {'processId': 121, 'claimedBeforeDelivery': True, 'pendingMessageClaim': True},
        }

    def test_control_is_forwarded_before_staging_and_ack_does_not_wait_for_it_again(self):
        with tempfile.TemporaryDirectory() as directory:
            experiment = self.experiment(directory)
            calls = []
            def next_signal(_self, name, _seconds):
                calls.append(name)
                if name == 'space-staged':
                    self.assertTrue((Path(directory) / 'control.json').is_file())
                return self.signals()[name]
            with patch.object(OriginalDeliveryExperiment, 'wait_app', next_signal):
                experiment.wait_app('space-staged')
                experiment.wait_app('ack')
            self.assertEqual(['listening', 'control', 'space-staged', 'ack'], calls)

    def test_old_binding_control_preserves_its_two_original_handshake(self):
        with tempfile.TemporaryDirectory() as directory:
            experiment = self.experiment(directory, 2)
            signals = self.signals()
            signals['listening'].pop('queuedSpaceWithoutBinding')
            signals['ack'].pop('pendingMessageClaim')
            with patch.object(OriginalDeliveryExperiment, 'wait_app', side_effect=lambda name, _: signals[name]) as read:
                self.assertEqual(signals['ack'], experiment.wait_app('ack'))
                self.assertEqual(['listening', 'control', 'ack'], [call.args[0] for call in read.call_args_list])

    def test_pending_case_rejects_prebound_start_without_forwarding_control(self):
        with tempfile.TemporaryDirectory() as directory:
            experiment = self.experiment(directory)
            value = self.signals()['listening'] | {'queuedSpaceWithoutBinding': False}
            with patch.object(OriginalDeliveryExperiment, 'wait_app', return_value=value) as read:
                with self.assertRaises(ValueError): experiment.wait_app('space-staged')
                read.assert_called_once_with('listening', 120)
            self.assertFalse((Path(directory) / 'listening.json').exists())

    def test_pending_ack_requires_a_message_claim_and_control_revision_is_strict(self):
        for phase, changes in [('ack', {'pendingMessageClaim': False}), ('control', {'publicationRevision': True}),
                               ('control', {'publicationRevision': 0}), ('control', {'durableWake': False})]:
            with self.subTest(phase=phase, changes=changes), tempfile.TemporaryDirectory() as directory:
                experiment = self.experiment(directory)
                signals = self.signals(); signals[phase] |= changes
                with patch.object(OriginalDeliveryExperiment, 'wait_app', side_effect=lambda name, _: signals[name]):
                    with self.assertRaises(ValueError): experiment.wait_app('ack')

    def example(self):
        sdk, fixture, group, staged = original_cases.MixedOriginalCheckpointTest().example()
        value = {k: v for k, v in sdk.items() if k != 'completeOriginalCount'}
        value.update(retainedOriginalCount=3, workerStaged=True, unboundQueued=True)
        return sdk, value, fixture, group, staged

    def test_worker_evidence_preserves_coordinates_without_claiming_sdk_inventory(self):
        sdk, value, fixture, group, staged = self.example()
        actual = pending_space_original(value, fixture, group, staged)
        self.assertEqual(value, actual)
        self.assertIsNot(value, actual)
        self.assertNotIn('completeOriginalCount', actual)
        with self.assertRaises(ValueError): space_original(value, fixture, group, staged)
        with self.assertRaises(ValueError): pending_space_original(sdk, fixture, group, staged)

    def test_worker_evidence_rejects_missing_claim_bounds_extra_payload_and_changed_original(self):
        _, value, fixture, group, staged = self.example()
        for changes in ({'workerStaged': False}, {'unboundQueued': False}, {'retainedOriginalCount': True},
                        {'retainedOriginalCount': 2}, {'completeOriginalCount': 3}, {'text': 'unexpected'},
                        {'transactionId': staged['originals'][0]['transactionId']}, {'originHash': 'invalid'},
                        {'nativeSessionId': '00000000-0000-0000-0000-000000000099'}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                pending_space_original(value | changes, fixture, group, staged)



if __name__ == "__main__":
    unittest.main()
