package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.time.ZoneOffset
import java.util.UUID

fun PayrollAmendmentReviewsRecord.toAmendmentReview() =
    PayrollAmendmentReview(
        id,
        amendmentId,
        reviewNumber,
        amendmentVersion,
        calculationId,
        approvalId,
        referenceDate,
        timezone,
        exposure,
        submittedBy,
        submittedAt.toInstant(),
        reason,
        PayrollAmendmentReviewStatus.valueOf(status),
        version,
    )

fun PayrollAmendmentReview.toRecord(company: UUID) =
    PayrollAmendmentReviewsRecord().also {
        it.companyId = company
        it.id = id
        it.amendmentId = amendmentId
        it.reviewNumber = number
        it.amendmentVersion = amendmentVersion
        it.calculationId = calculationId
        it.approvalId = approvalId
        it.referenceDate = referenceDate
        it.timezone = timezone
        it.exposure = exposure
        it.submittedBy = submittedBy
        it.submittedAt = submittedAt.atOffset(ZoneOffset.UTC)
        it.reason = reason
        it.status = status.name
        it.version = version
    }

fun PayrollAmendmentReviewChangesRecord.toAmendmentReviewChange() =
    PayrollAmendmentReviewChange(
        revision,
        amendmentRevision,
        PayrollAmendmentAction.valueOf(action),
        PayrollAmendmentReviewStatus.valueOf(status),
        approvalVersion,
        step,
        actorId,
        decidingFor,
        reason,
        recordedAt.toInstant(),
    )

fun PayrollAmendmentReviewChange.toRecord(company: UUID, review: PayrollAmendmentReview) =
    PayrollAmendmentReviewChangesRecord().also {
        it.companyId = company
        it.reviewId = review.id
        it.amendmentId = review.amendmentId
        it.revision = revision
        it.amendmentRevision = amendmentRevision
        it.action = action.name
        it.status = status.name
        it.approvalVersion = approvalVersion
        it.step = step
        it.actorId = actorId
        it.decidingFor = decidingFor
        it.reason = reason
        it.recordedAt = recordedAt.atOffset(ZoneOffset.UTC)
    }
