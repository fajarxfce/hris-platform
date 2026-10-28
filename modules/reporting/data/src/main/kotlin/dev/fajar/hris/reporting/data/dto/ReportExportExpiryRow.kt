package dev.fajar.hris.reporting.data.dto

import java.time.Instant
import java.util.UUID

data class ReportExportExpiryRow(
    val company: UUID,
    val account: UUID,
    val id: UUID,
    val expires: Instant,
    val due: Instant,
)

data class ReportExportRetirementRow(
    val company: UUID,
    val account: UUID,
    val id: UUID,
    val job: UUID,
    val status: String,
    val version: Long,
    val updated: Instant,
    val expires: Instant,
    val retired: Instant?,
)
