package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class OvertimeAssuranceTest : OvertimeAssuranceApiFixture() {
    @ParameterizedTest
    @ValueSource(strings = ["plan", "actual", "withdraw", "decide", "details", "list"])
    fun expiryAtTheAccountGuardCannotAuthorizeDataWritesOrReceiptReplay(action: String) {
        val scene = prepare(action)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val before = trackedTables.associateWith { rows(scene.fixture, it) }
        val unscoped = invoke(scene, actor.copy(companyId = null))
        assertEquals("company_required", (unscoped as Result.Failed).failure.code)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        val result =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { invoke(scene, actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                    barrier.release.countDown()
                    pending.get(10, TimeUnit.SECONDS)
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
        assertEquals("mfa_required", (result as Result.Failed).failure.code)
        assertEquals(before, trackedTables.associateWith { rows(scene.fixture, it) })
        val renewed = actor.copy(mfaVerifiedAt = clock.instant())
        val accepted = invoke(scene, renewed)
        assertTrue(accepted is Result.Success, accepted.toString())
        val committed = trackedTables.associateWith { rows(scene.fixture, it) }
        assertEquals("mfa_required", (invoke(scene, actor) as Result.Failed).failure.code)
        assertEquals(committed, trackedTables.associateWith { rows(scene.fixture, it) })
        val value = (accepted as Result.Success).value
        val expected =
            if (value is MutationReceipt) Result.Success(value.copy(replayed = true)) else accepted
        assertEquals(expected, invoke(scene, renewed))
        assertEquals(committed, trackedTables.associateWith { rows(scene.fixture, it) })
    }

    @ParameterizedTest
    @ValueSource(strings = ["plan", "actual", "withdraw", "decide"])
    fun expiryBeforeTransactionCompletionRollsBackBusinessHistoryApprovalAndReceipt(
        action: String
    ) {
        val scene = prepare(action)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val before = trackedTables.associateWith { rows(scene.fixture, it) }
        val original = if (action == "plan") null else details(scene.fixture, scene.id)
        overtimeProbe.beforeJournal = {
            if (it.action.startsWith("overtime.")) clock.set(clock.instant().plusSeconds(2))
        }
        try {
            val result = invoke(scene, actor)
            assertEquals("mfa_required", (result as Result.Failed).failure.code)
            assertEquals(before, trackedTables.associateWith { rows(scene.fixture, it) })
            if (original != null) assertEquals(original, details(scene.fixture, scene.id))
        } finally {
            overtimeProbe.clear()
        }
        val recovered = invoke(scene, actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(recovered is Result.Success, recovered.toString())
        assertEquals(
            before.getValue("operation_receipts") + 1,
            rows(scene.fixture, "operation_receipts"),
        )
    }
}
