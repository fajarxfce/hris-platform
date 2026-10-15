package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*

fun PayrollAmendmentPublication.toResponse() =
    PayrollAmendmentPublicationResponse(
        id,
        amendmentId,
        reviewId,
        calculationId,
        amendmentVersion,
        reviewVersion,
        approvalVersion,
        number,
        jobId,
        actorId,
        requestedAt,
        reason,
        status.name,
        completedAt,
        failureCode,
        completedBy,
    )

fun PayrollAmendmentPublicationDetails.toResponse() =
    PayrollAmendmentPublicationDetailsResponse(
        publication.toResponse(),
        PayrollRunProgressResponse(
            job.request.id,
            job.status.name,
            job.completedItems,
            job.request.totalItems,
            job.attempts,
            job.version,
            job.cancellationRequested,
            job.failureCode,
        ),
        amendment.toResponse(),
        availableActions.map { it.name }.sorted(),
    )
