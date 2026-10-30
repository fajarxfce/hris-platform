package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.math.BigDecimal
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ExpenseDraftInputSnapshotTest : ExpenseCommandAssuranceApiFixture() {
    @ParameterizedTest
    @ValueSource(strings = ["lines", "receipts"])
    fun theAdmittedDraftOwnsItsLinesAndNestedReceiptSelectionAcrossAWait(mode: String) {
        val original = prepare("save")
        val receipts = original.draft.lines.single().receiptRevisionIds.toMutableList()
        val lines = mutableListOf(original.draft.lines.single().copy(receiptRevisionIds = receipts))
        val scene = original.copy(draft = original.draft.copy(lines = lines))
        val barrier = AccountLockProbe.Barrier(scene.actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<MutationReceipt>> { invoke(scene) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                if (mode == "lines") lines[0] = lines[0].copy(amount = BigDecimal("900000.00"))
                else receipts.clear()
                barrier.release.countDown()
                val result = pending.get(10, TimeUnit.SECONDS)
                assertTrue(result is Result.Success, result.toString())
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        val draft = expenseDetails(scene.fixture)["draft"]
        assertEquals("150000.00", draft["totalAmount"].asString())
        assertEquals(1, draft["lines"][0]["receiptRevisionIds"].size())
        assertEquals(
            original.draft.lines.single().receiptRevisionIds.single().toString(),
            draft["lines"][0]["receiptRevisionIds"][0].asString(),
        )
        val committed = snapshot(scene)
        val replay = invoke(original)
        assertTrue(replay is Result.Success, replay.toString())
        assertTrue((replay as Result.Success).value.replayed)
        assertEquals(committed, snapshot(scene))
    }
}
