package dev.fajar.hris.reporting.domain.entities

import java.time.Instant
import java.util.UUID

data class ReportExportExpiryKey(
    val companyId: UUID,
    val accountId: UUID,
    val id: UUID,
    val expiresAt: Instant,
    val dueAt: Instant,
)

/** Cleanup deliberately does not decode a potentially unsupported report definition or schema. */
data class ReportExportRetirement(
    val companyId: UUID,
    val accountId: UUID,
    val id: UUID,
    val jobId: UUID,
    val status: ReportExportStatus,
    val version: Long,
    val updatedAt: Instant,
    val expiresAt: Instant,
    val retiredAt: Instant?,
)

data class ReportExportCleanupResult(val processed: Int, val deletedRows: Int, val retired: Int)
