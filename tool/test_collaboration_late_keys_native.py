"""Reject missing real-key, durable-retention, quiet-window and offline-reopen evidence."""
import copy
import unittest

from collaboration_late_keys_native import (retained_keys, late_keys, late_key_staged,
                                            late_key_ready, quiet_checkpoint, adopted_keys, reopened_keys)


class LateKeyEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.staged = {"processId": 123, "events": {n: "$" + n for n in
                       ("original", "peer", "firstEdit", "latestEdit", "redaction")}}
        self.retained = {"receiptDigest": "a" * 64, "recoveryGenerationDigest": "b" * 64,
                         "requestedGenerationDigest": "a" * 64, "recoveryComplete": False}
        self.proof = {name: True for name in ("committedBeforeLostReply", "keyReceivedDuringHeldReply",
                      "durableBeforeBothAcknowledgements", "firstReceiptNotRewritten", "sameRuntimeInstance",
                      "rawReceiptsDrained")}
        self.proof.update(receiptAttempts=2, receiptCommits=2, nativeAcknowledgements=2, retained=self.retained)
        self.ready = {"processId": 124, "lateKeys": self.proof}
        self.quiet = {"processId": 124, "observedMillis": 30_000}
        self.adopted = self.ready | {"quietMillis": 30_000, "processCollectorJoined": True, "runtimeReleased": True}
        self.reopened = {"processId": 125, "retained": self.retained,
                         "runtimeNeverOpened": True, "databaseReleased": True}

    def test_valid_evidence_is_detached_from_mutable_input(self):
        accepted = adopted_keys(self.adopted, self.ready, self.quiet)
        local = reopened_keys(self.reopened, accepted)
        self.assertEqual(accepted, self.adopted)
        self.assertEqual(local, self.reopened)
        self.retained["receiptDigest"] = "c" * 64
        self.assertEqual("a" * 64, accepted["lateKeys"]["retained"]["receiptDigest"])
        self.assertEqual("a" * 64, local["retained"]["receiptDigest"])

    def test_fresh_key_event_is_separate_and_from_the_staging_process(self):
        value = {"processId": 123, "eventId": "$fresh"}
        self.assertEqual(value, late_key_staged(value, self.staged))
        for changed in [{"eventId": event} for event in self.staged["events"].values()] + [
                {"eventId": "$bad event"}, {"eventId": 1}, {"processId": 124}, {"processId": True}, {"key": "private"}]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                late_key_staged(value | changed, self.staged)

    def test_receipt_and_successor_must_be_distinct_unfinished_obligations(self):
        for changed in [{"recoveryComplete": True}, {"recoveryComplete": 0}, {"requestedGenerationDigest": None},
                        {"requestedGenerationDigest": "c" * 64}, {"recoveryGenerationDigest": "a" * 64},
                        {"receiptDigest": "unsafe"}, {"roomKey": "private"}]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                retained_keys(self.retained | changed)

    def test_receipt_proof_cannot_omit_any_invariant(self):
        for name in self.proof:
            value = copy.deepcopy(self.proof)
            del value[name]
            with self.subTest(name=name), self.assertRaises(ValueError):
                late_keys(value)
        for name, value in self.proof.items():
            if type(value) is bool:
                for replacement in (False, 1, None):
                    with self.subTest(name=name, replacement=replacement), self.assertRaises(ValueError):
                        late_keys(self.proof | {name: replacement})

    def test_extra_receipt_writes_or_unbounded_attempts_are_rejected(self):
        for changed in [{"receiptAttempts": 1}, {"receiptAttempts": 5}, {"receiptAttempts": True},
                        {"receiptCommits": 3}, {"receiptCommits": 1}, {"nativeAcknowledgements": 1},
                        {"nativeAcknowledgements": 3}, {"nativeAcknowledgements": True}, {"token": "private"}]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                late_keys(self.proof | changed)
        self.assertEqual(4, late_keys(self.proof | {"receiptAttempts": 4})["receiptAttempts"])

    def test_application_must_reopen_in_a_new_process(self):
        self.assertEqual(self.ready, late_key_ready(self.ready, self.staged))
        for changed in [{"processId": 123}, {"processId": True}, {"body": "private"}, {"lateKeys": {}}]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                late_key_ready(self.ready | changed, self.staged)

    def test_quiet_window_requires_at_least_thirty_seconds_on_the_same_process(self):
        for elapsed in (30_000, 60_000):
            value = self.quiet | {"observedMillis": elapsed}
            self.assertEqual(value, quiet_checkpoint(value, self.ready))
        for changed in [{"observedMillis": 29_999}, {"observedMillis": 60_001}, {"observedMillis": True},
                        {"processId": 125}, {"processId": True}, {"extra": 1}]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                quiet_checkpoint(self.quiet | changed, self.ready)

    def test_quiet_completion_must_preserve_the_proof_and_release_resources(self):
        for changed in [{"lateKeys": self.proof | {"receiptAttempts": 3}}, {"quietMillis": 30_001},
                        {"quietMillis": True}, {"processId": 125}, {"processCollectorJoined": False},
                        {"runtimeReleased": 1}, {"body": "private"}]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                adopted_keys(self.adopted | changed, self.ready, self.quiet)

    def test_offline_reopen_requires_same_durable_facts_without_runtime_opening(self):
        altered = self.retained | {"receiptDigest": "c" * 64, "requestedGenerationDigest": "c" * 64}
        for changed in [{"retained": altered}, {"processId": 124}, {"processId": True},
                        {"runtimeNeverOpened": False}, {"databaseReleased": 1}, {"key": "private"}]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                reopened_keys(self.reopened | changed, self.adopted)

    def test_non_object_evidence_cannot_pass(self):
        for value in (None, [], "", True):
            for validator in (retained_keys, late_keys):
                with self.subTest(value=value, validator=validator.__name__), self.assertRaises(ValueError):
                    validator(value)
            with self.assertRaises(ValueError):
                reopened_keys(value, self.adopted)


if __name__ == "__main__":
    unittest.main()
