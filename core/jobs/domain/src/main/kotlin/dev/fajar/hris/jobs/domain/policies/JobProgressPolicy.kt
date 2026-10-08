package dev.fajar.hris.jobs.domain.policies

import dev.fajar.hris.jobs.domain.entities.*

fun validJobProgress(request: JobRequest, previous: Int, step: JobStep): Boolean =
    step.completedItems in previous..request.totalItems &&
        (step.finished || step.completedItems > previous) &&
        (!step.finished ||
            request.progressMode == JobProgressMode.UPPER_BOUND ||
            step.completedItems == request.totalItems)
