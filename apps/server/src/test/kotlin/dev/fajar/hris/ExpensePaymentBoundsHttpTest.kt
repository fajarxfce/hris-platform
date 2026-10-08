package dev.fajar.hris

import java.sql.SQLException
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExpensePaymentBoundsHttpTest : ExpensePaymentApiFixture() {
    @Test
    fun aMaximumSizeBatchSnapshotsExportsAndReconcilesOneHundredDistinctClaims() {
        val f = expenseFixture()
        expenseTemplate(f)
        expensePolicy(f, changes = mapOf("receiptRequired" to false))
        val manager = reviewer(f)
        val maker = payer(f)
        val checker = payer(f)
        val instructions =
            (1..100).map {
                val next = f.copy(claim = UUID.randomUUID(), line = UUID.randomUUID())
                val submission = pendingExpense(next, lines = listOf(expenseLine(next)))
                val approved = reviewExpense(next, submission, manager)
                assertEquals(200, approved.statusCode(), approved.body())
                paymentInstruction(submission)
            }
        val batch = UUID.randomUUID()
        val prepared = preparePayment(f, maker, batch, instructions)
        assertEquals(200, prepared.statusCode(), prepared.body())
        val detail = paymentDetails(f, maker, batch)
        assertEquals(100, detail.get("items").size())
        assertEquals("15000000.00", detail.get("totalAmount").asString())
        val released = paymentAction(f, checker, batch, "release", 0)
        assertEquals(200, released.statusCode(), released.body())
        val exported =
            get(maker.browser, "/api/v1/companies/${f.company}/expenses/payments/$batch/export")
        assertEquals(200, exported.statusCode(), exported.body())
        assertEquals(101, exported.body().split("\r\n").count { it.isNotEmpty() })
        assertTrue(exported.body().toByteArray().size < 262144)
        val outcomes = instructions.map { paymentResult(it["id"] as UUID) }
        val reconciled = reconcilePayment(f, maker, batch, 1, outcomes)
        assertEquals(200, reconciled.statusCode(), reconciled.body())
        assertEquals("CLOSED", paymentDetails(f, maker, batch).get("status").asString())
        assertEquals(
            100,
            database()
                .queryForObject(
                    "select count(*) from expense_payment_results where company_id=? and batch_id=?",
                    Int::class.java,
                    f.company,
                    batch,
                ),
        )
    }

    @Test
    fun twentyCancelledPreparationsExhaustFurtherAttemptsWithoutHidingTheirHistory() {
        val f = expenseFixture()
        expenseTemplate(f)
        val maker = payer(f)
        val submission = approvedExpense(f)
        repeat(20) {
            val batch = UUID.randomUUID()
            val prepared = preparePayment(f, maker, batch, listOf(paymentInstruction(submission)))
            assertEquals(200, prepared.statusCode(), prepared.body())
            assertEquals(200, paymentAction(f, maker, batch, "cancel", 0).statusCode())
        }
        val denied =
            preparePayment(f, maker, UUID.randomUUID(), listOf(paymentInstruction(submission)))
        assertEquals(409, denied.statusCode(), denied.body())
        assertEquals(
            "expense_payment_attempt_limit",
            json.readTree(denied.body()).get("code").asString(),
        )
        val today = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Jakarta"))
        val payables =
            get(
                maker.browser,
                "/api/v1/companies/${f.company}/expenses/payments/payables?from=$today&until=$today",
            )
        assertEquals(200, payables.statusCode(), payables.body())
        assertEquals(0, json.readTree(payables.body()).get("items").size())
        val progress = get(f.worker, "${f.path}/${f.claim}/payments")
        assertEquals(200, progress.statusCode(), progress.body())
        assertEquals(20, json.readTree(progress.body()).size())
    }

    @Test
    fun databaseRejectsSettlementWithoutItsImmutableResultEvidence() {
        val f = expenseFixture()
        expenseTemplate(f)
        val maker = payer(f)
        val checker = payer(f)
        val submission = approvedExpense(f)
        val batch = UUID.randomUUID()
        assertEquals(
            200,
            preparePayment(f, maker, batch, listOf(paymentInstruction(submission))).statusCode(),
        )
        assertEquals(200, paymentAction(f, checker, batch, "release", 0).statusCode())
        requireNotNull(database().dataSource).connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement("select set_config('hris.actor_id',?,true)").use {
                    it.setString(1, maker.account.toString())
                    it.execute()
                }
                connection
                    .prepareStatement(
                        "update expense_payment_batches set status='CLOSED',version=2 where company_id=? and id=?"
                    )
                    .use {
                        it.setObject(1, f.company)
                        it.setObject(2, batch)
                        assertEquals(1, it.executeUpdate())
                    }
                connection
                    .prepareStatement(
                        "update expense_payment_items set status='SUCCEEDED',version=2,batch_version=2,transaction_reference='MISSING-EVIDENCE',occurred_at=clock_timestamp() where company_id=? and batch_id=?"
                    )
                    .use {
                        it.setObject(1, f.company)
                        it.setObject(2, batch)
                        assertEquals(1, it.executeUpdate())
                    }
                connection
                    .prepareStatement(
                        "insert into expense_payment_actions(company_id,batch_id,version,kind,status,actor_id,reason,recorded_at) values(?,?,2,'RECONCILED','CLOSED',?,'Fixture settlement',clock_timestamp())"
                    )
                    .use {
                        it.setObject(1, f.company)
                        it.setObject(2, batch)
                        it.setObject(3, maker.account)
                        assertEquals(1, it.executeUpdate())
                    }
                val failed = assertThrows(SQLException::class.java) { connection.commit() }
                assertEquals("23514", failed.sqlState)
            } finally {
                connection.rollback()
            }
        }
        val detail = paymentDetails(f, maker, batch)
        assertEquals("RELEASED", detail.get("status").asString())
        assertEquals("PENDING", detail.get("items").get(0).get("status").asString())
    }
}
