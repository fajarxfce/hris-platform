package dev.fajar.hris

import dev.fajar.hris.approvals.domain.entities.ApprovalDecision
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.LeaveCarryRepository
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.repositories.PayrollCutoffRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class LeaveCarryCommandAssuranceTest : LeaveCarryStorageFixture() {
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var schedules: ScheduleRepository
    @Autowired private lateinit var documents: DocumentRepository
    @Autowired private lateinit var references: DocumentReferenceRepository
    @Autowired private lateinit var cutoffs: PayrollCutoffRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    private val security = IdentitySecurityPolicy(enforceMfa = true)
    private val selected = listOf(RequestedLeaveDay(LocalDate.of(2027, 3, 15), LeavePortion.FULL))

    private fun prepared(operation: String): Pair<PreparedCarry, UUID> {
        val c = carried()
        configureWorkAndApprovals(c.f)
        val id = UUID.randomUUID()
        if (operation in setOf("decide", "withdraw"))
            accountingBody(submit(c.f, id, listOf("2027-03-15" to "FULL")))
        if (operation == "close") clock.set(java.time.Instant.parse("2028-01-05T03:00:00Z"))
        return c to id
    }

    private fun actor(c: PreparedCarry, operation: String): Actor {
        val reviewer = operation == "decide"
        val operator = if (operation in setOf("adjust", "close")) accountingActor(c.f) else null
        val account = operator?.accountId ?: if (reviewer) c.f.managerAccount else c.f.account
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                account,
            )
        return Actor(
            account,
            c.f.company,
            operator?.permissions
                ?: if (reviewer) setOf("leave.team.approve") else setOf("leave.self.manage"),
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = operator?.credentialVersion ?: 0,
            mfaVerifiedAt = clock.instant(),
        )
    }

    private fun state(c: PreparedCarry): Map<String, Any?> = buildMap {
        for (table in
            listOf(
                "leave_requests",
                "leave_year_closings",
                "leave_carry_buckets",
                "leave_request_changes",
                "leave_allocations",
                "leave_carry_allocations",
                "leave_carry_movements",
                "leave_ledger",
                "approval_requests",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
                "mobile_sync_changes",
            )) {
            put(table, accountingRows(c.f, table))
        }
        put("bucket", storedBucket(c))
        put("balance", storedBalance(c))
        put(
            "requests",
            database()
                .queryForList(
                    "select id,status,version from leave_requests where company_id=? order by id",
                    c.f.company,
                ),
        )
    }

    private fun invoke(
        operation: String,
        c: PreparedCarry,
        actor: Actor,
        id: UUID,
        key: UUID,
        carry: LeaveCarryRepository = carryRepository,
        journal: ChangeJournalRepository = this.journal,
        days: List<RequestedLeaveDay> = selected,
        revision: String? = null,
    ): Result<*> =
        when (operation) {
            "close" ->
                CloseEmployeeLeaveYear(
                        carryLedger,
                        carryPolicies,
                        carryEntitlements,
                        people,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        clock,
                        carryRequests,
                        security,
                        carry,
                    )
                    .execute(
                        actor,
                        key,
                        id,
                        c.f.employee,
                        c.f.type,
                        2027,
                        success(
                                transactions.run(actor) {
                                    carryPolicies.find(c.f.company, c.f.type)
                                }
                            )!!
                            .version,
                        1,
                        0,
                        "Carry closing fixture",
                    )
            "adjust" ->
                AdjustLeaveBalance(
                        carryLedger,
                        carryPolicies,
                        people,
                        operations,
                        journal,
                        transactions,
                        clock,
                        companies,
                        members,
                        identities,
                        security,
                        carry,
                    )
                    .execute(
                        actor,
                        key,
                        c.f.employee,
                        c.f.type,
                        2027,
                        -2,
                        "Carry adjustment fixture",
                        1,
                    )
            "preview" ->
                PreviewLeaveRequest(
                        carryRequests,
                        carryLedger,
                        carryPolicies,
                        people,
                        companies,
                        schedules,
                        identities,
                        members,
                        transactions,
                        clock,
                        cutoffs,
                        security,
                        carry,
                    )
                    .execute(actor, c.f.employee, c.f.type, days)
            "submit" ->
                SubmitLeaveRequest(
                        carryRequests,
                        carryLedger,
                        carryPolicies,
                        people,
                        companies,
                        schedules,
                        carryApprovals,
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
                        c.f.employee,
                        c.f.type,
                        days,
                        "Carry assurance fixture",
                        previewRevision = revision,
                    )
            "decide" ->
                DecideLeaveRequest(
                        carryRequests,
                        carryLedger,
                        carryApprovals,
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
                        carry,
                    )
                    .execute(actor, key, id, 0, ApprovalDecision.APPROVE, "Carry approval fixture")
            "withdraw" ->
                WithdrawLeaveRequest(
                        carryRequests,
                        carryLedger,
                        carryApprovals,
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
                    .execute(actor, key, id, 0, "Carry withdrawal fixture")
            else -> error("Unknown fixture operation")
        }

    @ParameterizedTest
    @CsvSource(
        "submit,journal",
        "decide,journal",
        "withdraw,journal",
        "submit,carry",
        "preview,carry",
        "adjust,carry",
        "adjust,journal",
        "close,carry",
        "close,journal",
    )
    fun assuranceThatExpiresAfterAcquisitionOrJournalingRollsBackAllPrivateWork(
        operation: String,
        phase: String,
    ) {
        val (c, id) = prepared(operation)
        val actor =
            actor(c, operation)
                .copy(mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1))
        val key = UUID.randomUUID()
        val before = state(c)
        val lateCarry =
            object : LeaveCarryRepository by carryRepository {
                override fun buckets(
                    companyId: UUID,
                    employeeId: UUID,
                    typeId: UUID,
                    years: Set<Int>,
                ): Result<List<LeaveCarryBucket>> =
                    carryRepository.buckets(companyId, employeeId, typeId, years).also {
                        if (phase == "carry") clock.set(clock.instant().plusSeconds(2))
                    }
            }
        val lateJournal =
            object : ChangeJournalRepository {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    journal.record(actor, change).also {
                        if (phase == "journal") clock.set(clock.instant().plusSeconds(2))
                    }
            }
        val result = invoke(operation, c, actor, id, key, lateCarry, lateJournal)
        assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code)
        assertEquals(before, state(c))
        val recovered = invoke(operation, c, actor.copy(mfaVerifiedAt = clock.instant()), id, key)
        assertTrue(recovered is Result.Success, recovered.toString())
        if (operation == "preview") assertEquals(before, state(c))
        else {
            val receipt = (recovered as Result.Success).value as MutationReceipt
            assertEquals(
                Result.Success(receipt.copy(replayed = true)),
                invoke(operation, c, actor.copy(mfaVerifiedAt = clock.instant()), id, key),
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["submit", "decide", "withdraw", "adjust", "close"])
    fun cancellationAfterJournalCannotCommitTheCarryTransitionOrReceipt(operation: String) {
        val (c, id) = prepared(operation)
        val actor = actor(c, operation)
        val key = UUID.randomUUID()
        val before = state(c)
        val cancelled =
            object : ChangeJournalRepository {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> {
                    val saved = journal.record(actor, change)
                    assertTrue(saved is Result.Success, saved.toString())
                    throw CancellationException("Carry journal fixture")
                }
            }
        assertThrows(CancellationException::class.java) {
            invoke(operation, c, actor, id, key, journal = cancelled)
        }
        assertEquals(before, state(c))
        assertTrue(invoke(operation, c, actor, id, key) is Result.Success)
    }

    @ParameterizedTest
    @ValueSource(strings = ["preview", "submit"])
    fun callerOwnedDatesCannotAlterAReviewOrSubmissionAfterAcquisition(operation: String) {
        val (c, id) = prepared(operation)
        val actor = actor(c, operation)
        val key = UUID.randomUUID()
        val control = success(invoke("preview", c, actor, id, key)) as LeaveRequestPreview
        val callerDates = selected.toMutableList()
        val changingSource =
            object : LeaveCarryRepository by carryRepository {
                override fun buckets(
                    companyId: UUID,
                    employeeId: UUID,
                    typeId: UUID,
                    years: Set<Int>,
                ): Result<List<LeaveCarryBucket>> =
                    carryRepository.buckets(companyId, employeeId, typeId, years).also {
                        callerDates.clear()
                    }
            }
        val result =
            success(
                invoke(
                    operation,
                    c,
                    actor,
                    id,
                    key,
                    carry = changingSource,
                    days = callerDates,
                    revision = control.revision,
                )
            )
        assertTrue(callerDates.isEmpty())
        if (operation == "preview")
            assertEquals(control.revision, (result as LeaveRequestPreview).revision)
        else {
            val request =
                success(
                    transactions.run(accountingActor(c.f)) { carryRequests.find(c.f.company, id) }
                )!!
            assertEquals(selected.map { it.workDate }, request.days.map { it.workDate })
            assertEquals(2, storedBucket(c).reservedHalfDays)
        }
    }
}
