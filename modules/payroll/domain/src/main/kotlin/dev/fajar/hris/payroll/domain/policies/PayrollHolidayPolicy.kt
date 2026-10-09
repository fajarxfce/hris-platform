package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal
import java.time.YearMonth

fun calculatePayrollHoliday(facts: PayrollCalculationFacts): Result<HolidayAllowance?> {
    val valid = validatePayrollCalculationFacts(facts)
    if (valid is Result.Failed) return valid
    val input = facts.input.holidayAllowance ?: return Result.Success(null)
    if (input.priorPayment == PayrollPriorHolidayPayment.PAID) return Result.Success(null)
    // A prior-month statutory liability cannot be taxed silently as this month's ordinary pay.
    if (YearMonth.from(input.holidayDate.minusDays(7)) != facts.month)
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_thr_tax_month_mismatch"))
    val employment =
        facts.employment
            .filter { it.effectiveFrom <= input.holidayDate }
            .maxByOrNull { it.effectiveFrom }
            ?: return Result.Failed(
                Failure(FailureKind.CONFLICT, "payroll_employment_not_in_period")
            )
    if (input.continuousServiceFrom > employment.startDate)
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_thr_service_review_required"))
    val terms = facts.compensation
    val basis = requireNotNull(terms.payBasis)
    return assessHolidayService(
            basis.holidayAllowanceRuleId,
            input.continuousServiceFrom,
            employment.endDate,
            employment.contract,
            input.holidayDate,
            basis.serviceMonthConvention,
        )
        .flatMap { service ->
            calculateHolidayAllowance(
                basis.holidayAllowanceRuleId,
                service,
                input.holidayDate,
                terms.basicSalary +
                    terms.fixedEarnings.fold(BigDecimal.ZERO) { sum, e -> sum + e.amount },
                input.promisedAmount,
                basis.rounding,
            )
        }
}
