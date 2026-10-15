package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.schema.tables.records.PayrollAmendmentPublicationsRecord
import java.time.ZoneOffset
import java.util.UUID

fun PayrollAmendmentPublicationsRecord.toAmendmentPublication() =
    PayrollAmendmentPublication(
        id,
        amendmentId,
        reviewId,
        calculationId,
        amendmentVersion,
        reviewVersion,
        approvalVersion,
        attempt,
        jobId,
        actorId,
        requestedAt.toInstant(),
        reason,
        PayrollAmendmentPublicationStatus.valueOf(status),
        completedAt?.toInstant(),
        failureCode,
        completedBy,
    )

fun PayrollAmendmentPublication.toRecord(company: UUID) =
    PayrollAmendmentPublicationsRecord().also {
        it.companyId = company
        it.id = id
        it.amendmentId = amendmentId
        it.reviewId = reviewId
        it.calculationId = calculationId
        it.amendmentVersion = amendmentVersion
        it.reviewVersion = reviewVersion
        it.approvalVersion = approvalVersion
        it.attempt = number
        it.jobId = jobId
        it.actorId = actorId
        it.reason = reason
        it.requestedAt = requestedAt.atOffset(ZoneOffset.UTC)
        it.status = status.name
        it.completedAt = completedAt?.atOffset(ZoneOffset.UTC)
        it.completedBy = completedBy
        it.failureCode = failureCode
    }
