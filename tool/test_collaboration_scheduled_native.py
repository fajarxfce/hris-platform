import copy
import unittest
import uuid

from collaboration_scheduled_native import application_backgrounded, completion, queued_work, restored_work
from collaboration_scheduled_network import EmulatorNetwork
from collaboration_worker_native import checkpoint


class SchedulingEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.prepared = {"processId": 101, "messageId": str(uuid.uuid4()), "bodySha256": "a" * 64,
                         "state": "PREPARED", "taskKey": "hris.collaboration-delivery"}
        self.queued = self.prepared | {"state": "QUEUED", "workId": str(uuid.uuid4()), "jobId": 100, "jobNamespace": "androidx.work.systemjobscheduler",
                     "workState": "ENQUEUED", "networkConstraint": "CONNECTED", "inputKeys": ["sync_task"],
                     "workerClass": "dev.fajar.starter.worker.SyncWorker", "runAttemptCount": 0}
        self.restored = self.queued | {"processId": 202, "senderRuntimeOpen": False}
        self.result = self.restored | {"state": "COMPLETED", "workState": "SUCCEEDED", "eventId": "$ciphertext",
                     "senderRuntimeRestoredByWorker": True, "originalHistoryAcquired": True, "completionRetired": True}

    def test_preparation_handshake_uses_the_shared_checkpoint_validator(self):
        self.assertEqual(self.prepared, checkpoint(self.prepared, "PREPARED"))
        for state in ("QUEUED", "FROZEN_DRAFT", "COMPLETED", "UNKNOWN"):
            with self.subTest(state=state), self.assertRaises(ValueError):
                checkpoint(self.prepared, state)

    def test_queue_keeps_only_the_bound_task_metadata(self):
        result = queued_work(self.queued | {"body": "fictional-private", "credential": "excluded"}, self.prepared)
        self.assertNotIn("body", result)
        self.assertNotIn("credential", result)
        self.assertEqual(["sync_task"], result["inputKeys"])

    def test_queue_rejects_payloads_foreign_workers_or_missing_constraint(self):
        for change in ({"inputKeys": ["sync_task", "body"]}, {"workerClass": "OtherWorker"},
                       {"networkConstraint": "NOT_REQUIRED"}, {"workState": "RUNNING"}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                queued_work(self.queued | change, self.prepared)

    def test_queue_rejects_changed_intent_process_and_invalid_counters(self):
        for change in ({"processId": 202}, {"messageId": str(uuid.uuid4())}, {"bodySha256": "b" * 64},
                       {"jobNamespace": None}, {"jobNamespace": "bad namespace"}, {"jobNamespace": "-u"}, {"jobId": True}, {"jobId": -1}, {"runAttemptCount": False}, {"runAttemptCount": 1},
                       {"workId": "not-a-work-id"}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                queued_work(self.queued | change, self.prepared)

    def test_background_requires_new_process_closed_sender_and_original_work(self):
        self.assertEqual(202, restored_work(self.restored, self.queued)["processId"])
        for change in ({"processId": 101}, {"senderRuntimeOpen": True}, {"workId": str(uuid.uuid4())},
                       {"bodySha256": "b" * 64}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                restored_work(self.restored | change, self.queued)

    def test_completion_requires_android_success_originals_and_retirement(self):
        self.assertEqual("$ciphertext", completion(self.result, self.queued, self.restored)["eventId"])
        for change in ({"state": "QUEUED"}, {"workState": "FAILED"}, {"processId": 303},
                       {"originalHistoryAcquired": False}, {"completionRetired": False},
                       {"senderRuntimeRestoredByWorker": False}, {"workId": str(uuid.uuid4())},
                       {"runAttemptCount": 4}, {"runAttemptCount": False}, {"eventId": "$bad event"}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                completion(self.result | change, self.queued, self.restored)


class ActivityOwnershipTest(unittest.TestCase):
    def test_waits_until_the_proof_activity_is_no_longer_resumed(self):
        self.assertFalse(application_backgrounded("topResumedActivity=ActivityRecord{dev.fajar.hris.collaboration.proof/.HrisScheduledProofActivity}"))
        self.assertTrue(application_backgrounded("topResumedActivity=ActivityRecord{com.google.android.apps.nexuslauncher/.NexusLauncherActivity}"))

    def test_rejects_unknown_or_oversized_snapshots(self):
        for value in ("unrecognized", "x" * 262145, None):
            with self.assertRaises(ValueError):
                application_backgrounded(value)


class FakeDevice:
    serial = "emulator-5560"
    installed = True

    def __init__(self, mobile="1"):
        self.values = {"airplane_mode_on": "0", "wifi_on": "1", "mobile_data": mobile}
        self.calls = []
        self.lose_airplane_response = False

    def command(self, *args, timeout):
        self.calls.append(args)
        if args[1] == "settings":
            return self.values[args[-1]]
        if args[1] == "cmd":
            self.values["airplane_mode_on"] = "1" if args[-1] == "enable" else "0"
            if self.lose_airplane_response:
                self.lose_airplane_response = False
                raise RuntimeError("Lost owned emulator response")
        else:
            key = "wifi_on" if args[2] == "wifi" else "mobile_data"
            self.values[key] = "1" if args[-1] == "enable" else "0"
        return ""


class NetworkOwnershipTest(unittest.TestCase):
    def test_phone_and_unclaimed_package_are_rejected_before_any_command(self):
        for attribute, value in (("serial", "10.77.77.3:5555"), ("installed", False)):
            device = FakeDevice()
            setattr(device, attribute, value)
            with self.assertRaises(ValueError):
                EmulatorNetwork(device)
            self.assertEqual([], device.calls)

    def test_snapshot_must_start_in_the_expected_connected_configuration(self):
        device = FakeDevice()
        device.values["wifi_on"] = "0"
        with self.assertRaises(ValueError):
            EmulatorNetwork(device)
        self.assertTrue(all(call[1] == "settings" for call in device.calls))

    def test_restoration_preserves_original_mobile_data_setting(self):
        for mobile in ("0", "1"):
            device = FakeDevice(mobile)
            initial = copy.deepcopy(device.values)
            network = EmulatorNetwork(device)
            network.disable()
            self.assertEqual({"airplane_mode_on": "1", "wifi_on": "0", "mobile_data": "0"}, device.values)
            self.assertTrue(network.restore())
            self.assertEqual(initial, device.values)
            calls = len(device.calls)
            self.assertTrue(network.restore())
            self.assertEqual(calls, len(device.calls))

    def test_alternate_claimed_emulator_restores_its_original_connectivity(self):
        device = FakeDevice("0")
        device.serial = "emulator-5562"
        initial = copy.deepcopy(device.values)
        network = EmulatorNetwork(device)
        network.disable()
        self.assertTrue(network.restore())
        self.assertEqual(initial, device.values)

    def test_lost_mutation_response_still_restores_the_network(self):
        device = FakeDevice()
        initial = copy.deepcopy(device.values)
        network = EmulatorNetwork(device)
        device.lose_airplane_response = True
        with self.assertRaises(RuntimeError):
            network.disable()
        self.assertTrue(network.changed)
        self.assertTrue(network.restore())
        self.assertEqual(initial, device.values)


if __name__ == "__main__":
    unittest.main()
