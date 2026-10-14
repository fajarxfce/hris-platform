package dev.fajar.hris.payroll.domain.entities

import java.util.UUID

/** Minimal employment reference for salary administration, independent of effective salary. */
data class CompensationEmployee(
    val id: UUID,
    val employeeNumber: String,
    val name: String,
    val employmentVersion: Long,
)
