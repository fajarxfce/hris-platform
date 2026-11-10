"""Reject incomplete native evidence; preserve event and physical ownership comparisons."""
import unittest

from collaboration_event_reads_native import staged_events, adopted_events, event_coordinates, original_window


class EventReadEvidenceTest(unittest.TestCase):
    def staged(self):
        return {"processId": 123, "events": {n: "$" + n for n in ["original", "peer", "firstEdit", "latestEdit", "redaction"]},
                "originalHash": "a" * 64, "firstEditHash": "b" * 64, "latestEditHash": "c" * 64,
                "companyWindow": {"kind": "Unavailable", "code": "fixture.pending", "originals": 0, "anchorIncluded": False},
                "missingKeyObserved": True, "decryptedAfterSync": True, "redactedTargetSenderPreserved": True, "bothSdkClosed": True}

    def adopted(self):
        return {"processId": 124, "headEventId": "$latestEdit", "originalHash": "a" * 64, "latestEditHash": "c" * 64,
                "redactedEventId": "$peer", "redactionEventId": "$redaction", "spaceWindow": self.staged()["companyWindow"],
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


if __name__ == "__main__":
    unittest.main()
