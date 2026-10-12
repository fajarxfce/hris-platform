package dev.fajar.hris.workforce.data.mappers

import dev.fajar.hris.jobs.domain.entities.JobStatus
import dev.fajar.hris.people.domain.entities.EmploymentStatus
import dev.fajar.hris.workforce.data.models.WorkPeriodEmployeeRow
import dev.fajar.hris.workforce.data.models.WorkPeriodStateRow
import dev.fajar.hris.workforce.domain.entities.*

fun WorkPeriodStateRow.toState(): WorkPeriodState {
    val current = period.toPeriod()
    val job =
        attempt?.let {
            require(it.kind == "WORKFORCE_CLOSE" && it.id == current.jobId)
            require(it.totalItems in 1..5001 && it.completedItems in 0..it.totalItems)
            WorkPeriodAttempt(
                it.id,
                it.actorId,
                JobStatus.valueOf(it.status),
                it.totalItems - 1,
                minOf(it.completedItems, it.totalItems - 1),
                it.cancellationRequested,
                it.failureCode,
                it.createdAt.toInstant(),
                it.finishedAt?.toInstant(),
            )
        }
    require((current.jobId == null) == (job == null))
    if (current.status == WorkPeriodStatus.CLOSED) {
        require(job?.status == JobStatus.SUCCEEDED)
        require(attempt.completedItems == attempt.totalItems)
    }
    return WorkPeriodState(current, job)
}

fun WorkPeriodEmployeeRow.toReference(): WorkforceEmployeeReference {
    require(employeeNumber.isNotBlank() && legalName.isNotBlank() && version >= 0)
    return WorkforceEmployeeReference(
        id,
        employeeNumber,
        legalName,
        EmploymentStatus.valueOf(status),
        version,
    )
}
