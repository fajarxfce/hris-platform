package dev.fajar.hris.jobs.domain.policies

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.jobs.domain.entities.*

fun availableJobActions(actor: Actor, job: BackgroundJob): List<String> =
    if (
        job.status in setOf(JobStatus.QUEUED, JobStatus.RUNNING) &&
            !job.cancellationRequested &&
            (job.request.actorId == actor.accountId || "jobs.manage" in actor.permissions)
    )
        listOf("cancel")
    else emptyList()
