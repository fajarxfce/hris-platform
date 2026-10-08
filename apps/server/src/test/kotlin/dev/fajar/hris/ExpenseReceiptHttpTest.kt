package dev.fajar.hris

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExpenseReceiptHttpTest : ExpenseReviewApiFixture() {
    private fun download(
        f: ExpenseFixture,
        submission: UUID,
        revision: UUID,
        browser: HttpClient = f.worker,
        headers: Map<String, String> = emptyMap(),
        method: String = "GET",
    ): HttpResponse<ByteArray> {
        val request =
            HttpRequest.newBuilder(
                    URI(
                        "http://127.0.0.1:$port/api/v1/companies/${f.company}/expenses/submissions/$submission/receipts/$revision/content"
                    )
                )
                .timeout(Duration.ofSeconds(10))
        headers.forEach { (name, value) -> request.header(name, value) }
        return browser.send(
            request.method(method, HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )
    }

    @Test
    fun reviewersAndClaimOwnersReadEvidenceWithoutBroadPersonalDocumentPermissions() {
        val f = expenseFixture()
        val reviewerSession = reviewer(f)
        expenseTemplate(f)
        val bytes = ByteArray(1048600) { (it % 127).toByte() }
        pdf.copyInto(bytes)
        val revision = readyReceipt(f, bytes)
        val submission = pendingExpense(f, readyLines(f, revision))
        storageProbe.reads.set(0)
        val generic =
            get(
                reviewerSession.browser,
                "/api/v1/companies/${f.company}/documents/revisions/$revision",
            )
        assertEquals(403, generic.statusCode(), generic.body())
        val full = download(f, submission, revision, reviewerSession.browser)
        assertEquals(200, full.statusCode())
        assertArrayEquals(bytes, full.body())
        assertEquals("private, no-store", full.headers().firstValue("Cache-Control").orElseThrow())
        assertEquals("nosniff", full.headers().firstValue("X-Content-Type-Options").orElseThrow())
        assertTrue(
            full.headers().firstValue("Content-Disposition").orElseThrow().startsWith("attachment;")
        )
        val partial =
            download(f, submission, revision, headers = mapOf("Range" to "bytes=1048570-1048585"))
        assertEquals(206, partial.statusCode())
        assertArrayEquals(bytes.copyOfRange(1048570, 1048586), partial.body())
        assertEquals(
            "bytes 1048570-1048585/${bytes.size}",
            partial.headers().firstValue("Content-Range").orElseThrow(),
        )
    }

    @Test
    fun headAndConditionalResponsesAuthorizeFirstAndDoNotReadStorage() {
        val f = expenseFixture()
        val reviewerSession = reviewer(f)
        expenseTemplate(f)
        val revision = readyReceipt(f)
        val submission = pendingExpense(f, readyLines(f, revision))
        storageProbe.reads.set(0)
        val head = download(f, submission, revision, reviewerSession.browser, method = "HEAD")
        assertEquals(200, head.statusCode())
        val etag = head.headers().firstValue("ETag").orElseThrow()
        assertEquals(
            304,
            download(
                    f,
                    submission,
                    revision,
                    reviewerSession.browser,
                    mapOf("If-None-Match" to etag),
                )
                .statusCode(),
        )
        assertEquals(0, storageProbe.reads.get())
        val other =
            reviewer(f, expenseMember(f.company, listOf("company.read", "expenses.approve")))
        val denied =
            download(f, submission, revision, other.browser, mapOf("If-None-Match" to etag), "HEAD")
        assertEquals(404, denied.statusCode())
        assertEquals(0, storageProbe.reads.get())
        val range =
            download(
                f,
                submission,
                revision,
                reviewerSession.browser,
                mapOf("Range" to "bytes=0-2", "If-Range" to etag),
            )
        assertEquals(206, range.statusCode())
        val full =
            download(
                f,
                submission,
                revision,
                reviewerSession.browser,
                mapOf("Range" to "bytes=0-2", "If-Range" to "\"different-version\""),
            )
        assertEquals(200, full.statusCode())
        assertTrue(full.body().size > 3)
    }

    @Test
    fun aSubmissionCannotAuthorizeAnUnattachedOrCrossCompanyReceipt() {
        val f = expenseFixture()
        expenseTemplate(f)
        val revision = readyReceipt(f)
        val unrelated = readyReceipt(f)
        val submission = pendingExpense(f, readyLines(f, revision))
        storageProbe.reads.set(0)
        assertEquals(404, download(f, submission, unrelated).statusCode())
        assertEquals(404, download(f, f.claim, revision).statusCode())
        val foreign = company(f.admin, f.adminCsrf)
        assertEquals(
            404,
            download(f.copy(company = foreign), submission, revision, f.admin).statusCode(),
        )
        assertEquals(0, storageProbe.reads.get())
        for (range in listOf("bytes=9999999-", "bytes=0-1,3-4", "bytes=-0", "bytes=4-2")) {
            val invalid = download(f, submission, revision, headers = mapOf("Range" to range))
            assertEquals(416, invalid.statusCode())
            assertTrue(invalid.body().isEmpty())
        }
        assertEquals(0, storageProbe.reads.get())
    }

    @Test
    fun historicalReviewersCannotUseAnOldSubmissionToReadNewDraftAttachments() {
        val f = expenseFixture()
        val account =
            expenseMember(f.company, listOf("company.read", "approvals.read", "expenses.approve"))
        val reviewerSession = reviewer(f, account)
        expenseTemplate(
            f,
            changes =
                mapOf(
                    "stages" to
                        listOf(mapOf("assignment" to "NAMED", "accountIds" to listOf(account)))
                ),
        )
        val original = readyReceipt(f)
        val submission = pendingExpense(f, readyLines(f, original))
        assertEquals(200, withdrawExpense(f, submission, 1).statusCode())
        val replacement = readyReceipt(f)
        assertEquals(200, saveExpense(f, 2, lines = readyLines(f, replacement)).statusCode())
        storageProbe.reads.set(0)
        assertEquals(
            404,
            download(f, submission, replacement, reviewerSession.browser).statusCode(),
        )
        assertEquals(0, storageProbe.reads.get())
        assertEquals(200, download(f, submission, original, reviewerSession.browser).statusCode())
    }

    @Test
    fun corruptObjectBytesAreNeverDeliveredAsTheSubmittedReceipt() {
        val f = expenseFixture()
        expenseTemplate(f)
        val revision = readyReceipt(f)
        val submission = pendingExpense(f, readyLines(f, revision))
        val key =
            database()
                .queryForObject(
                    "select object_key from document_upload_chunks where revision_id=?",
                    String::class.java,
                    revision,
                )!!
        val stored = storageProbe.objects.getValue(key)
        storageProbe.objects[key] = stored.copy(bytes = ByteArray(stored.bytes.size))
        val failed = download(f, submission, revision)
        assertEquals(409, failed.statusCode())
        assertTrue(failed.body().isEmpty())
    }

    @Test
    fun pendingReadsRejectRevokedPermissionsCredentialsAssignmentsAndExpiredDelegations() {
        for (mode in listOf("permission", "credential", "assignment", "expiry")) {
            val f = expenseFixture()
            val account =
                expenseMember(
                    f.company,
                    listOf("company.read", "approvals.read", "expenses.approve"),
                )
            val reader = reviewer(f, account)
            if (mode == "expiry") expenseTemplate(f)
            else
                expenseTemplate(
                    f,
                    changes =
                        mapOf(
                            "stages" to
                                listOf(
                                    mapOf("assignment" to "NAMED", "accountIds" to listOf(account))
                                )
                        ),
                )
            val revision = readyReceipt(f)
            val submission = pendingExpense(f, readyLines(f, revision))
            val approval =
                UUID.fromString(
                    submissionDetails(f, submission).get("approval").get("id").asString()
                )
            val until = clock.instant().plusSeconds(60)
            if (mode == "expiry") expenseDelegation(f, reviewer(f), account, until = until)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            storageProbe.beforeRead = {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
            try {
                Executors.newSingleThreadExecutor().use { pool ->
                    val pending =
                        pool.submit<HttpResponse<ByteArray>> {
                            download(f, submission, revision, reader.browser)
                        }
                    try {
                        assertTrue(entered.await(5, TimeUnit.SECONDS))
                        when (mode) {
                            "permission" ->
                                database()
                                    .update(
                                        "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.approve'",
                                        f.company,
                                        account,
                                    )
                            "credential" ->
                                database()
                                    .update(
                                        "update accounts set security_version=security_version+1 where id=?",
                                        account,
                                    )
                            "assignment" ->
                                assertEquals(
                                    200,
                                    reassignExpense(f, approval, setOf(adminAccount())).statusCode(),
                                )
                            else -> clock.set(until.plusSeconds(1))
                        }
                    } finally {
                        release.countDown()
                    }
                    val result = pending.get(15, TimeUnit.SECONDS)
                    assertEquals(if (mode == "credential") 401 else 404, result.statusCode(), mode)
                    assertTrue(result.body().isEmpty(), mode)
                }
            } finally {
                release.countDown()
                storageProbe.beforeRead = null
                clock.set(Instant.now())
            }
        }
    }
}
