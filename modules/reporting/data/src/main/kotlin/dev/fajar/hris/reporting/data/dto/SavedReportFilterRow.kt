package dev.fajar.hris.reporting.data.dto

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class SavedReportFilterRow(
    val companyId: UUID,
    val accountId: UUID,
    val id: UUID,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val name: String?,
    val kind: String?,
    val definitionVersion: String?,
    val asOf: LocalDate?,
    val from: String?,
    val until: String?,
    val status: String?,
    val companies: List<UUID>,
)
