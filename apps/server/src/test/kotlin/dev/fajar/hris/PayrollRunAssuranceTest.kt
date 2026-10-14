package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.payroll.domain.usecases.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class PayrollRunAssuranceTest : PayrollRunApiFixture() {
    @Autowired private lateinit var runs: PayrollRunRepository
    @Autowired private lateinit var periods: PayrollPeriodRepository
    @Autowired private lateinit var sources: PayrollCalculationSourceRepository
    @Autowired private lateinit var policies: PayrollPolicyRepository
    @Autowired private lateinit var reviews: PayrollReviewRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var jobRepository: JobRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    private val security = IdentitySecurityPolicy()

    private data class Scenario(val fixture: CalculationFixture, val run: UUID, val actor: Actor)

    @ParameterizedTest
    @ValueSource(strings = ["review", "list", "detail", "employee"])
    fun expiredMfaAfterAnAccessWaitCannotReleaseCalculationData(action: String) {
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
    @ValueSource(strings = ["review", "list", "detail", "employee"])
    fun acquiredCalculationDataStaysPrivateWhenMfaExpiresBeforeDelivery(action: String) {
        val scene = prepare("read")
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        runProbe.afterRead = { kind, company ->
            if (company == actor.companyId && kind == action)
                clock.set(clock.instant().plusSeconds(2))
        }
        assertEquals("mfa_required", (read(action, scene, actor) as Result.Failed).failure.code)
        runProbe.clear()
        assertTrue(
            read(action, scene, actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["review", "list", "detail", "employee"])
    fun interruptedCalculationReadsReleaseGuardsAndScopeBeforeThreadReuse(action: String) {
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
        strings = ["start", "resume", "abandon", "start-replay", "resume-replay", "abandon-replay"]
    )
    fun writesAndOriginalReceiptsRequireRecentAssuranceAfterWaiting(action: String) {
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
    @ValueSource(strings = ["start", "resume", "abandon"])
    fun expiryDuringJournalingRollsBackJobsRunPeriodAndReceiptTogether(action: String) {
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
                "start" -> "payroll.calculation_started"
                "resume" -> "payroll.calculation_resumed"
                else -> "payroll.calculation_abandoned"
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
    @ValueSource(strings = ["start", "resume", "abandon"])
    fun expiryAfterWritingAReceiptStillRollsBackTheCompleteBusinessCommand(action: String) {
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
        val f = calculationFixture(prepare = false)
        val run =
            if (action == "start") UUID.randomUUID()
            else {
                val lease = beginRun(f)
                if (action == "resume") stopRun(f, lease) else drainRun(f, lease)
                runId(lease)
            }
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.people.preparer.account,
            )
        return Scenario(
            f,
            run,
            payrollActor(f.people.payroll, f.people.preparer).copy(mfaVerifiedAt = clock.instant()),
        )
    }

    private fun read(action: String, scene: Scenario, actor: Actor): Result<*> =
        when (action) {
            "review" ->
                GetPayrollCalculationReview(
                        periods,
                        runs,
                        sources,
                        policies,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.fixture.period)
            "list" ->
                ListPayrollRuns(
                        runs,
                        policies,
                        companies,
                        members,
                        identities,
                        transactions,
                        periods,
                        clock,
                        security,
                    )
                    .execute(actor, scene.fixture.period, null, 20)
            "detail" ->
                GetPayrollRun(
                        runs,
                        policies,
                        companies,
                        members,
                        identities,
                        transactions,
                        jobRepository,
                        periods,
                        reviews,
                        clock,
                        security,
                    )
                    .execute(actor, scene.run, null, 20)
            "employee" ->
                GetPayrollRunEmployee(
                        runs,
                        policies,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.run, scene.fixture.people.payroll.employee)
            else -> error("Unsupported calculation read")
        }

    private fun mutate(
        action: String,
        scene: Scenario,
        actor: Actor,
        operation: UUID,
        receiptStore: OperationRepository = operations,
    ): Result<MutationReceipt> =
        when (action.substringBefore('-')) {
            "start" ->
                StartPayrollCalculation(
                        runs,
                        periods,
                        policies,
                        people,
                        companies,
                        members,
                        identities,
                        jobRepository,
                        journal,
                        transactions,
                        clock,
                        sources,
                        receiptStore,
                        security,
                    )
                    .execute(
                        actor,
                        operation,
                        scene.run,
                        scene.fixture.period,
                        LocalDate.of(2026, 9, 30),
                        0,
                        scene.fixture.work.version,
                        0,
                        "Reviewed salary liability",
                        "Start calculation",
                    )
            "resume" ->
                ResumePayrollCalculation(
                        runs,
                        periods,
                        policies,
                        people,
                        companies,
                        members,
                        identities,
                        jobRepository,
                        receiptStore,
                        journal,
                        transactions,
                        security,
                        clock,
                    )
                    .execute(actor, operation, scene.run, 1, "Resume retained results")
            "abandon" ->
                AbandonPayrollCalculation(
                        runs,
                        periods,
                        policies,
                        people,
                        companies,
                        members,
                        identities,
                        jobRepository,
                        receiptStore,
                        journal,
                        transactions,
                        security,
                        clock,
                        reviews,
                    )
                    .execute(actor, operation, scene.run, 1, 2, "Correct calculation sources")
            else -> error("Unsupported calculation command")
        }

    private fun snapshot(actor: Actor): Map<String, Any> {
        val company = requireNotNull(actor.companyId)
        return listOf(
                "payroll_runs",
                "payroll_run_targets",
                "payroll_run_attempts",
                "payroll_period_changes",
                "background_jobs",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
            .associateWith<String, Any> { count(company, it) } +
            mapOf(
                "runStates" to
                    database()
                        .queryForList(
                            "select concat_ws('|',id,status,version,job_id) from payroll_runs where company_id=? order by id",
                            String::class.java,
                            company,
                        ),
                "periodStates" to
                    database()
                        .queryForList(
                            "select concat_ws('|',id,status,version,current_run_id) from payroll_periods where company_id=? order by id",
                            String::class.java,
                            company,
                        ),
            )
    }
}
