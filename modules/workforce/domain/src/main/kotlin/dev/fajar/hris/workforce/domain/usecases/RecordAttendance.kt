package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.*
import java.util.UUID

class RecordAttendance(
    private val periods: WorkPeriodRepository,
    private val attendance: AttendanceRepository,
    private val corrections: AttendanceCorrectionRepository,
    private val schedules: ScheduleRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        capture: AttendanceCapture,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("attendance.self.record")
        if (access is Result.Failed) return access
        val key =
            OperationKey(
                "attendance.record",
                operationId,
                listOf(
                    capture.id.toString(),
                    capture.employeeId.toString(),
                    capture.workDate.toString(),
                    capture.kind.name,
                    capture.capturedAt.toString(),
                    capture.deviceId.toString(),
                    capture.windowId?.toString(),
                    capture.offline.toString(),
                    capture.location?.latitude?.toString(),
                    capture.location?.longitude?.toString(),
                    capture.location?.accuracyMeters?.toString(),
                    capture.location?.mocked?.toString(),
                ),
            )
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            if ((replay as Result.Success).value == null) {
                val valid = validateAttendanceCapture(capture, clock.instant())
                if (valid is Result.Failed) return@run valid
            }
            val scheduleGuard = schedules.lock(company, shared = true)
            if (scheduleGuard is Result.Failed) return@run scheduleGuard
            val periodResult =
                if (replay.value == null)
                    periods.lockMonth(company, YearMonth.from(capture.workDate), false)
                else Result.Success(null)
            if (periodResult is Result.Failed) return@run periodResult
            val dayGuard = attendance.lockDay(company, capture.employeeId, capture.workDate)
            if (dayGuard is Result.Failed) return@run dayGuard
            val peopleGuard = people.lockReportingLines(company, shared = true)
            if (peopleGuard is Result.Failed) return@run peopleGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authorized =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (authorized is Result.Failed) return@run authorized
            val live = (authorized as Result.Success).value
            val permission = live.requirePermission("attendance.self.record")
            if (permission is Result.Failed) return@run permission
            val now = clock.instant()
            val foundCompany = companies.find(company)
            if (foundCompany is Result.Failed) return@run foundCompany
            val settings =
                (foundCompany as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            val today = now.atZone(ZoneId.of(settings.timezone)).toLocalDate()
            val found = people.find(company, capture.employeeId, today)
            if (found is Result.Failed) return@run found
            val employee =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (employee.person.accountId != live.accountId)
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            replay.value?.let {
                return@run Result.Success(it)
            }
            val valid = validateAttendanceCapture(capture, now)
            if (valid is Result.Failed) return@run valid
            if (!employee.terms.isWorkingOn(today))
                return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "employee_not_found"))
            val historical = people.find(company, capture.employeeId, capture.workDate)
            if (historical is Result.Failed) return@run historical
            if ((historical as Result.Success).value?.terms?.isWorkingOn(capture.workDate) != true)
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "employee_unavailable"))
            val period = requireNotNull((periodResult as Result.Success).value)
            val previous =
                attendance.entries(company, capture.employeeId, capture.workDate, capture.workDate)
            if (previous is Result.Failed) return@run previous
            val entries = (previous as Result.Success).value
            if (entries.size >= 32)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "attendance_daily_limit"))
            val existingSchedule = entries.firstOrNull()?.schedule
            val selectedSchedule =
                if (existingSchedule != null) Result.Success(existingSchedule)
                else
                    schedules
                        .calendar(company, capture.employeeId, capture.workDate, capture.workDate)
                        .flatMap(::resolveCalendar)
                        .map { it.days.single() }
            if (selectedSchedule is Result.Failed) return@run selectedSchedule
            val schedule = (selectedSchedule as Result.Success).value
            val window =
                if (capture.windowId == null) null
                else {
                    val result = attendance.findWindow(company, capture.windowId)
                    if (result is Result.Failed) return@run result
                    (result as Result.Success).value
                }
            val correctionResult =
                corrections.latest(company, capture.employeeId, capture.workDate, capture.workDate)
            if (correctionResult is Result.Failed) return@run correctionResult
            val corrected = (correctionResult as Result.Success).value.isNotEmpty()
            val assessed =
                assessAttendance(
                    capture,
                    actor.accountId,
                    window,
                    schedule,
                    entries,
                    now,
                    corrected,
                )
            if (assessed is Result.Failed) return@run assessed
            val rawAssessment = (assessed as Result.Success).value
            val assessment =
                if (workPeriodLocked(period))
                    rawAssessment.copy(
                        status = AttendanceStatus.PENDING,
                        issues = rawAssessment.issues + AttendanceIssue.PERIOD_LOCKED,
                        closingJobId = period.jobId,
                    )
                else rawAssessment
            val entry =
                AttendanceEntry(
                    capture,
                    actor.accountId,
                    now,
                    schedule,
                    assessment,
                    assessment.status,
                    null,
                    0,
                )
            attendance.record(company, entry).flatMap { receipt ->
                val consumed =
                    if (window == null) Result.Success(Unit)
                    else attendance.consumeWindow(company, window.id, capture.id)
                consumed
                    .flatMap { operations.record(actor, key, receipt) }
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "attendance_event",
                                capture.id,
                                "attendance.recorded",
                                mapOf(
                                    "employmentId" to capture.employeeId.toString(),
                                    "workDate" to capture.workDate.toString(),
                                    "status" to assessment.status.name,
                                ),
                            ),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
