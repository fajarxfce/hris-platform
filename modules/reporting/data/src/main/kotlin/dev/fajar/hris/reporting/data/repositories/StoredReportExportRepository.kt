package dev.fajar.hris.reporting.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.data.datasources.ReportExportDataSource
import dev.fajar.hris.reporting.data.mappers.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.ReportExportRepository
import java.time.Instant
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredReportExportRepository(
    private val source: ReportExportDataSource,
    private val json: ObjectMapper,
) : ReportExportRepository {
    override fun lockOwner(company: UUID, account: UUID) = safeDatabaseCall {
        source.lockOwner(company, account)
    }

    override fun quota(company: UUID, account: UUID) = safeDatabaseCall {
        val q = source.quota(company, account)
        require(
            q.active in 0..REPORT_EXPORT_MAX_ACTIVE + 1 &&
                q.retained in q.active..REPORT_EXPORT_MAX_RETAINED + 1
        )
        ReportExportQuota(q.active, q.retained)
    }

    override fun create(request: ReportExportRequest, jobId: UUID) =
        validateReportExportRequest(request).flatMap { r ->
            write { source.create(r.toExportRow(jobId, json)) }
        }

    override fun find(company: UUID, account: UUID, id: UUID, lock: Boolean) = safeDatabaseCall {
        source.find(company, account, id, lock)?.toReportExport(company, account, id, json)
    }

    override fun list(
        company: UUID,
        account: UUID,
        before: ReportExportCursor?,
        limit: Int,
    ): Result<List<ReportExport>> {
        if (limit !in 1..51 || before?.let { !isReportExportInstant(it.createdAt) } == true)
            return invalid("invalid_page")
        return safeDatabaseCall {
            val rows = source.list(company, account, before?.createdAt, before?.id, limit)
            require(rows.size <= limit)
            val values = rows.map { it.toReportExport(company, account, null, json) }
            var previous = before
            for (value in values) {
                val cursor = ReportExportCursor(value.request.createdAt, value.request.id)
                require(
                    previous == null ||
                        cursor.createdAt < previous.createdAt ||
                        (cursor.createdAt == previous.createdAt &&
                            cursor.id.toString() < previous.id.toString())
                )
                previous = cursor
            }
            java.util.List.copyOf(values)
        }
    }

    override fun captured(current: ReportExport, at: Instant) =
        change(
            current,
            current.copy(
                status = ReportExportStatus.RENDERING,
                snapshotId = current.request.id,
                version = current.version + 1,
                updatedAt = at,
            ),
        ) {
            val r = current.request
            source.captured(r.companyId, r.accountId, r.id, current.version, at)
        }

    override fun reserve(current: ReportExport, upload: ReportExportUpload): Result<Boolean> {
        if (!scope(current, upload)) return invalid("invalid_report_export_part")
        return validateReportExportUpload(upload).flatMap {
            change(
                current,
                current.copy(
                    reservationCount = current.reservationCount + 1,
                    reservedBytes = current.reservedBytes + upload.bytes,
                    version = current.version + 1,
                    updatedAt = upload.createdAt,
                ),
            ) {
                source.reserve(upload.toExportUploadRow(), current.version)
            }
        }
    }

    override fun accept(
        current: ReportExport,
        part: ReportExportPart,
        at: Instant,
    ): Result<Boolean> {
        if (!scope(current, part.upload)) return invalid("invalid_report_export_part")
        return validateReportExportPart(part).flatMap {
            change(
                current,
                current.copy(
                    completedRows = current.completedRows + part.upload.rows,
                    partCount = current.partCount + 1,
                    byteCount = current.byteCount + part.upload.bytes,
                    version = current.version + 1,
                    updatedAt = at,
                ),
            ) {
                source.accept(part.toExportPartRow(), current.version, at)
            }
        }
    }

    override fun parts(company: UUID, account: UUID, id: UUID) = safeDatabaseCall {
        val rows = source.parts(company, account, id, REPORT_EXPORT_MAX_PARTS + 1)
        require(rows.size <= REPORT_EXPORT_MAX_PARTS)
        val values = rows.map { it.toReportExportPart(company, account, id) }
        var after = 0
        var offset = 0L
        val ids = HashSet<UUID>()
        for ((index, value) in values.withIndex()) {
            val p = value.upload
            require(
                p.number == index + 1 &&
                    p.afterRow == after &&
                    p.offset == offset &&
                    ids.add(p.id) &&
                    (p.rows > 0 || values.size == 1)
            )
            after += p.rows
            offset += p.bytes
            require(after <= REPORT_SNAPSHOT_MAX_ROWS && offset <= REPORT_SNAPSHOT_MAX_BYTES)
        }
        java.util.List.copyOf(values)
    }

    override fun publish(current: ReportExport, tag: String, at: Instant) =
        change(
            current,
            current.copy(
                status = ReportExportStatus.READY,
                manifestTag = tag,
                finishedAt = at,
                version = current.version + 1,
                updatedAt = at,
            ),
        ) {
            val r = current.request
            source.publish(r.companyId, r.accountId, r.id, current.version, tag, at)
        }

    override fun stop(current: ReportExport, code: String, at: Instant) =
        change(
            current,
            current.copy(
                status = ReportExportStatus.STOPPED,
                failureCode = code,
                manifestTag = null,
                finishedAt = at,
                version = current.version + 1,
                updatedAt = at,
            ),
        ) {
            val r = current.request
            source.stop(r.companyId, r.accountId, r.id, current.version, code, at)
        }

    override fun resume(current: ReportExport, jobId: UUID, at: Instant) =
        change(
            current,
            current.copy(
                jobId = jobId,
                attempt = current.attempt + 1,
                status =
                    if (current.snapshotId == null) ReportExportStatus.QUEUED
                    else ReportExportStatus.RENDERING,
                failureCode = null,
                finishedAt = null,
                version = current.version + 1,
                updatedAt = at,
            ),
        ) {
            val r = current.request
            source.resume(r.companyId, r.accountId, r.id, current.version, jobId, at)
        }

    override fun retire(current: ReportExport, at: Instant) =
        change(
            current,
            current.copy(retiredAt = at, version = current.version + 1, updatedAt = at),
        ) {
            val r = current.request
            source.retire(r.companyId, r.accountId, r.id, current.version, at)
        }

    private fun scope(current: ReportExport, p: ReportExportUpload) =
        p.companyId == current.request.companyId &&
            p.accountId == current.request.accountId &&
            p.exportId == current.request.id &&
            p.number == current.partCount + 1 &&
            p.afterRow == current.completedRows &&
            p.offset == current.byteCount

    private fun change(
        current: ReportExport,
        next: ReportExport,
        action: () -> Int,
    ): Result<Boolean> =
        validateReportExport(current).flatMap {
            if (next.updatedAt < current.updatedAt) invalid("invalid_report_export_state")
            else validateReportExport(next).flatMap { write(action) }
        }

    private fun write(action: () -> Int) = safeDatabaseCall {
        action().also { require(it in 0..1) } == 1
    }

    private fun invalid(code: String) = Result.Failed(Failure(FailureKind.VALIDATION, code))
}
