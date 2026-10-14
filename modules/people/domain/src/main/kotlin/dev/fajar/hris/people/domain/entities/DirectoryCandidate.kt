package dev.fajar.hris.people.domain.entities

import java.time.LocalDate
import java.util.UUID

/** Internal projection inputs; employment terms never enter the public record. */
data class DirectoryCandidate(
    val id: UUID,
    val entry: DirectoryEntry,
    val status: EmploymentStatus?,
    val start: LocalDate?,
    val end: LocalDate?,
    val nextRevision: LocalDate?,
)
