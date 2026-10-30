package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.repositories.ExpensePolicyRepository
import dev.fajar.hris.expenses.domain.usecases.SaveExpenseCategory
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.ContractKind
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class, ExpenseCommandProbeConfiguration::class)
class ExpensePolicyAssuranceTest : PeopleApiFixture() {
    @Autowired private lateinit var policies: ExpensePolicyRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var accountProbe: AccountLockProbe
    @Autowired private lateinit var commandProbe: ExpenseCommandProbe
    private val security = IdentitySecurityPolicy()

    @Test
    fun admittedContractSelectionIsDetachedBeforeWaitingAndReceiptRecoveryRetainsIt() {
        val original = prepare(false)
        val contracts = original.category.policy.allowedContracts.toMutableSet()
        val scene =
            original.copy(
                category =
                    original.category.copy(
                        policy = original.category.policy.copy(allowedContracts = contracts)
                    )
            )
        val barrier = AccountLockProbe.Barrier(scene.actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<MutationReceipt>> { invoke(scene) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                contracts.remove(ContractKind.FIXED_TERM)
                barrier.release.countDown()
                val result = pending.get(10, TimeUnit.SECONDS)
                assertTrue(result is Result.Success, result.toString())
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
        val saved =
            transactions.run(scene.actor) {
                policies.find(requireNotNull(scene.actor.companyId), scene.category.id)
            }
        assertTrue(saved is Result.Success, saved.toString())
        assertEquals(
            original.category.policy.allowedContracts,
            (saved as Result.Success).value?.policy?.allowedContracts,
        )
        val before = snapshot(scene)
        val replay = invoke(original)
        assertTrue(replay is Result.Success, replay.toString())
        assertTrue((replay as Result.Success).value.replayed)
        assertEquals(before, snapshot(scene))
    }

    private data class Scenario(
        val actor: Actor,
        val category: ExpenseCategory,
        val expectedVersion: Long?,
        val operation: UUID = UUID.randomUUID(),
    )

    @AfterEach
    fun releasePolicyProbes() {
        accountProbe.current.getAndSet(null)?.release?.countDown()
        commandProbe.clear()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun expiryWhileWaitingCannotSaveOrReplayPolicy(existing: Boolean) {
        val scene = prepare(existing)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val before = snapshot(scene)
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
        assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code)
        assertEquals(before, snapshot(scene))
        val renewed = actor.copy(mfaVerifiedAt = clock.instant())
        val accepted = invoke(scene, renewed)
        assertTrue(accepted is Result.Success, accepted.toString())
        val committed = snapshot(scene)
        assertEquals("mfa_required", (invoke(scene, actor) as? Result.Failed)?.failure?.code)
        assertEquals(committed, snapshot(scene))
        assertEquals(
            Result.Success((accepted as Result.Success).value.copy(replayed = true)),
            invoke(scene, renewed),
        )
        assertEquals(committed, snapshot(scene))
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun expiryAfterJournalRollsBackPolicyRevisionAndReceiptTogether(existing: Boolean) {
        val scene = prepare(existing)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val before = snapshot(scene)
        commandProbe.afterJournal = {
            if (it.companyId == scene.actor.companyId && it.action == "expenses.category_saved")
                clock.set(clock.instant().plusSeconds(2))
        }
        try {
            val result = invoke(scene, actor)
            assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code)
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
    @ValueSource(booleans = [false, true])
    fun interruptedPolicySaveReleasesItsGuardsAndTheSameExecutorCanRecover(existing: Boolean) {
        val scene = prepare(existing)
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

    private fun prepare(existing: Boolean): Scenario {
        clock.set(Instant.now())
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash,mfa_secret_encrypted) select ?,?,'Expense policy fixture',password_hash,'fixture-enrolled' from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
        for (permission in listOf("company.read", "expenses.policy.manage")) {
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    company,
                    account,
                    permission,
                )
        }
        val actor =
            Actor(
                account,
                company,
                setOf("company.read", "expenses.policy.manage"),
                clock.instant(),
                UUID.randomUUID(),
                mfaVerifiedAt = clock.instant(),
                credentialVersion = 0,
            )
        val category =
            ExpenseCategory(
                UUID.randomUUID(),
                "TRAVEL",
                0,
                0,
                LocalDate.of(2026, 1, 1),
                ExpensePolicy(
                    "Business travel",
                    BigDecimal("500000.00"),
                    BigDecimal("2000000.00"),
                    true,
                    true,
                    30,
                    setOf(ContractKind.PERMANENT, ContractKind.FIXED_TERM),
                ),
                true,
            )
        if (existing) assertTrue(invoke(Scenario(actor, category, null)) is Result.Success)
        return Scenario(
            actor,
            category.copy(policy = category.policy.copy(name = "Reviewed travel")),
            if (existing) 0 else null,
        )
    }

    // HTTP fixture setup disables MFA; exercise the use case with enabled policy and real stores.
    private fun invoke(scene: Scenario, actor: Actor = scene.actor) =
        SaveExpenseCategory(
                policies,
                companies,
                members,
                identities,
                operations,
                journal,
                transactions,
                clock,
                security,
            )
            .execute(
                actor,
                scene.operation,
                scene.category,
                scene.expectedVersion,
                "Review expense policy",
            )

    private fun snapshot(scene: Scenario): Map<String, Any> {
        val company = checkNotNull(scene.actor.companyId)
        val tables =
            listOf(
                "expense_categories",
                "expense_category_revisions",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
        return tables.associateWith { table ->
            checkNotNull(
                database()
                    .queryForObject(
                        "select count(*) from $table where company_id=?",
                        Long::class.java,
                        company,
                    )
            )
        } +
            mapOf(
                "heads" to
                    database()
                        .queryForList(
                            "select id,code,version from expense_categories where company_id=? order by id",
                            company,
                        )
            )
    }
}
