package dev.fajar.hris.reporting.domain.entities

import dev.fajar.hris.administration.domain.entities.ClientRequest
import java.time.Instant
import java.util.UUID

/** Original source grants are retained narrowly and must intersect current access on every use. */
data class ReportExportRequest(
    val id: UUID,
    val companyId: UUID,
    val accountId: UUID,
    val selection: ReportSelection,
    val dataset: ReportExportDataset,
    val sources: Map<UUID, Set<String>>,
    val client: ClientRequest,
    val createdAt: Instant,
    val expiresAt: Instant,
    val schemaVersion: String = "report-export.v1",
    val formatVersion: String = "report-csv.v1",
)

enum class ReportExportStatus {
    QUEUED,
    RENDERING,
    READY,
    STOPPED,
}

data class ReportExport(
    val request: ReportExportRequest,
    val jobId: UUID,
    val status: ReportExportStatus,
    val attempt: Int,
    val version: Long,
    val updatedAt: Instant,
    val snapshotId: UUID?,
    val completedRows: Int,
    val partCount: Int,
    val byteCount: Long,
    val reservationCount: Int,
    val reservedBytes: Long,
    val failureCode: String?,
    val manifestTag: String?,
    val finishedAt: Instant?,
    val retiredAt: Instant?,
)

data class ReportExportQuota(val active: Int, val retained: Int)

data class ReportExportCursor(val createdAt: Instant, val id: UUID)

/** One immutable private-object reservation. An abandoned attempt's key is never reused. */
data class ReportExportUpload(
    val id: UUID,
    val companyId: UUID,
    val accountId: UUID,
    val exportId: UUID,
    val number: Int,
    val afterRow: Int,
    val rows: Int,
    val offset: Long,
    val bytes: Int,
    val sha256: String,
    val createdAt: Instant,
)

data class ReportExportPart(val upload: ReportExportUpload, val etag: String)

data class ReportExportManifest(val rows: Int, val bytes: Long, val tag: String)
