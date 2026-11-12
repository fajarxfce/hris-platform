"""Reject false account isolation, stale acknowledgement, and lost offline receipt evidence."""
import unittest

from collaboration_account_replacement_native import account_replacement, offline_receipt


class AccountReplacementEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.proof = {"receiptWrites": 1, "nativeAcknowledgements": 0, "receiptDigest": "a" * 64,
                      **{name: True for name in ("receiptCommittedBeforeReplacement",
                         "replacementParentPublished", "previousRuntimeClosed",
                         "replacementCannotReadOriginalReceipt", "lateReplyNotAcknowledged",
                         "originalReceiptRestored", "originalPrivateIdentityPreserved")}}

    def test_accepts_complete_evidence_without_mutating_it(self):
        before = self.proof.copy()
        self.assertEqual(before, account_replacement(self.proof))
        self.assertEqual(before, self.proof)

    def test_rejects_missing_or_private_fields(self):
        for name in self.proof:
            value = {k: v for k, v in self.proof.items() if k != name}
            with self.subTest(name=name), self.assertRaises(ValueError):
                account_replacement(value)
        for name in ("accessToken", "key", "body", "extra"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                account_replacement(self.proof | {name: "private"})

    def test_rejects_false_flags_rewrites_and_stale_acknowledgement(self):
        for name, value in self.proof.items():
            alternatives = (False, 1, None) if value is True else (True, -1, 2, "0", None)
            for invalid in alternatives:
                with self.subTest(name=name, invalid=invalid), self.assertRaises(ValueError):
                    account_replacement(self.proof | {name: invalid})
        with self.assertRaises(ValueError):
            account_replacement(self.proof | {"nativeAcknowledgements": 1})

    def test_rejects_non_objects_and_invalid_digests(self):
        for value in (None, [], True, ""):
            with self.subTest(value=value), self.assertRaises(ValueError):
                account_replacement(value)
        for value in ("", "a" * 63, "A" * 64, "g" * 64, 64, {}):
            with self.subTest(value=value), self.assertRaises(ValueError):
                account_replacement(self.proof | {"receiptDigest": value})

    def test_offline_requires_the_same_validated_receipt(self):
        local = {"incomingReceiptDigest": self.proof["receiptDigest"], "processId": 125}
        self.assertEqual(self.proof["receiptDigest"], offline_receipt(local, self.proof))
        for value in (None, {}, local | {"incomingReceiptDigest": "b" * 64},
                      local | {"incomingReceiptDigest": True}):
            with self.subTest(value=value), self.assertRaises(ValueError):
                offline_receipt(value, self.proof)
        with self.assertRaises(ValueError):
            offline_receipt(local, self.proof | {"originalReceiptRestored": False})


if __name__ == "__main__":
    unittest.main()
