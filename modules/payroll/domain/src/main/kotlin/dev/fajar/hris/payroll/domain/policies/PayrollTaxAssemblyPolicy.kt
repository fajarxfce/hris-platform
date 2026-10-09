package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.people.domain.entities.EmploymentStatus
import java.math.BigDecimal
import java.time.YearMonth

/** PMK168 articles5,7,10: employer health/JKK/JKM are taxable; employee JHT/JP are deductible. */
fun assemblePayrollTaxInput(
    facts: PayrollCalculationFacts,
    earnings: List<PayrollEarningLine>,
    contributions: List<InsuranceContribution>,
): Result<IncomeTaxInput> {
    val valid = validatePayrollCalculationFacts(facts)
    if (valid is Result.Failed) return valid
    if (
        earnings.size > 63 ||
            contributions.size > InsuranceProgram.entries.size ||
            contributions.map { it.program }.distinct().size != contributions.size ||
            earnings.any { !validPayrollAmount(it.amount) } ||
            contributions.any {
                !validPayrollAmount(it.employeeAmount) || !validPayrollAmount(it.employerAmount)
            }
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "payroll_amount_limit"))
    val month = facts.month
    val terms = facts.compensation
    val registration = terms.tax
    val first = registration.subjectiveFrom?.takeIf { it.year == month.year }?.monthValue ?: 1
    val last = registration.subjectiveUntil?.takeIf { it.year == month.year }?.monthValue ?: 12
    if (
        (registration.subjectiveFrom != null &&
            registration.subjectiveFrom > month.atEndOfMonth()) ||
            (registration.subjectiveUntil != null &&
                registration.subjectiveUntil < month.atDay(1)) ||
            month.monthValue !in first..last
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_subjective_period_mismatch"))
    val employment =
        facts.employment
            .filter { it.effectiveFrom <= month.atEndOfMonth() }
            .maxByOrNull { it.effectiveFrom }
            ?: return Result.Failed(
                Failure(FailureKind.CONFLICT, "payroll_employment_not_in_period")
            )
    if (employment.status == EmploymentStatus.ENDED && employment.endDate == null)
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_employment_review_required"))
    val finalPeriod =
        month.monthValue == 12 ||
            employment.endDate?.let { YearMonth.from(it) == month } == true ||
            registration.subjectiveUntil?.let { YearMonth.from(it) == month } == true
    val retirement =
        contributions
            .filter { it.program in setOf(InsuranceProgram.OLD_AGE, InsuranceProgram.PENSION) }
            .fold(terms.additionalRetirementContribution) { sum, item -> sum + item.employeeAmount }
    val nonCash =
        contributions
            .filter {
                it.program in
                    setOf(
                        InsuranceProgram.HEALTH,
                        InsuranceProgram.ACCIDENT,
                        InsuranceProgram.DEATH,
                    )
            }
            .fold(facts.input.nonCashTaxable) { sum, item -> sum + item.employerAmount }
    val otherDeductions =
        contributions
            .filter { it.program == InsuranceProgram.HEALTH }
            .fold(terms.otherNetDeduction) { sum, item -> sum + item.employeeAmount } +
            facts.input.deductions.fold(BigDecimal.ZERO) { sum, item -> sum + item.amount }
    val input =
        IncomeTaxInput(
            month,
            registration.residency,
            registration.ptkp,
            terms.treatment,
            earnings.fold(BigDecimal.ZERO) { sum, line -> sum + line.amount },
            earnings
                .filter { !it.taxable }
                .fold(BigDecimal.ZERO) { sum, line -> sum + line.amount },
            nonCash,
            retirement,
            terms.qualifiedDonation,
            otherDeductions,
            finalPeriod,
            last - first + 1,
            facts.taxHistory.history,
        )
    return validateIncomeTaxInput(input).map { input }
}
