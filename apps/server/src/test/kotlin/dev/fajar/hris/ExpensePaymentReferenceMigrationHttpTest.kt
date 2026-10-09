package dev.fajar.hris

import java.util.UUID
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["spring.flyway.target=53"])
class ExpensePaymentReferenceMigrationHttpTest : ExpensePaymentApiFixture() {
    @Test
    fun existingSettlementsAreBackfilledPerCompanyWithoutChangingTheirEvidence() {
        val companies =
            (1..2).map {
                val f = expenseFixture()
                expensePolicy(f, changes = mapOf("receiptRequired" to false))
                expenseTemplate(f)
                val submission = pendingExpense(f, lines = listOf(expenseLine(f)))
                val approved = reviewExpense(f, submission, reviewer(f))
                assertEquals(200, approved.statusCode(), approved.body())
                val maker = payer(f)
                val checker = payer(f)
                val batch = UUID.randomUUID()
                val item = UUID.randomUUID()
                val prepared =
                    preparePayment(f, maker, batch, listOf(paymentInstruction(submission, item)))
                assertEquals(200, prepared.statusCode(), prepared.body())
                val released = paymentAction(f, checker, batch, "release", 0)
                assertEquals(200, released.statusCode(), released.body())
                val settled =
                    reconcilePayment(
                        f,
                        checker,
                        batch,
                        1,
                        listOf(paymentResult(item, reference = "MIGRATION-BANK-REFERENCE")),
                    )
                assertEquals(200, settled.statusCode(), settled.body())
                f.company
            }
        val tables =
            listOf(
                "expense_payment_batches",
                "expense_payment_items",
                "expense_payment_actions",
                "expense_payment_results",
                "mobile_sync_changes",
            )
        val before =
            tables.associateWith { table ->
                database()
                    .queryForList(
                        "select to_jsonb(t)::text as evidence from $table t order by to_jsonb(t)::text"
                    )
            }
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .target("54")
            .load()
            .migrate()
        assertEquals(
            before,
            tables.associateWith { table ->
                database()
                    .queryForList(
                        "select to_jsonb(t)::text as evidence from $table t order by to_jsonb(t)::text"
                    )
            },
        )
        val references =
            database()
                .queryForList(
                    "select company_id,transaction_reference,origin from bank_payment_references"
                )
        assertEquals(companies.toSet(), references.map { it["company_id"] }.toSet())
        assertTrue(
            references.all {
                it["origin"] == "EXPENSE" &&
                    it["transaction_reference"] == "MIGRATION-BANK-REFERENCE"
            }
        )
        assertEquals(
            0,
            database().queryForObject("select count(*) from payroll_payment_heads", Int::class.java),
        )
    }
}
