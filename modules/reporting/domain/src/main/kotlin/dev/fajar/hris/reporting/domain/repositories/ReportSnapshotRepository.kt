package dev.fajar.hris.reporting.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.domain.entities.*
import java.time.Instant
import java.util.UUID

/**
 * Capture, append and seal share the owning use case's transaction and source guards. A committed
 * snapshot must be sealed. Source capture may insert a coherent projection directly into its rows.
 */
interface ReportSnapshotRepository {
    fun begin(capture: ReportSnapshotCapture): Result<Boolean>

    fun append(
        capture: ReportSnapshotCapture,
        after: Int,
        rows: List<List<ReportExportCell>>,
    ): Result<Unit>

    fun seal(capture: ReportSnapshotCapture): Result<ReportSnapshot>

    fun find(company: UUID, account: UUID, id: UUID): Result<ReportSnapshot?>

    /**
     * Reads a consecutive prefix of at most 500 rows and one MiB of stored JSON. Advance by the
     * returned row count, not the requested limit. The use case checks current access/expiry.
     */
    fun rows(snapshot: ReportSnapshot, after: Int, limit: Int): Result<List<ReportSnapshotRow>>

    /** Fixed expiry cleanup; neither a supplied timestamp nor the owner can retire live facts. */
    fun deleteExpiredRows(
        company: UUID,
        account: UUID,
        id: UUID,
        at: Instant,
        limit: Int,
    ): Result<Int>
}
