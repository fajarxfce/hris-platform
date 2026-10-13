package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ExpenseCommandAssuranceTest : ExpenseCommandAssuranceApiFixture() {
    @ParameterizedTest
    @ValueSource(strings = ["save", "edit", "cancel", "submit", "withdraw"])
    fun expiryWhileWaitingCannotMutateOrReplayAnOriginalReceipt(action: String) {
        val scene = prepare(action)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val before = snapshot(scene)
        assertEquals(
            "company_required",
            (invoke(scene, actor.copy(companyId = null)) as Result.Failed).failure.code,
        )
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        val result =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<MutationReceipt>> { invoke(scene, actor) }
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
        assertEquals(before, snapshot(scene))
        val renewed = actor.copy(mfaVerifiedAt = clock.instant())
        val accepted = invoke(scene, renewed)
        assertTrue(accepted is Result.Success, accepted.toString())
        val committed = snapshot(scene)
        assertEquals("mfa_required", (invoke(scene, actor) as Result.Failed).failure.code)
        assertEquals(committed, snapshot(scene))
        assertEquals(
            Result.Success((accepted as Result.Success).value.copy(replayed = true)),
            invoke(scene, renewed),
        )
        assertEquals(committed, snapshot(scene))
    }

    @ParameterizedTest
    @ValueSource(strings = ["save", "edit", "cancel", "submit", "withdraw"])
    fun expiryAfterAcquisitionRejectsBothTheMutationAndACompletedReceipt(action: String) {
        val scene = prepare(action)
        for (replay in listOf(false, true)) {
            if (replay)
                assertTrue(
                    invoke(scene, scene.actor.copy(mfaVerifiedAt = clock.instant()))
                        is Result.Success
                )
            val before = snapshot(scene)
            val actor =
                scene.actor.copy(
                    mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
                )
            commandProbe.afterClaim = { company ->
                if (company == scene.fixture.company) clock.set(clock.instant().plusSeconds(2))
            }
            try {
                val result = invoke(scene, actor)
                assertEquals("mfa_required", (result as Result.Failed).failure.code)
                assertEquals(before, snapshot(scene))
            } finally {
                commandProbe.clear()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["save", "edit", "cancel", "submit", "withdraw"])
    fun expiryAfterJournalRollsBackEvidenceHistoryApprovalAndOperationTogether(action: String) {
        val scene = prepare(action)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val before = snapshot(scene)
        commandProbe.afterJournal = {
            if (it.action.startsWith("expenses.")) clock.set(clock.instant().plusSeconds(2))
        }
        try {
            val result = invoke(scene, actor)
            assertEquals("mfa_required", (result as Result.Failed).failure.code)
            assertEquals(before, snapshot(scene))
        } finally {
            commandProbe.clear()
        }
        assertTrue(invoke(scene, actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success)
        assertEquals(
            (before.getValue("operation_receipts") as Long) + 1,
            snapshot(scene)["operation_receipts"],
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["save", "edit", "cancel", "submit", "withdraw"])
    fun interruptionReleasesGuardsAndTheSameExecutorCanRecoverTheOriginalOperation(action: String) {
        val scene = prepare(action)
        val before = snapshot(scene)
        val barrier = AccountLockProbe.Barrier(scene.actor.accountId)
        accountProbe.current.set(barrier)
        val owner = AtomicReference<Thread>()
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> {
                    owner.set(Thread.currentThread())
                    invoke(scene)
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                owner.get().interrupt()
                val failure =
                    assertThrows(ExecutionException::class.java) {
                        pending.get(10, TimeUnit.SECONDS)
                    }
                assertTrue(failure.cause is InterruptedException, failure.toString())
                assertEquals(before, snapshot(scene))
                accountProbe.current.set(null)
                val accepted =
                    pool.submit<Result<MutationReceipt>> { invoke(scene) }.get(10, TimeUnit.SECONDS)
                assertTrue(accepted is Result.Success, accepted.toString())
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }
}
