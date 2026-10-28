package dev.fajar.hris.reporting.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.domain.entities.*
import java.time.Instant
import java.util.UUID

/**
 * The caller owns authorization, job fencing and atomic transactions. Acquire the job guard, owner
 * mutex, then export row before access guards; provider I/O happens after releasing them.
 */
interface ReportExportRepository {
    fun lockOwner(company: UUID, account: UUID): Result<Unit>

    fun quota(company: UUID, account: UUID): Result<ReportExportQuota>

    fun create(request: ReportExportRequest, jobId: UUID): Result<Boolean>

    fun find(company: UUID, account: UUID, id: UUID, lock: Boolean = false): Result<ReportExport?>

    fun list(
        company: UUID,
        account: UUID,
        before: ReportExportCursor?,
        limit: Int,
    ): Result<List<ReportExport>>

    fun captured(current: ReportExport, at: Instant): Result<Boolean>

    fun reserve(current: ReportExport, upload: ReportExportUpload): Result<Boolean>

    fun accept(current: ReportExport, part: ReportExportPart, at: Instant): Result<Boolean>

    fun parts(company: UUID, account: UUID, id: UUID): Result<List<ReportExportPart>>

    fun publish(current: ReportExport, tag: String, at: Instant): Result<Boolean>

    fun stop(current: ReportExport, code: String, at: Instant): Result<Boolean>

    fun resume(current: ReportExport, jobId: UUID, at: Instant): Result<Boolean>

    /**
     * Only after expiry and physical cleanup; retained metadata continues to identify the export.
     */
    fun retire(current: ReportExport, at: Instant): Result<Boolean>
}
