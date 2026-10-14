package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class PayrollAmendmentDraftAssuranceTest : PayrollAmendmentDraftAssuranceApiFixture() {
    @ParameterizedTest
    @ValueSource(strings = ["create", "save", "calculate", "cancel"])
    fun recentProofIsRecheckedAfterWaitingForCurrentAccountAccess(action: String) {
        val scene = assuranceScene()
        val actor =
            scene.actor.copy(
                mfaVerifiedAt =
                    clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        val before = draftCounts(scene.draft.source)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> { protectedCommand(scene, action, actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(2))
                barrier.release.countDown()
                assertEquals(
                    "recent_authentication_required",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertEquals(before, draftCounts(scene.draft.source))
        val retried = protectedCommand(scene, action, actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(retried is Result.Success, retried.toString())
    }

    @ParameterizedTest
    @CsvSource(
        "create,password",
        "create,mfa",
        "save,password",
        "save,mfa",
        "calculate,password",
        "calculate,mfa",
        "cancel,password",
        "cancel,mfa",
    )
    fun proofExpiryAfterJournalWriteRollsBackTheWholeCommand(action: String, proof: String) {
        val scene = assuranceScene()
        val deadline = clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
        val actor =
            if (proof == "password") scene.actor.copy(authenticatedAt = deadline)
            else scene.actor.copy(mfaVerifiedAt = deadline)
        val before = draftCounts(scene.draft.source)
        payrollProbe.afterJournal = {
            if (it.action.startsWith("payroll.amendment_"))
                clock.set(clock.instant().plusSeconds(2))
        }
        val rejected = protectedCommand(scene, action, actor)
        payrollProbe.clear()
        assertEquals("recent_authentication_required", (rejected as Result.Failed).failure.code)
        assertEquals(before, draftCounts(scene.draft.source))
        val retried =
            protectedCommand(
                scene,
                action,
                actor.copy(authenticatedAt = clock.instant(), mfaVerifiedAt = clock.instant()),
            )
        assertTrue(retried is Result.Success, retried.toString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["create", "save", "calculate", "cancel"])
    fun aCommittedReceiptStillRequiresFreshProofImmediatelyBeforeReplay(action: String) {
        val scene = assuranceScene()
        val committed = protectedCommand(scene, action)
        assertTrue(committed is Result.Success, committed.toString())
        val before = draftCounts(scene.draft.source)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt =
                    clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        draftProbe.afterRead = { company ->
            if (company == actor.companyId) clock.set(clock.instant().plusSeconds(2))
        }
        val expired = protectedCommand(scene, action, actor)
        draftProbe.clear()
        assertEquals("recent_authentication_required", (expired as Result.Failed).failure.code)
        assertEquals(before, draftCounts(scene.draft.source))
        val recovered = protectedCommand(scene, action, actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(recovered is Result.Success, recovered.toString())
        assertTrue((recovered as Result.Success).value.replayed)
        assertEquals((committed as Result.Success).value.copy(replayed = true), recovered.value)
        assertEquals(before, draftCounts(scene.draft.source))
    }

    @ParameterizedTest
    @ValueSource(strings = ["details", "history", "revision", "calculation", "list"])
    fun retainedPrivateDataIsNotReturnedWhenMfaExpiresDuringAcquisition(action: String) {
        val scene = assuranceScene(calculated = true)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        draftProbe.afterRead = { company ->
            if (company == actor.companyId) clock.set(clock.instant().plusSeconds(2))
        }
        val rejected = protectedRead(scene, action, actor)
        draftProbe.clear()
        assertEquals("mfa_required", (rejected as Result.Failed).failure.code)
        val fresh = protectedRead(scene, action, actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(fresh is Result.Success, fresh.toString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["create", "calculate"])
    fun interruptionWhileAcquiringSourcesRollsBackWithoutAStoredFailureOrLeakedGuard(
        action: String
    ) {
        val scene = assuranceScene()
        val before = draftCounts(scene.draft.source)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        amendmentProbe.afterRead = { company ->
            if (company == scene.actor.companyId) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        val owner = AtomicReference<Thread>()
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> {
                    owner.set(Thread.currentThread())
                    protectedCommand(scene, action)
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                owner.get().interrupt()
                val failure =
                    assertThrows(ExecutionException::class.java) {
                        pending.get(10, TimeUnit.SECONDS)
                    }
                assertInstanceOf(InterruptedException::class.java, failure.cause)
                amendmentProbe.clear()
                assertEquals(before, draftCounts(scene.draft.source))
                val retried =
                    pool
                        .submit<Result<MutationReceipt>> {
                            assertFalse(Thread.currentThread().isInterrupted)
                            protectedCommand(scene, action)
                        }
                        .get(10, TimeUnit.SECONDS)
                assertTrue(retried is Result.Success, retried.toString())
            } finally {
                release.countDown()
                amendmentProbe.clear()
            }
        }
    }

    @ParameterizedTest
    @CsvSource(
        "permission,access_denied",
        "credential,session_revoked",
        "membership,company_access_denied",
    )
    fun revokedAuthorityIsCheckedAfterThePayrollGuardBeforeLoadingDraftTermsOrSources(
        change: String,
        code: String,
    ) {
        val scene = assuranceScene()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val armed = AtomicBoolean(true)
        var acquired = false
        payrollProbe.beforePolicyLock = { company, shared ->
            if (company == scene.actor.companyId && !shared && armed.compareAndSet(true, false)) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        draftProbe.afterRead = { if (it == scene.actor.companyId) acquired = true }
        amendmentProbe.afterRead = { if (it == scene.actor.companyId) acquired = true }
        val before = draftCounts(scene.draft.source)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> { protectedCommand(scene, "calculate") }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                when (change) {
                    "permission" ->
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.amend'",
                                scene.actor.companyId,
                                scene.actor.accountId,
                            )
                    "credential" ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                scene.actor.accountId,
                            )
                    "membership" ->
                        database()
                            .update(
                                "update company_memberships set active=false where company_id=? and account_id=?",
                                scene.actor.companyId,
                                scene.actor.accountId,
                            )
                }
                release.countDown()
                assertEquals(
                    code,
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                release.countDown()
                payrollProbe.clear()
                draftProbe.clear()
                amendmentProbe.clear()
            }
        }
        assertFalse(acquired)
        assertEquals(before, draftCounts(scene.draft.source))
    }
}
