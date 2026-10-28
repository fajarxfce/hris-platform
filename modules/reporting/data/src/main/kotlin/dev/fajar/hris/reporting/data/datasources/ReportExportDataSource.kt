package dev.fajar.hris.reporting.data.datasources

import dev.fajar.hris.reporting.data.dto.*
import java.time.Instant
import java.util.UUID

interface ReportExportDataSource {
    fun lockOwner(company: UUID, account: UUID)

    fun quota(company: UUID, account: UUID): ReportExportQuotaRow

    fun create(row: ReportExportRow): Int

    fun find(company: UUID, account: UUID, id: UUID, lock: Boolean): ReportExportRow?

    fun list(
        company: UUID,
        account: UUID,
        beforeAt: Instant?,
        beforeId: UUID?,
        limit: Int,
    ): List<ReportExportRow>

    fun captured(company: UUID, account: UUID, id: UUID, version: Long, at: Instant): Int

    fun reserve(row: ReportExportUploadRow, version: Long): Int

    fun accept(row: ReportExportPartRow, version: Long, at: Instant): Int

    fun parts(company: UUID, account: UUID, id: UUID, limit: Int): List<ReportExportPartRow>

    fun publish(
        company: UUID,
        account: UUID,
        id: UUID,
        version: Long,
        tag: String,
        at: Instant,
    ): Int

    fun stop(company: UUID, account: UUID, id: UUID, version: Long, code: String, at: Instant): Int

    fun resume(company: UUID, account: UUID, id: UUID, version: Long, job: UUID, at: Instant): Int

    fun retire(company: UUID, account: UUID, id: UUID, version: Long, at: Instant): Int
}
