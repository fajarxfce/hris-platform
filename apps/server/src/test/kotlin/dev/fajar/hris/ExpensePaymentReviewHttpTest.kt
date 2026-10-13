package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.JsonNode

class ExpensePaymentReviewHttpTest : ExpensePaymentAssuranceApiFixture() {
    private fun body(response: HttpResponse<String>): JsonNode =
        json.readTree(response.body()).also {
            assertEquals(200, response.statusCode(), response.body())
        }

    private fun actions(review: JsonNode) =
        review["availableActions"].iterator().asSequence().map { it.asString() }.toSet()

    @Test
    fun aReviewKeepsInstructionsHistoryAndPartialReconciliationAtTheSameVersion() {
        val f = expenseFixture()
        expenseTemplate(f)
        val maker = payer(f)
        val checker = payer(f)
        val submissions =
            (0..1).map {
                approvedExpense(f.copy(claim = UUID.randomUUID(), line = UUID.randomUUID()))
            }
        val instructions = submissions.map { paymentInstruction(it) }
        val ids = instructions.map { it["id"] as UUID }
        val batch = UUID.randomUUID()
        body(preparePayment(f, maker, batch, instructions))
        val path = "/api/v1/companies/${f.company}/expenses/payments/$batch/review"
        val prepared = body(get(maker.browser, path))
        assertEquals("300000.00", prepared["batch"]["totalAmount"].asString())
        assertEquals(
            "001234567890",
            prepared["batch"]["items"][0]["destination"]["accountNumber"].asString(),
        )
        assertEquals(setOf("CANCEL"), actions(prepared))
        assertEquals(setOf("RELEASE", "CANCEL"), actions(body(get(checker.browser, path))))
        body(paymentAction(f, checker, batch, "release", 0))
        assertEquals(setOf("RECONCILE", "EXPORT"), actions(body(get(maker.browser, path))))
        body(reconcilePayment(f, maker, batch, 1, listOf(paymentResult(ids[0]))))
        val partial = body(get(checker.browser, "$path?historyLimit=2"))
        assertEquals(2, partial["batch"]["version"].asLong())
        assertEquals("RELEASED", partial["batch"]["status"].asString())
        assertEquals(setOf("RECONCILE"), actions(partial))
        assertEquals("1", partial["history"]["nextCursor"].asString())
        assertEquals(
            listOf(0L, 1L),
            partial["history"]["items"]
                .iterator()
                .asSequence()
                .map { it["version"].asLong() }
                .toList(),
        )
        assertEquals(1, partial["results"].size())
        assertEquals(2, partial["results"][0]["batchVersion"].asLong())
        assertEquals(ids[0].toString(), partial["results"][0]["results"][0]["itemId"].asString())
        body(
            reconcilePayment(
                f,
                maker,
                batch,
                2,
                listOf(paymentResult(ids[1], "FAILED", null, confirmed = true)),
            )
        )
        val closed = body(get(maker.browser, "$path?historyAfter=1&historyLimit=2"))
        assertEquals(3, closed["batch"]["version"].asLong())
        assertEquals("CLOSED", closed["batch"]["status"].asString())
        assertEquals(emptySet<String>(), actions(closed))
        assertEquals(
            listOf(2L, 3L),
            closed["history"]["items"]
                .iterator()
                .asSequence()
                .map { it["version"].asLong() }
                .toList(),
        )
        assertTrue(closed["history"]["nextCursor"].isNull)
        assertEquals(2, closed["results"].size())
        assertEquals("FAILED", closed["results"][1]["results"][0]["status"].asString())
        val tail = body(get(maker.browser, "$path?historyAfter=3"))
        assertEquals(0, tail["history"]["items"].size())
        assertEquals(closed["batch"], tail["batch"])
        assertEquals(closed["results"], tail["results"])
    }

    @Test
    fun reviewsRequireFinanceScopeAndValidateResourceCursorsAfterAuthorization() {
        val f = expenseFixture()
        expenseTemplate(f)
        val maker = payer(f)
        val batch = UUID.randomUUID()
        body(preparePayment(f, maker, batch, listOf(paymentInstruction(approvedExpense(f)))))
        val path = "/api/v1/companies/${f.company}/expenses/payments/$batch/review"
        for (reader in listOf(f.worker, f.admin, f.supervisor)) {
            val denied = get(reader, "$path?historyAfter=100")
            assertEquals(403, denied.statusCode(), denied.body())
            assertFalse(denied.body().contains("001234567890"))
            assertFalse(json.readTree(denied.body()).has("batch"))
        }
        for (query in
            listOf("historyLimit=0", "historyLimit=201", "historyAfter=-1", "historyAfter=1")) {
            val invalid = get(maker.browser, "$path?$query")
            assertEquals(422, invalid.statusCode(), invalid.body())
            assertEquals("invalid_page", json.readTree(invalid.body())["code"].asString())
        }
        val missing =
            get(
                maker.browser,
                path.replace(batch.toString(), UUID.randomUUID().toString()) + "?historyAfter=100",
            )
        assertEquals(404, missing.statusCode(), missing.body())
        val other = expenseFixture()
        val foreign =
            get(payer(other).browser, path.replace(f.company.toString(), other.company.toString()))
        assertEquals(404, foreign.statusCode(), foreign.body())
        assertFalse(foreign.body().contains("001234567890"))
    }

    @Test
    fun aBeneficiaryWithFinancePermissionCannotReleaseOrReconcileTheirOwnExpense() {
        val f = expenseFixture()
        expenseTemplate(f)
        val maker = payer(f)
        val checker = payer(f)
        val batch = UUID.randomUUID()
        body(preparePayment(f, maker, batch, listOf(paymentInstruction(approvedExpense(f)))))
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.pay')",
                f.company,
                f.account,
            )
        val beneficiary = reviewer(f, f.account)
        val path = "/api/v1/companies/${f.company}/expenses/payments/$batch/review"
        assertEquals(setOf("CANCEL"), actions(body(get(beneficiary.browser, path))))
        assertEquals(403, paymentAction(f, beneficiary, batch, "release", 0).statusCode())
        body(paymentAction(f, checker, batch, "release", 0))
        assertEquals(setOf("EXPORT"), actions(body(get(beneficiary.browser, path))))
        assertEquals(setOf("RECONCILE", "EXPORT"), actions(body(get(checker.browser, path))))
    }

    @Test
    fun independentReadersShareOneSnapshotWhileReleaseWaitsForItsCompletion() {
        val f = expenseFixture()
        expenseTemplate(f)
        val maker = payer(f)
        val checker = payer(f)
        val batch = UUID.randomUUID()
        body(preparePayment(f, maker, batch, listOf(paymentInstruction(approvedExpense(f)))))
        val path = "/api/v1/companies/${f.company}/expenses/payments/$batch/review"
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = AtomicBoolean(true)
        paymentProbe.afterRead = { company, kind ->
            if (company == f.company && kind == "batch" && first.compareAndSet(true, false)) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
        }
        Executors.newFixedThreadPool(3).use { pool ->
            val reading = pool.submit<HttpResponse<String>> { get(checker.browser, path) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val parallel = pool.submit<HttpResponse<String>> { get(maker.browser, path) }
                assertEquals(
                    0,
                    body(parallel.get(3, TimeUnit.SECONDS))["batch"]["version"].asLong(),
                )
                val writing =
                    pool.submit<HttpResponse<String>> {
                        paymentAction(f, checker, batch, "release", 0)
                    }
                assertThrows(TimeoutException::class.java) {
                    writing.get(150, TimeUnit.MILLISECONDS)
                }
                release.countDown()
                val before = body(reading.get(5, TimeUnit.SECONDS))
                assertEquals("PREPARED", before["batch"]["status"].asString())
                assertEquals(0, before["batch"]["version"].asLong())
                assertEquals(1, before["history"]["items"].size())
                assertEquals(0, before["results"].size())
                assertEquals(setOf("RELEASE", "CANCEL"), actions(before))
                body(writing.get(5, TimeUnit.SECONDS))
                val after = body(get(checker.browser, path))
                assertEquals("RELEASED", after["batch"]["status"].asString())
                assertEquals(1, after["batch"]["version"].asLong())
                assertEquals(2, after["history"]["items"].size())
                assertEquals(setOf("RECONCILE", "EXPORT"), actions(after))
            } finally {
                release.countDown()
                paymentProbe.clear()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "credential", "membership"])
    fun accessRevokedWhileWaitingCannotReleaseBankDetails(mode: String) {
        val f = expenseFixture()
        expenseTemplate(f)
        val reader = payer(f)
        val batch = UUID.randomUUID()
        body(preparePayment(f, reader, batch, listOf(paymentInstruction(approvedExpense(f)))))
        val barrier = AccountLockProbe.Barrier(reader.account)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> {
                    get(
                        reader.browser,
                        "/api/v1/companies/${f.company}/expenses/payments/$batch/review",
                    )
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                when (mode) {
                    "credential" ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                reader.account,
                            )
                    "membership" ->
                        database()
                            .update(
                                "update company_memberships set active=false where company_id=? and account_id=?",
                                f.company,
                                reader.account,
                            )
                    else ->
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.pay'",
                                f.company,
                                reader.account,
                            )
                }
                barrier.release.countDown()
                val result = pending.get(10, TimeUnit.SECONDS)
                assertEquals(
                    if (mode == "credential") 401 else 403,
                    result.statusCode(),
                    result.body(),
                )
                assertFalse(result.body().contains("001234567890"))
                assertFalse(json.readTree(result.body()).has("batch"))
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun reviewChecksRecentAssuranceAfterTheLastReadAndPreservesAcquisitionFailures(mfa: Boolean) {
        val scene = preparePaymentScenario("review", mfa)
        val actor = expiringActor(scene)
        paymentProbe.afterRead = { company, kind ->
            if (company == scene.fixture.company && kind == "candidates")
                clock.set(clock.instant().plusSeconds(2))
        }
        try {
            val result = readPayment(scene, actor)
            assertEquals("recent_authentication_required", (result as Result.Failed).failure.code)
        } finally {
            paymentProbe.clear()
        }
        val failure = Failure(FailureKind.UNAVAILABLE, "storage_unavailable")
        paymentProbe.resultsFailure = failure
        try {
            assertEquals(Result.Failed(failure), readPayment(scene, renewedActor(scene)))
        } finally {
            paymentProbe.clear()
        }
        assertTrue(readPayment(scene, renewedActor(scene)) is Result.Success)
    }

    @Test
    fun interruptedReviewReleasesSharedGuardsAndCannotPreventAFreshRead() {
        val scene = preparePaymentScenario("review", true)
        val barrier = AccountLockProbe.Barrier(scene.actor.accountId)
        accountProbe.current.set(barrier)
        val owner = AtomicReference<Thread>()
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    owner.set(Thread.currentThread())
                    readPayment(scene)
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                owner.get().interrupt()
                val failure =
                    assertThrows(ExecutionException::class.java) {
                        pending.get(10, TimeUnit.SECONDS)
                    }
                assertTrue(failure.cause is InterruptedException, failure.toString())
                accountProbe.current.set(null)
                val recovered =
                    pool.submit<Result<*>> { readPayment(scene) }.get(10, TimeUnit.SECONDS)
                assertTrue(recovered is Result.Success, recovered.toString())
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }
}
