package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.usecases.PrepareExpensePaymentBatch
import dev.fajar.hris.expenses.domain.usecases.ReconcileExpensePaymentBatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class ExpensePaymentInputSnapshotTest : ExpensePaymentAssuranceApiFixture() {
    @Autowired private lateinit var prepareBatch: PrepareExpensePaymentBatch
    @Autowired private lateinit var reconcileBatch: ReconcileExpensePaymentBatch

    @ParameterizedTest
    @ValueSource(strings = ["prepare", "reconcile"])
    fun admittedPaymentInputsAndTheirJournalRemainTheSameAcrossAWait(action: String) {
        val scene = preparePaymentScenario(action, true)
        val originalItems =
            listOf(
                ExpensePaymentInstruction(
                    scene.item,
                    scene.submission,
                    ExpensePaymentDestination("014", "001234567890", "Test Employee"),
                )
            )
        val originalResults =
            listOf(
                ExpensePaymentResult(
                    scene.item,
                    ExpensePaymentItemStatus.SUCCEEDED,
                    scene.reference,
                    scene.at,
                    "Verified bank settlement",
                )
            )
        val items = originalItems.toMutableList()
        val results = originalResults.toMutableList()
        fun invoke(): Result<MutationReceipt> =
            if (action == "prepare")
                prepareBatch.execute(
                    scene.actor,
                    scene.operation,
                    scene.batch,
                    "Independent reimbursement",
                    items,
                    "Verified payment destinations",
                )
            else
                reconcileBatch.execute(
                    scene.actor,
                    scene.operation,
                    scene.batch,
                    1,
                    results,
                    "Bank statement reviewed",
                )
        val barrier = AccountLockProbe.Barrier(scene.actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<MutationReceipt>> { invoke() }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                items.clear()
                results.clear()
                barrier.release.countDown()
                val committed = pending.get(10, TimeUnit.SECONDS)
                assertTrue(committed is Result.Success, committed.toString())
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        val countField = if (action == "prepare") "itemCount" else "resultCount"
        val event =
            if (action == "prepare") "expenses.payment_prepared" else "expenses.payment_reconciled"
        assertEquals(
            "1",
            database()
                .queryForObject(
                    "select details->>? from audit_entries where company_id=? and resource_id=? and action=?",
                    String::class.java,
                    countField,
                    scene.fixture.company,
                    scene.batch,
                    event,
                ),
        )
        val before = paymentSnapshot(scene)
        items += originalItems
        results += originalResults
        val replay = invoke()
        assertTrue(replay is Result.Success, replay.toString())
        assertTrue((replay as Result.Success).value.replayed)
        assertEquals(before, paymentSnapshot(scene))
    }
}
