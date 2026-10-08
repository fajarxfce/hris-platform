package dev.fajar.hris

import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*

abstract class ExpensePaymentApiFixture : ExpenseReviewApiFixture() {
    protected fun payer(f: ExpenseFixture): Reviewer =
        reviewer(f, expenseMember(f.company, listOf("company.read", "expenses.pay")))

    protected fun approvedExpense(f: ExpenseFixture): UUID {
        val submission = pendingExpense(f)
        val approved = reviewExpense(f, submission, reviewer(f))
        assertEquals(200, approved.statusCode(), approved.body())
        return submission
    }

    protected fun paymentInstruction(
        submission: UUID,
        id: UUID = UUID.randomUUID(),
        accountName: String = "Test Employee",
    ): Map<String, Any?> =
        mapOf(
            "id" to id,
            "submissionId" to submission,
            "destination" to
                mapOf(
                    "bankCode" to "014",
                    "accountNumber" to "001234567890",
                    "accountName" to accountName,
                ),
        )

    protected fun preparePayment(
        f: ExpenseFixture,
        operator: Reviewer,
        id: UUID,
        items: List<Map<String, Any?>>,
        key: UUID = UUID.randomUUID(),
        title: String = "Employee reimbursements",
    ) =
        command(
            operator.browser,
            "/api/v1/companies/${f.company}/expenses/payments/$id",
            json.writeValueAsString(
                mapOf(
                    "title" to title,
                    "items" to items,
                    "reason" to "Verified payment destinations",
                )
            ),
            operator.csrf,
            key,
            "PUT",
        )

    protected fun paymentAction(
        f: ExpenseFixture,
        operator: Reviewer,
        id: UUID,
        action: String,
        version: Long,
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            operator.browser,
            "/api/v1/companies/${f.company}/expenses/payments/$id/$action",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Finance $action confirmed")
            ),
            operator.csrf,
            key,
        )

    protected fun paymentResult(
        id: UUID,
        status: String = "SUCCEEDED",
        reference: String? = UUID.randomUUID().toString(),
        // Bank confirmation follows release, including PostgreSQL timestamp precision.
        at: Instant = clock.instant().plusMillis(1).also { clock.set(it) },
        confirmed: Boolean = false,
    ): Map<String, Any?> =
        mapOf(
            "itemId" to id,
            "status" to status,
            "transactionReference" to reference,
            "occurredAt" to at.toString(),
            "reason" to "Bank settlement confirmation",
            "confirmedNoTransfer" to confirmed,
        )

    protected fun reconcilePayment(
        f: ExpenseFixture,
        operator: Reviewer,
        id: UUID,
        version: Long,
        results: List<Map<String, Any?>>,
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            operator.browser,
            "/api/v1/companies/${f.company}/expenses/payments/$id/reconcile",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to version,
                    "results" to results,
                    "reason" to "Bank statement reconciled",
                )
            ),
            operator.csrf,
            key,
        )

    protected fun paymentDetails(f: ExpenseFixture, operator: Reviewer, id: UUID) =
        get(operator.browser, "/api/v1/companies/${f.company}/expenses/payments/$id").let {
            assertEquals(200, it.statusCode(), it.body())
            json.readTree(it.body())
        }
}
