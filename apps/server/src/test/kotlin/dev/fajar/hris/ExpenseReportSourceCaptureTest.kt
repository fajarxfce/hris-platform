package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.repositories.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ExpenseReportSourceCaptureTest : ExpenseReportApiFixture() {
    @Autowired private lateinit var snapshots: ReportSnapshotRepository
    @Autowired private lateinit var source: ReportSnapshotSourceRepository
    @Autowired private lateinit var sourceTransactions: ReportSourceTransactionRunner

    private fun probe() = ReportSourceCaptureProbe(snapshots, source, sourceTransactions)

    @Test
    fun captureKeepsLatestSubmittedRevisionWindowAmountsAndPriorDraftUntilResubmission() {
        val f = reportFixture()
        val actor = Actor(adminAccount(), f.company, emptySet(), clock.instant(), UUID.randomUUID())
        val p = probe()
        val c =
            p.definition(
                actor,
                ReportKind.EXPENSES,
                reportFrom().toString(),
                reportUntil().toString(),
            )
        assertEquals(0, p.capture(actor, c).rows)
        val submitted =
            pendingExpense(
                f,
                listOf(
                    reportLine(f, "100.10", reportFrom().minusDays(1)),
                    reportLine(f, "0.01"),
                    reportLine(f, "123.45", reportUntil()),
                ),
            )
        val snapshot = p.capture(actor, c.copy(id = UUID.randomUUID()))
        val frozen = p.rows(actor, snapshot)
        val row = frozen.single()
        assertEquals(submitted.toString(), row["submission_id"])
        assertEquals("1", row["submission_number"])
        assertEquals("PENDING", row["status"])
        assertEquals("2", row["lines"])
        assertEquals("123.46", row["amount"])
        assertEquals("223.56", row["submission_amount"])
        assertEquals("IDR", row["currency"])
        body(
            reviewExpense(f, submitted, reviewer(f), decision = "RETURN", reason = "Clarify amount")
        )
        body(
            saveExpense(
                f,
                2,
                listOf(reportLine(f, "200.02")),
                changes = mapOf("title" to "Revised travel"),
            )
        )
        val draft = p.rows(actor, p.capture(actor, c.copy(id = UUID.randomUUID()))).single()
        assertEquals("DRAFT", draft["status"])
        assertEquals("Travel reimbursement", draft["title"])
        assertEquals("123.46", draft["amount"])
        assertEquals(submitted.toString(), draft["submission_id"])
        val next = UUID.randomUUID()
        body(submitExpense(f, next, 3))
        val latest = p.rows(actor, p.capture(actor, c.copy(id = UUID.randomUUID()))).single()
        assertEquals(next.toString(), latest["submission_id"])
        assertEquals("2", latest["submission_number"])
        assertEquals("200.02", latest["amount"])
        assertEquals("Revised travel", latest["title"])
        assertEquals(frozen, p.rows(actor, snapshot))
    }
}
