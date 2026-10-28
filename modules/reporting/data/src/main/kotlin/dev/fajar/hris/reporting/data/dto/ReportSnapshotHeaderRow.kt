package dev.fajar.hris.reporting.data.dto

import java.time.Instant
import java.util.UUID

data class ReportSnapshotHeaderRow(
    val companyId: UUID,
    val accountId: UUID,
    val id: UUID,
    val definition: String,
    val columns: String,
    val evaluatedAt: Instant,
    val expiresAt: Instant,
    val rows: Int?,
    val storedBytes: Long?,
)

data class ReportSnapshotCellsRow(val number: Int, val cells: String)
