package dev.fajar.hris.reporting.data.dto

import java.time.Instant
import java.util.UUID

data class ReportExportRow(
    val company: UUID,
    val account: UUID,
    val id: UUID,
    val definition: String,
    val columns: String,
    val sources: String,
    val client: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val job: UUID,
    val status: String,
    val attempt: Int,
    val version: Long,
    val updatedAt: Instant,
    val snapshot: UUID?,
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

data class ReportExportQuotaRow(val active: Int, val retained: Int)

data class ReportExportUploadRow(
    val id: UUID,
    val company: UUID,
    val account: UUID,
    val export: UUID,
    val number: Int,
    val afterRow: Int,
    val rows: Int,
    val offset: Long,
    val bytes: Int,
    val sha256: String,
    val createdAt: Instant,
)

data class ReportExportPartRow(val upload: ReportExportUploadRow, val etag: String)
