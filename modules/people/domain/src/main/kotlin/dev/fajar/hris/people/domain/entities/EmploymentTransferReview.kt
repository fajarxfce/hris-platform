package dev.fajar.hris.people.domain.entities

import dev.fajar.hris.organization.domain.entities.Company
import java.time.Instant
import java.time.LocalDate

data class EmploymentTransferReview(
    val employee: Employee,
    val sourceCompany: Company,
    val targetCompany: Company,
    val sourceDate: LocalDate,
    val targetDate: LocalDate,
    val evaluatedAt: Instant,
    val targetPermissions: Set<String>,
)
