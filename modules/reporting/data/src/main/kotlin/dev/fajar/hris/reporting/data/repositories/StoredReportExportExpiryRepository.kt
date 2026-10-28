package dev.fajar.hris.reporting.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.data.datasources.ReportExportExpiryDataSource
import dev.fajar.hris.reporting.data.dto.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.ReportExportExpiryRepository
import java.time.Instant

class StoredReportExportExpiryRepository(private val source: ReportExportExpiryDataSource) :
    ReportExportExpiryRepository {
    override fun due(at: Instant, limit: Int): Result<List<ReportExportExpiryKey>> {
        if (limit !in 1..4 || !isReportExportInstant(at))
            return exportExecutionFailure("invalid_cleanup_limit", FailureKind.VALIDATION)
        return safeDatabaseCall {
            val rows = source.due(at, limit)
            require(
                rows.size <= limit &&
                    rows.map { Triple(it.company, it.account, it.id) }.toSet().size == rows.size
            )
            java.util.List.copyOf(
                rows.map {
                    require(
                        isReportExportInstant(it.expires) &&
                            isReportExportInstant(it.due) &&
                            it.expires <= it.due &&
                            it.due <= at
                    )
                    ReportExportExpiryKey(it.company, it.account, it.id, it.expires, it.due)
                }
            )
        }
    }

    override fun inspect(key: ReportExportExpiryKey, lock: Boolean) = safeDatabaseCall {
        source.inspect(key.companyId, key.accountId, key.id, lock)?.let {
            val status = ReportExportStatus.valueOf(it.status)
            require(
                it.company == key.companyId &&
                    it.account == key.accountId &&
                    it.id == key.id &&
                    it.expires == key.expiresAt &&
                    it.version in 0..REPORT_EXPORT_MAX_VERSION &&
                    isReportExportInstant(it.updated) &&
                    isReportExportInstant(it.expires) &&
                    (it.retired == null ||
                        (isReportExportInstant(it.retired) &&
                            it.retired >= it.expires &&
                            it.retired <= it.updated &&
                            status in setOf(ReportExportStatus.READY, ReportExportStatus.STOPPED)))
            )
            ReportExportRetirement(
                it.company,
                it.account,
                it.id,
                it.job,
                status,
                it.version,
                it.updated,
                it.expires,
                it.retired,
            )
        }
    }

    override fun lockDue(key: ReportExportExpiryKey, at: Instant) = safeDatabaseCall {
        source.lockDue(key.companyId, key.accountId, key.id, at)
    }

    override fun stop(current: ReportExportRetirement, at: Instant) = change {
        source.stop(current.row(), at)
    }

    override fun retire(current: ReportExportRetirement, at: Instant) = change {
        source.retire(current.row(), at)
    }

    override fun postpone(key: ReportExportExpiryKey, at: Instant) = safeDatabaseCall {
        require(source.postpone(key.companyId, key.accountId, key.id, at) == 1)
    }

    override fun retry(key: ReportExportExpiryKey, at: Instant) = change {
        source.retry(key.companyId, key.accountId, key.id, key.dueAt, at)
    }

    override fun remove(key: ReportExportExpiryKey) = safeDatabaseCall {
        require(source.remove(key.companyId, key.accountId, key.id) == 1)
    }

    private fun change(action: () -> Int) = safeDatabaseCall {
        action().also { require(it in 0..1) } == 1
    }

    private fun ReportExportRetirement.row() =
        ReportExportRetirementRow(
            companyId,
            accountId,
            id,
            jobId,
            status.name,
            version,
            updatedAt,
            expiresAt,
            retiredAt,
        )
}
