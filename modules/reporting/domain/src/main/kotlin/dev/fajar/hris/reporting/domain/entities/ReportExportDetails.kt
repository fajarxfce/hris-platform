package dev.fajar.hris.reporting.domain.entities

import dev.fajar.hris.jobs.domain.entities.JobStatus
import java.time.Instant
import java.util.UUID

/** Owned status contains no facts, provider addresses or stored authority. */
data class ReportExportDetails(
    val id: UUID,
    val companyId: UUID,
    val selection: ReportSelection,
    val dataset: ReportExportDataset,
    val status: ReportExportStatus,
    val version: Long,
    val attempt: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant,
    val expired: Boolean,
    val retiredAt: Instant?,
    val completedRows: Int,
    val bytes: Long,
    val jobId: UUID,
    val jobStatus: JobStatus,
    val cancellationRequested: Boolean,
    val failureCode: String?,
)

data class ReportExportHistory(val items: List<ReportExportDetails>, val next: ReportExportCursor?)

data class ReportExportDownload(
    val id: UUID,
    val fileName: String,
    val bytes: Long,
    val tag: String,
)
