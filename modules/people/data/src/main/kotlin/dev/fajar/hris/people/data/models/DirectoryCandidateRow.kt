package dev.fajar.hris.people.data.models

import java.time.LocalDate
import java.util.UUID

data class DirectoryCandidateRow(
    val id: UUID,
    val employeeNumber: String,
    val name: String,
    val positionId: UUID?,
    val position: String?,
    val departmentId: UUID?,
    val department: String?,
    val status: String?,
    val start: LocalDate?,
    val end: LocalDate?,
    val nextRevision: LocalDate?,
)
