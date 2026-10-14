package dev.fajar.hris.people.domain.entities

import java.util.UUID

/** Public coworker fields only. A null employee is a previously published tombstone. */
data class DirectoryRecord(val id: UUID, val version: Long, val employee: DirectoryEntry?)

data class DirectoryEntry(
    val employeeNumber: String,
    val name: String,
    val positionId: UUID?,
    val position: String?,
    val departmentId: UUID?,
    val department: String?,
)
