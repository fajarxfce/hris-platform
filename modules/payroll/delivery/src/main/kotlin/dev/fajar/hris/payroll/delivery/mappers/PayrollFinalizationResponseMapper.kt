package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*

fun PayrollFinalization.toResponse() =
    PayrollFinalizationResponse(
        id,
        runId,
        reviewId,
        runVersion,
        reviewVersion,
        approvalVersion,
        number,
        jobId,
        actorId,
        requestedAt,
        reason,
        companyCode,
        companyName,
        publishedAt,
    )

fun PayrollFinalizationDetails.toResponse() =
    PayrollFinalizationDetailsResponse(
        finalization.toResponse(),
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
    )
