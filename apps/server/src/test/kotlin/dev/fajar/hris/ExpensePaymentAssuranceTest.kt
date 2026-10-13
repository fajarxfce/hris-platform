package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class ExpensePaymentAssuranceTest : ExpensePaymentAssuranceApiFixture() {
    @ParameterizedTest
    @CsvSource(
        "batch,true",
        "export,true",
        "history,true",
        "results,true",
        "batches,true",
        "payables,true",
        "batch,false",
        "export,false",
        "history,false",
        "results,false",
        "batches,false",
        "payables,false",
    )
    fun financeReadsRevalidateRecentAssuranceAfterFinalAcquisition(action: String, mfa: Boolean) {
        val scene = preparePaymentScenario(action, mfa)
        val actor = expiringActor(scene)
        val before = paymentSnapshot(scene)
        val stage = if (action == "export") "batch" else action
        paymentProbe.afterRead = { company, kind ->
            if (company == scene.fixture.company && kind == stage)
                clock.set(clock.instant().plusSeconds(2))
        }
        try {
            val result = readPayment(scene, actor)
            assertEquals("recent_authentication_required", (result as Result.Failed).failure.code)
            assertFalse(result.toString().contains("001234567890"))
            assertEquals(before, paymentSnapshot(scene))
        } finally {
            paymentProbe.clear()
        }
        val accepted = readPayment(scene, renewedActor(scene))
        assertTrue(accepted is Result.Success, accepted.toString())
    }

    @ParameterizedTest
    @CsvSource(
        "prepare,true",
        "release,true",
        "cancel,true",
        "reconcile,true",
        "prepare,false",
        "release,false",
        "cancel,false",
        "reconcile,false",
    )
    fun expiryDuringEvidenceAcquisitionRejectsNewCommandsAndOriginalReceipts(
        action: String,
        mfa: Boolean,
    ) {
        val scene = preparePaymentScenario(action, mfa)
        for (replay in listOf(false, true)) {
            if (replay) {
                val result = commandPayment(scene, renewedActor(scene))
                assertTrue(result is Result.Success, result.toString())
            }
            val actor = expiringActor(scene)
            val before = paymentSnapshot(scene)
            val stage = if (action == "cancel") "batch" else "candidates"
            paymentProbe.afterRead = { company, kind ->
                if (company == scene.fixture.company && kind == stage)
                    clock.set(clock.instant().plusSeconds(2))
            }
            try {
                val result = commandPayment(scene, actor)
                assertEquals(
                    "recent_authentication_required",
                    (result as Result.Failed).failure.code,
                )
                assertEquals(before, paymentSnapshot(scene))
            } finally {
                paymentProbe.clear()
            }
        }
        val committed = paymentSnapshot(scene)
        val replayed = commandPayment(scene, renewedActor(scene))
        assertTrue(replayed is Result.Success, replayed.toString())
        assertTrue((replayed as Result.Success).value.replayed)
        assertEquals(committed, paymentSnapshot(scene))
    }

    @ParameterizedTest
    @CsvSource(
        "prepare,true",
        "release,true",
        "cancel,true",
        "reconcile,true",
        "prepare,false",
        "release,false",
        "cancel,false",
        "reconcile,false",
    )
    fun lateExpiryRollsBackPaymentItemsReferencesHistoryAndJournalTogether(
        action: String,
        mfa: Boolean,
    ) {
        val scene = preparePaymentScenario(action, mfa)
        val actor = expiringActor(scene)
        val before = paymentSnapshot(scene)
        paymentProbe.afterJournal = {
            if (it.companyId == scene.fixture.company && it.action.startsWith("expenses.payment_"))
                clock.set(clock.instant().plusSeconds(2))
        }
        try {
            val result = commandPayment(scene, actor)
            assertEquals("recent_authentication_required", (result as Result.Failed).failure.code)
            assertEquals(before, paymentSnapshot(scene))
        } finally {
            paymentProbe.clear()
        }
        val restored = commandPayment(scene, renewedActor(scene))
        assertTrue(restored is Result.Success, restored.toString())
        val committed = paymentSnapshot(scene)
        assertEquals(
            (before.getValue("operation_receipts") as Long) + 1,
            committed["operation_receipts"],
        )
        val replayed = commandPayment(scene, renewedActor(scene))
        assertTrue(replayed is Result.Success, replayed.toString())
        assertTrue((replayed as Result.Success).value.replayed)
        assertEquals(committed, paymentSnapshot(scene))
    }

    @ParameterizedTest
    @ValueSource(strings = ["prepare", "release", "cancel", "reconcile"])
    fun interruptionReleasesTheTransactionAndTheSameExecutorRecoversTheOriginalOperation(
        action: String
    ) {
        val scene = preparePaymentScenario(action, true)
        val before = paymentSnapshot(scene)
        val barrier = AccountLockProbe.Barrier(scene.actor.accountId)
        accountProbe.current.set(barrier)
        val owner = AtomicReference<Thread>()
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> {
                    owner.set(Thread.currentThread())
                    commandPayment(scene)
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                owner.get().interrupt()
                val failure =
                    assertThrows(ExecutionException::class.java) {
                        pending.get(10, TimeUnit.SECONDS)
                    }
                assertTrue(failure.cause is InterruptedException, failure.toString())
                assertEquals(before, paymentSnapshot(scene))
                accountProbe.current.set(null)
                val recovered =
                    pool
                        .submit<Result<MutationReceipt>> { commandPayment(scene) }
                        .get(10, TimeUnit.SECONDS)
                assertTrue(recovered is Result.Success, recovered.toString())
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun anAcquisitionFailureKeepsItsClassificationEvenIfAssuranceAlsoExpires(mfa: Boolean) {
        val scene = preparePaymentScenario("results", mfa)
        val actor = expiringActor(scene)
        val failure = Failure(FailureKind.UNAVAILABLE, "storage_unavailable")
        paymentProbe.resultsFailure = failure
        paymentProbe.afterRead = { company, kind ->
            if (company == scene.fixture.company && kind == "results")
                clock.set(clock.instant().plusSeconds(2))
        }
        try {
            assertEquals(Result.Failed(failure), readPayment(scene, actor))
        } finally {
            paymentProbe.clear()
        }
        assertTrue(readPayment(scene, renewedActor(scene)) is Result.Success)
    }
}
