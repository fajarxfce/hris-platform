package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(ExpenseReadProbeConfiguration::class, AccountLockProbeConfiguration::class)
class ExpenseClaimReviewHttpTest : ExpensePaymentApiFixture() {
    @Autowired private lateinit var readProbe: ExpenseReadProbe
    @Autowired private lateinit var accountProbe: AccountLockProbe

    private fun body(response: HttpResponse<String>) =
        json.readTree(response.body()).also {
            assertEquals(200, response.statusCode(), response.body())
        }

    @AfterEach
    fun releaseReviewProbes() {
        readProbe.current.getAndSet(null)?.release?.countDown()
        accountProbe.current.getAndSet(null)?.release?.countDown()
    }

    @Test
    fun reviewPagesHistoryAndSubmissionsWithoutReplacingTheCurrentDraft() {
        val f = expenseFixture()
        expenseTemplate(f)
        val lines = readyLines(f)
        val firstSubmission = pendingExpense(f, lines)
        body(withdrawExpense(f, firstSubmission, 1))
        body(saveExpense(f, 2, lines, mapOf("title" to "Corrected travel")))
        val secondSubmission = UUID.randomUUID()
        body(submitExpense(f, secondSubmission, 3))
        val path = "${f.path}/${f.claim}/review"
        val first = body(get(f.worker, "$path?historyLimit=2&submissionsLimit=1"))
        assertEquals(4, first["claim"]["version"].asLong())
        assertEquals("PENDING", first["claim"]["status"].asString())
        assertEquals("Corrected travel", first["claim"]["draft"]["title"].asString())
        assertEquals(secondSubmission.toString(), first["claim"]["latestSubmissionId"].asString())
        assertEquals(
            listOf(4L, 3L),
            first["history"]["items"]
                .iterator()
                .asSequence()
                .map { it["version"].asLong() }
                .toList(),
        )
        assertEquals("3", first["history"]["nextCursor"].asString())
        assertEquals(secondSubmission.toString(), first["submissions"]["items"][0]["id"].asString())
        assertEquals("2", first["submissions"]["nextCursor"].asString())
        assertEquals(0, first["payments"].size())
        val last = body(get(f.worker, "$path?historyAfter=3&submissionsAfter=2"))
        assertEquals(first["claim"], last["claim"])
        assertEquals(
            listOf(2L, 1L, 0L),
            last["history"]["items"].iterator().asSequence().map { it["version"].asLong() }.toList(),
        )
        assertTrue(last["history"]["nextCursor"].isNull)
        assertEquals(firstSubmission.toString(), last["submissions"]["items"][0]["id"].asString())
        assertEquals("Travel reimbursement", last["submissions"]["items"][0]["title"].asString())
        assertTrue(last["submissions"]["nextCursor"].isNull)
        val empty = body(get(f.worker, "$path?historyAfter=0&submissionsAfter=1"))
        assertEquals(0, empty["history"]["items"].size())
        assertEquals(0, empty["submissions"]["items"].size())
        assertEquals(first["claim"], empty["claim"])
        for (query in
            listOf(
                "historyLimit=0",
                "historyLimit=201",
                "historyAfter=-1",
                "historyAfter=5",
                "submissionsLimit=0",
                "submissionsLimit=21",
                "submissionsAfter=0",
                "submissionsAfter=3",
                "submissionsAfter=21",
            )) {
            val invalid = get(f.worker, "$path?$query")
            assertEquals(422, invalid.statusCode(), invalid.body())
            assertEquals("invalid_page", json.readTree(invalid.body())["code"].asString())
        }
    }

    @Test
    fun assignedSubmissionReviewersCannotDiscoverUnsubmittedDraftsOrTheirVersions() {
        val f = expenseFixture()
        val assigned =
            reviewer(
                f,
                expenseMember(
                    f.company,
                    listOf("company.read", "approvals.read", "expenses.approve"),
                ),
            )
        expenseTemplate(
            f,
            changes =
                mapOf(
                    "stages" to
                        listOf(
                            mapOf("assignment" to "NAMED", "accountIds" to listOf(assigned.account))
                        )
                ),
        )
        val submission = pendingExpense(f)
        val submittedPath = "/api/v1/companies/${f.company}/expenses/submissions/$submission"
        body(get(assigned.browser, submittedPath))
        val path = "${f.path}/${f.claim}/review"
        for (query in listOf("", "?historyAfter=2", "?submissionsAfter=2")) {
            val denied = get(assigned.browser, "$path$query")
            assertEquals(404, denied.statusCode(), denied.body())
            assertEquals("expense_claim_not_found", json.readTree(denied.body())["code"].asString())
            assertFalse(json.readTree(denied.body()).has("claim"))
            assertFalse(denied.body().contains("Travel reimbursement"))
        }
        body(get(f.supervisor, path))
        body(get(f.admin, path))
        val foreign = company(f.admin, f.adminCsrf)
        val missing = get(f.admin, path.replace(f.company.toString(), foreign.toString()))
        assertEquals(404, missing.statusCode(), missing.body())
        assertFalse(missing.body().contains("Travel reimbursement"))
    }

    @Test
    fun sharedReadersCannotMixDraftAndHistoryAcrossAConcurrentSave() {
        val f = expenseFixture()
        body(saveExpense(f))
        val path = "${f.path}/${f.claim}/review"
        val barrier = ExpenseReadProbe.Barrier(f.company, "claim-head")
        readProbe.current.set(barrier)
        Executors.newFixedThreadPool(3).use { pool ->
            val pending = pool.submit<HttpResponse<String>> { get(f.worker, path) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val parallel = pool.submit<HttpResponse<String>> { get(f.worker, path) }
                assertEquals(
                    0,
                    body(parallel.get(3, TimeUnit.SECONDS))["claim"]["version"].asLong(),
                )
                val saving =
                    pool.submit<HttpResponse<String>> {
                        saveExpense(f, 0, changes = mapOf("title" to "Revised travel"))
                    }
                assertThrows(TimeoutException::class.java) {
                    saving.get(150, TimeUnit.MILLISECONDS)
                }
                barrier.release.countDown()
                val previous = body(pending.get(5, TimeUnit.SECONDS))
                assertEquals(0, previous["claim"]["version"].asLong())
                assertEquals("Travel reimbursement", previous["claim"]["draft"]["title"].asString())
                assertEquals(
                    listOf(0L),
                    previous["history"]["items"]
                        .iterator()
                        .asSequence()
                        .map { it["version"].asLong() }
                        .toList(),
                )
                body(saving.get(5, TimeUnit.SECONDS))
                val next = body(get(f.worker, path))
                assertEquals(1, next["claim"]["version"].asLong())
                assertEquals("Revised travel", next["claim"]["draft"]["title"].asString())
                assertEquals(
                    listOf(1L, 0L),
                    next["history"]["items"]
                        .iterator()
                        .asSequence()
                        .map { it["version"].asLong() }
                        .toList(),
                )
            } finally {
                barrier.release.countDown()
                readProbe.current.set(null)
            }
        }
    }

    @Test
    fun reviewIncludesPaymentProgressWithoutBankDestinationsOrReferences() {
        val f = expenseFixture()
        expenseTemplate(f)
        val submission = approvedExpense(f)
        val maker = payer(f)
        val checker = payer(f)
        val batch = UUID.randomUUID()
        val item = UUID.randomUUID()
        body(preparePayment(f, maker, batch, listOf(paymentInstruction(submission, item))))
        body(paymentAction(f, checker, batch, "release", 0))
        body(
            reconcilePayment(
                f,
                checker,
                batch,
                1,
                listOf(paymentResult(item, reference = "private-bank-confirmation")),
            )
        )
        val review = body(get(f.worker, "${f.path}/${f.claim}/review"))
        assertEquals("APPROVED", review["claim"]["status"].asString())
        assertEquals(1, review["payments"].size())
        val progress = review["payments"][0]
        assertEquals(batch.toString(), progress["batchId"].asString())
        assertEquals(item.toString(), progress["itemId"].asString())
        assertEquals("SUCCEEDED", progress["status"].asString())
        assertFalse(progress["releasedAt"].isNull)
        assertFalse(progress["settledAt"].isNull)
        for (privateValue in
            listOf(
                "accountNumber",
                "accountName",
                "bankCode",
                "transactionReference",
                "private-bank-confirmation",
                "001234567890",
            )) assertFalse(review.toString().contains(privateValue))
    }

    @Test
    fun permissionRemovedDuringAccountAcquisitionCannotReleaseTheAggregate() {
        val f = expenseFixture()
        body(saveExpense(f))
        val barrier = AccountLockProbe.Barrier(f.account)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> { get(f.worker, "${f.path}/${f.claim}/review") }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.self.manage'",
                        f.company,
                        f.account,
                    )
                barrier.release.countDown()
                val denied = pending.get(10, TimeUnit.SECONDS)
                assertEquals(404, denied.statusCode(), denied.body())
                assertFalse(json.readTree(denied.body()).has("claim"))
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }
}
