package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class ExpensePaymentEnrollmentTest : ExpensePaymentAssuranceApiFixture() {
    @ParameterizedTest
    @ValueSource(
        strings = ["batch", "review", "export", "history", "results", "batches", "payables"]
    )
    fun currentEnrollmentIsRequiredBeforeReadingBankEvidence(action: String) {
        val scene = preparePaymentScenario(action, true)
        val original = readPayment(scene)
        assertTrue(original is Result.Success, original.toString())
        database()
            .update(
                "update accounts set mfa_secret_encrypted=null where id=?",
                scene.actor.accountId,
            )
        val before = paymentSnapshot(scene)
        try {
            val result = readPayment(scene)
            assertTrue(result is Result.Failed, "Unenrolled account acquired payment evidence")
            assertEquals("mfa_setup_required", (result as Result.Failed).failure.code)
            assertEquals(before, paymentSnapshot(scene))
        } finally {
            database()
                .update(
                    "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                    scene.actor.accountId,
                )
        }
        assertEquals(original, readPayment(scene))
        assertEquals(before, paymentSnapshot(scene))
    }

    @ParameterizedTest
    @CsvSource(
        "prepare,false",
        "release,false",
        "cancel,false",
        "reconcile,false",
        "prepare,true",
        "release,true",
        "cancel,true",
        "reconcile,true",
    )
    fun currentEnrollmentIsRequiredForCommandsAndTheirOriginalReceipts(
        action: String,
        replay: Boolean,
    ) {
        val scene = preparePaymentScenario(action, true)
        if (replay) {
            val committed = commandPayment(scene)
            assertTrue(committed is Result.Success, committed.toString())
        }
        database()
            .update(
                "update accounts set mfa_secret_encrypted=null where id=?",
                scene.actor.accountId,
            )
        val before = paymentSnapshot(scene)
        try {
            val denied = commandPayment(scene)
            assertTrue(
                denied is Result.Failed,
                "Unenrolled account completed payment command or replay",
            )
            assertEquals("mfa_setup_required", (denied as Result.Failed).failure.code)
            assertEquals(before, paymentSnapshot(scene))
        } finally {
            database()
                .update(
                    "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                    scene.actor.accountId,
                )
        }
        val recovered = commandPayment(scene)
        assertTrue(recovered is Result.Success, recovered.toString())
        assertEquals(replay, (recovered as Result.Success).value.replayed)
        val committed = paymentSnapshot(scene)
        assertEquals(
            (before.getValue("operation_receipts") as Long) + if (replay) 0 else 1,
            committed["operation_receipts"],
        )
        val repeated = commandPayment(scene)
        assertTrue(repeated is Result.Success, repeated.toString())
        assertTrue((repeated as Result.Success).value.replayed)
        assertEquals(committed, paymentSnapshot(scene))
    }
}
