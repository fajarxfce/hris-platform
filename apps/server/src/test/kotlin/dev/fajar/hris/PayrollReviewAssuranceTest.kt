package dev.fajar.hris

import dev.fajar.hris.approvals.domain.entities.ApprovalDecision
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.payroll.domain.usecases.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class PayrollReviewAssuranceTest : PayrollReviewApiFixture() {
    @Autowired private lateinit var runs: PayrollRunRepository
    @Autowired private lateinit var policies: PayrollPolicyRepository
    @Autowired private lateinit var reviews: PayrollReviewRepository
    @Autowired private lateinit var approvals: ApprovalRepository
    @Autowired private lateinit var finalizations: PayrollFinalizationRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var jobs: JobRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    private val security = IdentitySecurityPolicy()

    private data class Scenario(val run: UUID, val review: UUID, val actor: Actor)

    @ParameterizedTest
    @ValueSource(strings = ["preparation", "list", "detail"])
    fun accessWaitCannotReleaseReviewDataAfterMfaExpires(action: String) {
        val scene = prepare("read")
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { read(action, scene, actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(2))
                barrier.release.countDown()
                assertEquals(
                    "mfa_required",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        assertTrue(
            read(action, scene, actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["preparation", "list", "detail"])
    fun completedAcquisitionStillRequiresCurrentAssurance(action: String) {
        val scene = prepare("read")
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        reviewProbe.afterRead = { kind, company ->
            if (kind == action && company == actor.companyId)
                clock.set(clock.instant().plusSeconds(2))
        }
        assertEquals("mfa_required", (read(action, scene, actor) as Result.Failed).failure.code)
        reviewProbe.clear()
        assertTrue(
            read(action, scene, actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["preparation", "list", "detail"])
    fun interruptedReadsReleaseGuardsBeforeTheThreadIsReused(action: String) {
        val scene = prepare("read")
        val barrier = AccountLockProbe.Barrier(scene.actor.accountId)
        accountProbe.current.set(barrier)
        val owner = AtomicReference<Thread>()
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    owner.set(Thread.currentThread())
                    read(action, scene, scene.actor)
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                owner.get().interrupt()
                val failure =
                    assertThrows(ExecutionException::class.java) {
                        pending.get(10, TimeUnit.SECONDS)
                    }
                assertInstanceOf(InterruptedException::class.java, failure.cause)
                accountProbe.current.set(null)
                val recovered =
                    pool
                        .submit<Result<*>> {
                            assertFalse(Thread.currentThread().isInterrupted)
                            read(action, scene, scene.actor)
                        }
                        .get(10, TimeUnit.SECONDS)
                assertTrue(recovered is Result.Success, recovered.toString())
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            ["submit", "decide", "withdraw", "submit-replay", "decide-replay", "withdraw-replay"]
    )
    fun commandsAndTheirOriginalReceiptsRequireRecentMfaAfterWaiting(action: String) {
        val scene = prepare(action.substringBefore('-'))
        val operation = UUID.randomUUID()
        val replay = action.endsWith("replay")
        if (replay) assertTrue(mutate(action, scene, scene.actor, operation) is Result.Success)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt =
                    clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        val before = snapshot(actor)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<MutationReceipt>> { mutate(action, scene, actor, operation) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(2))
                barrier.release.countDown()
                assertEquals(
                    "recent_authentication_required",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
                assertEquals(before, snapshot(actor))
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        val recovered =
            mutate(action, scene, actor.copy(mfaVerifiedAt = clock.instant()), operation)
        assertTrue(recovered is Result.Success, recovered.toString())
        assertEquals(replay, (recovered as Result.Success).value.replayed)
    }

    @ParameterizedTest
    @ValueSource(strings = ["submit", "decide", "withdraw"])
    fun expiryDuringTheJournalRollsBackReviewAndApprovalTogether(action: String) {
        val scene = prepare(action)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt =
                    clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        val before = snapshot(actor)
        val operation = UUID.randomUUID()
        val change =
            when (action) {
                "submit" -> "payroll.review_submitted"
                "decide" -> "payroll.review_decided"
                else -> "payroll.review_withdrawn"
            }
        payrollProbe.beforeJournal = { row ->
            if (row.action == change) clock.set(clock.instant().plusSeconds(2))
        }
        assertEquals(
            "recent_authentication_required",
            (mutate(action, scene, actor, operation) as Result.Failed).failure.code,
        )
        payrollProbe.clear()
        assertEquals(before, snapshot(actor))
        assertTrue(
            mutate(action, scene, actor.copy(mfaVerifiedAt = clock.instant()), operation)
                is Result.Success
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["submit", "decide", "withdraw"])
    fun expiryAfterTheReceiptStillRollsBackAllStateAndEvidence(action: String) {
        val scene = prepare(action)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt =
                    clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        val before = snapshot(actor)
        val operation = UUID.randomUUID()
        val delayed =
            object : OperationRepository by operations {
                override fun record(
                    actor: Actor,
                    key: OperationKey,
                    receipt: MutationReceipt,
                ): Result<Unit> =
                    operations.record(actor, key, receipt).also {
                        clock.set(clock.instant().plusSeconds(2))
                    }
            }
        assertEquals(
            "recent_authentication_required",
            (mutate(action, scene, actor, operation, delayed) as Result.Failed).failure.code,
        )
        assertEquals(before, snapshot(actor))
        assertTrue(
            mutate(action, scene, actor.copy(mfaVerifiedAt = clock.instant()), operation)
                is Result.Success
        )
    }

    private fun prepare(action: String): Scenario {
        clock.set(Instant.now())
        val f = calculationFixture()
        val run = calculated(f)
        reviewTemplate(f)
        val id = UUID.randomUUID()
        if (action != "submit") payrollBody(submitReview(f, run, id))
        val member = if (action == "decide") f.people.reviewer else f.people.preparer
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                member.account,
            )
        return Scenario(
            run,
            id,
            payrollActor(f.people.payroll, member).copy(mfaVerifiedAt = clock.instant()),
        )
    }

    private fun read(action: String, scene: Scenario, actor: Actor): Result<*> =
        when (action) {
            "preparation" ->
                GetPayrollReviewPreparation(
                        reviews,
                        runs,
                        policies,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.run)
            "list" ->
                ListPayrollReviews(
                        reviews,
                        runs,
                        policies,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.run, null, 8)
            "detail" ->
                GetPayrollReview(
                        reviews,
                        policies,
                        approvals,
                        companies,
                        members,
                        identities,
                        transactions,
                        runs,
                        finalizations,
                        jobs,
                        clock,
                        security,
                    )
                    .execute(actor, scene.review)
            else -> error("Unsupported review read")
        }

    private fun mutate(
        action: String,
        scene: Scenario,
        actor: Actor,
        operation: UUID,
        receipts: OperationRepository = operations,
    ): Result<MutationReceipt> =
        when (action.substringBefore('-')) {
            "submit" ->
                SubmitPayrollReview(
                        reviews,
                        runs,
                        policies,
                        approvals,
                        companies,
                        members,
                        identities,
                        receipts,
                        journal,
                        transactions,
                        security,
                        clock,
                    )
                    .execute(
                        actor,
                        operation,
                        scene.review,
                        scene.run,
                        1,
                        "Review calculated payroll",
                    )
            "decide" ->
                DecidePayrollReview(
                        reviews,
                        runs,
                        policies,
                        approvals,
                        companies,
                        members,
                        identities,
                        receipts,
                        journal,
                        transactions,
                        security,
                        clock,
                    )
                    .execute(
                        actor,
                        operation,
                        scene.review,
                        0,
                        0,
                        ApprovalDecision.APPROVE,
                        "Independent approval",
                    )
            "withdraw" ->
                WithdrawPayrollReview(
                        reviews,
                        runs,
                        policies,
                        approvals,
                        companies,
                        members,
                        identities,
                        receipts,
                        journal,
                        transactions,
                        security,
                        clock,
                        finalizations,
                        jobs,
                    )
                    .execute(actor, operation, scene.review, 0, 0, "Correct retained payroll")
            else -> error("Unsupported review command")
        }

    private fun snapshot(actor: Actor): Map<String, Any> {
        val company = requireNotNull(actor.companyId)
        return listOf(
                "payroll_reviews",
                "payroll_review_changes",
                "approval_requests",
                "approval_decisions",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
            .associateWith<String, Any> { count(company, it) } +
            mapOf(
                "reviews" to
                    database()
                        .queryForList(
                            "select concat_ws('|',id,status,version) from payroll_reviews where company_id=? order by id",
                            String::class.java,
                            company,
                        ),
                "approvals" to
                    database()
                        .queryForList(
                            "select concat_ws('|',id,status,version,current_step) from approval_requests where company_id=? order by id",
                            String::class.java,
                            company,
                        ),
            )
    }
}
