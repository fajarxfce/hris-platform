package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import java.net.URLEncoder
import java.net.http.HttpResponse
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.JsonNode

@TestPropertySource(properties = [MOBILE_SYNC_TEST_KEYS])
class ExpensePaymentSyncHttpTest : ExpensePaymentApiFixture() {
    @Test
    fun paymentChangesRetainEveryAttemptWithoutRevisingTheApprovedClaim() {
        val f = expenseFixture()
        expenseTemplate(f)
        val submission = approvedExpense(f)
        val maker = payer(f)
        val checker = payer(f)
        val claimPath = "${f.path}/${f.claim}"
        val original = body(get(f.worker, "$claimPath?scope=SELF"))
        val empty = body(get(f.worker, "$claimPath/payment-progress"))
        assertEquals(f.claim.toString(), empty["claimId"].asString())
        assertEquals(0L, empty["version"].asLong())
        assertEquals(0, empty["attempts"].size())
        val worker = mobileSyncTestWorker(postgres.jdbcUrl, database())
        assertTrue(worker.maintain.execute() is Result.Success)
        val path = "/api/v1/companies/${f.company}/sync"
        val selected = "collections=EXPENSE_CLAIMS,EXPENSE_PAYMENTS"
        val initial = body(get(f.worker, "$path/bootstrap?$selected"))
        assertEquals(1, initial["items"].size())
        assertEquals("EXPENSE_CLAIMS", initial["items"][0]["collection"].asString())

        for (outcome in listOf("CANCELLED", "FAILED", "SUCCEEDED")) {
            val batch = UUID.randomUUID()
            val item = UUID.randomUUID()
            val operation = UUID.randomUUID()
            val instructions = listOf(paymentInstruction(submission, item, "Private destination"))
            val prepared = body(preparePayment(f, maker, batch, instructions, operation))
            if (outcome == "CANCELLED") {
                body(paymentAction(f, maker, batch, "cancel", 0))
            } else {
                body(paymentAction(f, checker, batch, "release", 0))
                body(
                    reconcilePayment(
                        f,
                        maker,
                        batch,
                        1,
                        listOf(
                            paymentResult(
                                item,
                                outcome,
                                reference = if (outcome == "SUCCEEDED") "SYNC-SETTLEMENT" else null,
                                confirmed = outcome == "FAILED",
                            )
                        ),
                    )
                )
            }
            val progress = body(get(f.worker, "$claimPath/payment-progress"))
            assertEquals(prepared, body(preparePayment(f, maker, batch, instructions, operation)))
            assertEquals(progress, body(get(f.worker, "$claimPath/payment-progress")))
        }

        assertTrue(worker.maintain.execute() is Result.Success)
        val cursor = URLEncoder.encode(initial["changesCursor"].asString(), Charsets.UTF_8)
        val changes = body(get(f.worker, "$path/changes?cursor=$cursor&$selected"))["items"]
        assertEquals(
            (1L..8L).toList(),
            changes.iterator().asSequence().map { it["version"].asLong() }.toList(),
        )
        assertTrue(changes.all { it["collection"].asString() == "EXPENSE_PAYMENTS" })
        assertTrue(changes.all { it["id"].asString() == f.claim.toString() })
        val progress = body(get(f.worker, "$claimPath/payment-progress"))
        assertEquals(8L, progress["version"].asLong())
        assertEquals(
            setOf("CANCELLED", "FAILED", "SUCCEEDED"),
            progress["attempts"].iterator().asSequence().map { it["status"].asString() }.toSet(),
        )
        assertEquals(progress["attempts"], body(get(f.worker, "$claimPath/payments")))
        assertTrue(
            progress["attempts"].all {
                it.properties().map { property -> property.key }.toSet() ==
                    setOf("batchId", "itemId", "status", "createdAt", "releasedAt", "settledAt")
            }
        )
        assertFalse(progress.toString().contains("Private destination"))
        assertFalse(progress.toString().contains("SYNC-SETTLEMENT"))
        assertEquals(original, body(get(f.worker, "$claimPath?scope=SELF")))
        val snapshot = body(get(f.worker, "$path/bootstrap?$selected"))
        assertEquals(
            setOf("EXPENSE_CLAIMS", "EXPENSE_PAYMENTS"),
            snapshot["items"].iterator().asSequence().map { it["collection"].asString() }.toSet(),
        )
        val defaults = body(get(f.worker, "$path/bootstrap"))
        assertEquals(
            listOf("EXPENSE_CLAIMS"),
            defaults["collections"].iterator().asSequence().map { it.asString() }.toList(),
        )
        assertEquals(1, defaults["items"].size())
    }

    @Test
    fun explicitFinanceReadStillSynchronizesOnlyOwnedClaimsAndRechecksRevocation() {
        val f = expenseFixture()
        expenseTemplate(f)
        val submission = approvedExpense(f)
        val other =
            f.copy(
                employee = employee(f.admin, f.adminCsrf, f.company, manager = f.manager),
                claim = UUID.randomUUID(),
                line = UUID.randomUUID(),
            )
        val otherSubmission = pendingExpense(other, asAdmin = true)
        body(reviewExpense(other, otherSubmission, reviewer(other)))
        val maker = payer(f)
        body(
            preparePayment(
                f,
                maker,
                UUID.randomUUID(),
                listOf(paymentInstruction(submission), paymentInstruction(otherSubmission)),
            )
        )
        database()
            .update(
                "update membership_permissions set permission='expenses.read' where company_id=? and account_id=? and permission='expenses.self.manage'",
                f.company,
                f.account,
            )
        val worker = mobileSyncTestWorker(postgres.jdbcUrl, database())
        assertTrue(worker.maintain.execute() is Result.Success)
        val path = "/api/v1/companies/${f.company}/sync"
        val selected = "collections=EXPENSE_CLAIMS,EXPENSE_PAYMENTS"
        assertEquals(403, get(f.worker, "$path/bootstrap").statusCode())
        val snapshot = body(get(f.worker, "$path/bootstrap?$selected"))
        assertEquals(2, snapshot["items"].size())
        assertTrue(snapshot["items"].all { it["id"].asString() == f.claim.toString() })
        assertEquals(
            1L,
            body(get(f.worker, "${f.path}/${f.claim}/payment-progress"))["version"].asLong(),
        )
        for (suffix in listOf("?scope=SELF", "/payments?scope=SELF", "/payment-progress")) {
            assertEquals(404, get(f.worker, "${f.path}/${other.claim}$suffix").statusCode())
        }
        assertEquals(200, get(f.worker, "${f.path}/${other.claim}/payments").statusCode())
        val unbound = reviewer(f, expenseMember(f.company, listOf("company.read", "expenses.read")))
        assertEquals(0, body(get(unbound.browser, "$path/bootstrap?$selected"))["items"].size())
        assertEquals(
            404,
            get(unbound.browser, "${f.path}/${f.claim}/payment-progress").statusCode(),
        )
        val otherCompany = company(f.admin, f.adminCsrf)
        assertEquals(
            403,
            get(f.worker, "/api/v1/companies/$otherCompany/sync/bootstrap?$selected").statusCode(),
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.read'",
                f.company,
                f.account,
            )
        val cursor = URLEncoder.encode(snapshot["changesCursor"].asString(), Charsets.UTF_8)
        assertEquals(403, get(f.worker, "$path/changes?cursor=$cursor&$selected").statusCode())
        assertEquals(404, get(f.worker, "${f.path}/${f.claim}/payment-progress").statusCode())
    }

    private fun body(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }
}
