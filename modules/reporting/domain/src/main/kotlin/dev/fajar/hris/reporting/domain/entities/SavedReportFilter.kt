package dev.fajar.hris.reporting.domain.entities

import java.time.Instant
import java.util.UUID

/** A null definition is a retained deletion identity; its former name and selection are erased. */
data class SavedReportFilter(
    val id: UUID,
    val companyId: UUID,
    val accountId: UUID,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val definition: SavedReportFilterDefinition?,
)
