package dev.fajar.hris

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.PayrollPublicationReadiness
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.payroll.domain.usecases.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class PayrollFinalizationAssuranceTest : PayrollFinalizationApiFixture() {
    @Autowired private lateinit var finalizations: PayrollFinalizationRepository
    @Autowired private lateinit var runs: PayrollRunRepository
    @Autowired private lateinit var policies: PayrollPolicyRepository
    @Autowired private lateinit var reviews: PayrollReviewRepository
    @Autowired private lateinit var approvals: ApprovalRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var jobs: JobRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    private val security = IdentitySecurityPolicy()

    private data class Scenario(val fixture: PublicationFixture, val id: UUID, val actor: Actor)

    @ParameterizedTest
    @ValueSource(strings = ["directory", "preparation", "list", "detail"])
    fun waitingForAccessCannotReleasePublicationDataAfterMfaExpires(action: String) {
        val scene = prepare()
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
    @ValueSource(strings = ["directory", "preparation", "list", "detail"])
    fun completedAcquisitionStillRequiresCurrentAssurance(action: String) {
        val scene = prepare()
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        finalizationProbe.afterRead = { kind, company ->
            if (kind == action && company == actor.companyId)
                clock.set(clock.instant().plusSeconds(2))
        }
        assertEquals("mfa_required", (read(action, scene, actor) as Result.Failed).failure.code)
        finalizationProbe.clear()
        assertTrue(
            read(action, scene, actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["directory", "preparation", "list", "detail"])
    fun interruptedReadReleasesGuardsAndLeavesNoThreadOwnership(action: String) {
        val scene = prepare()
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
    @ValueSource(strings = ["directory", "preparation", "list", "detail"])
    fun publicationReadsIntersectOriginalAndCurrentAuthority(action: String) {
        val scene = prepare()
        assertEquals(
            "access_denied",
            (read(action, scene, scene.actor.copy(permissions = setOf("company.read")))
                    as Result.Failed)
                .failure
                .code,
        )
        val barrier = AccountLockProbe.Barrier(scene.actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { read(action, scene, scene.actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='payroll.finalize'",
                        scene.actor.companyId,
                        scene.actor.accountId,
                    )
                barrier.release.countDown()
                assertEquals(
                    "access_denied",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun startAndOriginalReceiptStillRequireRecentMfaAfterWaiting(replay: Boolean) {
        val scene = prepare(started = false)
        val operation = UUID.randomUUID()
        if (replay) assertTrue(mutate(scene, scene.actor, operation) is Result.Success)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt =
                    clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        val before = snapshot(actor)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<MutationReceipt>> { mutate(scene, actor, operation) }
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
        val recovered = mutate(scene, actor.copy(mfaVerifiedAt = clock.instant()), operation)
        assertTrue(recovered is Result.Success, recovered.toString())
        assertEquals(replay, (recovered as Result.Success).value.replayed)
    }

    @ParameterizedTest
    @ValueSource(strings = ["queue", "journal", "receipt"])
    fun lateExpiryRollsBackJobPublicationAdmissionJournalAndReceipt(boundary: String) {
        val scene = prepare(started = false)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt =
                    clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        val before = snapshot(actor)
        val operation = UUID.randomUUID()
        val receipts =
            object : OperationRepository by operations {
                override fun record(
                    actor: Actor,
                    key: OperationKey,
                    receipt: MutationReceipt,
                ): Result<Unit> =
                    operations.record(actor, key, receipt).also {
                        if (boundary == "receipt") clock.set(clock.instant().plusSeconds(2))
                    }
            }
        val queue =
            object : JobRepository by jobs {
                override fun pendingCount(companyId: UUID): Result<Int> =
                    jobs.pendingCount(companyId).also {
                        if (boundary == "queue") clock.set(clock.instant().plusSeconds(2))
                    }
            }
        payrollProbe.beforeJournal = {
            if (boundary == "journal" && it.action == "payroll.finalization_started")
                clock.set(clock.instant().plusSeconds(2))
        }
        assertEquals(
            "recent_authentication_required",
            (mutate(scene, actor, operation, receipts, queue) as Result.Failed).failure.code,
        )
        payrollProbe.clear()
        assertEquals(before, snapshot(actor))
        assertTrue(
            mutate(scene, actor.copy(mfaVerifiedAt = clock.instant()), operation) is Result.Success
        )
    }

    @Test
    fun sourceAcquisitionFailuresKeepTheirClassificationAndDomainReadinessBlocksStart() {
        val scene = prepare(started = false)
        val unavailable = Result.Failed(Failure(FailureKind.CONFLICT, "database_conflict"))
        val failed =
            object : PayrollFinalizationRepository by finalizations {
                override fun readiness(
                    company: UUID,
                    run: UUID,
                ): Result<PayrollPublicationReadiness> = unavailable
            }
        assertEquals(unavailable, preparation(failed).execute(scene.actor, scene.fixture.run))
        val changed =
            object : PayrollFinalizationRepository by finalizations {
                override fun readiness(company: UUID, run: UUID) =
                    Result.Success(PayrollPublicationReadiness(1, 0, 0, 0))
            }
        val blocked = preparation(changed).execute(scene.actor, scene.fixture.run) as Result.Success
        assertEquals("stale_employment_version", blocked.value.blockingIssues.single().code)
        assertTrue(blocked.value.availableActions.isEmpty())
        assertEquals(0, count(scene.actor.companyId!!, "payroll_finalizations"))
    }

    private fun prepare(started: Boolean = true): Scenario {
        clock.set(Instant.now())
        val f = approved()
        val id = UUID.randomUUID()
        if (started) payrollBody(startFinalization(f, finalizationBody(f, id)))
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.finalizer.account,
            )
        return Scenario(
            f,
            id,
            payrollActor(f.calculation.people.payroll, f.finalizer)
                .copy(mfaVerifiedAt = clock.instant()),
        )
    }

    private fun preparation(repository: PayrollFinalizationRepository = finalizations) =
        GetPayrollFinalizationPreparation(
            repository,
            runs,
            reviews,
            policies,
            approvals,
            people,
            companies,
            members,
            identities,
            jobs,
            transactions,
            clock,
            security,
        )

    private fun read(action: String, scene: Scenario, actor: Actor): Result<*> =
        when (action) {
            "directory" ->
                ListPayrollPublicationRuns(
                        finalizations,
                        policies,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, YearMonth.of(2026, 9), YearMonth.of(2026, 9), null, null, 50)
            "preparation" -> preparation().execute(actor, scene.fixture.run)
            "list" ->
                ListPayrollFinalizations(
                        finalizations,
                        policies,
                        companies,
                        members,
                        identities,
                        transactions,
                        runs,
                        clock,
                        security,
                    )
                    .execute(actor, scene.fixture.run, null, 8)
            "detail" ->
                GetPayrollFinalization(
                        finalizations,
                        policies,
                        companies,
                        members,
                        identities,
                        transactions,
                        jobs,
                        runs,
                        reviews,
                        clock,
                        security,
                    )
                    .execute(actor, scene.id)
            else -> error("Unsupported finalization read")
        }

    private fun mutate(
        scene: Scenario,
        actor: Actor,
        operation: UUID,
        receipts: OperationRepository = operations,
        queue: JobRepository = jobs,
    ): Result<MutationReceipt> =
        StartPayrollFinalization(
                finalizations,
                runs,
                reviews,
                policies,
                approvals,
                people,
                companies,
                members,
                identities,
                queue,
                journal,
                transactions,
                security,
                clock,
                receipts,
            )
            .execute(
                actor,
                operation,
                scene.id,
                scene.fixture.run,
                scene.fixture.review,
                1,
                1,
                1,
                "Publish independently approved payroll",
            )

    private fun snapshot(actor: Actor): Map<String, Int> =
        listOf(
                "payroll_finalizations",
                "payroll_assessments",
                "background_jobs",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
            .associateWith { count(actor.companyId!!, it) }
}
