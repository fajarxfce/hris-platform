package dev.fajar.hris.leave.domain.entities

import dev.fajar.hris.jobs.domain.entities.JobStatus
import java.time.Instant

data class LeaveAccrualSchedulePeriod(
    val scheduledFor: Instant,
    val batch: LeaveBatch,
    val jobStatus: JobStatus,
    val completedEmployees: Int,
)
