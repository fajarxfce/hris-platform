package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class PayrollAmendmentReviewAssuranceTest : PayrollAmendmentReviewAssuranceApiFixture() {
    @ParameterizedTest
    @CsvSource(
        "submit,permission,access_denied",
        "submit,credential,session_revoked",
        "submit,membership,company_access_denied",
        "approve,permission,access_denied",
        "approve,credential,session_revoked",
        "approve,membership,company_access_denied",
        "withdraw,permission,access_denied",
        "withdraw,credential,session_revoked",
        "withdraw,membership,company_access_denied",
    )
    fun currentRevocationCannotBeBypassedAfterWaiting(
        action: String,
        change: String,
        code: String,
    ) {
        val scene = reviewAssuranceScene(action)
        val before = amendmentReviewCounts(scene.review)
        val barrier = AccountLockProbe.Barrier(scene.actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> { protectedReviewCommand(scene, action) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                when (change) {
                    "permission" ->
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                                scene.actor.companyId,
                                scene.actor.accountId,
                                if (action == "approve") "payroll.amend.review" else "payroll.amend",
                            )
                    "credential" ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                scene.actor.accountId,
                            )
                    else ->
                        database()
                            .update(
                                "update company_memberships set active=false where company_id=? and account_id=?",
                                scene.actor.companyId,
                                scene.actor.accountId,
                            )
                }
                barrier.release.countDown()
                assertEquals(
                    code,
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertEquals(before, amendmentReviewCounts(scene.review))
    }

    @ParameterizedTest
    @ValueSource(strings = ["submit", "approve", "reject", "withdraw"])
    fun recentMfaIsRecheckedAfterTheAccountGuard(action: String) {
        val scene = reviewAssuranceScene(action)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt =
                    clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        val before = amendmentReviewCounts(scene.review)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> {
                    protectedReviewCommand(scene, action, actor)
                }
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
        assertEquals(before, amendmentReviewCounts(scene.review))
        val recovered =
            protectedReviewCommand(scene, action, actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(recovered is Result.Success, recovered.toString())
    }

    @ParameterizedTest
    @CsvSource(
        "submit,password",
        "submit,mfa",
        "approve,password",
        "approve,mfa",
        "reject,password",
        "reject,mfa",
        "withdraw,password",
        "withdraw,mfa",
    )
    fun expiryAfterJournalingRollsBackApprovalAndAmendmentEvidence(action: String, proof: String) {
        val scene = reviewAssuranceScene(action)
        val deadline = clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
        val actor =
            if (proof == "password") scene.actor.copy(authenticatedAt = deadline)
            else scene.actor.copy(mfaVerifiedAt = deadline)
        val before = amendmentReviewCounts(scene.review)
        payrollProbe.afterJournal = {
            if (it.action.startsWith("payroll.amendment_review_"))
                clock.set(clock.instant().plusSeconds(2))
        }
        val failed = protectedReviewCommand(scene, action, actor)
        payrollProbe.clear()
        assertEquals("recent_authentication_required", (failed as Result.Failed).failure.code)
        assertEquals(before, amendmentReviewCounts(scene.review))
        val recovered =
            protectedReviewCommand(
                scene,
                action,
                actor.copy(authenticatedAt = clock.instant(), mfaVerifiedAt = clock.instant()),
            )
        assertTrue(recovered is Result.Success, recovered.toString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["submit", "approve", "reject", "withdraw"])
    fun committedReceiptsStillRequireCurrentProofAfterAcquisition(action: String) {
        val scene = reviewAssuranceScene(action)
        val committed = protectedReviewCommand(scene, action)
        assertTrue(committed is Result.Success, committed.toString())
        val before = amendmentReviewCounts(scene.review)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt =
                    clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        draftProbe.afterRead = {
            if (it == actor.companyId) clock.set(clock.instant().plusSeconds(2))
        }
        val failed = protectedReviewCommand(scene, action, actor)
        draftProbe.clear()
        assertEquals("recent_authentication_required", (failed as Result.Failed).failure.code)
        assertEquals(before, amendmentReviewCounts(scene.review))
        val recovered =
            protectedReviewCommand(scene, action, actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(recovered is Result.Success, recovered.toString())
        assertEquals(
            (committed as Result.Success).value.copy(replayed = true),
            (recovered as Result.Success).value,
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["review", "reviews", "draft"])
    fun acquiredPrivateReviewDataIsDiscardedIfMfaExpires(action: String) {
        val scene = reviewAssuranceScene("approve")
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val reads = AtomicInteger()
        amendmentReviewProbe.afterRead = {
            if (
                it == actor.companyId && reads.incrementAndGet() == if (action == "review") 2 else 1
            )
                clock.set(clock.instant().plusSeconds(2))
        }
        val failed = protectedReviewRead(scene, action, actor)
        amendmentReviewProbe.clear()
        assertEquals("mfa_required", (failed as Result.Failed).failure.code)
        val recovered =
            protectedReviewRead(scene, action, actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(recovered is Result.Success, recovered.toString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["submit", "approve", "withdraw"])
    fun cancellationDuringPendingAcquisitionReleasesTransactionResources(action: String) {
        val scene = reviewAssuranceScene(action)
        val before = amendmentReviewCounts(scene.review)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val hook: (java.util.UUID) -> Unit = {
            if (it == scene.actor.companyId) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        if (action == "withdraw") amendmentReviewProbe.afterRead = hook
        else amendmentProbe.afterRead = hook
        val owner = AtomicReference<Thread>()
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> {
                    owner.set(Thread.currentThread())
                    protectedReviewCommand(scene, action)
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                owner.get().interrupt()
                val failed =
                    assertThrows(ExecutionException::class.java) {
                        pending.get(10, TimeUnit.SECONDS)
                    }
                assertInstanceOf(InterruptedException::class.java, failed.cause)
                amendmentReviewProbe.clear()
                amendmentProbe.clear()
                assertEquals(before, amendmentReviewCounts(scene.review))
                val recovered =
                    pool
                        .submit<Result<MutationReceipt>> {
                            assertFalse(Thread.currentThread().isInterrupted)
                            protectedReviewCommand(scene, action)
                        }
                        .get(10, TimeUnit.SECONDS)
                assertTrue(recovered is Result.Success, recovered.toString())
            } finally {
                release.countDown()
                amendmentReviewProbe.clear()
                amendmentProbe.clear()
            }
        }
    }
}
