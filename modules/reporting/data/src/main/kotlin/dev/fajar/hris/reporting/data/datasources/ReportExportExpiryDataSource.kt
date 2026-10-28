package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.*
import java.time.Instant
import java.util.UUID

interface ReportExportExpiryDataSource {
    fun due(at: Instant, limit: Int): List<ReportExportExpiryRow>

    fun inspect(company: UUID, account: UUID, id: UUID, lock: Boolean): ReportExportRetirementRow?

    fun lockDue(company: UUID, account: UUID, id: UUID, at: Instant): Boolean

    fun stop(row: ReportExportRetirementRow, at: Instant): Int

    fun retire(row: ReportExportRetirementRow, at: Instant): Int

    fun postpone(company: UUID, account: UUID, id: UUID, at: Instant): Int

    fun retry(company: UUID, account: UUID, id: UUID, observed: Instant, at: Instant): Int

    fun remove(company: UUID, account: UUID, id: UUID): Int
}
