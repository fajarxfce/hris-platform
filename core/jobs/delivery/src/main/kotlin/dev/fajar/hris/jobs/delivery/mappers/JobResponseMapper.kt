package dev.fajar.hris.jobs.delivery.mappers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.jobs.delivery.responses.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.policies.availableJobActions

fun BackgroundJob.toResponse(actor: Actor): JobResponse =
    JobResponse(
        request.id,
        request.kind.name,
        status.name,
        completedItems,
        request.totalItems,
        request.progressMode.name,
        attempts,
        cancellationRequested,
        failureCode,
        request.createdAt.toString(),
        finishedAt?.toString(),
        version,
        availableJobActions(actor, this),
    )

fun JobPage.toResponse(actor: Actor): JobPageResponse =
    JobPageResponse(items.map { it.toResponse(actor) }, nextCreatedAt?.toString(), nextId)
