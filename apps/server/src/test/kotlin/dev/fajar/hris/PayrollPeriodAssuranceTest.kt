package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.payroll.domain.usecases.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class PayrollPeriodAssuranceTest : PayrollPeriodApiFixture() {
    @Autowired private lateinit var periods: PayrollPeriodRepository
    @Autowired private lateinit var policies: PayrollPolicyRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    // General HTTP fixtures omit MFA enrollment; exercise the production policy explicitly.
    private val security = IdentitySecurityPolicy()
    private val getPeriod
        get() =
            GetPayrollPeriod(
                periods,
                policies,
                people,
                companies,
                members,
                identities,
                transactions,
                clock,
                security,
            )

    private val listPeriods
        get() =
            ListPayrollPeriods(
                periods,
                policies,
                companies,
                members,
                identities,
                transactions,
                clock,
                security,
            )

    private val employees
        get() =
            ListPayrollPeriodEmployees(
                periods,
                people,
                companies,
                members,
                identities,
                transactions,
                clock,
                security,
            )

    private val cancelPeriod
        get() =
            CancelPayrollPeriod(
                periods,
                policies,
                companies,
                members,
                identities,
                operations,
                journal,
                transactions,
                security,
                clock,
            )

    private val createWithAssurance
        get() =
            CreatePayrollPeriod(
                periods,
                policies,
                people,
                companies,
                members,
                identities,
                operations,
                journal,
                transactions,
                security,
                clock,
            )

    private data class Scenario(val fixture: ProcessingFixture, val actor: Actor, val id: UUID)

    @ParameterizedTest
    @ValueSource(strings = ["list", "detail", "employees"])
    fun expiredMfaAfterAnAccessWaitCannotReleasePayrollData(action: String) {
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
    @ValueSource(strings = ["list", "detail", "employees"])
    fun acquiredResultsRemainPrivateWhenAssuranceExpiresBeforeDelivery(action: String) {
        val scene = prepare()
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val advance: (String, UUID) -> Unit = { kind, company ->
            if (company == actor.companyId && kind == action)
                clock.set(clock.instant().plusSeconds(2))
        }
        periodProbe.afterRead = advance
        payrollProbe.afterCompensationRead = advance
        assertEquals("mfa_required", (read(action, scene, actor) as Result.Failed).failure.code)
        periodProbe.clear()
        payrollProbe.clear()
        assertTrue(
            read(action, scene, actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["list", "detail", "employees"])
    fun cancellationReleasesGuardsAndDoesNotLeakInterruptionToTheNextRequest(action: String) {
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
                val next =
                    pool
                        .submit<Result<*>> {
                            assertFalse(Thread.currentThread().isInterrupted)
                            read(action, scene, scene.actor)
                        }
                        .get(10, TimeUnit.SECONDS)
                assertTrue(next is Result.Success, next.toString())
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["create", "cancel", "create-replay", "cancel-replay"])
    fun originalReceiptsAndNewCommandsRequireRecentAssuranceAfterWaiting(action: String) {
        val scene = prepare()
        val id = if (action.startsWith("create")) UUID.randomUUID() else scene.id
        if (action.startsWith("create"))
            assertTrue(
                cancelPeriod.execute(scene.actor, UUID.randomUUID(), scene.id, 0, "Replace draft")
                    is Result.Success
            )
        val operation = UUID.randomUUID()
        val replay = action.endsWith("replay")
        if (replay) assertTrue(mutate(action, scene, scene.actor, id, operation) is Result.Success)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt =
                    clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        val before = snapshot(actor)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { mutate(action, scene, actor, id, operation) }
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
            mutate(action, scene, actor.copy(mfaVerifiedAt = clock.instant()), id, operation)
        assertTrue(recovered is Result.Success, recovered.toString())
        assertEquals(replay, (recovered as Result.Success).value.replayed)
    }

    @ParameterizedTest
    @ValueSource(strings = ["create", "cancel"])
    fun expiryDuringJournalCompletionRollsBackTheEntirePeriodCommand(action: String) {
        val scene = prepare()
        if (action == "create")
            assertTrue(
                cancelPeriod.execute(scene.actor, UUID.randomUUID(), scene.id, 0, "Replace draft")
                    is Result.Success
            )
        val id = if (action == "create") UUID.randomUUID() else scene.id
        val operation = UUID.randomUUID()
        val actor =
            scene.actor.copy(
                mfaVerifiedAt =
                    clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        val before = snapshot(actor)
        payrollProbe.beforeJournal = { row ->
            if (
                row.action ==
                    if (action == "create") "payroll.period_created" else "payroll.period_cancelled"
            )
                clock.set(clock.instant().plusSeconds(2))
        }
        assertEquals(
            "recent_authentication_required",
            (mutate(action, scene, actor, id, operation) as Result.Failed).failure.code,
        )
        payrollProbe.clear()
        assertEquals(before, snapshot(actor))
        assertTrue(
            mutate(action, scene, actor.copy(mfaVerifiedAt = clock.instant()), id, operation)
                is Result.Success
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["create", "cancel", "create-replay", "cancel-replay"])
    fun currentEnrollmentIsRequiredForCommandsAndReceipts(action: String) {
        val scene = prepare()
        val id = if (action.startsWith("create")) UUID.randomUUID() else scene.id
        if (action.startsWith("create"))
            assertTrue(
                cancelPeriod.execute(scene.actor, UUID.randomUUID(), scene.id, 0, "Replace draft")
                    is Result.Success
            )
        val operation = UUID.randomUUID()
        val replay = action.endsWith("replay")
        if (replay) assertTrue(mutate(action, scene, scene.actor, id, operation) is Result.Success)
        database()
            .update(
                "update accounts set mfa_secret_encrypted=null where id=?",
                scene.actor.accountId,
            )
        val before = snapshot(scene.actor)
        try {
            val rejected = mutate(action, scene, scene.actor, id, operation)
            assertEquals(
                "mfa_setup_required",
                (rejected as? Result.Failed)?.failure?.code,
                rejected.toString(),
            )
            assertEquals(before, snapshot(scene.actor))
        } finally {
            database()
                .update(
                    "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                    scene.actor.accountId,
                )
        }
        val recovered = mutate(action, scene, scene.actor, id, operation)
        assertTrue(recovered is Result.Success, recovered.toString())
        assertEquals(replay, (recovered as Result.Success).value.replayed)
        assertEquals(
            Result.Success(recovered.value.copy(replayed = true)),
            mutate(action, scene, scene.actor, id, operation),
        )
    }

    private fun prepare(): Scenario {
        clock.set(Instant.now())
        val f = processingFixture()
        val id = UUID.randomUUID()
        payrollBody(periodCreate(f, periodBody(f, id)))
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.preparer.account,
            )
        return Scenario(
            f,
            payrollActor(f.payroll, f.preparer).copy(mfaVerifiedAt = clock.instant()),
            id,
        )
    }

    private fun read(action: String, scene: Scenario, actor: Actor): Result<*> =
        when (action) {
            "list" ->
                listPeriods.execute(
                    actor,
                    YearMonth.of(2026, 9),
                    YearMonth.of(2026, 9),
                    null,
                    null,
                    20,
                )
            "detail" -> getPeriod.execute(actor, scene.id, null, 20, null, 20)
            "employees" -> employees.execute(actor, "", null, 20)
            else -> error("Unsupported fixture action")
        }

    private fun mutate(
        action: String,
        scene: Scenario,
        actor: Actor,
        id: UUID,
        operation: UUID,
    ): Result<MutationReceipt> =
        if (action.startsWith("create"))
            createWithAssurance.execute(
                actor,
                operation,
                id,
                YearMonth.of(2026, 9),
                LocalDate.of(2026, 10, 2),
                setOf(scene.fixture.payroll.employee),
                "Prepare payroll",
            )
        else cancelPeriod.execute(actor, operation, id, 0, "Replace payroll draft")

    private fun snapshot(actor: Actor) =
        listOf(
                "payroll_periods",
                "payroll_period_members",
                "payroll_period_changes",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
            .associateWith { count(requireNotNull(actor.companyId), it) }
}
