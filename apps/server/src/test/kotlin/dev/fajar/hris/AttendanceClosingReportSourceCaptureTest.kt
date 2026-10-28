package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.repositories.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AttendanceClosingReportSourceCaptureTest : AttendanceClosingReportApiFixture() {
    @Autowired private lateinit var snapshots: ReportSnapshotRepository
    @Autowired private lateinit var source: ReportSnapshotSourceRepository
    @Autowired private lateinit var sourceTransactions: ReportSourceTransactionRunner

    private fun probe() = ReportSourceCaptureProbe(snapshots, source, sourceTransactions)

    @Test
    fun capturePinsPublishedReferencesAndLatestCorrectionsAcrossMonthsAndExcludesFutureHire() {
        val f = isolatedFixture()
        mixedCalendar(f)
        val future = UUID.randomUUID()
        reportBody(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/employees",
                """{"id":"$future","employeeNumber":"FUTURE","person":{"id":"${UUID.randomUUID()}","legalName":"Future employee","nationality":"ID"},"terms":{"effectiveFrom":"2026-10-01","startDate":"2026-10-01","contract":"PERMANENT","status":"ACTIVE"},"reason":"Future hire"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        )
        val p = probe()
        val c = p.definition(f.actor, ReportKind.ATTENDANCE_CLOSINGS, "2026-08", "2026-10")
        assertEquals(0, p.capture(f.actor, c).rows)
        finishMonth(f, "2026-08", 2)
        finishMonth(f, "2026-09", 2)
        val original = p.capture(f.actor, c.copy(id = UUID.randomUUID()))
        val before = p.rows(f.actor, original)
        assertEquals(listOf("2026-08", "2026-09"), before.map { it["month"] })
        assertTrue(
            before.all { it["employee_id"] == f.employee.toString() && it["adjustment_id"] == null }
        )
        assertEquals(listOf("31", "30"), before.map { it["days"] })
        capture(f)
        adjusted(f, adjustmentBody(adjustmentReview(f)))
        val snapshot = p.capture(f.actor, c.copy(id = UUID.randomUUID()))
        val frozen = p.rows(f.actor, snapshot)
        val september = frozen.last()
        assertEquals("480", september["original_accepted_minutes"])
        assertEquals("900", september["current_accepted_minutes"])
        assertNotNull(september["adjustment_id"])
        assertEquals("1", september["adjustment_revision"])
        assertEquals(before.last()["closing_job_id"], september["closing_job_id"])
        assertEquals(before.last()["closing_version"], september["closing_version"])
        assertEquals("Asia/Jakarta", september["timezone"])
        adjusted(f, adjustmentBody(adjustmentReview(f, "2026-09-29"), "2026-09-29", absence = true))
        assertEquals(frozen, p.rows(f.actor, snapshot))
        assertEquals(before, p.rows(f.actor, original))
        val latest = p.rows(f.actor, p.capture(f.actor, c.copy(id = UUID.randomUUID()))).last()
        assertEquals("2", latest["adjustment_revision"])
        assertNotEquals(september["adjustment_id"], latest["adjustment_id"])
        assertEquals("420", latest["current_accepted_minutes"])
    }
}
