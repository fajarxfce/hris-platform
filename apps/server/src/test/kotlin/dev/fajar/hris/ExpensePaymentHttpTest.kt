package dev.fajar.hris

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExpensePaymentHttpTest : ExpensePaymentApiFixture() {
    @Test
    fun paymentPreparationReleaseAndSettlementKeepOriginalEvidenceAndReceipts() {
        val f = expenseFixture()
        expenseTemplate(f)
        val maker = payer(f)
        val checker = payer(f)
        val submission = approvedExpense(f)
        val batch = UUID.randomUUID()
        val item = UUID.randomUUID()
        val operation = UUID.randomUUID()
        val instructions =
            listOf(paymentInstruction(submission, item, "=Sensitive formula, \"quoted\""))
        val prepared = preparePayment(f, maker, batch, instructions, operation)
        assertEquals(200, prepared.statusCode(), prepared.body())
        val detail = paymentDetails(f, maker, batch)
        assertEquals("PREPARED", detail.get("status").asString())
        assertEquals("150000.00", detail.get("totalAmount").asString())
        assertEquals(
            "001234567890",
            detail.get("items").get(0).get("destination").get("accountNumber").asString(),
        )
        assertEquals(
            409,
            get(maker.browser, "/api/v1/companies/${f.company}/expenses/payments/$batch/export")
                .statusCode(),
        )
        assertEquals(403, paymentAction(f, maker, batch, "release", 0).statusCode())
        val releaseKey = UUID.randomUUID()
        val release = paymentAction(f, checker, batch, "release", 0, releaseKey)
        assertEquals(200, release.statusCode(), release.body())
        val exported =
            get(checker.browser, "/api/v1/companies/${f.company}/expenses/payments/$batch/export")
        assertEquals(200, exported.statusCode(), exported.body())
        assertTrue(exported.body().contains(item.toString()))
        assertTrue(exported.body().contains("'001234567890"))
        assertTrue(exported.body().contains("'=Sensitive formula"))
        assertTrue(exported.body().contains("150000.00"))
        assertEquals(
            "private, no-store",
            exported.headers().firstValue("Cache-Control").orElseThrow(),
        )
        assertEquals(
            exported.body(),
            get(maker.browser, "/api/v1/companies/${f.company}/expenses/payments/$batch/export")
                .body(),
        )
        val resultKey = UUID.randomUUID()
        val results = listOf(paymentResult(item))
        val paid = reconcilePayment(f, maker, batch, 1, results, resultKey)
        assertEquals(200, paid.statusCode(), paid.body())
        assertEquals("CLOSED", paymentDetails(f, maker, batch).get("status").asString())
        assertEquals(
            409,
            get(maker.browser, "/api/v1/companies/${f.company}/expenses/payments/$batch/export")
                .statusCode(),
        )
        assertEquals(
            prepared.body(),
            preparePayment(f, maker, batch, instructions, operation).body(),
        )
        assertEquals(
            release.body(),
            paymentAction(f, checker, batch, "release", 0, releaseKey).body(),
        )
        assertEquals(paid.body(), reconcilePayment(f, maker, batch, 1, results, resultKey).body())
        assertEquals(
            409,
            preparePayment(f, maker, UUID.randomUUID(), listOf(paymentInstruction(submission)))
                .statusCode(),
        )
        val evidence =
            get(maker.browser, "/api/v1/companies/${f.company}/expenses/payments/$batch/results")
        assertEquals(200, evidence.statusCode(), evidence.body())
        assertEquals(1, json.readTree(evidence.body()).size())
        assertEquals("APPROVED", submissionDetails(f, submission).get("claimStatus").asString())
        val progress = get(f.worker, "${f.path}/${f.claim}/payments")
        assertEquals(200, progress.statusCode(), progress.body())
        val paymentProgress = json.readTree(progress.body())[0]
        assertEquals("SUCCEEDED", paymentProgress.get("status").asString())
        assertFalse(paymentProgress.has("destination"))
        assertFalse(paymentProgress.has("transactionReference"))
        val unrelated =
            reviewer(f, expenseMember(f.company, listOf("company.read", "expenses.self.manage")))
        assertEquals(404, get(unrelated.browser, "${f.path}/${f.claim}/payments").statusCode())
        val history =
            get(
                maker.browser,
                "/api/v1/companies/${f.company}/expenses/payments/$batch/history?limit=2",
            )
        assertEquals(200, history.statusCode(), history.body())
        assertEquals(2, json.readTree(history.body()).get("items").size())
        assertEquals("1", json.readTree(history.body()).get("nextCursor").asString())
        val actions =
            get(
                maker.browser,
                "/api/v1/companies/${f.company}/expenses/payments/$batch/history?after=1",
            )
        assertEquals(1, json.readTree(actions.body()).get("items").size())
    }

    @Test
    fun partialBankOutcomesReserveUnknownItemsAndAllowOnlyConfirmedFailuresToRetry() {
        val f = expenseFixture()
        expenseTemplate(f)
        val maker = payer(f)
        val checker = payer(f)
        val submissions =
            (0..2).map {
                approvedExpense(f.copy(claim = UUID.randomUUID(), line = UUID.randomUUID()))
            }
        val items = submissions.map { paymentInstruction(it) }
        val ids = items.map { it["id"] as UUID }
        val batch = UUID.randomUUID()
        assertEquals(200, preparePayment(f, maker, batch, items).statusCode())
        assertEquals(200, paymentAction(f, checker, batch, "release", 0).statusCode())
        val first = reconcilePayment(f, maker, batch, 1, listOf(paymentResult(ids[0])))
        assertEquals(200, first.statusCode(), first.body())
        assertEquals("RELEASED", paymentDetails(f, maker, batch).get("status").asString())
        assertEquals(
            409,
            get(maker.browser, "/api/v1/companies/${f.company}/expenses/payments/$batch/export")
                .statusCode(),
        )
        assertEquals(
            409,
            preparePayment(f, maker, UUID.randomUUID(), listOf(paymentInstruction(submissions[2])))
                .statusCode(),
        )
        assertEquals(
            422,
            reconcilePayment(f, maker, batch, 2, listOf(paymentResult(ids[1], "FAILED", null)))
                .statusCode(),
        )
        val failed =
            reconcilePayment(
                f,
                maker,
                batch,
                2,
                listOf(paymentResult(ids[1], "FAILED", null, confirmed = true)),
            )
        assertEquals(200, failed.statusCode(), failed.body())
        val retry =
            preparePayment(f, maker, UUID.randomUUID(), listOf(paymentInstruction(submissions[1])))
        assertEquals(200, retry.statusCode(), retry.body())
        assertEquals(
            409,
            reconcilePayment(f, maker, batch, 3, listOf(paymentResult(ids[1]))).statusCode(),
        )
        val last = reconcilePayment(f, maker, batch, 3, listOf(paymentResult(ids[2])))
        assertEquals(200, last.statusCode(), last.body())
        assertEquals("CLOSED", paymentDetails(f, maker, batch).get("status").asString())
        assertEquals(
            409,
            get(maker.browser, "/api/v1/companies/${f.company}/expenses/payments/$batch/export")
                .statusCode(),
        )
    }

    @Test
    fun preparedCancellationReleasesReservationsButReleasedPaymentsCannotBeCancelled() {
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
        val key = UUID.randomUUID()
        val cancelled = paymentAction(f, maker, batch, "cancel", 0, key)
        assertEquals(200, cancelled.statusCode(), cancelled.body())
        assertEquals(cancelled.body(), paymentAction(f, maker, batch, "cancel", 0, key).body())
        assertEquals(
            "CANCELLED",
            paymentDetails(f, maker, batch).get("items").get(0).get("status").asString(),
        )
        val replacement = UUID.randomUUID()
        assertEquals(
            200,
            preparePayment(f, maker, replacement, listOf(paymentInstruction(submission)))
                .statusCode(),
        )
        assertEquals(200, paymentAction(f, checker, replacement, "release", 0).statusCode())
        assertEquals(409, paymentAction(f, maker, replacement, "cancel", 1).statusCode())
    }

    @Test
    fun reconciliationRejectsDuplicateBankReferencesAndRollsBackEveryItem() {
        val f = expenseFixture()
        expenseTemplate(f)
        val maker = payer(f)
        val checker = payer(f)
        val submissions =
            (0..2).map {
                approvedExpense(f.copy(claim = UUID.randomUUID(), line = UUID.randomUUID()))
            }
        val instructions = submissions.map { paymentInstruction(it) }
        val ids = instructions.map { it["id"] as UUID }
        val batch = UUID.randomUUID()
        assertEquals(200, preparePayment(f, maker, batch, instructions).statusCode())
        assertEquals(200, paymentAction(f, checker, batch, "release", 0).statusCode())
        val reference = UUID.randomUUID().toString()
        assertEquals(
            422,
            reconcilePayment(
                    f,
                    maker,
                    batch,
                    1,
                    listOf(
                        paymentResult(ids[0], reference = reference),
                        paymentResult(ids[1], reference = reference),
                    ),
                )
                .statusCode(),
        )
        assertEquals(
            200,
            reconcilePayment(
                    f,
                    maker,
                    batch,
                    1,
                    listOf(paymentResult(ids[0], reference = reference)),
                )
                .statusCode(),
        )
        val duplicate =
            reconcilePayment(
                f,
                maker,
                batch,
                2,
                listOf(paymentResult(ids[1]), paymentResult(ids[2], reference = reference)),
            )
        assertEquals(409, duplicate.statusCode(), duplicate.body())
        val current = paymentDetails(f, maker, batch)
        assertEquals(2, current.get("version").asLong())
        assertEquals(
            2,
            current.get("items").iterator().asSequence().count {
                it.get("status").asString() == "PENDING"
            },
        )
    }

    @Test
    fun paymentInputAndPagesAreBoundedAndAvailableClaimsFollowReservations() {
        val f = expenseFixture()
        expenseTemplate(f)
        val maker = payer(f)
        val pending = pendingExpense(f)
        val batch = UUID.randomUUID()
        assertEquals(
            409,
            preparePayment(f, maker, batch, listOf(paymentInstruction(pending))).statusCode(),
        )
        assertEquals(200, reviewExpense(f, pending, reviewer(f)).statusCode())
        val item = paymentInstruction(pending)
        assertEquals(422, preparePayment(f, maker, batch, emptyList()).statusCode())
        assertEquals(
            422,
            preparePayment(f, maker, batch, List(101) { paymentInstruction(pending) }).statusCode(),
        )
        assertEquals(422, preparePayment(f, maker, batch, listOf(item, item)).statusCode())
        assertEquals(
            422,
            preparePayment(
                    f,
                    maker,
                    batch,
                    listOf(
                        item +
                            ("destination" to
                                mapOf(
                                    "bankCode" to "014",
                                    "accountNumber" to "12345",
                                    "accountName" to "Test",
                                ))
                    ),
                )
                .statusCode(),
        )
        assertEquals(
            422,
            preparePayment(
                    f,
                    maker,
                    batch,
                    listOf(paymentInstruction(pending, accountName = "invalid\nname")),
                )
                .statusCode(),
        )
        val today = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Jakarta"))
        val path = "/api/v1/companies/${f.company}/expenses/payments"
        val query = "from=$today&until=$today"
        val available = get(maker.browser, "$path/payables?$query")
        assertEquals(200, available.statusCode(), available.body())
        assertEquals(1, json.readTree(available.body()).get("items").size())
        assertEquals(200, preparePayment(f, maker, batch, listOf(item)).statusCode())
        assertEquals(
            0,
            json.readTree(get(maker.browser, "$path/payables?$query").body()).get("items").size(),
        )
        val listed = get(maker.browser, "$path?$query&status=PREPARED&limit=1")
        assertEquals(200, listed.statusCode(), listed.body())
        assertEquals(
            batch.toString(),
            json.readTree(listed.body()).get("items").get(0).get("id").asString(),
        )
        assertEquals(422, get(maker.browser, "$path?$query&limit=201").statusCode())
        assertEquals(422, get(maker.browser, "$path?from=2025-01-01&until=2026-12-31").statusCode())
    }
}
