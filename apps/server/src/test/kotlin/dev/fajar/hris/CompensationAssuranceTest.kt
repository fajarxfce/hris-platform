package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.EmployeeCompensation
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class CompensationAssuranceTest : PayrollApiFixture() {
    @Autowired private lateinit var policies: PayrollPolicyRepository
    @Autowired private lateinit var compensations: CompensationRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var cutoffs: PayrollCutoffRepository
    private val security = IdentitySecurityPolicy()

    private data class Scenario(val actor: Actor, val salary: EmployeeCompensation)

    @ParameterizedTest
    @ValueSource(strings = ["effective", "history", "list", "review", "employees"])
    fun proofExpiringDuringAccessWaitCannotAuthorizePrivateReads(action: String) {
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
    @ValueSource(strings = ["effective", "history", "list", "review", "employees"])
    fun proofExpiringAfterAcquisitionCannotReleasePrivateData(action: String) {
        val scene = prepare()
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        payrollProbe.afterCompensationRead = { kind, company ->
            if (company == actor.companyId && kind == if (action == "review") "history" else action)
                clock.set(clock.instant().plusSeconds(2))
        }
        try {
            assertEquals("mfa_required", (read(action, scene, actor) as Result.Failed).failure.code)
        } finally {
            payrollProbe.clear()
        }
        assertTrue(
            read(action, scene, actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["effective", "history", "list", "review", "employees"])
    fun cancellationReleasesReadGuardsForTheNextSalaryCommand(action: String) {
        val scene = prepare()
        val barrier = AccountLockProbe.Barrier(scene.actor.accountId)
        accountProbe.current.set(barrier)
        val owner = AtomicReference<Thread>()
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    owner.set(Thread.currentThread())
                    read(action, scene)
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                owner.get().interrupt()
                val failure =
                    assertThrows(ExecutionException::class.java) {
                        pending.get(10, TimeUnit.SECONDS)
                    }
                assertTrue(failure.cause is InterruptedException, failure.toString())
                accountProbe.current.set(null)
                val recovered =
                    pool
                        .submit<Result<MutationReceipt>> { save(scene, UUID.randomUUID()) }
                        .get(10, TimeUnit.SECONDS)
                assertTrue(recovered is Result.Success, recovered.toString())
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["effective", "history", "list", "review", "employees"])
    fun readsCannotAdoptNewGrantsOrRetainRevokedAuthority(action: String) {
        val scene = prepare()
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission like 'payroll.%'",
                scene.actor.companyId,
                scene.actor.accountId,
            )
        assertEquals("access_denied", (read(action, scene) as Result.Failed).failure.code)
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.read')",
                scene.actor.companyId,
                scene.actor.accountId,
            )
        assertEquals(
            "access_denied",
            (read(action, scene, scene.actor.copy(permissions = setOf("company.read")))
                    as Result.Failed)
                .failure
                .code,
        )
        assertTrue(read(action, scene) is Result.Success)
        database()
            .update(
                "update accounts set security_version=security_version+1 where id=?",
                scene.actor.accountId,
            )
        assertEquals("session_revoked", (read(action, scene) as Result.Failed).failure.code)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun expiredRecentProofCannotSaveOrReplayAfterWaiting(replay: Boolean) {
        val scene = prepare()
        val operation = UUID.randomUUID()
        if (replay) assertTrue(save(scene, operation) is Result.Success)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt =
                    clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        val before = snapshot(scene.actor)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<MutationReceipt>> { save(scene, operation, actor) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(2))
                barrier.release.countDown()
                assertEquals(
                    "recent_authentication_required",
                    (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                )
                assertEquals(before, snapshot(scene.actor))
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        val recovered = save(scene, operation, actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(recovered is Result.Success, recovered.toString())
        assertEquals(replay, (recovered as Result.Success).value.replayed)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun lateAuthenticationOrMfaExpiryRollsBackSalaryJournalAndReceipt(mfa: Boolean) {
        val scene = prepare()
        val proof = clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
        val actor =
            if (mfa) scene.actor.copy(mfaVerifiedAt = proof)
            else scene.actor.copy(authenticatedAt = proof)
        val operation = UUID.randomUUID()
        val before = snapshot(scene.actor)
        payrollProbe.beforeJournal = {
            if (it.companyId == actor.companyId && it.action == "payroll.compensation_saved")
                clock.set(clock.instant().plusSeconds(2))
        }
        try {
            assertEquals(
                "recent_authentication_required",
                (save(scene, operation, actor) as Result.Failed).failure.code,
            )
            assertEquals(before, snapshot(scene.actor))
        } finally {
            payrollProbe.clear()
        }
        val renewed = actor.copy(authenticatedAt = clock.instant(), mfaVerifiedAt = clock.instant())
        val recovered = save(scene, operation, renewed)
        assertTrue(recovered is Result.Success, recovered.toString())
        assertEquals(
            before.getValue("employee_compensation_revisions") + 1,
            snapshot(scene.actor).getValue("employee_compensation_revisions"),
        )
        assertEquals(
            Result.Success((recovered as Result.Success).value.copy(replayed = true)),
            save(scene, operation, renewed),
        )
    }

    private fun prepare(): Scenario {
        clock.set(Instant.now())
        val fixture = payrollFixture()
        payrollBody(compensation(fixture))
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                fixture.operator.account,
            )
        val actor = payrollActor(fixture).copy(mfaVerifiedAt = clock.instant())
        val loaded =
            transactions.run(actor) { compensations.current(fixture.company, fixture.employee) }
        val salary = requireNotNull((loaded as Result.Success).value)
        return Scenario(
            actor,
            salary.copy(
                terms =
                    salary.terms.copy(
                        basicSalary = salary.terms.basicSalary + java.math.BigDecimal("100000")
                    )
            ),
        )
    }

    private fun read(action: String, scene: Scenario, actor: Actor = scene.actor): Result<*> =
        when (action) {
            "effective" ->
                GetEmployeeCompensation(
                        policies,
                        compensations,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.salary.employeeId, YearMonth.of(2026, 6))
            "history" ->
                GetCompensationHistory(
                        policies,
                        compensations,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.salary.employeeId, null, 50)
            "list" ->
                ListEmployeeCompensations(
                        policies,
                        compensations,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, YearMonth.of(2026, 6), null, 50)
            "review" ->
                GetCompensationReview(
                        policies,
                        compensations,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.salary.employeeId, YearMonth.of(2026, 6), null, 50)
            "employees" ->
                ListCompensationEmployees(
                        compensations,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, "", null, 20)
            else -> error("Unsupported test action")
        }

    private fun save(
        scene: Scenario,
        operation: UUID,
        actor: Actor = scene.actor,
    ): Result<MutationReceipt> =
        SaveEmployeeCompensation(
                policies,
                compensations,
                people,
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
                scene.salary.employeeId,
                scene.salary.effectiveFrom,
                scene.salary.terms,
                0,
                0,
                "Review compensation",
            )

    private fun snapshot(actor: Actor): Map<String, Int> =
        listOf(
                "employee_compensations",
                "employee_compensation_revisions",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
            .associateWith { count(requireNotNull(actor.companyId), it) }
}
