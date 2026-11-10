"""Reject incomplete native evidence; preserve event and physical ownership comparisons."""
import unittest

from collaboration_event_reads_native import staged_events, adopted_events, event_coordinates, original_window, retained_history, incoming_history, reopened_history


class EventReadEvidenceTest(unittest.TestCase):
    def staged(self):
        return {"processId": 123, "events": {n: "$" + n for n in ["original", "peer", "firstEdit", "latestEdit", "redaction"]},
                "originalHash": "a" * 64, "firstEditHash": "b" * 64, "latestEditHash": "c" * 64,
                "companyWindow": {"kind": "Unavailable", "code": "fixture.pending", "originals": 0, "anchorIncluded": False},
                "missingKeyObserved": True, "decryptedAfterSync": True, "redactedTargetSenderPreserved": True, "bothSdkClosed": True}

    def retained(self):
        return {"messages": 1, "originalEventId": "$original", "replacementEventId": "$latestEdit",
                "redactedEventId": "$peer", "originalHash": "a" * 64, "replacementHash": "c" * 64,
                "revisionHash": "d" * 64, "frontierHash": "e" * 64, "pendingHash": "f" * 64,
                "providerReferences": 8, "pendingReferences": 6,
                "physicalProviderPreserved": True, "providerExhaustedWithPending": True,
                **{n: True for n in ["originalTextPreserved", "replacementTextPreserved",
                                    "originalSenderTombstone", "noOriginalFactForEdit", "noSpaceSendWork"]}}

    def incoming(self):
        return {"retained": self.retained(), "replayUnchanged": True, "originalJournalUnchanged": True, "providerPages": 2}

    def reopened(self):
        return {"processId": 125, "incoming": self.retained(), "sameRevision": True,
                "runtimeNeverOpened": True, "databaseReleased": True}

    def adopted(self):
        return {"processId": 124, "headEventId": "$latestEdit", "originalHash": "a" * 64, "latestEditHash": "c" * 64,
                "redactedEventId": "$peer", "redactionEventId": "$redaction", "spaceWindow": self.staged()["companyWindow"],
                "incoming": self.incoming(),
                **{n: True for n in ["logicalRuntimePreserved", "physicalOwnerPreserved", "oldCompanyReadRejected",
                                    "foreignRuntimeReadRejected", "redactedTargetSenderPreserved", "runtimeReleased"]}}

    def test_preserves_distinct_original_edits_and_redaction_without_aliasing_input(self):
        source = self.staged()
        accepted = staged_events(source)
        self.assertEqual(source, accepted)
        source["events"]["firstEdit"] = "$different"
        self.assertEqual("$firstEdit", accepted["events"]["firstEdit"])
        self.assertEqual(self.adopted(), adopted_events(self.adopted(), accepted))

    def test_rejects_missing_keys_provisioning_claims_payloads_and_boolean_process_ids(self):
        for changed in [{"missingKeyObserved": False}, {"decryptedAfterSync": 1}, {"bothSdkClosed": False},
                        {"processId": True}, {"body": "private"}, {"latestEditHash": "unsafe"}]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                staged_events(self.staged() | changed)
        for events in [{}, self.staged()["events"] | {"latestEdit": "$original"},
                       self.staged()["events"] | {"original": "$bad event"}]:
            with self.subTest(events=events), self.assertRaises(ValueError):
                event_coordinates(events)

    def test_rejects_relabeling_lost_ownership_changed_content_and_unexpected_payload(self):
        for changed in [{"headEventId": "$original"}, {"redactedEventId": "$redaction"},
                        {"originalHash": "b" * 64}, {"physicalOwnerPreserved": False},
                        {"runtimeReleased": 1}, {"body": "private"}]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                adopted_events(self.adopted() | changed, staged_events(self.staged()))

    def test_records_both_possible_old_reader_outcomes_but_no_facts_on_failure(self):
        value = self.staged()["companyWindow"]
        for valid in [value, {"kind": "Success", "code": "none", "originals": 2, "anchorIncluded": False},
                      {"kind": "Success", "code": "none", "originals": 2, "anchorIncluded": True}]:
            self.assertEqual(valid, original_window(valid))
        for changed in [{"originals": 1}, {"anchorIncluded": True}, {"originals": True},
                        {"kind": "provider body"}, {"kind": []}, {"code": "unsafe text"}, {"body": "private"}]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                original_window(value | changed)


    def test_incoming_evidence_is_copied_and_remains_independent_of_original_receipts(self):
        source = self.incoming()
        result = incoming_history(source, self.staged())
        source["retained"]["originalHash"] = "f" * 64
        self.assertEqual(self.incoming(), result)
        self.assertEqual(self.reopened(), reopened_history(self.reopened(), self.adopted()))

    def test_incoming_rejects_wrong_identity_hash_count_and_body_exposure(self):
        for changed in [{"originalEventId": "$latestEdit"}, {"replacementEventId": "$original"},
                        {"redactedEventId": "$redaction"}, {"originalHash": "c" * 64},
                        {"replacementHash": "a" * 64}, {"messages": True}, {"messages": 2},
                        {"revisionHash": "unsafe"}, {"body": "private"}, {"noOriginalFactForEdit": False}]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                retained_history(self.retained() | changed, self.staged())

    def test_incoming_rejects_changed_replay_or_outgoing_journal(self):
        for changed in [{"replayUnchanged": False}, {"originalJournalUnchanged": 1}, {"token": "private"}]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                incoming_history(self.incoming() | changed, self.staged())

    def test_reopened_requires_a_new_process_and_released_database_without_a_runtime(self):
        for changed in [{"processId": 124}, {"processId": True}, {"sameRevision": False},
                        {"runtimeNeverOpened": False}, {"databaseReleased": 1}, {"body": "private"}]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                reopened_history(self.reopened() | changed, self.adopted())

    def test_reopened_rejects_changed_storage_revision_or_any_retained_fact(self):
        for changed in [{"revisionHash": "e" * 64}, {"originalHash": "e" * 64},
                        {"redactedEventId": "$other"}, {"noSpaceSendWork": False}]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                reopened_history(self.reopened() | {"incoming": self.retained() | changed}, self.adopted())


    def test_provider_progress_requires_exact_counts_and_independent_fingerprints(self):
        for changed in [{"providerReferences": True}, {"providerReferences": 4}, {"providerReferences": 51},
                        {"pendingReferences": True}, {"pendingReferences": 0}, {"pendingReferences": 7},
                        {"frontierHash": "unsafe"}, {"pendingHash": "unsafe"}, {"cursor": "private"}]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                retained_history(self.retained() | changed, self.staged())

    def test_provider_exhaustion_cannot_claim_pending_resolution_or_relabel_original_device(self):
        for changed in [{"physicalProviderPreserved": False}, {"physicalProviderPreserved": 1},
                        {"providerExhaustedWithPending": False}, {"providerExhaustedWithPending": 1}]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                retained_history(self.retained() | changed, self.staged())

    def test_provider_pages_have_a_finite_integer_bound(self):
        for count in [1,2,3,4]:
            valid=self.incoming() | {"providerPages": count}
            self.assertEqual(valid,incoming_history(valid,self.staged()))
        for count in [0,5,-1,True,2.0,"2"]:
            with self.subTest(count=count), self.assertRaises(ValueError):
                incoming_history(self.incoming() | {"providerPages": count},self.staged())

    def test_reopened_requires_identical_provider_frontier_and_pending_reference_identity(self):
        for changed in [{"frontierHash": "a" * 64}, {"pendingHash": "a" * 64},
                        {"providerReferences": 9, "pendingReferences": 7}, {"physicalProviderPreserved": False}]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                reopened_history(self.reopened() | {"incoming": self.retained() | changed},self.adopted())


if __name__ == "__main__":
    unittest.main()
