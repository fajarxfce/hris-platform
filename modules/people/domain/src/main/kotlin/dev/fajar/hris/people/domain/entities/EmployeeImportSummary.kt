package dev.fajar.hris.people.domain.entities

import dev.fajar.hris.jobs.domain.entities.JobStatus

data class EmployeeImportSummary(
    val batch: EmployeeImport,
    val counts: Map<EmployeeImportRowStatus, Int>,
    val jobStatus: JobStatus,
    val cancellationRequested: Boolean,
    val availableActions: List<String>,
)
