package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class LeaveBalanceAssuranceTest : LeaveAccountingApiFixture() {
    @Autowired private lateinit var ledgerRepository: LeaveLedgerRepository
    @Autowired private lateinit var policies: LeavePolicyRepository
    @Autowired private lateinit var entitlements: LeaveEntitlementRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var requests: LeaveRequestRepository

    @ParameterizedTest
    @ValueSource(
        strings = ["list", "ledger", "account", "entitlements", "adjust", "accrue", "close"]
    )
    fun readsCommandsAndReceiptsRequireUnexpiredAssuranceAfterAcquisition(operation: String) {
        val f = preparedBalance()
        val actor = balanceOperator(f, assurance = true)
        val invoke = invocation(f, operation)
        val before = accountingRows(f, "leave_ledger")
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { invoke(actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    clock.set(clock.instant().plusSeconds(2))
                    barrier.release.countDown()
                    assertEquals(
                        "mfa_required",
                        (pending.get(5, TimeUnit.SECONDS) as? Result.Failed)?.failure?.code,
                    )
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            assertEquals(before, accountingRows(f, "leave_ledger"))
            val renewed = actor.copy(mfaVerifiedAt = clock.instant())
            val allowed = invoke(renewed)
            assertTrue(allowed is Result.Success, allowed.toString())
            if (operation in setOf("adjust", "accrue", "close")) {
                val rows = accountingRows(f, "leave_ledger")
                assertEquals("mfa_required", (invoke(actor) as? Result.Failed)?.failure?.code)
                val receipt = (allowed as Result.Success).value as MutationReceipt
                assertEquals(Result.Success(receipt.copy(replayed = true)), invoke(renewed))
                assertEquals(rows, accountingRows(f, "leave_ledger"))
            }
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["list", "ledger", "account", "entitlements", "adjust", "accrue", "close"]
    )
    fun aPreviouslyResolvedBalanceGrantCannotSurviveRevocation(operation: String) {
        val f = preparedBalance()
        val actor = balanceOperator(f, assurance = true).copy(mfaVerifiedAt = clock.instant())
        val invoke = invocation(f, operation)
        val before = accountingRows(f, "leave_ledger")
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { invoke(actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=?",
                            f.company,
                            actor.accountId,
                        )
                    barrier.release.countDown()
                    val expected =
                        when (operation) {
                            "list",
                            "ledger" -> "employee_not_found"
                            "account",
                            "entitlements" -> "leave_account_not_found"
                            else -> "access_denied"
                        }
                    assertEquals(
                        expected,
                        (pending.get(5, TimeUnit.SECONDS) as? Result.Failed)?.failure?.code,
                    )
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
            assertEquals(before, accountingRows(f, "leave_ledger"))
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["list", "ledger"])
    fun cancellationDuringAcquisitionReleasesTheBalanceAndAccessGuards(operation: String) {
        val f = preparedBalance()
        val actor = balanceOperator(f, assurance = true).copy(mfaVerifiedAt = clock.instant())
        val read = invocation(f, operation)
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { read(actor) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    assertTrue(pending.cancel(true))
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
                // Joining work on the same executor also observes transaction cleanup.
                assertEquals(true, pool.submit<Boolean> { true }.get(5, TimeUnit.SECONDS))
            }
            val result = invocation(f, "adjust")(actor)
            assertTrue(result is Result.Success, result.toString())
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
        }
    }

    private fun preparedBalance(): LeaveFixture {
        val f = accountingFixture(at = Instant.parse("2027-01-02T03:00:00Z"))
        accountingBody(adjust(f, "2", expectedVersion = 0))
        return f
    }

    private fun balanceOperator(f: LeaveFixture, assurance: Boolean): Actor {
        val permissions =
            setOf("leave.read", "leave.manage", "leave.accrual.post", "leave.year.close")
        permissions.forEach {
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?) on conflict do nothing",
                    f.company,
                    f.managerAccount,
                    it,
                )
        }
        if (assurance)
            database()
                .update(
                    "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                    f.managerAccount,
                )
        val security = IdentitySecurityPolicy()
        return Actor(
            f.managerAccount,
            f.company,
            permissions,
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
            mfaVerifiedAt =
                if (assurance) clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
                else null,
        )
    }

    private fun invocation(f: LeaveFixture, operation: String): (Actor) -> Result<*> {
        val key = UUID.randomUUID()
        val id = UUID.randomUUID()
        val security = IdentitySecurityPolicy()
        return when (operation) {
            "list" -> { actor ->
                    ListEmployeeLeaveBalances(
                            ledgerRepository,
                            policies,
                            people,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, f.employee, 2026, null, 20)
                }
            "ledger" -> { actor ->
                    GetLeaveLedger(
                            ledgerRepository,
                            policies,
                            people,
                            transactions,
                            clock,
                            companies,
                            members,
                            identities,
                            security,
                        )
                        .execute(actor, f.employee, f.type, 2026, null, 20)
                }
            "account" -> {
                val account =
                    database()
                        .queryForObject(
                            "select id from leave_accounts where company_id=? and employment_id=? and type_id=? and balance_year=2026",
                            UUID::class.java,
                            f.company,
                            f.employee,
                            f.type,
                        )!!
                { actor ->
                    GetLeaveAccount(
                            ledgerRepository,
                            people,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, account)
                }
            }
            "entitlements" -> { actor ->
                    GetLeaveEntitlements(
                            ledgerRepository,
                            people,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            entitlements,
                            policies,
                            security,
                        )
                        .execute(actor, f.employee, f.type, 2026)
                }
            "adjust" -> { actor ->
                    AdjustLeaveBalance(
                            ledgerRepository,
                            policies,
                            people,
                            operations,
                            journal,
                            transactions,
                            clock,
                            companies,
                            members,
                            identities,
                            security,
                        )
                        .execute(actor, key, f.employee, f.type, 2026, 2, "Reviewed adjustment", 1)
                }
            "accrue" -> { actor ->
                    PostEmployeeLeaveAccrual(
                            ledgerRepository,
                            policies,
                            entitlements,
                            people,
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
                            key,
                            id,
                            f.employee,
                            f.type,
                            YearMonth.of(2026, 12),
                            0,
                            1,
                            1,
                            "Reviewed entitlement",
                        )
                }
            "close" -> { actor ->
                    CloseEmployeeLeaveYear(
                            ledgerRepository,
                            policies,
                            entitlements,
                            people,
                            companies,
                            members,
                            identities,
                            operations,
                            journal,
                            transactions,
                            clock,
                            requests,
                            security,
                        )
                        .execute(
                            actor,
                            key,
                            id,
                            f.employee,
                            f.type,
                            2026,
                            1,
                            1,
                            0,
                            "Reviewed year closing",
                        )
                }
            else -> error("Unknown fixture operation")
        }
    }
}
