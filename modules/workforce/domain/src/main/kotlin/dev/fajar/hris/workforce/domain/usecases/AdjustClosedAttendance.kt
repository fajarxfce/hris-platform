package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.*
import java.util.UUID

class AdjustClosedAttendance(
    private val periods: WorkPeriodRepository,
    private val adjustments: AttendanceAdjustmentRepository,
    private val attendance: AttendanceRepository,
    private val corrections: AttendanceCorrectionRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        employeeId: UUID,
        date: LocalDate,
        reviewRevision: String,
        clockIn: Instant?,
        clockOut: Instant?,
        breakMinutes: Int,
        decisions: List<AttendanceEvidenceDecision>,
        reason: String,
    ): Result<MutationReceipt> {
        val allowed = actor.requirePermission("attendance.correct")
        if (allowed is Result.Failed) return allowed
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (
            date.year !in 2000..2100 ||
                !reviewRevision.matches(Regex("[0-9a-f]{64}")) ||
                decisions.size > 32 ||
                decisions.map { it.eventId }.distinct().size != decisions.size ||
                decisions.any { it.expectedVersion < 0 || it.expectedVersion == Long.MAX_VALUE } ||
                reason.isBlank() ||
                reason.length > 1000
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_attendance_adjustment"))
        val evidence = decisions.sortedBy { it.eventId.toString() }.toList()
        val key =
            OperationKey(
                "attendance.adjust",
                operationId,
                buildList {
                    add(employeeId.toString())
                    add(date.toString())
                    add(reviewRevision)
                    add(clockIn?.toString())
                    add(clockOut?.toString())
                    add(breakMinutes.toString())
                    add(reason)
                    for (decision in evidence) {
                        add(decision.eventId.toString())
                        add(decision.expectedVersion.toString())
                        add(decision.decision.name)
                    }
                },
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val receipt = (replay as Result.Success).value
            val locked = periods.lockExisting(company, YearMonth.from(date), exclusive = true)
            if (locked is Result.Failed) return@run locked
            val dayGuard = attendance.lockDay(company, employeeId, date)
            if (dayGuard is Result.Failed) return@run dayGuard
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authority = identities.access(actor.accountId, company)
            if (authority is Result.Failed) return@run authority
            val access = (authority as Result.Success).value
            val checked = validateCompanySessionActor(actor, access, clock.instant(), security)
            if (checked is Result.Failed) return@run checked
            val live = (checked as Result.Success).value
            val permission = live.requirePermission("attendance.correct")
            if (permission is Result.Failed) return@run permission
            val employeeResult = people.find(company, employeeId, date)
            if (employeeResult is Result.Failed) return@run employeeResult
            val employee =
                (employeeResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            val currentResult = people.findAtInstant(company, employeeId, clock.instant())
            if (currentResult is Result.Failed) return@run currentResult
            val currentEmployee = (currentResult as Result.Success).value
            if (
                employee.person.accountId == actor.accountId ||
                    currentEmployee?.person?.accountId == actor.accountId
            )
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "self_correction_denied"))
            val entriesResult = attendance.entries(company, employeeId, date, date)
            if (entriesResult is Result.Failed) return@run entriesResult
            val entries = (entriesResult as Result.Success).value
            if (
                evidence.isNotEmpty() &&
                    (!canReviewAttendance(live, currentEmployee, employee.person.accountId) ||
                        entries.any { entry ->
                            entry.accountId == actor.accountId &&
                                evidence.any { it.eventId == entry.capture.id }
                        })
            )
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "attendance_review_denied"))
            if (receipt != null)
                return@run validateCompanySessionActor(actor, access, clock.instant(), security)
                    .map { receipt }
            val period = (locked as Result.Success).value
            val jobId = period?.jobId
            if (period?.status != WorkPeriodStatus.CLOSED || jobId == null)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "attendance_adjustment_requires_closed_period")
                )
            val now = clock.instant()
            val valid =
                validateAttendanceCorrection(date, clockIn, clockOut, breakMinutes, reason, now)
            if (valid is Result.Failed) return@run valid
            if (!employee.terms.isWorkingOn(date))
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "employee_unavailable"))
            val originalResult = periods.snapshot(company, jobId, employeeId)
            if (originalResult is Result.Failed) return@run originalResult
            val original =
                (originalResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "work_period_snapshot_unavailable")
                    )
            val previousResult = adjustments.latest(company, jobId, employeeId)
            if (previousResult is Result.Failed) return@run previousResult
            val previous = (previousResult as Result.Success).value
            val revision = previous?.summary?.revision ?: 0
            val correctionResult = corrections.latest(company, employeeId, date, date)
            if (correctionResult is Result.Failed) return@run correctionResult
            val previousCorrection = (correctionResult as Result.Success).value.singleOrNull()
            if (revision == Long.MAX_VALUE || previousCorrection?.version == Long.MAX_VALUE)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "attendance_adjustment_version_exhausted")
                )
            val reference =
                WorkforceEmployeeReference(
                    employee.id,
                    employee.employeeNumber,
                    employee.person.legalName,
                    employee.terms.status,
                    employee.version,
                )
            if (
                attendanceAdjustmentRevision(
                    company,
                    reference,
                    period,
                    date,
                    revision,
                    previousCorrection,
                    entries,
                ) != reviewRevision
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "attendance_adjustment_changed")
                )
            val verified = validateAdjustmentEvidence(entries, evidence)
            if (verified is Result.Failed) return@run verified
            if (
                evidence.isEmpty() &&
                    previousCorrection != null &&
                    previousCorrection.clockIn == clockIn &&
                    previousCorrection.clockOut == clockOut &&
                    previousCorrection.breakMinutes == breakMinutes
            )
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "attendance_adjustment_unchanged")
                )
            val source = previous?.snapshot ?: original
            val day =
                source.days.singleOrNull { it.workDate == date }
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "attendance_adjustment_source_invalid")
                    )
            if (day.correctionId != previousCorrection?.id)
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "attendance_adjustment_source_invalid")
                )
            val correction =
                AttendanceCorrection(
                    UUID.randomUUID(),
                    employeeId,
                    date,
                    clockIn,
                    clockOut,
                    breakMinutes,
                    day.schedule,
                    actor.accountId,
                    now,
                    reason,
                    previousCorrection?.version?.plus(1) ?: 0,
                )
            val reviewedEntries =
                entries.map { entry ->
                    val decision = evidence.singleOrNull { it.eventId == entry.capture.id }
                    if (decision == null) entry
                    else
                        entry.copy(
                            status =
                                if (decision.decision == AttendanceReviewDecision.ACCEPT)
                                    AttendanceStatus.ACCEPTED
                                else AttendanceStatus.REJECTED,
                            review =
                                AttendanceReview(actor.accountId, decision.decision, now, reason),
                            version = entry.version + 1,
                        )
                }
            val changed = adjustClosedWorkSnapshot(source, correction, reviewedEntries)
            if (changed is Result.Failed) return@run changed
            for (entry in
                reviewedEntries.filter { updated ->
                    evidence.any { it.eventId == updated.capture.id }
                }) {
                val result =
                    attendance.review(company, entry.capture.id, requireNotNull(entry.review))
                if (result is Result.Failed) return@run result
                val audit =
                    journal.record(
                        actor,
                        ChangeRecord(
                            "attendance_event",
                            entry.capture.id,
                            "attendance.reviewed",
                            mapOf("decision" to entry.review.decision.name),
                            reason,
                        ),
                    )
                if (audit is Result.Failed) return@run audit
            }
            val saved = corrections.save(company, correction, previousCorrection?.version)
            if (saved is Result.Failed) return@run saved
            val correctionAudit =
                journal.record(
                    actor,
                    ChangeRecord(
                        "attendance_correction",
                        correction.id,
                        "attendance.corrected",
                        mapOf(
                            "employmentId" to employeeId.toString(),
                            "workDate" to date.toString(),
                        ),
                        reason,
                    ),
                )
            if (correctionAudit is Result.Failed) return@run correctionAudit
            val adjustment =
                AttendanceAdjustment(
                    AttendanceAdjustmentSummary(
                        UUID.randomUUID(),
                        revision + 1,
                        date,
                        correction.id,
                        actor.accountId,
                        now,
                        reason,
                    ),
                    jobId,
                    (changed as Result.Success).value,
                )
            val appended = adjustments.append(company, adjustment)
            if (appended is Result.Failed) return@run appended
            val result = (appended as Result.Success).value
            val audit =
                journal.record(
                    actor,
                    ChangeRecord(
                        "attendance_adjustment",
                        result.id,
                        "attendance.adjusted",
                        mapOf(
                            "employmentId" to employeeId.toString(),
                            "workDate" to date.toString(),
                            "closingJobId" to jobId.toString(),
                            "revision" to adjustment.summary.revision.toString(),
                        ),
                        reason,
                    ),
                )
            if (audit is Result.Failed) return@run audit
            val recorded = operations.record(actor, key, result)
            if (recorded is Result.Failed) return@run recorded
            validateCompanySessionActor(actor, access, clock.instant(), security).map { result }
        }
    }
}
