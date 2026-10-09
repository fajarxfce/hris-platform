package dev.fajar.hris.leave.domain.usecases

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.documents.domain.repositories.DocumentReferenceRepository
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.policies.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.repositories.PayrollCutoffRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.ScheduledDay
import dev.fajar.hris.workforce.domain.policies.resolveCalendar
import dev.fajar.hris.workforce.domain.repositories.ScheduleRepository
import java.time.*
import java.util.UUID

class SubmitLeaveRequest(
    private val requests: LeaveRequestRepository,
    private val ledger: LeaveLedgerRepository,
    private val policies: LeavePolicyRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val schedules: ScheduleRepository,
    private val approvals: ApprovalRepository,
    private val identities: IdentityRepository,
    private val members: MembershipRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val documents: DocumentRepository,
    private val references: DocumentReferenceRepository,
    private val cutoffs: PayrollCutoffRepository,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        id: UUID,
        employeeId: UUID,
        typeId: UUID,
        days: List<RequestedLeaveDay>,
        reason: String,
        attachmentRevisionIds: List<UUID> = emptyList(),
    ): Result<MutationReceipt> {
        if ("leave.manage" !in actor.permissions && "leave.self.manage" !in actor.permissions)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        val valid = validateRequestedLeaveDays(days, reason)
        if (valid is Result.Failed) return valid
        val attachmentInput = validateLeaveAttachmentIds(attachmentRevisionIds)
        if (attachmentInput is Result.Failed) return attachmentInput
        val key =
            OperationKey(
                "leave.request_submit",
                operationId,
                listOf(id.toString(), employeeId.toString(), typeId.toString(), reason) +
                    days
                        .sortedBy { it.workDate }
                        .flatMap { listOf(it.workDate.toString(), it.portion.name) } +
                    if (attachmentRevisionIds.isEmpty()) emptyList()
                    else
                        listOf("attachments") + attachmentRevisionIds.map { it.toString() }.sorted(),
            )
        val company = requireNotNull(actor.companyId)
        val from = days.minOf { it.workDate }
        val until = days.maxOf { it.workDate }
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val lock = ledger.lock(company, employeeId)
            if (lock is Result.Failed) return@run lock
            val cutoffLock = cutoffs.lock(company)
            if (cutoffLock is Result.Failed) return@run cutoffLock
            val peopleLock = people.lockReportingLines(company)
            if (peopleLock is Result.Failed) return@run peopleLock
            if (attachmentRevisionIds.isNotEmpty()) {
                val documentLock = documents.lock(company)
                if (documentLock is Result.Failed) return@run documentLock
            }
            val approvalLock = approvals.lock(company)
            if (approvalLock is Result.Failed) return@run approvalLock
            val companyLock = companies.lock(company)
            if (companyLock is Result.Failed) return@run companyLock
            val memberLock = members.lock(company)
            if (memberLock is Result.Failed) return@run memberLock
            val accountLock = identities.lockAccount(actor.accountId)
            if (accountLock is Result.Failed) return@run accountLock
            val checkedActor =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checkedActor is Result.Failed) return@run checkedActor
            val live = (checkedActor as Result.Success).value
            if ("leave.manage" !in live.permissions && "leave.self.manage" !in live.permissions)
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val now = clock.instant()
            val companyResult = companies.find(company)
            if (companyResult is Result.Failed) return@run companyResult
            val settings =
                (companyResult as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = now.atZone(ZoneId.of(settings.timezone)).toLocalDate()
            val employeeResult = people.find(company, employeeId, until)
            if (employeeResult is Result.Failed) return@run employeeResult
            val employee =
                (employeeResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            val currentResult = people.find(company, employeeId, today)
            if (currentResult is Result.Failed) return@run currentResult
            val current = (currentResult as Result.Success).value
            if (
                "leave.manage" !in live.permissions &&
                    (employee.person.accountId != actor.accountId ||
                        current?.terms?.isWorkingOn(today) != true)
            )
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))

            val frozen =
                cutoffs.frozenMonths(
                    company,
                    employeeId,
                    days.map { java.time.YearMonth.from(it.workDate) }.toSet(),
                )
            if (frozen is Result.Failed) return@run frozen
            if ((frozen as Result.Success).value)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "payroll_period_frozen"))

            val calendarResult =
                schedules.calendar(company, employeeId, from, until).flatMap(::resolveCalendar)
            if (calendarResult is Result.Failed) return@run calendarResult
            val calendar = (calendarResult as Result.Success).value
            val requestedDates = days.map { it.workDate }.toSet()
            if (
                calendar.days.any { it.workDate in requestedDates && it is ScheduledDay.Unassigned }
            )
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "leave_schedule_missing"))
            val first =
                calendar.days
                    .filterIsInstance<ScheduledDay.Work>()
                    .firstOrNull { it.workDate in requestedDates }
                    ?.workDate
                    ?: return@run Result.Failed(
                        Failure(FailureKind.VALIDATION, "no_leave_working_days")
                    )
            val typeResult = policies.effective(company, typeId, first)
            if (typeResult is Result.Failed) return@run typeResult
            val type = (typeResult as Result.Success).value
            if (type == null || !type.active)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "leave_type_unavailable"))
            if (type.policy.attachmentRequired && attachmentRevisionIds.isEmpty())
                return@run Result.Failed(
                    Failure(
                        FailureKind.VALIDATION,
                        "leave_attachment_required",
                        fields = mapOf("attachmentRevisionIds" to "required"),
                    )
                )
            val attachments = mutableListOf<LeaveAttachment>()
            for (revisionId in attachmentRevisionIds.sortedBy { it.toString() }) {
                val revisionResult = documents.revision(company, revisionId)
                if (revisionResult is Result.Failed) return@run revisionResult
                val revision =
                    (revisionResult as Result.Success).value
                        ?: return@run Result.Failed(
                            Failure(FailureKind.VALIDATION, "leave_attachment_unavailable")
                        )
                val documentResult = documents.find(company, revision.documentId)
                if (documentResult is Result.Failed) return@run documentResult
                val evidence =
                    snapshotLeaveAttachment(
                        (documentResult as Result.Success).value,
                        revision,
                        employeeId,
                    )
                if (evidence is Result.Failed) return@run evidence
                attachments += (evidence as Result.Success).value
            }
            val history = people.effectiveRevisions(company, employeeId, from, until)
            if (history is Result.Failed) return@run history
            val planned =
                planLeaveDays(days, calendar, (history as Result.Success).value, type.policy)
            if (planned is Result.Failed) return@run planned
            val charged = (planned as Result.Success).value
            val occupied = requests.occupancy(company, employeeId, from, until)
            if (occupied is Result.Failed) return@run occupied
            val overlap = validateLeaveOverlap(charged, (occupied as Result.Success).value)
            if (overlap is Result.Failed) return@run overlap
            val movements = leaveLedgerMovements(charged, LeaveBalanceEffect.RESERVE)
            for (movement in movements) {
                val balance = ledger.balance(company, employeeId, typeId, movement.year)
                if (balance is Result.Failed) return@run balance
                val enough = validateLeaveMovement((balance as Result.Success).value, movement)
                if (enough is Result.Failed) return@run enough
            }
            val context =
                ApprovalContext(
                    UUID.randomUUID(),
                    id,
                    ApprovalKind.LEAVE,
                    actor.accountId,
                    employee.person.accountId,
                    if (current?.terms?.isWorkingOn(today) == true) current.managerAccountId
                    else null,
                    today,
                    type.code,
                    java.math.BigDecimal.ZERO,
                    now,
                )
            val templateResult =
                approvals.templates(company, ApprovalKind.LEAVE, today).flatMap {
                    selectApprovalTemplate(it, context)
                }
            if (templateResult is Result.Failed) return@run templateResult
            val template = (templateResult as Result.Success).value
            val candidateResult =
                members.candidates(
                    company,
                    approvalCandidateIds(template, context),
                    approvalCandidatePermissions(template),
                    201,
                )
            if (candidateResult is Result.Failed) return@run candidateResult
            val snapshotResult =
                snapshotApproval(template, context, (candidateResult as Result.Success).value)
            if (snapshotResult is Result.Failed) return@run snapshotResult
            val snapshot = (snapshotResult as Result.Success).value
            val request =
                LeaveRequest(
                    id,
                    employeeId,
                    employee.employeeNumber,
                    employee.person.legalName,
                    employee.person.accountId,
                    actor.accountId,
                    now,
                    LeavePolicySnapshot(type.id, type.code, type.appliedRevision, type.policy),
                    charged,
                    reason,
                    LeaveStatus.PENDING,
                    snapshot.id,
                    null,
                    0,
                    attachments.toList(),
                )
            val entries =
                movements.map {
                    LeaveLedgerEntry(
                        UUID.randomUUID(),
                        employeeId,
                        typeId,
                        it.year,
                        it.kind,
                        id,
                        id,
                        it.availableDelta,
                        it.reservedDelta,
                        it.consumedDelta,
                        actor.accountId,
                        now,
                        reason,
                    )
                }
            if (attachmentRevisionIds.isNotEmpty()) {
                val retained =
                    references.retain(
                        company,
                        DocumentReferenceOrigin(DocumentReferenceKind.LEAVE_REQUEST, id, 0),
                        attachmentRevisionIds.toSet(),
                        actor.accountId,
                        now,
                    )
                if (retained is Result.Failed) return@run retained
            }
            approvals
                .create(company, snapshot)
                .flatMap { requests.create(company, request) }
                .flatMap { receipt ->
                    ledger
                        .append(company, entries)
                        .flatMap { operations.record(actor, key, receipt) }
                        .flatMap {
                            journal.record(
                                actor,
                                ChangeRecord(
                                    "leave_request",
                                    id,
                                    "leave.request_submitted",
                                    mapOf(
                                        "employmentId" to employeeId.toString(),
                                        "approvalId" to snapshot.id.toString(),
                                    ),
                                    reason,
                                ),
                            )
                        }
                        .map { receipt }
                }
        }
    }
}
