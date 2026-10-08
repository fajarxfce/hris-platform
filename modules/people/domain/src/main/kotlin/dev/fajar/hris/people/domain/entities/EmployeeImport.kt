package dev.fajar.hris.people.domain.entities

import java.time.Instant
import java.util.UUID

data class EmployeeImport(
    val id: UUID,
    val fileName: String,
    val sourceHash: String,
    val rowCount: Int,
    val status: EmployeeImportStatus,
    val jobId: UUID,
    val version: Long,
    val createdBy: UUID,
    val createdAt: Instant,
    val reason: String,
)
