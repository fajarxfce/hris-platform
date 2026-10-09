package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.*
import java.time.YearMonth
import java.util.UUID

fun workPeriodLocked(period: WorkPeriod): Boolean =
    period.status in setOf(WorkPeriodStatus.PROCESSING, WorkPeriodStatus.CLOSED)

fun requireMutablePeriod(period: WorkPeriod): Result<Unit> =
    if (workPeriodLocked(period)) Result.Failed(Failure(FailureKind.CONFLICT, "work_period_locked"))
    else Result.Success(Unit)

fun snapshotWorkPeriod(
    employeeId: UUID,
    period: WorkPeriod,
    calendar: EmployeeCalendar,
    entries: List<AttendanceEntry>,
    corrections: List<AttendanceCorrection>,
    overtime: List<OvertimeRequest> = emptyList(),
): Result<WorkPeriodSnapshot> {
    if (period.status != WorkPeriodStatus.PROCESSING || period.jobId == null)
        return Result.Failed(Failure(FailureKind.CONFLICT, "work_period_not_processing"))
    val from = period.month.atDay(1)
    if (
        calendar.days.size != period.month.lengthOfMonth() ||
            calendar.days.any { YearMonth.from(it.workDate) != period.month } ||
            calendar.days.map { it.workDate }.distinct().size != calendar.days.size
    )
        return Result.Failed(Failure(FailureKind.UNEXPECTED, "period_calendar_inconsistent"))
    if (
        overtime.size > 128 ||
            overtime.map { it.id }.distinct().size != overtime.size ||
            overtime.any {
                it.employeeId != employeeId ||
                    YearMonth.from(it.workDate) != period.month ||
                    it.status != OvertimeStatus.APPROVED ||
                    it.actual == null ||
                    it.approvalId == null ||
                    it.approvedMinutes != it.actual.workedMinutes
            }
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "period_overtime_inconsistent"))
    val overtimeByDate = overtime.groupBy { it.workDate }
    val evidence =
        entries.filterNot {
            it.status == AttendanceStatus.PENDING && it.initial.closingJobId == period.jobId
        }
    val byDate = evidence.groupBy { it.capture.workDate }
    val corrected = corrections.associateBy { it.workDate }
    val days = mutableListOf<ClosedWorkDay>()
    for (date in (0 until period.month.lengthOfMonth()).map { from.plusDays(it.toLong()) }) {
        val summary = summarizeAttendance(date, byDate[date].orEmpty(), corrected[date])
        if (summary.pendingCount > 0 || summary.incomplete)
            return Result.Failed(
                Failure(
                    FailureKind.CONFLICT,
                    if (summary.pendingCount > 0) "attendance_verification_required"
                    else "attendance_incomplete",
                    mapOf("workDate" to date.toString(), "employeeId" to employeeId.toString()),
                )
            )
        val schedule =
            summary.correction?.schedule
                ?: summary.entries.firstOrNull()?.schedule
                ?: calendar.days.single { it.workDate == date }
        val accepted = summary.entries.filter { it.status == AttendanceStatus.ACCEPTED }
        val fact =
            when {
                summary.correction != null ->
                    if (summary.correction.clockIn == null) WorkDayFact.ABSENCE_RECORDED
                    else WorkDayFact.WORKED
                accepted.any { it.capture.kind == AttendanceKind.CLOCK_IN } -> WorkDayFact.WORKED
                schedule is ScheduledDay.Off -> WorkDayFact.OFF
                schedule is ScheduledDay.Unassigned -> WorkDayFact.UNASSIGNED
                else -> WorkDayFact.UNRECORDED
            }
        days +=
            ClosedWorkDay(
                date,
                fact,
                summary.acceptedMinutes,
                schedule,
                summary.entries.map { it.capture.id },
                summary.correction?.id,
                overtimeByDate[date].orEmpty().map {
                    ApprovedOvertime(
                        it.id,
                        it.version,
                        requireNotNull(it.actual),
                        it.approvedMinutes,
                        it.schedule,
                    )
                },
            )
    }
    return Result.Success(WorkPeriodSnapshot(employeeId, period.month, days.toList()))
}
