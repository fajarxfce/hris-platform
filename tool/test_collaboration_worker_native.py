import unittest

from collaboration_worker_native import checkpoint, evidence, diagnostic


class WorkerEvidenceTest(unittest.TestCase):
    def queued(self, pid=123):
        return {"processId": pid, "messageId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                "bodySha256": "a" * 64, "state": "QUEUED", "taskKey": "hris.collaboration-delivery"}

    def completed(self):
        return self.queued(456) | {"state": "COMPLETED", "eventId": "$event", "passes": 1,
                                   "encryptedReceived": True, "senderRuntimeRestoredByWorker": True,
                                   "originalHistoryAcquired": True, "completionRetired": True,
                                   "repeatedWorkerComplete": True}

    def test_nested_instrumentation_diagnostic_is_preserved_without_private_payloads(self):
        result = diagnostic({"complete": False, "failureCategory": "FixtureFailure", "privateBody": "hidden"})
        self.assertEqual("FixtureFailure", result["failureCategory"])
        self.assertNotIn("privateBody", result)
        self.assertFalse(diagnostic(None)["instrumentationComplete"])
        self.assertEqual({"failureCategory": "InvalidDiagnostic"}, diagnostic([]))

    def test_checkpoint_filters_private_extra_fields(self):
        value = self.queued() | {"body": "fictional private body", "credential": "fixture credential"}
        self.assertEqual(self.queued(), checkpoint(value))

    def test_malformed_process_identity_digest_or_state_is_rejected(self):
        for update in ({"processId": True}, {"processId": 0}, {"processId": 2147483648},
                       {"messageId": "INVALID"}, {"messageId": self.queued()["messageId"].upper()},
                       {"bodySha256": "A" * 64}, {"state": "SENT"}, {"taskKey": "foreign"}):
            with self.subTest(update=update), self.assertRaises(ValueError):
                checkpoint(self.queued() | update)

    def test_restored_checkpoint_requires_sender_runtime_to_be_closed(self):
        for update in ({}, {"senderRuntimeOpen": True}, {"senderRuntimeOpen": 0}):
            with self.subTest(update=update), self.assertRaises(ValueError):
                checkpoint(self.queued(456) | update, restored=True)
        self.assertEqual(self.queued(456), checkpoint(self.queued(456) | {"senderRuntimeOpen": False}, restored=True))

    def test_distinct_processes_and_exact_persistent_intent_are_required(self):
        self.assertEqual("$event", evidence(self.completed(), self.queued(), self.queued(456))["eventId"])
        for update in ({"processId": 123}, {"bodySha256": "b" * 64},
                       {"messageId": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"}):
            with self.subTest(update=update), self.assertRaises(ValueError):
                evidence(self.completed() | update, self.queued(), self.queued(456))

    def test_incomplete_or_unbounded_native_evidence_is_rejected(self):
        for update in ({"passes": True}, {"passes": 0}, {"passes": 5}, {"eventId": "invalid"},
                       {"encryptedReceived": False}, {"senderRuntimeRestoredByWorker": False},
                       {"originalHistoryAcquired": False}, {"completionRetired": False},
                       {"repeatedWorkerComplete": False}):
            with self.subTest(update=update), self.assertRaises(ValueError):
                evidence(self.completed() | update, self.queued(), self.queued(456))


if __name__ == "__main__":
    unittest.main()
