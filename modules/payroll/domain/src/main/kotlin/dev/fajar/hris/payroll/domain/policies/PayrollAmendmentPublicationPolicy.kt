package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.approvals.domain.entities.ApprovalKind
import dev.fajar.hris.approvals.domain.entities.ApprovalRequest
import dev.fajar.hris.approvals.domain.entities.ApprovalStatus
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*

const val PAYROLL_AMENDMENT_MAXIMUM_PUBLICATIONS = 8

fun nextPayrollAmendmentPublication(
    amendment: PayrollAmendmentSummary,
    previous: PayrollAmendmentPublication?,
): Result<Int> =
    when {
        amendment.status != PayrollAmendmentStatus.APPROVED ->
            Result.Failed(
                Failure(FailureKind.CONFLICT, "payroll_amendment_approved_review_required")
            )
        previous != null && previous.status != PayrollAmendmentPublicationStatus.STOPPED ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_amendment_publication_active"))
        (previous?.number ?: 0) >= PAYROLL_AMENDMENT_MAXIMUM_PUBLICATIONS ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_amendment_publication_limit"))
        // Admission and its terminal outcome both fit before the cancellation-only slot.
        amendment.version > PAYROLL_AMENDMENT_MAXIMUM_VERSION - 3 ->
            Result.Failed(Failure(FailureKind.CONFLICT, "payroll_amendment_revision_limit"))
        else -> Result.Success((previous?.number ?: 0) + 1)
    }

fun requireApprovedPayrollAmendment(
    amendment: PayrollAmendmentSummary,
    review: PayrollAmendmentReview,
    approval: ApprovalRequest?,
    calculation: PayrollAmendmentCalculationAttempt,
): Result<Unit> =
    if (
        amendment.status !in
            setOf(PayrollAmendmentStatus.APPROVED, PayrollAmendmentStatus.PUBLISHING) ||
            review.amendmentId != amendment.id ||
            review.id != amendment.latestReviewId ||
            review.status != PayrollAmendmentReviewStatus.APPROVED ||
            approval == null ||
            approval.id != review.approvalId ||
            approval.resourceId != review.id ||
            approval.kind != ApprovalKind.PAYROLL_AMENDMENT ||
            approval.status != ApprovalStatus.APPROVED ||
            calculation.summary.amendmentId != amendment.id ||
            calculation.summary.id != review.calculationId ||
            calculation.summary.id != amendment.latestCalculationId ||
            calculation.summary.status != PayrollAmendmentCalculationStatus.SUCCEEDED ||
            calculation.preview == null
    )
        Result.Failed(Failure(FailureKind.CONFLICT, "payroll_amendment_approved_review_required"))
    else Result.Success(Unit)

/** Match all observed months, including unchanged predecessors, before promotion. */
fun requirePayrollAmendmentPublicationSources(
    amendment: PayrollAmendment,
    preview: PayrollAmendmentPreview,
    sources: List<PayrollAmendmentSource>,
    attendance: List<PayrollAmendmentAttendance> = emptyList(),
): Result<Unit> {
    if (Thread.currentThread().isInterrupted) throw InterruptedException()
    val proposal =
        preparePayrollAmendmentCorrections(
            amendment.summary.firstAssessmentId,
            sources,
            amendment.terms.sourceVersions,
            amendment.terms.corrections,
            attendance,
        )
    if (proposal is Result.Failed) return proposal
    val corrected = (proposal as Result.Success).value.associateBy { it.assessmentId }
    val observed = sources.map { PayrollAmendmentSourceReference(it.assessmentId, it.revision) }
    if (
        sources.size !in 1..12 ||
            observed != preview.sourceVersions ||
            amendment.terms.sourceVersions.sortedBy { it.assessmentId } !=
                observed.sortedBy { it.assessmentId }
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_amendment_sources_changed"))
    val first = sources.indexOfFirst { it.assessmentId == amendment.summary.firstAssessmentId }
    if (
        first < 0 ||
            preview.firstAssessmentId != amendment.summary.firstAssessmentId ||
            preview.calculations.size != sources.size - first
    )
        return Result.Failed(
            Failure(FailureKind.CONFLICT, "payroll_amendment_recalculation_required")
        )
    for ((index, result) in preview.calculations.withIndex()) {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val source = sources[first + index]
        val expectedFacts = corrected[source.assessmentId]?.facts ?: source.facts
        val predecessor = sources.getOrNull(first + index - 1)
        val expectedRevision = predecessor?.let { it.revision + if (index > 0) 1 else 0 } ?: 0
        val expectedReference =
            predecessor?.let {
                "assessment:${it.assessmentId}" +
                    if (expectedRevision == 0L) "" else ":$expectedRevision"
            }
        if (
            result.source != observed[first + index] ||
                result.target.previousAssessmentId != predecessor?.assessmentId ||
                result.target.previousAssessmentRevision != expectedRevision ||
                result.original != source.calculation ||
                result.facts.month != source.facts.month ||
                result.facts.copy(taxHistory = expectedFacts.taxHistory) != expectedFacts ||
                (predecessor != null && result.facts.taxHistory.reference != expectedReference)
        )
            return Result.Failed(
                Failure(FailureKind.CONFLICT, "payroll_amendment_recalculation_required")
            )
    }
    return Result.Success(Unit)
}
