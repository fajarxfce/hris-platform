package dev.fajar.hris

import java.net.http.HttpResponse
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(ExpenseReadProbeConfiguration::class, AccountLockProbeConfiguration::class)
class ExpenseSubmissionActionsHttpTest : ExpenseReviewApiFixture() {
    @Autowired private lateinit var readProbe: ExpenseReadProbe
    @Autowired private lateinit var accountProbe: AccountLockProbe

    private fun body(response: HttpResponse<String>) =
        json.readTree(response.body()).also {
            assertEquals(200, response.statusCode(), response.body())
        }

    @AfterEach
    fun releaseSubmissionProbes() {
        readProbe.current.getAndSet(null)?.release?.countDown()
        accountProbe.current.getAndSet(null)?.release?.countDown()
    }

    @Test
    fun onlyTheCurrentIndependentStageCanDecideWhileOtherReadersRetainTheirOwnScope() {
        val f = expenseFixture()
        val first = reviewer(f)
        val second =
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
                            mapOf("assignment" to "NAMED", "accountIds" to listOf(first.account)),
                            mapOf("assignment" to "NAMED", "accountIds" to listOf(second.account)),
                        )
                ),
        )
        val id = pendingExpense(f)
        val path = "/api/v1/companies/${f.company}/expenses/submissions/$id"
        val active = body(get(first.browser, path))
        assertTrue(active["canReadClaim"].asBoolean())
        assertEquals("DECIDE", active["availableActions"][0].asString())
        val future = body(get(second.browser, path))
        assertFalse(future["canReadClaim"].asBoolean())
        assertEquals(0, future["availableActions"].size())
        assertEquals(404, get(second.browser, "$path?scope=SELF").statusCode())
        assertEquals(404, get(second.browser, "${f.path}/${f.claim}/review").statusCode())
        for (reader in listOf(f.worker, f.admin)) {
            val ordinary = body(get(reader, path))
            assertTrue(ordinary["canReadClaim"].asBoolean())
            assertEquals(0, ordinary["availableActions"].size())
        }
        val owned = body(get(f.worker, "$path?scope=SELF"))
        assertTrue(owned["canReadClaim"].asBoolean())
        assertEquals(0, owned["availableActions"].size())

        body(reviewExpense(f, id, first))
        val historical = body(get(first.browser, path))
        assertEquals(1, historical["approval"]["currentStep"].asInt())
        assertEquals(0, historical["availableActions"].size())
        val next = body(get(second.browser, path))
        assertEquals("DECIDE", next["availableActions"][0].asString())
        assertEquals(2, next["claimVersion"].asLong())
        body(reviewExpense(f, id, second, 2, 1, "RETURN", "Clarify the submitted amount"))
        val returned = body(get(second.browser, path))
        assertTrue(returned["current"].asBoolean())
        assertEquals("RETURNED", returned["claimStatus"].asString())
        assertEquals(0, returned["availableActions"].size())
        assertEquals(2, returned["reviews"].size())
    }

    @Test
    fun reassignmentChangesAvailableActionsAndTheCommandStillRequiresBothObservedVersions() {
        val f = expenseFixture()
        expenseTemplate(f)
        val id = pendingExpense(f)
        val first = reviewer(f)
        val second =
            reviewer(
                f,
                expenseMember(
                    f.company,
                    listOf("company.read", "approvals.read", "expenses.approve"),
                ),
            )
        val path = "/api/v1/companies/${f.company}/expenses/submissions/$id"
        val original = body(get(first.browser, path))
        body(
            reassignExpense(
                f,
                java.util.UUID.fromString(original["approval"]["id"].asString()),
                setOf(second.account),
            )
        )
        assertEquals(0, body(get(first.browser, path))["availableActions"].size())
        val replacement = body(get(second.browser, path))
        assertEquals("DECIDE", replacement["availableActions"][0].asString())
        assertEquals(1, replacement["approval"]["version"].asLong())
        assertEquals(1, replacement["claimVersion"].asLong())
        val stale = reviewExpense(f, id, second)
        assertEquals(409, stale.statusCode(), stale.body())
        assertEquals("approval_changed", json.readTree(stale.body())["code"].asString())
        body(reviewExpense(f, id, second, approvalVersion = 1))
        assertEquals(0, body(get(second.browser, path))["availableActions"].size())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun delegationExpiryBeforeDeliveryRemovesOnlyTheAuthorityThatDependedOnIt(
        companyRead: Boolean
    ) {
        val scene = delegatedReadFixture(companyRead)
        val initial = body(get(scene.delegate.browser, scene.path))
        assertEquals("DECIDE", initial["availableActions"][0].asString())
        assertEquals(companyRead, initial["canReadClaim"].asBoolean())
        val barrier = ExpenseReadProbe.Barrier(scene.fixture.company, "submission")
        readProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> { get(scene.delegate.browser, scene.path) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(scene.until)
                barrier.release.countDown()
                val response = pending.get(10, TimeUnit.SECONDS)
                if (companyRead) {
                    val current = body(response)
                    assertTrue(current["canReadClaim"].asBoolean())
                    assertEquals(0, current["availableActions"].size())
                } else {
                    assertEquals(404, response.statusCode(), response.body())
                    assertEquals(
                        "expense_submission_not_found",
                        json.readTree(response.body())["code"].asString(),
                    )
                    assertFalse(response.body().contains("Travel reimbursement"))
                }
            } finally {
                barrier.release.countDown()
                readProbe.current.set(null)
            }
        }
    }

    @Test
    fun aDelegatorRevokedBeforeItsAccountGuardCannotAuthorizeSubmittedEvidence() {
        val scene = delegatedReadFixture()
        val barrier = AccountLockProbe.Barrier(scene.from.account)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> { get(scene.delegate.browser, scene.path) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertEquals(
                    1,
                    database()
                        .update("update accounts set active=false where id=?", scene.from.account),
                )
                barrier.release.countDown()
                val response = pending.get(10, TimeUnit.SECONDS)
                assertEquals(404, response.statusCode(), response.body())
                assertFalse(response.body().contains("Travel reimbursement"))
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @Test
    fun submissionAcquisitionHoldsDelegatedAccountAuthorityUntilItsTransactionCompletes() {
        val scene = delegatedReadFixture()
        val barrier = ExpenseReadProbe.Barrier(scene.fixture.company, "submission")
        readProbe.current.set(barrier)
        Executors.newFixedThreadPool(2).use { pool ->
            val reading =
                pool.submit<HttpResponse<String>> { get(scene.delegate.browser, scene.path) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val entered = CountDownLatch(1)
                val revoking =
                    pool.submit<Int> {
                        entered.countDown()
                        database()
                            .update(
                                "update accounts set active=false where id=?",
                                scene.from.account,
                            )
                    }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertThrows(TimeoutException::class.java) {
                    revoking.get(150, TimeUnit.MILLISECONDS)
                }
                barrier.release.countDown()
                assertEquals(
                    "DECIDE",
                    body(reading.get(10, TimeUnit.SECONDS))["availableActions"][0].asString(),
                )
                assertEquals(1, revoking.get(10, TimeUnit.SECONDS))
                val denied = get(scene.delegate.browser, scene.path)
                assertEquals(404, denied.statusCode(), denied.body())
            } finally {
                barrier.release.countDown()
                readProbe.current.set(null)
            }
        }
    }

    private data class DelegatedReadFixture(
        val fixture: ExpenseFixture,
        val from: Reviewer,
        val delegate: Reviewer,
        val path: String,
        val until: Instant,
    )

    private fun delegatedReadFixture(companyRead: Boolean = false): DelegatedReadFixture {
        val f = expenseFixture()
        expenseTemplate(f)
        val id = pendingExpense(f)
        val from = reviewer(f)
        val permissions =
            listOf("company.read", "approvals.read", "expenses.approve") +
                if (companyRead) listOf("expenses.read") else emptyList()
        val delegated = reviewer(f, expenseMember(f.company, permissions))
        val until = clock.instant().plusSeconds(2)
        val delegation = expenseDelegation(f, from, delegated.account, until = until)
        // PostgreSQL persists microsecond precision; expire at the stored boundary.
        val storedUntil =
            requireNotNull(
                    database()
                        .queryForObject(
                            "select valid_until from approval_delegations where company_id=? and id=?",
                            OffsetDateTime::class.java,
                            f.company,
                            delegation,
                        )
                )
                .toInstant()
        return DelegatedReadFixture(
            f,
            from,
            delegated,
            "/api/v1/companies/${f.company}/expenses/submissions/$id",
            storedUntil,
        )
    }
}
