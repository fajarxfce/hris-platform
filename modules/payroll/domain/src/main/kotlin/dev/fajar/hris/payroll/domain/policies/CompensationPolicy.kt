package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal
import java.time.YearMonth

fun validateCompensation(
    terms: CompensationTerms,
    effectiveFrom: YearMonth,
    reason: String,
): Result<Unit> {
    val fields = linkedMapOf<String, String>()
    if (effectiveFrom.year !in 2024..2100) fields["effectiveFrom"] = "out_of_range"
    if (reason.isBlank() || reason.length > 1000) fields["reason"] = "invalid"
    if (terms.fixedEarnings.size > 20) fields["terms.fixedEarnings"] = "too_many"
    val earnings = terms.fixedEarnings.take(20)
    if (earnings.map { it.code }.distinct().size != earnings.size)
        fields["terms.fixedEarnings"] = "duplicate"
    for ((index, line) in earnings.withIndex()) {
        if (!line.code.matches(Regex("[A-Z][A-Z0-9_]{0,31}")))
            fields["terms.fixedEarnings[$index].code"] = "invalid"
        if (line.name.isBlank() || line.name.length > 100)
            fields["terms.fixedEarnings[$index].name"] = "invalid"
        if (!validPayrollAmount(line.amount))
            fields["terms.fixedEarnings[$index].amount"] = "out_of_range"
    }
    for ((field, value) in
        mapOf(
            "basicSalary" to terms.basicSalary,
            "insuranceWage" to terms.insuranceWage,
            "additionalRetirementContribution" to terms.additionalRetirementContribution,
            "qualifiedDonation" to terms.qualifiedDonation,
            "otherNetDeduction" to terms.otherNetDeduction,
        )) if (!validPayrollAmount(value)) fields["terms.$field"] = "out_of_range"
    if (terms.basicSalary.signum() <= 0) fields["terms.basicSalary"] = "positive_required"
    if (terms.insuranceWage.signum() <= 0) fields["terms.insuranceWage"] = "positive_required"
    if (
        terms.additionalHealthDependents !in 0..5 ||
            (terms.additionalHealthDependents > 0 &&
                InsuranceProgram.HEALTH !in terms.insurancePrograms)
    )
        fields["terms.additionalHealthDependents"] = "invalid"
    if (
        (terms.insurancePrograms.size != InsuranceProgram.entries.size &&
            terms.insuranceExemptionReason.isNullOrBlank()) ||
            (terms.insuranceExemptionReason?.length ?: 0) > 1000
    )
        fields["terms.insuranceExemptionReason"] = "required"
    val tax = terms.tax
    if (
        !tax.residenceCountry.matches(Regex("[A-Z]{2}")) ||
            (tax.residency == TaxResidency.RESIDENT && tax.residenceCountry != "ID") ||
            (tax.residency == TaxResidency.NON_RESIDENT && tax.residenceCountry == "ID")
    )
        fields["terms.tax.residenceCountry"] = "invalid"
    if (tax.verifiedOn.year !in 1900..2100) fields["terms.tax.verifiedOn"] = "out_of_range"
    if (
        tax.verificationReference.isBlank() ||
            tax.verificationReference.length > 200 ||
            tax.verificationReference.any(Char::isISOControl)
    )
        fields["terms.tax.verificationReference"] = "invalid"
    if (
        tax.subjectiveFrom != null &&
            (tax.subjectiveFrom.year !in 1900..2100 ||
                tax.subjectiveFrom > effectiveFrom.atEndOfMonth())
    )
        fields["terms.tax.subjectiveFrom"] = "invalid"
    if (
        tax.subjectiveUntil != null &&
            (tax.subjectiveUntil.year !in 1900..2100 ||
                tax.subjectiveUntil < effectiveFrom.atDay(1) ||
                (tax.subjectiveFrom != null && tax.subjectiveUntil < tax.subjectiveFrom))
    )
        fields["terms.tax.subjectiveUntil"] = "invalid"
    if (
        tax.residency == TaxResidency.NON_RESIDENT &&
            (tax.subjectiveFrom != null || tax.subjectiveUntil != null)
    )
        fields["terms.tax.residency"] = "invalid_subjective_period"
    if (fields.isNotEmpty())
        return Result.Failed(
            Failure(FailureKind.VALIDATION, "invalid_compensation", fields = fields)
        )
    val cash =
        terms.basicSalary + earnings.fold(BigDecimal.ZERO) { total, line -> total + line.amount }
    if (
        cash > PAYROLL_MAXIMUM_MONTHLY_AMOUNT ||
            terms.additionalRetirementContribution +
                terms.qualifiedDonation +
                terms.otherNetDeduction > cash
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_compensation_amount"))
    return Result.Success(Unit)
}

fun compensationOperationParts(terms: CompensationTerms): List<String?> =
    listOf(
        terms.basicSalary.stripTrailingZeros().toPlainString(),
        terms.treatment.name,
        terms.tax.residency.name,
        terms.tax.ptkp.name,
        terms.tax.residenceCountry,
        terms.tax.subjectiveFrom?.toString(),
        terms.tax.subjectiveUntil?.toString(),
        terms.tax.verifiedOn.toString(),
        terms.tax.verificationReference,
        terms.insuranceWage.stripTrailingZeros().toPlainString(),
        terms.insurancePrograms.map { it.name }.sorted().joinToString(","),
        terms.insuranceExemptionReason,
        terms.accidentRisk.name,
        terms.additionalHealthDependents.toString(),
        terms.additionalRetirementContribution.stripTrailingZeros().toPlainString(),
        terms.qualifiedDonation.stripTrailingZeros().toPlainString(),
        terms.otherNetDeduction.stripTrailingZeros().toPlainString(),
    ) +
        terms.fixedEarnings
            .sortedBy { it.code }
            .flatMap {
                listOf(
                    it.code,
                    it.name,
                    it.amount.stripTrailingZeros().toPlainString(),
                    it.taxable.toString(),
                )
            }
