package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.time.YearMonth

/** Advance exactly one published month. Opening/previous-employer credits are included once. */
fun derivePayrollTaxHistory(
    target: PayrollRunTarget,
    month: YearMonth,
    opening: PayrollTaxOpening,
    registration: TaxRegistration,
    previous: PayrollTaxAssessment?,
): Result<PayrollTaxOpeningTerms> {
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
    if (
        opening.id != target.taxOpeningId ||
            opening.version != target.taxOpeningRevision ||
            opening.employeeId != target.employeeId ||
            opening.year != month.year
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_tax_history_mismatch"))
    if (target.previousAssessmentId == null) {
        if (previous != null || target.previousAssessmentRevision != 0L)
            return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_tax_history_changed"))
        return Result.Success(opening.terms)
    }
    if (previous == null || previous.id != target.previousAssessmentId)
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_tax_assessment_missing"))
    if (previous.revision < 0 || previous.revision != target.previousAssessmentRevision)
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_tax_history_changed"))
    if (previous.month >= month || previous.month.year != month.year)
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_tax_history_out_of_order"))
    if (previous.month != month.minusMonths(1))
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_tax_history_incomplete"))
    if (previous.employeeId != target.employeeId || previous.input.finalPeriod)
        return Result.Failed(
            Failure(FailureKind.CONFLICT, "payroll_tax_continuation_review_required")
        )
    if (
        previous.openingId != opening.id ||
            previous.openingRevision != opening.version ||
            previous.input.month != previous.month ||
            previous.registration.residency != registration.residency ||
            previous.registration.ptkp != registration.ptkp ||
            previous.registration.subjectiveFrom != registration.subjectiveFrom ||
            previous.registration.subjectiveUntil != registration.subjectiveUntil
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_tax_history_mismatch"))
    val prior = previous.input.history
    val history =
        prior.copy(
            taxableGross = prior.taxableGross + previous.calculation.taxableGross,
            retirementContributions =
                prior.retirementContributions + previous.input.retirementContributions,
            qualifiedDonations = prior.qualifiedDonations + previous.input.qualifiedDonations,
            withheld = prior.withheld + previous.calculation.withheld,
            employmentMonths = prior.employmentMonths + 1,
        )
    val terms =
        PayrollTaxOpeningTerms(
            previous.month.monthValue,
            registration.residency,
            registration.ptkp,
            history,
            if (previous.revision == 0L) "assessment:${previous.id}"
            else "assessment:${previous.id}:${previous.revision}",
        )
    return validatePayrollTaxOpening(month.year, terms, "Published tax continuity").map { terms }
}
