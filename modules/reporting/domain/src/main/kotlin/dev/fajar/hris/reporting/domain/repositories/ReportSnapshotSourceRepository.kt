package dev.fajar.hris.reporting.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.reporting.domain.entities.*

interface ReportSnapshotSourceRepository {
    /**
     * Freeze a known detail projection in one SQL snapshot, without materializing its rows in the
     * application. The caller owns source authorization, begin/capture/seal and their transaction.
     */
    fun captureDetails(capture: ReportSnapshotCapture): Result<ReportCaptureTotals>
}
