package dev.fajar.hris

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExpenseReportPaymentHttpTest : ExpensePaymentApiFixture() {
    @Test
    fun bankReservationsPartialSettlementFailureAndReceiptReplayDoNotRewriteSubmittedExpenseAmounts() {
        val f = expenseFixture()
        expenseTemplate(f)
        val submissions =
            List(2) { approvedExpense(f.copy(claim = UUID.randomUUID(), line = UUID.randomUUID())) }
        val date = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Jakarta"))
        val path = "/api/v1/companies/${f.company}/reports/expenses?from=$date&until=$date"
        fun report() =
            get(f.admin, path).let {
                assertEquals(200, it.statusCode(), it.body())
                json.readTree(it.body())
            }
        val original = report()
        assertEquals("300000.00", original["totals"]["amount"].asString())
        assertEquals(2, original["totals"]["claims"].asLong())
        fun unchanged() {
            val current = report()
            assertEquals(original["totals"], current["totals"])
            assertEquals(original["statuses"], current["statuses"])
        }
        val maker = payer(f)
        val checker = payer(f)
        val items = submissions.map { paymentInstruction(it) }
        val ids = items.map { it["id"] as UUID }
        val batch = UUID.randomUUID()
        assertEquals(200, preparePayment(f, maker, batch, items).statusCode())
        unchanged()
        assertEquals(200, paymentAction(f, checker, batch, "release", 0).statusCode())
        unchanged()
        val key = UUID.randomUUID()
        val results = listOf(paymentResult(ids[0]))
        val paid = reconcilePayment(f, maker, batch, 1, results, key)
        assertEquals(200, paid.statusCode(), paid.body())
        assertEquals(paid.body(), reconcilePayment(f, maker, batch, 1, results, key).body())
        unchanged()
        val failed =
            reconcilePayment(
                f,
                maker,
                batch,
                2,
                listOf(paymentResult(ids[1], "FAILED", null, confirmed = true)),
            )
        assertEquals(200, failed.statusCode(), failed.body())
        assertEquals("CLOSED", paymentDetails(f, maker, batch)["status"].asString())
        unchanged()
        assertEquals(
            200,
            preparePayment(f, maker, UUID.randomUUID(), listOf(paymentInstruction(submissions[1])))
                .statusCode(),
        )
        unchanged()
    }
}
