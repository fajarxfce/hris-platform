package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal

fun validatePayrollTaxOpening(
    year: Int,
    terms: PayrollTaxOpeningTerms,
    reason: String,
): Result<Unit> {
    val fields = linkedMapOf<String, String>()
    if (year !in 2024..2100) fields["year"] = "out_of_range"
    if (terms.throughMonth !in 0..11) fields["terms.throughMonth"] = "out_of_range"
    if (
        terms.reference.isBlank() ||
            terms.reference.length > 200 ||
            terms.reference.any(Char::isISOControl)
    )
        fields["terms.reference"] = "invalid"
    if (reason.isBlank() || reason.length > 1000) fields["reason"] = "invalid"
    val h = terms.history
    val amounts =
        mapOf(
            "taxableGross" to h.taxableGross,
            "retirementContributions" to h.retirementContributions,
            "qualifiedDonations" to h.qualifiedDonations,
            "withheld" to h.withheld,
            "previousEmployerNet" to h.previousEmployerNet,
            "previousEmployerWithheld" to h.previousEmployerWithheld,
        )
    for ((field, value) in amounts) if (!validPayrollAmount(value, yearly = true))
        fields["terms.history.$field"] = "out_of_range"
    if (h.employmentMonths !in 0..terms.throughMonth.coerceIn(0, 11))
        fields["terms.history.employmentMonths"] = "out_of_range"
    if (fields.isNotEmpty())
        return Result.Failed(
            Failure(FailureKind.VALIDATION, "invalid_payroll_tax_opening", fields = fields)
        )
    if (
        h.retirementContributions + h.qualifiedDonations > h.taxableGross ||
            h.taxableGross > PAYROLL_MAXIMUM_MONTHLY_AMOUNT * BigDecimal(h.employmentMonths) ||
            (h.employmentMonths == 0 &&
                listOf(h.taxableGross, h.retirementContributions, h.qualifiedDonations, h.withheld)
                    .any { it.signum() != 0 })
    )
        fields["terms.history"] = "inconsistent"
    if (terms.throughMonth == 0 && amounts.values.any { it.signum() != 0 })
        fields["terms.history"] = "no_previous_month"
    if (
        terms.residency == TaxResidency.NON_RESIDENT &&
            (h.previousEmployerNet.signum() != 0 || h.previousEmployerWithheld.signum() != 0)
    )
        fields["terms.history"] = "non_resident_reconciliation_not_applicable"
    return if (fields.isEmpty()) Result.Success(Unit)
    else
        Result.Failed(
            Failure(FailureKind.VALIDATION, "invalid_payroll_tax_opening", fields = fields)
        )
}

fun payrollTaxOpeningParts(terms: PayrollTaxOpeningTerms): List<String?> =
    listOf(
        terms.throughMonth.toString(),
        terms.residency.name,
        terms.ptkp.name,
        terms.reference,
        terms.history.taxableGross.stripTrailingZeros().toPlainString(),
        terms.history.retirementContributions.stripTrailingZeros().toPlainString(),
        terms.history.qualifiedDonations.stripTrailingZeros().toPlainString(),
        terms.history.withheld.stripTrailingZeros().toPlainString(),
        terms.history.employmentMonths.toString(),
        terms.history.previousEmployerNet.stripTrailingZeros().toPlainString(),
        terms.history.previousEmployerWithheld.stripTrailingZeros().toPlainString(),
    )

fun canReadPayrollTaxOpening(actor: Actor): Boolean =
    actor.permissions.any { it in setOf("payroll.read", "payroll.calculate", "payroll.review") }
