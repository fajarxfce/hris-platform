package dev.fajar.hris.people.domain.entities

import java.time.LocalDate
import java.util.UUID

/** Manager references never acquire another person's private profile. */
data class SelfProfileManagerCandidate(
    val id: UUID,
    val employeeNumber: String,
    val legalName: String,
    val status: EmploymentStatus,
    val startDate: LocalDate,
    val endDate: LocalDate?,
)
