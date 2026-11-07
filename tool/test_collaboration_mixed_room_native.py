import unittest

from collaboration_mixed_room_native import evidence


class MixedRoomEvidenceTest(unittest.TestCase):
    def fixture(self):
        return dict(complete=True, originalReadyIntentRetained=True, unavailableOriginalRetained=True,
                    unavailableReceiptAbsent=True, recipientRuntimeClosedMillis=100,
                    unavailableQueuedMillis=200, readyQueuedMillis=300, journalObservedMillis=420,
                    sentObservedMillis=425, completionObservedMillis=430, eventId="$actual-fixture-event")

    def test_requires_actual_completion_and_retained_unavailable_intent(self):
        for key in ("complete", "originalReadyIntentRetained", "unavailableOriginalRetained", "unavailableReceiptAbsent"):
            value = self.fixture()
            value[key] = False
            with self.subTest(key=key), self.assertRaises(ValueError):
                evidence(value)

    def test_rejects_crossed_milestones_and_unbounded_or_boolean_times(self):
        for key, replacement in (("readyQueuedMillis", 99), ("sentObservedMillis", 299),
                                 ("completionObservedMillis", 299), ("journalObservedMillis", 299),
                                 ("sentObservedMillis", 300001), ("readyQueuedMillis", True)):
            value = self.fixture() | {key: replacement}
            with self.subTest(key=key, replacement=replacement), self.assertRaises(ValueError):
                evidence(value)

    def test_missing_journal_observation_does_not_fabricate_one(self):
        value = self.fixture()
        del value["journalObservedMillis"]
        self.assertNotIn("journalObservedMillis", evidence(value))
        self.assertEqual(425, evidence(value)["sentObservedMillis"])

    def test_requires_real_event_reference_and_detaches_output(self):
        value = self.fixture()
        observed = evidence(value)
        value["sentObservedMillis"] = 999
        self.assertEqual(425, observed["sentObservedMillis"])
        for event in (None, "fake-ack", "$has space", "$" + "x" * 255):
            with self.subTest(event=event), self.assertRaises(ValueError):
                evidence(self.fixture() | {"eventId": event})


if __name__ == "__main__":
    unittest.main()
