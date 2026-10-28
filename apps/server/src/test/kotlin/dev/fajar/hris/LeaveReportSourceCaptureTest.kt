package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.data.datasources.ReportSnapshotSourceDataSource
import dev.fajar.hris.reporting.data.dto.ReportDetailCaptureQuery
import dev.fajar.hris.reporting.data.mappers.toSnapshotCells
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.reportExportPrefixCells
import dev.fajar.hris.reporting.domain.repositories.*
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class LeaveReportSourceCaptureTest : LeaveReportApiFixture() {
    @Autowired private lateinit var snapshots: ReportSnapshotRepository
    @Autowired private lateinit var source: ReportSnapshotSourceRepository
    @Autowired private lateinit var sourceTransactions: ReportSourceTransactionRunner

    private fun probe() = ReportSourceCaptureProbe(snapshots, source, sourceTransactions)

    @Autowired private lateinit var rawSource: ReportSnapshotSourceDataSource

    private fun sourceActor(f: LeaveFixture) =
        Actor(f.account, f.company, emptySet(), clock.instant(), UUID.randomUUID())

    @Test
    fun frozenClippedDaysAndStatusSurviveLaterWithdrawalAndNewRequests() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "10"))
        val p = probe()
        val actor = sourceActor(f)
        val c = p.definition(actor, ReportKind.LEAVE, "2026-10-06", "2026-10-09")
        assertEquals(0, p.capture(actor, c).rows)
        val id = UUID.randomUUID()
        body(submit(f, id, listOf("2026-10-05" to "FULL", "2026-10-06" to "FIRST_HALF")))
        val snapshot = p.capture(actor, c.copy(id = UUID.randomUUID()))
        val frozen = p.rows(actor, snapshot)
        assertEquals(1, frozen.size)
        val row = frozen.single()
        assertEquals(id.toString(), row["request_id"])
        assertEquals("1", row["half_days"])
        assertEquals("0.5", row["days"])
        assertEquals("3", row["request_half_days"])
        assertEquals("1.5", row["request_days"])
        assertEquals("PENDING", row["status"])
        assertEquals(requests(f)["items"].single()["employeeName"].asString(), row["employee_name"])
        body(action(f, id, "withdraw", 0))
        body(submit(f, UUID.randomUUID(), listOf("2026-10-07" to "FULL")))
        assertEquals(frozen, p.rows(actor, snapshot))
        val newer = p.rows(actor, p.capture(actor, c.copy(id = UUID.randomUUID())))
        assertEquals(2, newer.size)
        assertEquals("CANCELLED", newer.single { it["request_id"] == id.toString() }["status"])
    }

    @Test
    fun sqlOverflowNeverInsertsAPrefixAndFailedCaptureRollsBackItsHeader() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        body(adjust(f, "10"))
        for (day in listOf("2026-10-06", "2026-10-07")) body(
            submit(f, UUID.randomUUID(), listOf(day to "FULL"))
        )
        val actor = sourceActor(f)
        val p = probe()
        for ((maxRows, maxBytes) in listOf(1 to 104857600L, 100000 to 1L)) {
            val c = p.definition(actor, ReportKind.LEAVE, "2026-10-06", "2026-10-09")
            val rejected =
                sourceTransactions.runWithReportSources(actor, setOf(f.company)) {
                    snapshots.begin(c).flatMap {
                        val totals =
                            rawSource.captureLeave(
                                ReportDetailCaptureQuery(
                                    c.companyId,
                                    c.accountId,
                                    c.id,
                                    LocalDate.parse("2026-10-06"),
                                    LocalDate.parse("2026-10-09"),
                                    reportExportPrefixCells(c).toSnapshotCells(json),
                                    maxRows,
                                    maxBytes,
                                )
                            )
                        assertEquals(2, totals.rows)
                        assertEquals(0, totals.inserted)
                        Result.Failed(Failure(FailureKind.VALIDATION, "capture_rejected"))
                    }
                }
            assertInstanceOf(Result.Failed::class.java, rejected)
            assertEquals(
                Result.Success(null),
                sourceTransactions.runWithReportSources(actor, setOf(f.company)) {
                    snapshots.find(f.company, actor.accountId, c.id)
                },
            )
        }
    }
}
