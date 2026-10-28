package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

interface LeaveReportDataSource {
    fun aggregate(company: UUID, from: LocalDate, until: LocalDate): List<LeaveReportAggregateRow>

    fun requests(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        status: String?,
        afterAt: OffsetDateTime?,
        afterId: UUID?,
        limit: Int,
    ): List<LeaveReportRequestRow>
}
