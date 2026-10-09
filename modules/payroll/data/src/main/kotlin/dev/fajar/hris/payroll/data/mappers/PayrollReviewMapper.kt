package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.time.ZoneOffset
import java.util.UUID

fun PayrollReviewsRecord.toReview() =
    PayrollReview(
        id,
        runId,
        reviewNumber,
        runVersion,
        approvalId,
        referenceDate,
        PayrollRunTotals(employeeCount, taxableGross, withheld, takeHome),
        submittedBy,
        submittedAt.toInstant(),
        reason,
        PayrollReviewStatus.valueOf(status),
        version,
    )

fun PayrollReviewChangesRecord.toReviewChange() =
    PayrollReviewChange(
        revision,
        PayrollReviewAction.valueOf(action),
        PayrollReviewStatus.valueOf(status),
        approvalVersion,
        step,
        actorId,
        decidingFor,
        reason,
        recordedAt.toInstant(),
    )

fun PayrollReview.toRecord(company: UUID) =
    PayrollReviewsRecord().also {
        it.companyId = company
        it.id = id
        it.runId = runId
        it.reviewNumber = number
        it.runVersion = runVersion
        it.approvalId = approvalId
        it.referenceDate = referenceDate
        it.employeeCount = totals.employeeCount
        it.taxableGross = totals.taxableGross
        it.withheld = totals.withheld
        it.takeHome = totals.takeHome
        it.submittedBy = submittedBy
        it.submittedAt = submittedAt.atOffset(ZoneOffset.UTC)
        it.reason = reason
        it.status = status.name
        it.version = version
    }

fun PayrollReviewChange.toRecord(company: UUID, review: UUID) =
    PayrollReviewChangesRecord().also {
        it.companyId = company
        it.reviewId = review
        it.revision = revision
        it.action = action.name
        it.status = status.name
        it.approvalVersion = approvalVersion
        it.step = step
        it.actorId = actorId
        it.decidingFor = decidingFor
        it.reason = reason
        it.recordedAt = at.atOffset(ZoneOffset.UTC)
    }
