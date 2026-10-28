package dev.fajar.hris.reporting.data.dto

import java.time.LocalDate
import java.util.UUID

data class ReportDetailCaptureQuery(
    val company: UUID,
    val account: UUID,
    val snapshot: UUID,
    val from: LocalDate,
    val until: LocalDate,
    val prefix: String,
    val maximumRows: Int,
    val maximumBytes: Long,
)

data class ReportCaptureTotalsRow(
    val rows: Int,
    val storedBytes: Long,
    val invalidSources: Long,
    val inserted: Int,
)
