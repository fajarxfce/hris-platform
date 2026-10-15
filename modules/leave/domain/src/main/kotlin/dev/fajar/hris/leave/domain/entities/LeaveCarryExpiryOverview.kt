package dev.fajar.hris.leave.domain.entities

import dev.fajar.hris.jobs.domain.entities.JobStatus

data class LeaveCarryExpiryOverview(
    val expiry: LeaveCarryExpiry,
    val typeCode: String,
    val typeName: String,
    val status: JobStatus,
    val completedEmployees: Int,
    val failureCode: String?,
)
