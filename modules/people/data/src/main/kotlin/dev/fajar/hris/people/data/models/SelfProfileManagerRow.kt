package dev.fajar.hris.people.data.models

import java.time.LocalDate
import java.util.UUID

data class SelfProfileManagerRow(
    val id: UUID,
    val employeeNumber: String,
    val legalName: String,
    val status: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
)
