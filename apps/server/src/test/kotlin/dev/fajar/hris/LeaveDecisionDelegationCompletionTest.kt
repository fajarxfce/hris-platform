package dev.fajar.hris

import dev.fajar.hris.approvals.domain.entities.ApprovalDecision
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.leave.domain.usecases.DecideLeaveRequest
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.repositories.PayrollCutoffRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired

class LeaveDecisionDelegationCompletionTest : ApprovalApiFixture() {
    @Autowired private lateinit var requests: LeaveRequestRepository
    @Autowired private lateinit var ledger: LeaveLedgerRepository
    @Autowired private lateinit var approvals: ApprovalRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var cutoffs: PayrollCutoffRepository
    @Autowired private lateinit var carry: LeaveCarryRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository

    @ParameterizedTest
    @CsvSource(
        "false,APPROVE,beneficiary",
        "false,REJECT,beneficiary",
        "true,APPROVE,beneficiary",
        "true,REJECT,beneficiary",
        "false,APPROVE,journal",
        "false,REJECT,journal",
        "true,APPROVE,journal",
        "true,REJECT,journal",
    )
    fun delegationMustAuthorizeTheCurrentStageThroughLeaveAndCancellationDecision(
        cancellation: Boolean,
        decision: ApprovalDecision,
        boundary: String,
    ) {
        val f = leaveFixture()
        val id = pending(f)
        if (cancellation) {
            assertEquals(200, decide(f, id, 0).statusCode())
            val response = action(f, id, "cancellation", 1)
            assertEquals(200, response.statusCode(), response.body())
        }
        val recipient = reviewer(f.company)
        val until = clock.instant().plusSeconds(1)
        val delegation = UUID.randomUUID()
        fun delegate(version: Long?, deadline: Instant) {
            val response =
                saveDelegation(
                    f,
                    delegation,
                    delegationBody(
                        f,
                        recipient.account,
                        version,
                        changes =
                            mapOf(
                                "kind" to if (cancellation) "LEAVE_CANCELLATION" else "LEAVE",
                                "validUntil" to deadline.toString(),
                            ),
                    ),
                )
            assertEquals(200, response.statusCode(), response.body())
        }
        delegate(null, until)
        val actor =
            Actor(
                recipient.account,
                f.company,
                setOf("approvals.read", "leave.approve"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        var expire = true
        val observedPeople =
            object : PeopleRepository by people {
                override fun accountForEmployee(companyId: UUID, employeeId: UUID) =
                    people.accountForEmployee(companyId, employeeId).also {
                        if (expire && boundary == "beneficiary") clock.set(until.plusSeconds(1))
                    }
            }
        val observedJournal =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord) =
                    journal.record(actor, change).also {
                        if (expire && boundary == "journal") clock.set(until.plusSeconds(1))
                    }
            }
        val subject =
            DecideLeaveRequest(
                requests,
                ledger,
                approvals,
                identities,
                companies,
                observedPeople,
                members,
                operations,
                observedJournal,
                transactions,
                clock,
                cutoffs,
                IdentitySecurityPolicy(enforceMfa = false),
                carry,
            )
        val key = UUID.randomUUID()
        fun invoke() =
            subject.execute(
                actor,
                key,
                id,
                if (cancellation) 2 else 0,
                decision,
                "Reviewed leave decision",
            )
        fun state(): Map<String, Any?> = buildMap {
            for (table in
                listOf(
                    "leave_requests",
                    "leave_request_changes",
                    "leave_allocations",
                    "leave_ledger",
                    "approval_requests",
                    "approval_decisions",
                    "operation_receipts",
                    "audit_entries",
                    "outbox_events",
                    "mobile_sync_changes",
                )) put(
                table,
                database()
                    .queryForObject(
                        "select count(*) from $table where company_id=?",
                        Long::class.java,
                        f.company,
                    ),
            )
            put(
                "request",
                database()
                    .queryForList(
                        "select id,status,version from leave_requests where company_id=? order by id",
                        f.company,
                    ),
            )
            put(
                "approvals",
                database()
                    .queryForList(
                        "select id,status,version,current_step from approval_requests where company_id=? order by id",
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
        try {
            val before = state()
            val denied = invoke()
            assertEquals(
                "not_assigned_approver",
                (denied as? Result.Failed)?.failure?.code,
                "Expired delegation must not commit a decision",
            )
            assertEquals(before, state())
            expire = false
            delegate(0, clock.instant().plusSeconds(60))
            val renewed = invoke()
            assertTrue(renewed is Result.Success, renewed.toString())
            val committed = state()
            assertEquals(
                Result.Success((renewed as Result.Success).value.copy(replayed = true)),
                invoke(),
            )
            assertEquals(committed, state())
        } finally {
            clock.set(Instant.now())
        }
    }

    @ParameterizedTest
    @CsvSource(
        "false,expiry",
        "true,expiry",
        "false,beneficiary",
        "true,beneficiary",
        "false,phase",
        "true,phase",
        "false,overflow",
        "true,overflow",
    )
    fun originalReceiptUsesItsRetainedApprovalAndCurrentIndependentScope(
        cancellation: Boolean,
        change: String,
    ) {
        val base = leaveFixture()
        val f =
            if (change == "beneficiary") {
                val employee = UUID.randomUUID()
                val response =
                    command(
                        base.admin,
                        "/api/v1/companies/${base.company}/employees",
                        json.writeValueAsString(
                            mapOf(
                                "id" to employee,
                                "employeeNumber" to "E${employee.toString().take(8)}",
                                "person" to
                                    mapOf(
                                        "id" to UUID.randomUUID(),
                                        "legalName" to "Unbound leave employee",
                                        "nationality" to "ID",
                                    ),
                                "terms" to
                                    mapOf(
                                        "effectiveFrom" to "2025-01-01",
                                        "startDate" to "2025-01-01",
                                        "status" to "ACTIVE",
                                        "contract" to "PERMANENT",
                                        "managerId" to base.manager,
                                    ),
                                "reason" to "Independent receipt fixture",
                            )
                        ),
                        base.adminCsrf,
                        UUID.randomUUID(),
                    )
                assertEquals(200, response.statusCode(), response.body())
                base.copy(employee = employee, worker = base.admin, workerCsrf = base.adminCsrf)
            } else base
        val id = pending(f)
        if (cancellation) {
            assertEquals(200, decide(f, id, 0).statusCode())
            val response = action(f, id, "cancellation", 1)
            assertEquals(200, response.statusCode(), response.body())
        }
        val recipient = reviewer(f.company)
        val until = clock.instant().plusSeconds(1)
        val delegation = UUID.randomUUID()
        fun delegate(version: Long?, deadline: Instant) {
            val response =
                saveDelegation(
                    f,
                    delegation,
                    delegationBody(
                        f,
                        recipient.account,
                        version,
                        changes =
                            mapOf(
                                "kind" to if (cancellation) "LEAVE_CANCELLATION" else "LEAVE",
                                "validUntil" to deadline.toString(),
                            ),
                    ),
                )
            assertEquals(200, response.statusCode(), response.body())
        }
        delegate(null, until)
        if (change == "overflow") {
            val column = if (cancellation) "cancellation_approval_id" else "approval_id"
            val approval =
                database()
                    .queryForObject(
                        "select $column from leave_requests where company_id=? and id=?",
                        UUID::class.java,
                        f.company,
                        id,
                    )!!
            val reassigned = reassign(f, approval, setOf(recipient.account))
            assertEquals(200, reassigned.statusCode(), reassigned.body())
        }
        val actor =
            Actor(
                recipient.account,
                f.company,
                setOf("approvals.read", "leave.approve"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val subject =
            DecideLeaveRequest(
                requests,
                ledger,
                approvals,
                identities,
                companies,
                people,
                members,
                operations,
                journal,
                transactions,
                clock,
                cutoffs,
                IdentitySecurityPolicy(enforceMfa = false),
                carry,
            )
        val key = UUID.randomUUID()
        fun invoke() =
            subject.execute(
                actor,
                key,
                id,
                if (cancellation) 2 else 0,
                if (cancellation) ApprovalDecision.REJECT else ApprovalDecision.APPROVE,
                "Original reviewed decision",
            )
        fun state(): Map<String, Any?> = buildMap {
            for (table in
                listOf(
                    "leave_request_changes",
                    "leave_ledger",
                    "approval_decisions",
                    "operation_receipts",
                    "audit_entries",
                    "outbox_events",
                    "mobile_sync_changes",
                )) put(
                table,
                database()
                    .queryForObject(
                        "select count(*) from $table where company_id=?",
                        Long::class.java,
                        f.company,
                    ),
            )
            put(
                "request",
                database()
                    .queryForList(
                        "select id,status,version from leave_requests where company_id=? order by id",
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
        try {
            val original = invoke()
            assertTrue(original is Result.Success, original.toString())
            val replayed = Result.Success((original as Result.Success).value.copy(replayed = true))
            when (change) {
                "overflow" -> seedDelegations(f, recipient.account, 200)
                "expiry" -> clock.set(until.plusSeconds(1))
                "beneficiary" -> {
                    val response =
                        command(
                            f.admin,
                            "/api/v1/companies/${f.company}/employees/${f.employee}/account-link",
                            json.writeValueAsString(
                                mapOf(
                                    "accountId" to recipient.account,
                                    "expectedVersion" to 0,
                                    "reason" to "Identity verified independently",
                                )
                            ),
                            f.adminCsrf,
                            UUID.randomUUID(),
                        )
                    assertEquals(200, response.statusCode(), response.body())
                }
                else -> {
                    val version = if (cancellation) 3L else 1L
                    val response = action(f, id, "cancellation", version)
                    assertEquals(200, response.statusCode(), response.body())
                    val current =
                        database()
                            .queryForObject(
                                "select cancellation_approval_id from leave_requests where company_id=? and id=?",
                                UUID::class.java,
                                f.company,
                                id,
                            )!!
                    val other = reviewer(f.company)
                    val reassigned = reassign(f, current, setOf(other.account))
                    assertEquals(200, reassigned.statusCode(), reassigned.body())
                }
            }
            val before = state()
            val result = invoke()
            if (change in setOf("phase", "overflow"))
                assertEquals(
                    replayed,
                    result,
                    "Original receipt retains its independent scope after later workflow or delegation changes",
                )
            else
                assertEquals(
                    if (change == "expiry") "not_assigned_approver" else "self_approval_denied",
                    (result as? Result.Failed)?.failure?.code,
                )
            assertEquals(before, state())
            if (change == "expiry") {
                delegate(0, clock.instant().plusSeconds(60))
                val renewed = state()
                assertEquals(replayed, invoke())
                assertEquals(renewed, state())
            }
        } finally {
            clock.set(Instant.now())
        }
    }
}
