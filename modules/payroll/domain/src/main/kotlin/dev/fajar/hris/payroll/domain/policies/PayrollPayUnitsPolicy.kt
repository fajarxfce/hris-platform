package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.people.domain.entities.*
import java.math.BigDecimal
import java.time.LocalDate

/** Missing attendance requires a reviewed resolution; only approved unpaid slots reduce pay. */
fun assessPayrollPayUnits(facts: PayrollCalculationFacts): Result<PayrollPayUnits> {
    val valid = validatePayrollCalculationFacts(facts)
    if (valid is Result.Failed) return valid
    val basis = requireNotNull(facts.compensation.payBasis)
    val terms = facts.employment.sortedBy { it.effectiveFrom }
    val leave = mutableMapOf<Pair<LocalDate, Int>, Boolean>()
    for (day in facts.leaveDays) for (half in payrollDayHalves(day.portion)) {
        if (leave.put(day.date to half, day.paid) != null)
            return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_leave_overlap"))
    }
    val resolutions =
        facts.input.dayResolutions
            .flatMap { day ->
                payrollDayHalves(day.portion).map { (day.workDate to it) to day.disposition }
            }
            .toMap()
    val consumed = mutableSetOf<Pair<LocalDate, Int>>()
    val employed = linkedSetOf<LocalDate>()
    var payableHalves = 0
    var unpaidHalves = 0
    var scheduledDays = 0
    for (day in facts.workDays.sortedBy { it.date }) {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val employment = terms.lastOrNull { it.effectiveFrom <= day.date }
        val inEmployment =
            employment != null &&
                day.date >= employment.startDate &&
                (employment.endDate == null || day.date <= employment.endDate)
        if (!inEmployment) {
            if (day.overtime.isNotEmpty())
                return Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_overtime_outside_employment")
                )
            continue
        }
        requireNotNull(employment)
        if (
            employment.status in setOf(EmploymentStatus.SUSPENDED, EmploymentStatus.ENDED) ||
                employment.contract !in setOf(ContractKind.PERMANENT, ContractKind.FIXED_TERM)
        )
            return Result.Failed(
                Failure(FailureKind.CONFLICT, "payroll_employment_review_required")
            )
        employed.add(day.date)
        if (day.schedule == PayrollScheduleKind.WORK) scheduledDays++
        if (
            basis.proration == PayrollProrationBasis.SCHEDULED_DAYS &&
                day.schedule == PayrollScheduleKind.UNASSIGNED
        )
            return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_schedule_incomplete"))
        val counted =
            basis.proration == PayrollProrationBasis.CALENDAR_DAYS ||
                day.schedule == PayrollScheduleKind.WORK
        for (half in 1..2) {
            val slot = day.date to half
            val paidLeave = leave[slot]
            val resolution = resolutions[slot]
            if (paidLeave != null && day.schedule != PayrollScheduleKind.WORK)
                return Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_leave_schedule_mismatch")
                )
            val unresolved =
                day.schedule != PayrollScheduleKind.OFF &&
                    day.attendance in
                        setOf(
                            PayrollAttendanceKind.UNRECORDED,
                            PayrollAttendanceKind.ABSENCE_RECORDED,
                            PayrollAttendanceKind.UNASSIGNED,
                        )
            if (resolution != null && (!unresolved || paidLeave != null))
                return Result.Failed(
                    Failure(FailureKind.CONFLICT, "payroll_resolution_not_applicable")
                )
            if (resolution != null || paidLeave != null) consumed.add(slot)
            if (counted && unresolved && paidLeave == null && resolution == null)
                return Result.Failed(
                    Failure(
                        FailureKind.CONFLICT,
                        "payroll_day_review_required",
                        parameters =
                            mapOf("workDate" to day.date.toString(), "half" to half.toString()),
                    )
                )
            val unpaid = paidLeave == false || resolution == PayrollDayDisposition.UNPAID
            if (counted) {
                if (unpaid) unpaidHalves++ else payableHalves++
            }
        }
    }
    if (employed.isEmpty())
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_employment_not_in_period"))
    if ((leave.keys + resolutions.keys).any { it !in consumed })
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_resolution_not_applicable"))
    val total =
        when (basis.proration) {
            PayrollProrationBasis.CALENDAR_DAYS -> BigDecimal(facts.month.lengthOfMonth())
            PayrollProrationBasis.SCHEDULED_DAYS ->
                facts.input.scheduledMonthUnits
                    ?: return Result.Failed(
                        Failure(FailureKind.CONFLICT, "payroll_scheduled_units_required")
                    )
        }
    if (
        basis.proration == PayrollProrationBasis.SCHEDULED_DAYS &&
            (total < BigDecimal(scheduledDays) ||
                (employed.size == facts.month.lengthOfMonth() &&
                    total.compareTo(BigDecimal(scheduledDays)) != 0))
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_scheduled_units_mismatch"))
    return Result.Success(
        PayrollPayUnits(
            total,
            BigDecimal(payableHalves).divide(BigDecimal(2)),
            BigDecimal(unpaidHalves).divide(BigDecimal(2)),
            employed.toSet(),
        )
    )
}

fun payrollDayHalves(portion: PayrollDayPortion): List<Int> =
    when (portion) {
        PayrollDayPortion.FULL -> listOf(1, 2)
        PayrollDayPortion.FIRST_HALF -> listOf(1)
        PayrollDayPortion.SECOND_HALF -> listOf(2)
    }
