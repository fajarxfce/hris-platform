package dev.fajar.hris.leave.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.people.domain.entities.EmploymentRevision
import java.time.*

fun leaveAccrualPeriod(month: YearMonth, frequency: LeaveAccrualFrequency): YearMonth =
    if (frequency == LeaveAccrualFrequency.ANNUAL) YearMonth.of(month.year, 1) else month

/** Applies an explicitly selected monthly policy. No permission, retry, or storage behavior. */
fun calculateLeaveAccrual(
    month: YearMonth,
    today: LocalDate,
    history: List<EmploymentRevision>,
    policy: LeavePolicy,
): Result<LeaveAccrualAward> {
    val accrual =
        policy.accrual
            ?: return Result.Failed(Failure(FailureKind.VALIDATION, "leave_accrual_not_configured"))
    val valid = validateLeaveAccrualPolicy(accrual, policy.allowPartialDays)
    if (valid is Result.Failed) return valid
    if (accrual.frequency == LeaveAccrualFrequency.MANUAL)
        return Result.Failed(Failure(FailureKind.VALIDATION, "leave_accrual_not_configured"))
    if (
        month.year !in 1900..2199 ||
            month > YearMonth.from(today) ||
            (accrual.frequency == LeaveAccrualFrequency.MONTHLY &&
                !today.isAfter(month.atEndOfMonth()))
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "leave_accrual_not_due"))
    if (history.size > 1000)
        return Result.Failed(Failure(FailureKind.CONFLICT, "employment_history_capacity"))
    val from = month.atDay(1)
    val until = minOf(month.atEndOfMonth(), today)
    val eligible = mutableListOf<LocalDate>()
    for (offset in 0..java.time.temporal.ChronoUnit.DAYS.between(from, until).toInt()) {
        if (Thread.currentThread().isInterrupted)
            throw InterruptedException("Leave accrual interrupted")
        val date = from.plusDays(offset.toLong())
        val terms =
            history
                .filter { it.terms.effectiveFrom <= date }
                .maxWithOrNull(
                    compareBy<EmploymentRevision> { it.terms.effectiveFrom }.thenBy { it.revision }
                )
                ?.terms
        if (
            terms != null &&
                terms.isWorkingOn(date) &&
                terms.contract in policy.allowedContracts &&
                date >= terms.startDate.plusMonths(policy.minServiceMonths.toLong())
        )
            eligible += date
    }
    if (
        eligible.isEmpty() ||
            (accrual.frequency == LeaveAccrualFrequency.MONTHLY &&
                eligible.size != month.lengthOfMonth())
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "leave_employee_ineligible"))
    return Result.Success(
        LeaveAccrualAward(
            leaveAccrualPeriod(month, accrual.frequency),
            eligible.first(),
            if (accrual.frequency == LeaveAccrualFrequency.ANNUAL) eligible.first()
            else eligible.last(),
            accrual.halfDaysPerPeriod,
        )
    )
}
