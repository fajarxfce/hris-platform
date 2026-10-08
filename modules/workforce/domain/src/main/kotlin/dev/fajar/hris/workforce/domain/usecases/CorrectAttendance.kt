package dev.fajar.hris.workforce.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.policies.*
import dev.fajar.hris.workforce.domain.repositories.*
import java.time.*
import java.util.UUID

class CorrectAttendance(
    private val periods: WorkPeriodRepository,
    private val attendance: AttendanceRepository,
    private val corrections: AttendanceCorrectionRepository,
    private val schedules: ScheduleRepository,
    private val people: PeopleRepository,
    private val companies: CompanyRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        employeeId: UUID,
        date: LocalDate,
        clockIn: Instant?,
        clockOut: Instant?,
        breakMinutes: Int,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("attendance.correct")
        if (access is Result.Failed) return access
        if ((expectedVersion ?: 0) < 0)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
        val company = requireNotNull(actor.companyId)
        val key =
            OperationKey(
                "attendance.correct",
                operationId,
                listOf(
                    employeeId.toString(),
                    date.toString(),
                    clockIn?.toString(),
                    clockOut?.toString(),
                    breakMinutes.toString(),
                    expectedVersion?.toString(),
                    reason,
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val now = clock.instant()
            val valid =
                validateAttendanceCorrection(date, clockIn, clockOut, breakMinutes, reason, now)
            if (valid is Result.Failed) return@run valid
            val companyResult = companies.find(company)
            if (companyResult is Result.Failed) return@run companyResult
            val settings =
                (companyResult as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
            if (date.isAfter(now.atZone(ZoneId.of(settings.timezone)).toLocalDate()))
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "future_attendance_correction")
                )
            val employeeResult = people.find(company, employeeId, date)
            if (employeeResult is Result.Failed) return@run employeeResult
            val employee =
                (employeeResult as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "employee_not_found")
                    )
            if (employee.person.accountId == actor.accountId)
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "self_correction_denied"))
            if (!employee.terms.isWorkingOn(date))
                return@run Result.Failed(Failure(FailureKind.VALIDATION, "employee_unavailable"))
            val mutable =
                periods
                    .lockMonth(company, YearMonth.from(date), false)
                    .flatMap(::requireMutablePeriod)
            if (mutable is Result.Failed) return@run mutable
            val lock = attendance.lockDay(company, employeeId, date)
            if (lock is Result.Failed) return@run lock
            val entriesResult = attendance.entries(company, employeeId, date, date)
            if (entriesResult is Result.Failed) return@run entriesResult
            val entries = (entriesResult as Result.Success).value
            if (entries.any { it.status == AttendanceStatus.PENDING })
                return@run Result.Failed(
                    Failure(FailureKind.CONFLICT, "attendance_verification_required")
                )
            val correctionResult = corrections.latest(company, employeeId, date, date)
            if (correctionResult is Result.Failed) return@run correctionResult
            val previous = (correctionResult as Result.Success).value.singleOrNull()
            if (previous?.version != expectedVersion)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            val existingSchedule = previous?.schedule ?: entries.firstOrNull()?.schedule
            val snapshotResult =
                if (existingSchedule != null) Result.Success(existingSchedule)
                else
                    schedules
                        .calendar(company, employeeId, date, date)
                        .flatMap(::resolveCalendar)
                        .map { it.days.single() }
            if (snapshotResult is Result.Failed) return@run snapshotResult
            val correction =
                AttendanceCorrection(
                    UUID.randomUUID(),
                    employeeId,
                    date,
                    clockIn,
                    clockOut,
                    breakMinutes,
                    (snapshotResult as Result.Success).value,
                    actor.accountId,
                    now,
                    reason,
                    (expectedVersion?.plus(1) ?: 0),
                )
            corrections.save(company, correction, expectedVersion).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord(
                                "attendance_correction",
                                receipt.id,
                                "attendance.corrected",
                                mapOf(
                                    "employmentId" to employeeId.toString(),
                                    "workDate" to date.toString(),
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
