package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.util.UUID

const val PAYROLL_AMENDMENT_MAXIMUM_VERSION = 999L
const val PAYROLL_AMENDMENT_MAXIMUM_CALCULATIONS = 32

fun validatePayrollAmendmentCommand(expectedVersion: Long?, reason: String): Result<Unit> {
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
    if (reason.isBlank() || reason.length > 1000 || reason.any(Char::isISOControl))
        return Result.Failed(
            Failure(
                FailureKind.VALIDATION,
                "invalid_payroll_amendment",
                fields = mapOf("reason" to "invalid"),
            )
        )
    if (expectedVersion != null && expectedVersion !in 0..PAYROLL_AMENDMENT_MAXIMUM_VERSION)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
    return Result.Success(Unit)
}

/** Validate bounded command identity before constructing canonical operation parts. */
fun validatePayrollAmendmentDraft(
    firstAssessmentId: UUID,
    terms: PayrollAmendmentTerms,
    expectedVersion: Long?,
    reason: String,
): Result<Unit> {
    val command = validatePayrollAmendmentCommand(expectedVersion, reason)
    if (command is Result.Failed) return command
    if (
        terms.sourceVersions.size !in 1..12 ||
            terms.corrections.size !in 1..12 ||
            terms.sourceVersions.map { it.assessmentId }.distinct().size !=
                terms.sourceVersions.size ||
            terms.sourceVersions.any { it.revision < 0 || it.revision == Long.MAX_VALUE } ||
            terms.sourceVersions.none { it.assessmentId == firstAssessmentId } ||
            terms.corrections.map { it.assessmentId }.distinct().size != terms.corrections.size ||
            terms.corrections.none { it.assessmentId == firstAssessmentId } ||
            terms.corrections.any { change ->
                (change.compensation == null && change.input == null) ||
                    terms.sourceVersions.none { it.assessmentId == change.assessmentId }
            }
    )
        return Result.Failed(
            Failure(FailureKind.VALIDATION, "invalid_payroll_amendment_corrections")
        )
    for (change in terms.corrections) {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        // Bounds precede canonicalization; month-dependent rules use the acquired source below.
        if (
            (change.compensation?.fixedEarnings?.size ?: 0) > 20 ||
                (change.input?.earnings?.size ?: 0) > 40 ||
                (change.input?.deductions?.size ?: 0) > 20 ||
                (change.input?.dayResolutions?.size ?: 0) > 62
        )
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_payroll_amendment_corrections")
            )
        val compensation = change.compensation
        val input = change.input
        val amounts =
            listOfNotNull(
                compensation?.basicSalary,
                compensation?.insuranceWage,
                compensation?.additionalRetirementContribution,
                compensation?.qualifiedDonation,
                compensation?.otherNetDeduction,
                compensation?.payBasis?.regularNonFixedWage,
                input?.nonCashTaxable,
                input?.scheduledMonthUnits,
                input?.holidayAllowance?.promisedAmount,
            ) +
                compensation?.fixedEarnings.orEmpty().map { it.amount } +
                input?.earnings.orEmpty().map { it.amount } +
                input?.deductions.orEmpty().map { it.amount }
        if (amounts.any { !validPayrollAmount(it) })
            return Result.Failed(
                Failure(
                    FailureKind.VALIDATION,
                    "invalid_payroll_amendment",
                    fields = mapOf("corrections" to "out_of_range"),
                )
            )
        val strings =
            listOfNotNull(
                compensation?.insuranceExemptionReason,
                compensation?.tax?.residenceCountry,
                compensation?.tax?.verificationReference,
                compensation?.payBasis?.overtimeRuleId,
                compensation?.payBasis?.holidayAllowanceRuleId,
                compensation?.payBasis?.overtimeExemptionReference,
                compensation?.payBasis?.reviewReference,
                input?.reviewReference,
                input?.holidayAllowance?.reviewReference,
            ) +
                compensation?.fixedEarnings.orEmpty().flatMap { listOf(it.code, it.name) } +
                input?.earnings.orEmpty().flatMap { listOf(it.code, it.name) } +
                input?.deductions.orEmpty().flatMap { listOf(it.code, it.name) } +
                input?.dayResolutions.orEmpty().map { it.reference }
        if (strings.any { it.length > 1000 })
            return Result.Failed(
                Failure(
                    FailureKind.VALIDATION,
                    "invalid_payroll_amendment",
                    fields = mapOf("corrections" to "too_long"),
                )
            )
    }
    return Result.Success(Unit)
}

fun validatePayrollAmendmentRemuneration(
    corrections: List<PayrollAmendmentCorrection>,
    reason: String,
): Result<Unit> {
    if (corrections.size !in 1..12)
        return Result.Failed(
            Failure(FailureKind.VALIDATION, "invalid_payroll_amendment_corrections")
        )
    for (correction in corrections) {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val compensation =
            validateCompensation(correction.facts.compensation, correction.facts.month, reason)
        if (compensation is Result.Failed) return compensation
        val input = validatePayrollInput(correction.facts.month, correction.facts.input, reason)
        if (input is Result.Failed) return input
    }
    return Result.Success(Unit)
}

fun payrollAmendmentParts(terms: PayrollAmendmentTerms): List<String?> =
    listOf("sources", terms.sourceVersions.size.toString()) +
        terms.sourceVersions
            .sortedBy { it.assessmentId }
            .flatMap { listOf(it.assessmentId.toString(), it.revision.toString()) } +
        listOf("corrections", terms.corrections.size.toString()) +
        terms.corrections
            .sortedBy { it.assessmentId }
            .flatMap {
                listOf(
                    it.assessmentId.toString(),
                    "compensation",
                    (it.compensation != null).toString(),
                ) +
                    (it.compensation?.let(::compensationOperationParts) ?: emptyList()) +
                    listOf("input", (it.input != null).toString()) +
                    (it.input?.let(::payrollInputParts) ?: emptyList())
            }

fun availablePayrollAmendmentActions(
    actor: Actor,
    amendment: PayrollAmendmentSummary,
    beneficiaryId: UUID?,
    latest: PayrollAmendmentCalculationSummary?,
    latestReview: PayrollAmendmentReview? = null,
): Set<PayrollAmendmentAvailableAction> {
    if (
        "payroll.amend" !in actor.permissions ||
            beneficiaryId == actor.accountId ||
            amendment.status !in
                setOf(
                    PayrollAmendmentStatus.DRAFT,
                    PayrollAmendmentStatus.CALCULATED,
                    PayrollAmendmentStatus.REJECTED,
                ) ||
            amendment.version == PAYROLL_AMENDMENT_MAXIMUM_VERSION
    )
        return emptySet()
    return buildSet {
        add(PayrollAmendmentAvailableAction.CANCEL)
        // The final revision remains available to cancel an exhausted draft.
        if (amendment.version >= PAYROLL_AMENDMENT_MAXIMUM_VERSION - 1) return@buildSet
        add(PayrollAmendmentAvailableAction.EDIT)
        if (
            latest != null &&
                validatePayrollAmendmentSubmission(amendment, latest, latestReview) is
                    Result.Success
        )
            add(PayrollAmendmentAvailableAction.SUBMIT)
        if (
            amendment.status == PayrollAmendmentStatus.DRAFT &&
                (latest?.number ?: 0) < PAYROLL_AMENDMENT_MAXIMUM_CALCULATIONS
        )
            add(PayrollAmendmentAvailableAction.CALCULATE)
    }
}
