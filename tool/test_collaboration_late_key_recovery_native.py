"""Reject lost obligations, partial projection, and false offline recovery evidence."""
import copy
import unittest

from collaboration_late_key_recovery_native import recovered_history, recovered_checkpoint


class LateKeyRecoveryEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.staged = {"events": {"original": "$original", "latestEdit": "$edit", "peer": "$peer"},
                       "originalHash": "a" * 64, "latestEditHash": "b" * 64}
        self.late = {"eventId": "$late"}
        self.retained = {"receiptDigest": "c" * 64}
        self.content = {
            "messages": 2, "originalEventId": "$original", "replacementEventId": "$edit",
            "redactedEventId": "$peer", "originalHash": "a" * 64, "replacementHash": "b" * 64,
            "revisionHash": "d" * 64, "frontierHash": "e" * 64, "pendingHash": "f" * 64,
            "providerReferences": 9, "pendingReferences": 0, "providerExhaustedWithPending": False,
            "lateEventId": "$late", "lateHash": "1" * 64, "receiptDigest": "c" * 64,
            "messageOrder": ["$late", "$original"],
            **{name: True for name in ("originalTextPreserved", "replacementTextPreserved",
                "originalSenderTombstone", "noOriginalFactForEdit", "noSpaceSendWork",
                "physicalProviderPreserved", "companyStorage", "recoveryComplete", "registeredWorkerDrained")}}
        self.resumed = {"processId": 125, "recovered": self.content,
                        "runtimeNeverOpened": False, "resourcesReleased": True}

    def validate(self, value):
        return recovered_history(value, self.staged, self.late, self.retained)

    def test_accepts_complete_projection_without_mutating_it(self):
        before = copy.deepcopy(self.content)
        self.assertEqual(before, self.validate(self.content))
        self.assertEqual(before, self.content)

    def test_rejects_missing_invariants_and_private_fields(self):
        for name in self.content:
            value = self.content.copy()
            del value[name]
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.validate(value)
        for extra in ("body", "key", "accessToken"):
            with self.subTest(extra=extra), self.assertRaises(ValueError):
                self.validate(self.content | {extra: "private"})

    def test_rejects_collapsed_obligation_and_unfinished_worker(self):
        for changed in ({"receiptDigest": "4" * 64}, {"registeredWorkerDrained": False},
                        {"registeredWorkerDrained": 1}, {"recoveryComplete": False},
                        {"pendingReferences": 1}, {"companyStorage": False}, {"lateHash": "bad"}):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                self.validate(self.content | changed)

    def test_rejects_missing_duplicate_and_wrong_messages(self):
        for changed in ({"messages": 1}, {"messages": True}, {"lateEventId": "$different"},
                        {"messageOrder": ["$late", "$late"]}, {"messageOrder": ["$late"]},
                        {"messageOrder": ["$late", {}]}, {"originalHash": "3" * 64},
                        {"replacementTextPreserved": False}, {"originalSenderTombstone": False}):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                self.validate(self.content | changed)

    def test_requires_new_process_and_released_resources(self):
        self.assertEqual(self.resumed, recovered_checkpoint(self.resumed, {"processId": 124},
            self.staged, self.late, self.retained, offline=False))
        for changed in ({"processId": 124}, {"processId": True}, {"resourcesReleased": False},
                        {"runtimeNeverOpened": True}, {"extra": 1}):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                recovered_checkpoint(self.resumed | changed, {"processId": 124}, self.staged,
                                     self.late, self.retained, offline=False)

    def test_offline_requires_identical_revision_order_and_content(self):
        local = self.resumed | {"processId": 126, "runtimeNeverOpened": True}
        self.assertEqual(local, recovered_checkpoint(local, self.resumed, self.staged,
            self.late, self.retained, offline=True))
        for changed in ({"revisionHash": "9" * 64}, {"lateHash": "9" * 64},
                        {"messageOrder": ["$original", "$late"]}):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                recovered_checkpoint(local | {"recovered": self.content | changed}, self.resumed,
                                     self.staged, self.late, self.retained, offline=True)

    def test_non_objects_cannot_pass(self):
        for value in (None, [], True, ""):
            with self.subTest(value=value), self.assertRaises(ValueError):
                self.validate(value)


if __name__ == "__main__":
    unittest.main()
