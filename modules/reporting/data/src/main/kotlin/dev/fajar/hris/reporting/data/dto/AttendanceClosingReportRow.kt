package dev.fajar.hris.reporting.data.dto

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class AttendanceClosingReportRow(
    val month: LocalDate?,
    val periodId: UUID?,
    val jobId: UUID?,
    val version: Long?,
    val closedAt: OffsetDateTime?,
    val timezone: String?,
    val counts: AttendanceClosingCountsRow,
    val invalidSources: Long = 0,
)
