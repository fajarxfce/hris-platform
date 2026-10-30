package dev.fajar.hris

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.repositories.PayrollCutoffRepository
import dev.fajar.hris.people.domain.entities.ContractKind
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired

class LeaveCommandCompletionTest : LeaveAccountingApiFixture() {
    @Autowired
    private lateinit var schedules: dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
    @Autowired
    private lateinit var documents: dev.fajar.hris.documents.domain.repositories.DocumentRepository
    @Autowired
    private lateinit var references:
        dev.fajar.hris.documents.domain.repositories.DocumentReferenceRepository
    @Autowired private lateinit var carry: LeaveCarryRepository
    @Autowired private lateinit var requests: LeaveRequestRepository
    @Autowired private lateinit var policies: LeavePolicyRepository
    @Autowired private lateinit var ledger: LeaveLedgerRepository
    @Autowired private lateinit var entitlements: LeaveEntitlementRepository
    @Autowired private lateinit var approvals: ApprovalRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var cutoffs: PayrollCutoffRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository

    private fun replayed(result: Result<*>) =
        Result.Success(((result as Result.Success).value as MutationReceipt).copy(replayed = true))

    private fun state(f: LeaveFixture): Map<String, Any?> = buildMap {
        for (table in
            listOf(
                "leave_requests",
                "leave_request_changes",
                "leave_allocations",
                "leave_ledger",
                "leave_accrual_postings",
                "leave_type_revisions",
                "approval_requests",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
                "mobile_sync_changes",
            )) put(table, accountingRows(f, table))
        for (table in listOf("leave_requests", "leave_types", "approval_requests")) put(
            "$table heads",
            database()
                .queryForList(
                    "select id,version from $table where company_id=? order by id",
                    f.company,
                ),
        )
        put(
            "balances",
            database()
                .queryForList(
                    "select * from leave_accounts where company_id=? order by id",
                    f.company,
                ),
        )
    }

    @ParameterizedTest
    @CsvSource("policy,journal", "accrual,journal", "accrual,receipt", "cancellation,journal")
    fun commandsAndIndependentAccrualReceiptsRequireAssuranceThroughCompletion(
        operation: String,
        boundary: String,
    ) {
        val f = accountingFixture()
        val id = UUID.randomUUID()
        if (operation == "cancellation") {
            configureWorkAndApprovals(f)
            accountingBody(adjust(f, "5"))
            accountingBody(submit(f, id, listOf("2026-10-05" to "FULL")))
            accountingBody(decide(f, id, 0))
        }
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val base =
            if (operation == "cancellation")
                Actor(
                    f.account,
                    f.company,
                    setOf("leave.self.manage"),
                    clock.instant(),
                    UUID.randomUUID(),
                    credentialVersion = 0,
                )
            else accountingActor(f)
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                base.accountId,
            )
        val original = clock.instant()
        val actor = base.copy(mfaVerifiedAt = original.minus(security.maximumMfaAge).plusSeconds(1))
        val current =
            transactions
                .run(actor) { policies.find(f.company, f.type) }
                .let { (it as Result.Success).value!! }
        val type =
            current.copy(
                effectiveFrom = LocalDate.of(2027, 1, 1),
                policy = current.policy.copy(name = "Reviewed leave policy"),
            )
        var expire = false
        var completions = 0
        fun complete() {
            if (expire) {
                completions++
                clock.set(original.plusSeconds(2))
            }
        }
        val observedJournal =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord) =
                    journal.record(actor, change).also { if (boundary == "journal") complete() }
            }
        val observedPeople =
            object : PeopleRepository by people {
                override fun accountForEmployee(companyId: UUID, employeeId: UUID) =
                    people.accountForEmployee(companyId, employeeId).also {
                        if (boundary == "receipt") complete()
                    }
            }
        val key = UUID.randomUUID()
        fun invoke(selected: Actor): Result<*> =
            when (operation) {
                "policy" ->
                    SaveLeaveType(
                            policies,
                            operations,
                            observedJournal,
                            transactions,
                            companies,
                            members,
                            identities,
                            clock,
                            security,
                        )
                        .execute(selected, key, type, current.version, "Reviewed policy")
                "accrual" ->
                    PostEmployeeLeaveAccrual(
                            ledger,
                            policies,
                            entitlements,
                            observedPeople,
                            companies,
                            members,
                            identities,
                            operations,
                            observedJournal,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(
                            selected,
                            key,
                            id,
                            f.employee,
                            f.type,
                            YearMonth.of(2026, 9),
                            0,
                            1,
                            0,
                            "Reviewed entitlement",
                        )
                else ->
                    RequestLeaveCancellation(
                            requests,
                            ledger,
                            approvals,
                            identities,
                            people,
                            companies,
                            members,
                            operations,
                            observedJournal,
                            transactions,
                            clock,
                            cutoffs,
                            security,
                        )
                        .execute(selected, key, id, 1, "Changed leave plan")
            }
        try {
            val originalReceipt =
                if (boundary == "receipt")
                    invoke(actor.copy(mfaVerifiedAt = original)).also {
                        assertTrue(it is Result.Success, it.toString())
                    }
                else null
            val before = state(f)
            expire = true
            val denied = invoke(actor)
            assertEquals(1, completions)
            assertEquals(
                "mfa_required",
                (denied as? Result.Failed)?.failure?.code,
                "Expired assurance must roll back the entire command or deny its receipt",
            )
            assertEquals(before, state(f))
            expire = false
            val renewed = invoke(actor.copy(mfaVerifiedAt = clock.instant()))
            assertTrue(renewed is Result.Success, renewed.toString())
            if (originalReceipt != null) assertEquals(replayed(originalReceipt), renewed)
            val after = state(f)
            assertEquals(replayed(renewed), invoke(actor.copy(mfaVerifiedAt = clock.instant())))
            assertEquals(after, state(f))
        } finally {
            clock.set(Instant.now())
        }
    }

    @Test
    fun policyContractsAreCopiedBeforeAcquisitionAndKeepTheirOriginalOperationHash() {
        val f = accountingFixture()
        val actor = accountingActor(f)
        val current =
            transactions
                .run(actor) { policies.find(f.company, f.type) }
                .let { (it as Result.Success).value!! }
        val contracts = mutableSetOf(ContractKind.PERMANENT)
        val type =
            current.copy(
                effectiveFrom = LocalDate.of(2027, 1, 1),
                policy = current.policy.copy(allowedContracts = contracts),
            )
        val source =
            object : LeavePolicyRepository by policies {
                override fun lock(companyId: UUID, shared: Boolean) =
                    policies.lock(companyId, shared).also {
                        contracts.clear()
                        contracts += ContractKind.FIXED_TERM
                    }
            }
        val key = UUID.randomUUID()
        val saved =
            SaveLeaveType(
                    source,
                    operations,
                    journal,
                    transactions,
                    companies,
                    members,
                    identities,
                    clock,
                    IdentitySecurityPolicy(enforceMfa = false),
                )
                .execute(actor, key, type, current.version, "Stable contract selection")
        assertTrue(saved is Result.Success, saved.toString())
        val stored =
            transactions
                .run(actor) { policies.find(f.company, f.type) }
                .let { (it as Result.Success).value!! }
        assertEquals(setOf(ContractKind.PERMANENT), stored.policy.allowedContracts)
        val replay =
            SaveLeaveType(
                    policies,
                    operations,
                    journal,
                    transactions,
                    companies,
                    members,
                    identities,
                    clock,
                    IdentitySecurityPolicy(enforceMfa = false),
                )
                .execute(
                    actor,
                    key,
                    type.copy(
                        policy = type.policy.copy(allowedContracts = setOf(ContractKind.PERMANENT))
                    ),
                    current.version,
                    "Stable contract selection",
                )
        assertEquals(replayed(saved), replay)
    }

    @Test
    fun cancellationReceiptCannotBypassRevokedManagementOfAnotherEmployeeRequest() {
        val f = accountingFixture()
        configureWorkAndApprovals(f)
        accountingBody(adjust(f, "5"))
        val id = UUID.randomUUID()
        accountingBody(submit(f, id, listOf("2026-10-05" to "FULL")))
        accountingBody(decide(f, id, 0))
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Leave operator',password_hash from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                f.company,
                account,
            )
        for (permission in listOf("leave.manage", "leave.self.manage")) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                f.company,
                account,
                permission,
            )
        val actor =
            Actor(
                account,
                f.company,
                setOf("leave.manage", "leave.self.manage"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val key = UUID.randomUUID()
        val subject =
            RequestLeaveCancellation(
                requests,
                ledger,
                approvals,
                identities,
                people,
                companies,
                members,
                operations,
                journal,
                transactions,
                clock,
                cutoffs,
                IdentitySecurityPolicy(enforceMfa = false),
            )
        fun invoke() = subject.execute(actor, key, id, 1, "Changed leave plan")
        val original = invoke()
        assertTrue(original is Result.Success, original.toString())
        val before = state(f)
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.manage'",
                f.company,
                account,
            )
        assertEquals("leave_request_not_found", (invoke() as? Result.Failed)?.failure?.code)
        assertEquals(before, state(f))
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.manage')",
                f.company,
                account,
            )
        assertEquals(replayed(original), invoke())
        assertEquals(before, state(f))
    }

    @ParameterizedTest
    @CsvSource("submit", "withdraw")
    fun requestReceiptsRetainCurrentManagementOfTheOriginalEmployee(operation: String) {
        val f = accountingFixture()
        configureWorkAndApprovals(f)
        accountingBody(adjust(f, "5"))
        val id = UUID.randomUUID()
        if (operation == "withdraw") accountingBody(submit(f, id, listOf("2026-10-05" to "FULL")))
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Leave operator',password_hash from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                f.company,
                account,
            )
        for (permission in listOf("leave.manage", "leave.self.manage")) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                f.company,
                account,
                permission,
            )
        val actor =
            Actor(
                account,
                f.company,
                setOf("leave.manage", "leave.self.manage"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val key = UUID.randomUUID()
        val security = IdentitySecurityPolicy(enforceMfa = false)
        fun invoke(): Result<MutationReceipt> =
            if (operation == "submit")
                SubmitLeaveRequest(
                        requests,
                        ledger,
                        policies,
                        people,
                        companies,
                        schedules,
                        approvals,
                        identities,
                        members,
                        operations,
                        journal,
                        transactions,
                        clock,
                        documents,
                        references,
                        cutoffs,
                        security,
                        carry,
                    )
                    .execute(
                        actor,
                        key,
                        id,
                        f.employee,
                        f.type,
                        listOf(RequestedLeaveDay(LocalDate.of(2026, 10, 5), LeavePortion.FULL)),
                        "Independent request",
                    )
            else
                WithdrawLeaveRequest(
                        requests,
                        ledger,
                        approvals,
                        identities,
                        companies,
                        members,
                        operations,
                        journal,
                        transactions,
                        clock,
                        cutoffs,
                        security,
                        carry,
                    )
                    .execute(actor, key, id, 0, "Changed leave plan")
        val original = invoke()
        assertTrue(original is Result.Success, original.toString())
        val before = state(f)
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.manage'",
                f.company,
                account,
            )
        assertEquals("leave_request_not_found", (invoke() as? Result.Failed)?.failure?.code)
        assertEquals(before, state(f))
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.manage')",
                f.company,
                account,
            )
        assertEquals(replayed(original), invoke())
        assertEquals(before, state(f))
    }
}
