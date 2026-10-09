package dev.fajar.hris.jobs.delivery.mappers

import dev.fajar.hris.jobs.delivery.responses.*
import dev.fajar.hris.jobs.domain.entities.*

fun JobDetails.toResponse(): JobResponse =
    JobResponse(
        job.request.id,
        job.request.kind.name,
        job.status.name,
        job.completedItems,
        job.request.totalItems,
        job.request.progressMode.name,
        job.attempts,
        job.cancellationRequested,
        job.failureCode,
        job.request.createdAt.toString(),
        job.finishedAt?.toString(),
        job.version,
        availableActions,
        job.request.scheduledFor?.toString(),
        job.availableAt.toString(),
    )

fun JobDetailsPage.toResponse(): JobPageResponse =
    JobPageResponse(items.map { it.toResponse() }, nextCreatedAt?.toString(), nextId)
