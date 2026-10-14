package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.PayrollPolicy
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.payroll.domain.usecases.*
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

class PayrollPolicyAssuranceTest : PayrollApiFixture() {
    @Autowired private lateinit var policies: PayrollPolicyRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var cutoffs: PayrollCutoffRepository
    private val security = IdentitySecurityPolicy()

    private data class Scenario(val actor: Actor, val policy: PayrollPolicy)

    @ParameterizedTest
    @ValueSource(strings = ["effective", "history", "rules", "review"])
    fun proofExpiringDuringAccessWaitCannotAuthorizeReads(action: String) {
        val scene = prepare()
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { read(action, actor) }
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
        assertTrue(read(action, actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success)
    }

    @ParameterizedTest
    @ValueSource(strings = ["effective", "history", "review"])
    fun proofExpiringAfterAcquisitionCannotReleaseResults(action: String) {
        val scene = prepare()
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        payrollProbe.afterPolicyRead = { kind, company ->
            if (company == actor.companyId && kind == if (action == "review") "history" else action)
                clock.set(clock.instant().plusSeconds(2))
        }
        try {
            assertEquals("mfa_required", (read(action, actor) as Result.Failed).failure.code)
        } finally {
            payrollProbe.clear()
        }
        assertTrue(read(action, actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success)
    }

    @ParameterizedTest
    @ValueSource(strings = ["effective", "history", "rules", "review"])
    fun cancellationReleasesGuardsForTheNextCommand(action: String) {
        val scene = prepare()
        val barrier = AccountLockProbe.Barrier(scene.actor.accountId)
        accountProbe.current.set(barrier)
        val owner = AtomicReference<Thread>()
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    owner.set(Thread.currentThread())
                    read(action, scene.actor)
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                owner.get().interrupt()
                val thrown =
                    assertThrows(ExecutionException::class.java) {
                        pending.get(10, TimeUnit.SECONDS)
                    }
                assertTrue(thrown.cause is InterruptedException, thrown.toString())
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
    @ValueSource(booleans = [false, true])
    fun expiredRecentProofCannotMutateOrReplayAfterWaiting(replay: Boolean) {
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
        val accepted = save(scene, operation, actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(accepted is Result.Success, accepted.toString())
        assertEquals(replay, (accepted as Result.Success).value.replayed)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun lateAuthenticationOrMfaExpiryRollsBackRevisionJournalAndReceipt(mfa: Boolean) {
        val scene = prepare()
        val proof = clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
        val actor =
            if (mfa) scene.actor.copy(mfaVerifiedAt = proof)
            else scene.actor.copy(authenticatedAt = proof)
        val operation = UUID.randomUUID()
        val before = snapshot(scene.actor)
        payrollProbe.beforeJournal = {
            if (it.companyId == actor.companyId && it.action == "payroll.policy_saved")
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
        val accepted = save(scene, operation, renewed)
        assertTrue(accepted is Result.Success, accepted.toString())
        assertEquals(
            before.getValue("payroll_policy_revisions") + 1,
            snapshot(scene.actor).getValue("payroll_policy_revisions"),
        )
        assertEquals(
            Result.Success((accepted as Result.Success).value.copy(replayed = true)),
            save(scene, operation, renewed),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["effective", "history", "rules", "review"])
    fun readsIntersectOriginalAndLivePayrollGrants(action: String) {
        val scene = prepare()
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission like 'payroll.%'",
                scene.actor.companyId,
                scene.actor.accountId,
            )
        assertEquals("access_denied", (read(action, scene.actor) as Result.Failed).failure.code)
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.read')",
                scene.actor.companyId,
                scene.actor.accountId,
            )
        val oldScope = scene.actor.copy(permissions = setOf("company.read"))
        assertEquals("access_denied", (read(action, oldScope) as Result.Failed).failure.code)
        assertTrue(read(action, scene.actor) is Result.Success)
    }

    private fun prepare(): Scenario {
        clock.set(Instant.now())
        val fixture = payrollFixture()
        payrollBody(policy(fixture))
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                fixture.operator.account,
            )
        val actor = payrollActor(fixture).copy(mfaVerifiedAt = clock.instant())
        val current = transactions.run(actor) { policies.current(fixture.company) }
        val policy = requireNotNull((current as Result.Success).value)
        return Scenario(
            actor,
            policy.copy(
                minimumMonthlyWage = policy.minimumMonthlyWage + java.math.BigDecimal("100000")
            ),
        )
    }

    private fun read(action: String, actor: Actor): Result<*> =
        when (action) {
            "effective" ->
                GetPayrollPolicy(
                        policies,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, YearMonth.of(2026, 6))
            "history" ->
                GetPayrollPolicyHistory(
                        policies,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, null, 50)
            "rules" ->
                ListPayrollIncomeTaxRules(
                        policies,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor)
            "review" ->
                GetPayrollPolicyReview(
                        policies,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, YearMonth.of(2026, 6), null, 50)
            else -> error("Unsupported test action")
        }

    private fun save(
        scene: Scenario,
        operation: UUID,
        actor: Actor = scene.actor,
    ): Result<MutationReceipt> =
        SavePayrollPolicy(
                policies,
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
            .execute(actor, operation, scene.policy, 0, "Review company payroll policy")

    private fun snapshot(actor: Actor): Map<String, Int> =
        listOf(
                "payroll_policies",
                "payroll_policy_revisions",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
            .associateWith { count(requireNotNull(actor.companyId), it) }
}
