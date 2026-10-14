package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.PayrollInputTerms
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.payroll.domain.usecases.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.math.BigDecimal
import java.time.Instant
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

class PayrollInputAssuranceTest : PayrollPeriodApiFixture() {
    @Autowired private lateinit var inputs: PayrollInputRepository
    @Autowired private lateinit var sources: PayrollWorkSourceRepository
    @Autowired private lateinit var policies: PayrollPolicyRepository
    @Autowired private lateinit var cutoffs: PayrollCutoffRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    private val security = IdentitySecurityPolicy()
    private val month = YearMonth.of(2026, 9)

    private data class Scenario(
        val fixture: ProcessingFixture,
        val source: WorkSource,
        val actor: Actor,
    )

    @ParameterizedTest
    @ValueSource(
        strings = ["detail", "revision", "history", "list", "employees", "review", "source"]
    )
    fun anExpiredProofAfterAnAccessWaitCannotReleaseInputData(action: String) {
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
    @ValueSource(
        strings = ["detail", "revision", "history", "list", "employees", "review", "source"]
    )
    fun acquiredInputResultsStayPrivateWhenAssuranceExpiresBeforeDelivery(action: String) {
        val scene = prepare()
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        periodProbe.afterInputRead = { kind, company ->
            if (company == actor.companyId && kind == if (action == "review") "history" else action)
                clock.set(clock.instant().plusSeconds(2))
        }
        assertEquals("mfa_required", (read(action, scene, actor) as Result.Failed).failure.code)
        periodProbe.clear()
        assertTrue(
            read(action, scene, actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["detail", "revision", "history", "list", "employees", "review", "source"]
    )
    fun cancelledReadsReleaseTheirGuardsAndDoNotContaminateTheNextRequest(action: String) {
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
    @ValueSource(strings = ["save", "verify", "save-replay", "verify-replay"])
    fun originalReceiptsAndNewInputCommandsRequireRecentAssuranceAfterWaiting(action: String) {
        val scene =
            prepare(withInput = !action.startsWith("save"), verifier = action.startsWith("verify"))
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
    @ValueSource(strings = ["save", "verify"])
    fun expiryDuringInputJournalingRollsBackTheHeadHistoryAndReceipt(action: String) {
        val scene = prepare(withInput = action != "save", verifier = action == "verify")
        val actor =
            scene.actor.copy(
                mfaVerifiedAt =
                    clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        val operation = UUID.randomUUID()
        val before = snapshot(actor)
        payrollProbe.beforeJournal = { row ->
            if (
                row.action ==
                    if (action == "save") "payroll.input_saved" else "payroll.input_verified"
            )
                clock.set(clock.instant().plusSeconds(2))
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
    @ValueSource(strings = ["save", "verify"])
    fun replayAlsoRechecksAssuranceAfterReadingTheBeneficiaryOrHistoricalPreparers(action: String) {
        val scene = prepare(withInput = action != "save", verifier = action == "verify")
        val operation = UUID.randomUUID()
        assertTrue(mutate(action, scene, scene.actor, operation) is Result.Success)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt =
                    clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        val before = snapshot(actor)
        val delayedPeople =
            object : PeopleRepository by people {
                override fun accountForEmployee(companyId: UUID, employeeId: UUID): Result<UUID?> =
                    people.accountForEmployee(companyId, employeeId).also {
                        if (action == "save" && companyId == actor.companyId)
                            clock.set(clock.instant().plusSeconds(2))
                    }
            }
        periodProbe.afterInputRead = { kind, company ->
            if (action == "verify" && company == actor.companyId && kind == "authors")
                clock.set(clock.instant().plusSeconds(2))
        }
        assertEquals(
            "recent_authentication_required",
            (mutate(action, scene, actor, operation, delayedPeople) as Result.Failed).failure.code,
        )
        periodProbe.clear()
        assertEquals(before, snapshot(actor))
        val recovered =
            mutate(action, scene, actor.copy(mfaVerifiedAt = clock.instant()), operation)
        assertTrue(recovered is Result.Success, recovered.toString())
        assertTrue((recovered as Result.Success).value.replayed)
    }

    private fun prepare(withInput: Boolean = true, verifier: Boolean = false): Scenario {
        clock.set(Instant.now())
        val f = processingFixture()
        val source = closeWork(f)
        if (withInput) payrollBody(inputSave(f, source))
        val member = if (verifier) f.reviewer else f.preparer
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                member.account,
            )
        return Scenario(
            f,
            source,
            payrollActor(f.payroll, member).copy(mfaVerifiedAt = clock.instant()),
        )
    }

    private fun read(action: String, scene: Scenario, actor: Actor): Result<*> =
        when (action) {
            "detail",
            "revision" ->
                GetPayrollInput(
                        inputs,
                        policies,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(
                        actor,
                        scene.fixture.payroll.employee,
                        month,
                        if (action == "revision") 0 else null,
                    )
            "history" ->
                GetPayrollInputHistory(
                        inputs,
                        policies,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.fixture.payroll.employee, month, null, 20)
            "list" ->
                ListPayrollInputs(
                        inputs,
                        policies,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, month, null, null, 20)
            "employees" ->
                ListPayrollInputEmployees(
                        inputs,
                        policies,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, month, "", null, null, 20)
            "review" ->
                GetPayrollInputReview(
                        inputs,
                        sources,
                        policies,
                        cutoffs,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.fixture.payroll.employee, month, null, 20)
            "source" ->
                GetPayrollWorkSource(
                        sources,
                        policies,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.fixture.payroll.employee, month)
            else -> error("Unsupported input read")
        }

    private fun mutate(
        action: String,
        scene: Scenario,
        actor: Actor,
        operation: UUID,
        peopleSource: PeopleRepository = people,
    ): Result<MutationReceipt> =
        if (action.startsWith("save"))
            SavePayrollInput(
                    inputs,
                    sources,
                    policies,
                    peopleSource,
                    companies,
                    members,
                    identities,
                    operations,
                    journal,
                    transactions,
                    security,
                    clock,
                    cutoffs,
                )
                .execute(
                    actor,
                    operation,
                    scene.fixture.payroll.employee,
                    month,
                    scene.source.job,
                    scene.source.version,
                    0,
                    PayrollInputTerms(
                        emptyList(),
                        emptyList(),
                        BigDecimal.ZERO,
                        null,
                        emptyList(),
                        null,
                        "Reviewed monthly input",
                    ),
                    null,
                    "Prepare monthly input",
                )
        else
            VerifyPayrollInput(
                    inputs,
                    sources,
                    policies,
                    peopleSource,
                    companies,
                    members,
                    identities,
                    operations,
                    journal,
                    transactions,
                    security,
                    clock,
                    cutoffs,
                )
                .execute(
                    actor,
                    operation,
                    scene.fixture.payroll.employee,
                    month,
                    0,
                    "Independent input verification",
                )

    private fun snapshot(actor: Actor) =
        listOf(
                "payroll_inputs",
                "payroll_input_revisions",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
            .associateWith { count(requireNotNull(actor.companyId), it) }
}
