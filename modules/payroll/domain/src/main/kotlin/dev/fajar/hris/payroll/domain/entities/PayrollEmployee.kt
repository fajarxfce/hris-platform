package dev.fajar.hris.payroll.domain.entities

import java.util.UUID

/**
 * Minimal current employment reference for payroll administration; excludes private profile fields.
 */
data class PayrollEmployee(
    val id: UUID,
    val employeeNumber: String,
    val name: String,
    val employmentVersion: Long,
)
