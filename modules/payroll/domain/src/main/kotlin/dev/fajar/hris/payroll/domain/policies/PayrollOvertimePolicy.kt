package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal

/** Combines approved intervals by work date before consuming the statutory hourly bands. */
fun calculatePayrollOvertime(facts: PayrollCalculationFacts): Result<List<PayrollOvertimeDay>> {
    val valid = validatePayrollCalculationFacts(facts)
    if (valid is Result.Failed) return valid
    val terms = facts.compensation
    val basis = requireNotNull(terms.payBasis)
    if (basis.overtimeEligibility == OvertimeEligibility.EXEMPT) return Result.Success(emptyList())
    val fixedWage = terms.fixedEarnings.fold(BigDecimal.ZERO) { sum, line -> sum + line.amount }
    val days = mutableListOf<PayrollOvertimeDay>()
    for (day in facts.workDays.sortedBy { it.date }) {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        if (day.overtime.isEmpty()) continue
        if (day.schedule == PayrollScheduleKind.UNASSIGNED)
            return Result.Failed(
                Failure(FailureKind.CONFLICT, "payroll_overtime_schedule_required")
            )
        val kind =
            when {
                day.schedule == PayrollScheduleKind.WORK -> OvertimeDayKind.WORKDAY
                basis.workWeek == PayrollWorkWeek.FIVE_DAYS ->
                    OvertimeDayKind.REST_OR_HOLIDAY_FIVE_DAYS
                day.officialHoliday && day.date.dayOfWeek == basis.shortestWorkDay ->
                    OvertimeDayKind.HOLIDAY_SHORT_SIX_DAYS
                else -> OvertimeDayKind.REST_OR_HOLIDAY_SIX_DAYS
            }
        val result =
            calculateOvertimePay(
                basis.overtimeRuleId,
                terms.basicSalary,
                fixedWage,
                basis.regularNonFixedWage,
                kind,
                day.overtime.sumOf { it.minutes },
                basis.rounding,
            )
        if (result is Result.Failed) return result
        days.add(PayrollOvertimeDay(day.date, (result as Result.Success).value))
    }
    return Result.Success(days.toList())
}
