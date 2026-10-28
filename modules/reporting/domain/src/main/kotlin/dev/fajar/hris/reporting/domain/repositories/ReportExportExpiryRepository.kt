package dev.fajar.hris.reporting.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.domain.entities.*
import java.time.Instant

interface ReportExportExpiryRepository {
    /** Global discovery is available only to the database worker capability. */
    fun due(at: Instant, limit: Int): Result<List<ReportExportExpiryKey>>

    fun inspect(key: ReportExportExpiryKey, lock: Boolean = false): Result<ReportExportRetirement?>

    fun lockDue(key: ReportExportExpiryKey, at: Instant): Result<Boolean>

    fun stop(current: ReportExportRetirement, at: Instant): Result<Boolean>

    fun retire(current: ReportExportRetirement, at: Instant): Result<Boolean>

    fun postpone(key: ReportExportExpiryKey, at: Instant): Result<Unit>

    /** Retry only the unchanged discovery position; never overwrite another worker's progress. */
    fun retry(key: ReportExportExpiryKey, at: Instant): Result<Boolean>

    fun remove(key: ReportExportExpiryKey): Result<Unit>
}
