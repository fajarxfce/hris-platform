package dev.fajar.hris.people.domain.policies

import dev.fajar.hris.jobs.domain.entities.JobStatus
import dev.fajar.hris.people.domain.entities.*

/** Observed review actions; commands recheck versions, authority, and execution policy. */
fun availableEmployeeImportActions(
    status: EmployeeImportStatus,
    counts: Map<EmployeeImportRowStatus, Int>,
    jobStatus: JobStatus,
    cancellationRequested: Boolean,
): List<String> = buildList {
    if (status in setOf(EmployeeImportStatus.COMPLETED, EmployeeImportStatus.CANCELLED))
        return@buildList
    if (
        status == EmployeeImportStatus.REVIEW &&
            (counts[EmployeeImportRowStatus.READY] ?: 0) > 0 &&
            (counts[EmployeeImportRowStatus.PENDING] ?: 0) == 0
    )
        add("apply")
    if (
        status in
            setOf(
                EmployeeImportStatus.STOPPED,
                EmployeeImportStatus.PREVIEWING,
                EmployeeImportStatus.IMPORTING,
            ) && jobStatus in setOf(JobStatus.FAILED, JobStatus.CANCELLED)
    )
        add("resume")
    if (!(cancellationRequested && jobStatus in setOf(JobStatus.QUEUED, JobStatus.RUNNING)))
        add("cancel")
}
