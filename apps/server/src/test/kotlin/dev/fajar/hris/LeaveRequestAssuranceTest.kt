package dev.fajar.hris

import dev.fajar.hris.approvals.domain.entities.ApprovalDecision
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.DocumentReferenceRepository
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.entities.LeavePortion
import dev.fajar.hris.leave.domain.entities.RequestedLeaveDay
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.repositories.PayrollCutoffRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class LeaveRequestAssuranceTest : ApprovalApiFixture() {
    @Autowired private lateinit var requests: LeaveRequestRepository
    @Autowired private lateinit var ledger: LeaveLedgerRepository
    @Autowired private lateinit var policies: LeavePolicyRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var approvals: ApprovalRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var cutoffs: PayrollCutoffRepository
    @Autowired private lateinit var schedules: ScheduleRepository
    @Autowired private lateinit var documents: DocumentRepository
    @Autowired private lateinit var references: DocumentReferenceRepository
    @Autowired private lateinit var probe: AccountLockProbe

    @ParameterizedTest
    @ValueSource(strings = ["list", "details"])
    fun originalReadPermissionCannotSurviveRevocationDuringGuardAcquisition(operation: String) {
        val f = leaveFixture()
        val id = pending(f)
        val operator = reviewer(f.company, listOf("leave.read"))
        val actor =
            Actor(
                operator.account,
                f.company,
                setOf("leave.read"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val security = IdentitySecurityPolicy(enforceMfa = false)
        val barrier = AccountLockProbe.Barrier(operator.account)
        probe.current.set(barrier)
        try {
            val result =
                Executors.newSingleThreadExecutor().use { executor ->
                    val reading =
                        executor.submit<Result<*>> {
                            if (operation == "list") {
                                ListLeaveRequests(
                                        requests,
                                        people,
                                        transactions,
                                        clock,
                                        identities,
                                        members,
                                        security,
                                    )
                                    .execute(actor, null, null, null, 20)
                            } else {
                                GetLeaveRequest(
                                        requests,
                                        ledger,
                                        people,
                                        approvals,
                                        members,
                                        identities,
                                        transactions,
                                        clock,
                                        cutoffs,
                                        security,
                                    )
                                    .execute(actor, id, null, 20)
                            }
                        }
                    try {
                        assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission='leave.read'",
                                f.company,
                                operator.account,
                            )
                        barrier.release.countDown()
                        reading.get(10, TimeUnit.SECONDS)
                    } finally {
                        barrier.release.countDown()
                    }
                }
            assertEquals(
                if (operation == "list") "employee_scope_required" else "leave_request_not_found",
                (result as? Result.Failed)?.failure?.code,
            )
        } finally {
            barrier.release.countDown()
            probe.current.set(null)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["list", "details", "submit", "decide", "withdraw", "cancellation"])
    fun expiredAssuranceCannotAuthorizeAReadMutationOrOriginalReceipt(operation: String) {
        val f = leaveFixture()
        val pending = pending(f)
        val permissions = setOf("leave.read", "leave.manage", "leave.approve", "approvals.read")
        val operator = reviewer(f.company, permissions.toList())
        if (operation == "decide") {
            assertEquals(
                200,
                reassign(f, approvalId(f.company, pending), setOf(operator.account)).statusCode(),
            )
        }
        if (operation == "cancellation") assertEquals(200, decide(f, pending, 0).statusCode())
        val version = if (operation == "cancellation") 1L else 0L
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                operator.account,
            )
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val key = UUID.randomUUID()
        val id = if (operation == "submit") UUID.randomUUID() else pending
        val actor =
            Actor(
                operator.account,
                f.company,
                permissions,
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1),
            )
        val invoke: (Actor) -> Result<*> = { current ->
            when (operation) {
                "list" ->
                    ListLeaveRequests(
                            requests,
                            people,
                            transactions,
                            clock,
                            identities,
                            members,
                            security,
                        )
                        .execute(current, null, null, null, 20)
                "details" ->
                    GetLeaveRequest(
                            requests,
                            ledger,
                            people,
                            approvals,
                            members,
                            identities,
                            transactions,
                            clock,
                            cutoffs,
                            security,
                        )
                        .execute(current, id, null, 20)
                "submit" ->
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
                        )
                        .execute(
                            current,
                            key,
                            id,
                            f.employee,
                            f.type,
                            listOf(RequestedLeaveDay(LocalDate.of(2026, 10, 6), LeavePortion.FULL)),
                            "Approved absence request",
                        )
                "decide" ->
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
                            security,
                        )
                        .execute(
                            current,
                            key,
                            id,
                            version,
                            ApprovalDecision.APPROVE,
                            "Reviewed request",
                        )
                "withdraw" ->
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
                        )
                        .execute(current, key, id, version, "Leave plan changed")
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
                            journal,
                            transactions,
                            clock,
                            cutoffs,
                            security,
                        )
                        .execute(current, key, id, version, "Leave plan changed")
            }
        }
        val barrier = AccountLockProbe.Barrier(operator.account)
        probe.current.set(barrier)
        try {
            val expired =
                Executors.newSingleThreadExecutor().use { executor ->
                    val running = executor.submit<Result<*>> { invoke(actor) }
                    try {
                        assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                        clock.set(clock.instant().plusSeconds(2))
                        barrier.release.countDown()
                        running.get(10, TimeUnit.SECONDS)
                    } finally {
                        barrier.release.countDown()
                        probe.current.set(null)
                    }
                }
            assertEquals("mfa_required", (expired as? Result.Failed)?.failure?.code)
            assertEquals(
                version,
                database()
                    .queryForObject(
                        "select version from leave_requests where company_id=? and id=?",
                        Long::class.java,
                        f.company,
                        pending,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from audit_entries where company_id=? and actor_id=?",
                        Int::class.java,
                        f.company,
                        operator.account,
                    ),
            )
            val renewed = actor.copy(mfaVerifiedAt = clock.instant())
            val result = invoke(renewed)
            assertTrue(result is Result.Success, result.toString())
            if (operation !in setOf("list", "details")) {
                val receipt =
                    ((result as Result.Success).value as MutationReceipt).copy(replayed = true)
                assertEquals(Result.Success(receipt), invoke(renewed))
                clock.set(clock.instant().plus(security.maximumMfaAge))
                assertEquals("mfa_required", (invoke(renewed) as? Result.Failed)?.failure?.code)
                assertEquals(
                    Result.Success(receipt),
                    invoke(renewed.copy(mfaVerifiedAt = clock.instant())),
                )
                assertEquals(
                    1,
                    database()
                        .queryForObject(
                            "select count(*) from audit_entries where company_id=? and actor_id=?",
                            Int::class.java,
                            f.company,
                            operator.account,
                        ),
                )
            }
        } finally {
            barrier.release.countDown()
            probe.current.set(null)
        }
    }
}
