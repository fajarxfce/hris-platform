package dev.fajar.hris.leave.domain.entities

import dev.fajar.hris.people.domain.entities.EmploymentStatus
import java.util.UUID

/** Self-service selection without personal-profile or administrative assignment fields. */
data class LeaveEmployment(
    val id: UUID,
    val employeeNumber: String,
    val status: EmploymentStatus,
    val version: Long,
)
