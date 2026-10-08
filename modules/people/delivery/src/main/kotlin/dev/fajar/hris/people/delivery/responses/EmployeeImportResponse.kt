package dev.fajar.hris.people.delivery.responses

import java.time.Instant
import java.util.UUID

data class EmployeeImportResponse(
    val id: UUID,
    val fileName: String,
    val sourceHash: String,
    val rowCount: Int,
    val status: String,
    val jobId: UUID,
    val version: Long,
    val createdBy: UUID,
    val createdAt: Instant,
    val reason: String,
)
