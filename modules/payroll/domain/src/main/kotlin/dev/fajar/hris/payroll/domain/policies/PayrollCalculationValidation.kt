package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.validateEmployment
import java.time.YearMonth

/** Regular monthly wages and benefits share one reviewed tax month (PMK168 article19). */
fun validatePayrollCalculationFacts(facts: PayrollCalculationFacts): Result<Unit> {
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
    val month = facts.month
    if (month.year !in 2024..2100)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_period"))
    if (
        facts.incomeDueDate !in month.atDay(1)..month.atEndOfMonth() ||
            facts.plannedPaymentDate < month.atDay(1) ||
            facts.plannedPaymentDate > month.atEndOfMonth().plusDays(62)
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_tax_month_review_required"))
    val configuration = validatePayrollPolicy(facts.policy, "calculation")
    if (configuration is Result.Failed) return configuration
    val effective = requireEffectivePayrollPolicy(facts.policy, month)
    if (effective is Result.Failed) return effective
    val compensation = validateCompensation(facts.compensation, month, "calculation")
    if (compensation is Result.Failed) return compensation
    if (facts.compensation.payBasis == null)
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_pay_basis_required"))
    val input = validatePayrollInput(month, facts.input, "calculation")
    if (input is Result.Failed) return input
    val history = validatePayrollTaxOpening(month.year, facts.taxHistory, "calculation")
    if (history is Result.Failed) return history
    if (facts.taxHistory.throughMonth != month.monthValue - 1)
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_tax_history_incomplete"))
    if (
        facts.taxHistory.residency != facts.compensation.tax.residency ||
            facts.taxHistory.ptkp != facts.compensation.tax.ptkp
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_tax_history_mismatch"))
    if (
        facts.employment.size !in 1..128 ||
            facts.workDays.size != month.lengthOfMonth() ||
            facts.leaveDays.size > 62
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_source_incomplete"))
    if (
        facts.employment.map { it.effectiveFrom }.distinct().size != facts.employment.size ||
            facts.employment.any {
                validateEmployment(it, "calculation") is Result.Failed ||
                    it.effectiveFrom.year !in 1900..2200 ||
                    it.startDate.year !in 1900..2200 ||
                    it.endDate?.let { end -> end.year !in 1900..2200 || end < it.startDate } == true
            }
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_employment_review_required"))
    if (
        facts.workDays.map { it.date }.distinct().size != month.lengthOfMonth() ||
            facts.workDays.any { YearMonth.from(it.date) != month || it.overtime.size > 24 } ||
            facts.leaveDays.any { YearMonth.from(it.date) != month || it.revision < 0 }
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_source_incomplete"))
    val overtime = facts.workDays.flatMap { it.overtime }
    if (
        overtime.map { it.requestId }.distinct().size != overtime.size ||
            overtime.any { it.revision < 0 || it.minutes !in 1..720 }
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_overtime_evidence_invalid"))
    for (day in facts.workDays) {
        val coherent =
            when (day.schedule) {
                PayrollScheduleKind.WORK ->
                    day.attendance in
                        setOf(
                            PayrollAttendanceKind.WORKED,
                            PayrollAttendanceKind.ABSENCE_RECORDED,
                            PayrollAttendanceKind.UNRECORDED,
                        )
                PayrollScheduleKind.OFF ->
                    day.attendance in
                        setOf(
                            PayrollAttendanceKind.OFF,
                            PayrollAttendanceKind.WORKED,
                            PayrollAttendanceKind.ABSENCE_RECORDED,
                        )
                PayrollScheduleKind.UNASSIGNED ->
                    day.attendance in
                        setOf(
                            PayrollAttendanceKind.UNASSIGNED,
                            PayrollAttendanceKind.WORKED,
                            PayrollAttendanceKind.ABSENCE_RECORDED,
                        )
            }
        if (!coherent || (day.officialHoliday && day.schedule != PayrollScheduleKind.OFF))
            return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_source_inconsistent"))
    }
    return Result.Success(Unit)
}
