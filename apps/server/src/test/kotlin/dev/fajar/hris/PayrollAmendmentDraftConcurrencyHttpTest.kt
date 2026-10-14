package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentDraftConcurrencyHttpTest : PayrollAmendmentDraftApiFixture() {
    @Test
    fun concurrentIdenticalDraftCreationAndCalculationReturnOneDurableReceipt() {
        val f = amendmentFixture()
        val draft =
            DraftFixture(
                f,
                UUID.randomUUID(),
                UUID.randomUUID(),
                draftBody(f, amendmentPreparation(f)),
            )
        Executors.newFixedThreadPool(2).use { pool ->
            val receipts =
                (1..2)
                    .map {
                        pool.submit<HttpResponse<String>> {
                            saveDraft(f, draft.id, draft.body, draft.operation)
                        }
                    }
                    .map { payrollBody(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(receipts[0], receipts[1])
            assertEquals(1, count(f.payroll.company, "payroll_amendments"))
            assertEquals(1, count(f.payroll.company, "payroll_amendment_revisions"))
            val key = UUID.randomUUID()
            val calculated =
                (1..2)
                    .map {
                        pool.submit<HttpResponse<String>> {
                            draftCommand(draft, "calculate", 0, key)
                        }
                    }
                    .map { payrollBody(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(calculated[0], calculated[1])
            assertEquals(1, count(f.payroll.company, "payroll_amendment_calculations"))
            assertEquals(1, count(f.payroll.company, "payroll_amendment_results"))
            assertEquals(2, count(f.payroll.company, "payroll_amendment_revisions"))
        }
        val before = draftCounts(f)
        val changed = draftBody(f, amendmentPreparation(f), reason = "Different reviewed proposal")
        assertEquals(409, saveDraft(f, draft.id, changed, draft.operation).statusCode())
        assertEquals(before, draftCounts(f))
    }

    @Test
    fun competingEditAndCalculationCannotBothCommitAgainstTheSameDraftVersion() {
        val draft = draftFixture()
        val f = draft.source
        val preparation = amendmentPreparation(f)
        val edit =
            draftBody(f, preparation, 0, listOf(remuneration(preparation, difference = "2000000")))
        val before = draftCounts(f)
        Executors.newFixedThreadPool(2).use { pool ->
            val results =
                listOf(
                        pool.submit<HttpResponse<String>> { saveDraft(f, draft.id, edit) },
                        pool.submit<HttpResponse<String>> { draftCommand(draft, "calculate", 0) },
                    )
                    .map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(
                listOf(200, 409),
                results.map { it.statusCode() }.sorted(),
                results.map { it.body() }.toString(),
            )
            assertEquals(
                "stale_version",
                json.readTree(results.single { it.statusCode() == 409 }.body())["code"].asString(),
            )
        }
        assertEquals(1, draftView(draft)["amendment"]["summary"]["version"].asLong())
        assertEquals(
            before.getValue("payroll_amendment_revisions") + 1,
            count(f.payroll.company, "payroll_amendment_revisions"),
        )
        assertEquals(
            before.getValue("operation_receipts") + 1,
            count(f.payroll.company, "operation_receipts"),
        )
        assertEquals(
            before.getValue("audit_entries") + 1,
            count(f.payroll.company, "audit_entries"),
        )
        assertEquals(
            before.getValue("outbox_events") + 1,
            count(f.payroll.company, "outbox_events"),
        )
    }
}
