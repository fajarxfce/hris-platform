import unittest

from collaboration_worker_native import checkpoint, evidence, diagnostic, incoming_evidence, progress_evidence


class WorkerEvidenceTest(unittest.TestCase):
    def queued(self, pid=123):
        return {"processId": pid, "messageId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                "bodySha256": "a" * 64, "state": "QUEUED", "taskKey": "hris.collaboration-delivery"}

    def completed(self):
        return self.queued(456) | {"state": "COMPLETED", "eventId": "$event", "passes": 1,
                                   "encryptedReceived": True, "senderRuntimeRestoredByWorker": True,
                                   "originalHistoryAcquired": True, "completionRetired": True,
                                   "repeatedWorkerComplete": True, "foregroundHistoryPaged": True,
                                   "foregroundStoppedHistoryRetained": True, "historyPages": 2,
                                   "providerReferences": 185, "pendingStateReferences": 9,
                                   "historyPeerEvents": [f"$peer-{i}" for i in range(175)], "incoming": self.incoming()}

    def incoming(self):
        return {"incomingWithoutOutbox": True, "incomingWithoutSelection": True,
                "incomingFreshNativeAdmission": True, "incomingOriginalAfterOfflineSeed": True,
                "incomingHistoryRetainedAfterReopen": True, "incomingRuntimeReleased": True,
                "incomingAllOriginalsRecovered": True, "incomingTraversalRetainedAfterReopen": True,
                "incomingBrowseUnchanged": True, "incomingPasses": 12, "incomingSavedOriginals": 176,
                "incomingContainerReopens": 12}

    def test_incoming_evidence_requires_every_boundary_and_bounded_integer_counts(self):
        for invalid in (None, [], {}, self.incoming() | {"incomingPasses": True},
                        self.incoming() | {"incomingPasses": 1}, self.incoming() | {"incomingPasses": 41},
                        self.incoming() | {"incomingSavedOriginals": True},
                        self.incoming() | {"incomingSavedOriginals": 175},
                        self.incoming() | {"incomingSavedOriginals": 177},
                        self.incoming() | {"incomingContainerReopens": 11},
                        self.incoming() | {"incomingContainerReopens": True}):
            with self.assertRaises(ValueError):
                incoming_evidence(invalid)
        for key, value in self.incoming().items():
            if type(value) is bool:
                with self.subTest(key=key), self.assertRaises(ValueError):
                    incoming_evidence(self.incoming() | {key: False})

    def test_incoming_evidence_discards_private_extra_fields(self):
        self.assertEqual(self.incoming(), incoming_evidence(self.incoming() | {"body": "hidden", "token": "hidden"}))
        with self.assertRaises(ValueError):
            evidence(self.completed() | {"incoming": None}, self.queued(), self.queued(456))

    def test_phase_progress_is_bounded_and_discards_private_extra_fields(self):
        value = {"phase": "incoming", "seeded": 175, "elapsedMillis": 120000}
        self.assertEqual(value, progress_evidence(value | {"body": "hidden", "token": "hidden"}))
        for update in ({"phase": "hidden"}, {"seeded": True}, {"seeded": -1}, {"seeded": 176},
                       {"elapsedMillis": 480001}, {"elapsedMillis": False}):
            with self.subTest(update=update), self.assertRaises(ValueError):
                progress_evidence(value | update)

    def test_incoming_progress_requires_bounded_counts_flags_and_safe_codes(self):
        value = {"pass": 12, "originals": 176, "references": 183, "hasNext": False,
                 "hasPendingSeek": True, "retryRequired": True, "result": "Retry",
                 "failureKind": "Unavailable", "failureCode": "client.collaboration_history_pending"}
        self.assertEqual(value, progress_evidence(value | {"body": "hidden", "token": "hidden"}, True))
        for update in ({"pass": True}, {"pass": 0}, {"pass": 41}, {"originals": 177},
                       {"references": 257}, {"references": -1}, {"hasNext": 0}, {"hasPendingSeek": 1},
                       {"retryRequired": "yes"}, {"result": "hidden"}, {"failureCode": "private body"},
                       {"failureKind": "bad-value"}):
            with self.subTest(update=update), self.assertRaises(ValueError):
                progress_evidence(value | update, True)

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

    def test_foreground_history_requires_bounded_progress_and_distinct_peer_events(self):
        for update in ({"historyPages": True}, {"historyPages": 1}, {"historyPages": 13},
                       {"providerReferences": 175}, {"providerReferences": 225},
                       {"pendingStateReferences": True}, {"pendingStateReferences": 21},
                       {"historyPeerEvents": ["$peer"] * 175}, {"historyPeerEvents": []},
                       {"historyPeerEvents": ["$event"] + [f"$peer-{i}" for i in range(174)]},
                       {"foregroundHistoryPaged": False}, {"foregroundStoppedHistoryRetained": False}):
            with self.subTest(update=update), self.assertRaises(ValueError):
                evidence(self.completed() | update, self.queued(), self.queued(456))
        value = self.completed() | {"body": "private", "credential": "private"}
        result = evidence(value, self.queued(), self.queued(456))
        self.assertNotIn("body", result)
        self.assertNotIn("credential", result)
        value["historyPeerEvents"].clear()
        self.assertEqual(175, len(result["historyPeerEvents"]))

    def test_incomplete_or_unbounded_native_evidence_is_rejected(self):
        for update in ({"passes": True}, {"passes": 0}, {"passes": 5}, {"eventId": "invalid"},
                       {"encryptedReceived": False}, {"senderRuntimeRestoredByWorker": False},
                       {"originalHistoryAcquired": False}, {"completionRetired": False},
                       {"repeatedWorkerComplete": False}):
            with self.subTest(update=update), self.assertRaises(ValueError):
                evidence(self.completed() | update, self.queued(), self.queued(456))

    def frozen(self, pid=123):
        return self.queued(pid) | {"state": "FROZEN_DRAFT", "pendingCommandCount": 0,
                                   "draftRevision": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"}

    def test_frozen_boundary_requires_an_empty_outbox_and_original_draft_revision(self):
        self.assertEqual(self.frozen(), checkpoint(self.frozen(), "FROZEN_DRAFT"))
        for update in ({"pendingCommandCount": False}, {"pendingCommandCount": 1},
                       {"draftRevision": None}, {"draftRevision": "invalid"}, {"state": "QUEUED"}):
            with self.subTest(update=update), self.assertRaises(ValueError):
                checkpoint(self.frozen() | update, "FROZEN_DRAFT")
        with self.assertRaises(ValueError):
            checkpoint(self.frozen())

    def test_frozen_recovery_cannot_be_substituted_with_queued_or_recreated_intent(self):
        completed = self.completed() | {"initialState": "FROZEN_DRAFT", "frozenDraftRecovered": True,
                                       "outboxEmptyBeforeRecovery": True, "draftConfirmedCleared": True}
        result = evidence(completed, self.frozen(), self.frozen(456))
        self.assertEqual("FROZEN_DRAFT", result["initialState"])
        self.assertTrue(result["draftConfirmedCleared"])
        for update in ({"initialState": "QUEUED"}, {"frozenDraftRecovered": False},
                       {"outboxEmptyBeforeRecovery": False}, {"draftConfirmedCleared": False}):
            with self.subTest(update=update), self.assertRaises(ValueError):
                evidence(completed | update, self.frozen(), self.frozen(456))
        for restored in (self.queued(456), self.frozen(456) | {"draftRevision": self.queued()["messageId"]}):
            with self.subTest(restored=restored), self.assertRaises(ValueError):
                evidence(completed, self.frozen(), restored)
        with self.assertRaises(ValueError):
            evidence(completed, self.queued(), self.queued(456))


    def cancelled(self, pid=123):
        return self.queued(pid) | {"state": "CANCELLED_DRAFT", "pendingCommandCount": 1,
            "cancelledMessageId": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "cancelledBodySha256": "b" * 64,
            "cancelledDraftRevision": "cccccccc-cccc-4ccc-8ccc-cccccccccccc", "cancelledArchiveRetained": True,
            "cancelledReceiptAbsent": True, "cancelledReplayDidNotSchedule": True}

    def cancelled_completed(self):
        return self.completed() | {"initialState": "CANCELLED_DRAFT", **{k: v for k, v in self.cancelled().items()
            if k.startswith("cancelled") and k != "cancelledDraftRevision"},
            "cancelledDraftConfirmedCleared": True, "cancelledAbsentFromHistory": True}

    def test_cancelled_checkpoint_requires_distinct_identity_archive_and_independent_pending_command(self):
        self.assertEqual(self.cancelled(), checkpoint(self.cancelled(), "CANCELLED_DRAFT"))
        for update in ({"cancelledMessageId": self.queued()["messageId"]}, {"cancelledMessageId": "invalid"},
                       {"cancelledBodySha256": "A" * 64}, {"cancelledBodySha256": None},
                       {"cancelledDraftRevision": "invalid"}, {"pendingCommandCount": True},
                       {"pendingCommandCount": 0}, {"pendingCommandCount": 2},
                       {"cancelledArchiveRetained": False}, {"cancelledReceiptAbsent": False},
                       {"cancelledReplayDidNotSchedule": 1}):
            with self.subTest(update=update), self.assertRaises(ValueError):
                checkpoint(self.cancelled() | update, "CANCELLED_DRAFT")
        private = self.cancelled() | {"body": "hidden", "credential": "hidden"}
        self.assertEqual(self.cancelled(), checkpoint(private, "CANCELLED_DRAFT"))

    def test_cancelled_recovery_requires_original_archive_and_exact_draft_across_death(self):
        proof = self.cancelled_completed()
        self.assertEqual("CANCELLED_DRAFT", evidence(proof, self.cancelled(), self.cancelled(456))["initialState"])
        for key in ("cancelledArchiveRetained", "cancelledReceiptAbsent", "cancelledReplayDidNotSchedule",
                    "cancelledDraftConfirmedCleared", "cancelledAbsentFromHistory"):
            with self.subTest(key=key), self.assertRaises(ValueError):
                evidence(proof | {key: False}, self.cancelled(), self.cancelled(456))
        for change in ({"cancelledMessageId": "dddddddd-dddd-4ddd-8ddd-dddddddddddd"},
                       {"cancelledBodySha256": "c" * 64}, {"cancelledDraftRevision": self.queued()["messageId"]}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                evidence(proof, self.cancelled(), self.cancelled(456) | change)
        for change in ({"initialState": "QUEUED"}, {"cancelledBodySha256": "c" * 64},
                       {"cancelledMessageId": self.queued()["messageId"]}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                evidence(proof | change, self.cancelled(), self.cancelled(456))
        with self.assertRaises(ValueError):
            evidence(proof, self.queued(), self.queued(456))

    def test_cancelled_proof_never_exports_raw_body_or_private_extra_fields(self):
        proof = self.cancelled_completed() | {"cancelledBody": "hidden", "credential": "hidden"}
        result = evidence(proof, self.cancelled(), self.cancelled(456))
        self.assertNotIn("cancelledBody", result)
        self.assertNotIn("credential", result)


if __name__ == "__main__":
    unittest.main()
