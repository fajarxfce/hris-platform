package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import java.net.URLEncoder
import java.net.http.HttpResponse
import java.util.UUID
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
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.JsonNode

@Import(ExpenseReadProbeConfiguration::class, AccountLockProbeConfiguration::class)
@TestPropertySource(properties = [MOBILE_SYNC_TEST_KEYS])
class ExpenseClaimRecordHttpTest : ExpensePaymentApiFixture() {
    @Autowired private lateinit var readProbe: ExpenseReadProbe
    @Autowired private lateinit var accountProbe: AccountLockProbe

    private fun body(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    @AfterEach
    fun releaseRecordProbes() {
        readProbe.current.getAndSet(null)?.release?.countDown()
        accountProbe.current.getAndSet(null)?.release?.countDown()
    }

    @Test
    fun recordRetainsTheCurrentClaimAndLatestSubmissionWhilePagingActivity() {
        val f = expenseFixture()
        expenseTemplate(f)
        val path = "${f.path}/${f.claim}/record"
        body(saveExpense(f, lines = readyLines(f)))
        val draft = body(get(f.worker, path))
        assertTrue(draft["submission"].isNull)
        assertEquals(0L, draft["claim"]["version"].asLong())
        val first = UUID.randomUUID()
        body(submitExpense(f, first, 0))
        body(withdrawExpense(f, first, 1))
        body(saveExpense(f, 2, readyLines(f), mapOf("title" to "Corrected travel")))
        val second = UUID.randomUUID()
        body(submitExpense(f, second, 3))
        val current = body(get(f.worker, "$path?historyLimit=2"))
        assertEquals(
            setOf("claim", "submission", "history"),
            current.properties().map { it.key }.toSet(),
        )
        assertEquals(4L, current["claim"]["version"].asLong())
        assertEquals(second.toString(), current["submission"]["id"].asString())
        assertEquals("Corrected travel", current["submission"]["title"].asString())
        assertEquals(4L, current["submission"]["claimVersion"].asLong())
        assertEquals("PENDING", current["submission"]["claimStatus"].asString())
        assertEquals(
            listOf(4L, 3L),
            current["history"]["items"]
                .iterator()
                .asSequence()
                .map { it["version"].asLong() }
                .toList(),
        )
        assertEquals("3", current["history"]["nextCursor"].asString())
        val older = body(get(f.worker, "$path?historyAfter=3"))
        assertEquals(current["claim"], older["claim"])
        assertEquals(current["submission"], older["submission"])
        assertEquals(
            listOf(2L, 1L, 0L),
            older["history"]["items"]
                .iterator()
                .asSequence()
                .map { it["version"].asLong() }
                .toList(),
        )
        assertTrue(older["history"]["nextCursor"].isNull)
        assertEquals(0, body(get(f.worker, "$path?historyAfter=0"))["history"]["items"].size())
        for (query in
            listOf("historyLimit=0", "historyLimit=21", "historyAfter=-1", "historyAfter=5")) {
            val invalid = get(f.worker, "$path?$query")
            assertEquals(422, invalid.statusCode(), invalid.body())
            assertEquals("invalid_page", json.readTree(invalid.body())["code"].asString())
        }
        for (field in
            listOf("availableActions", "canReadClaim", "duplicateReceiptDigests")) assertFalse(
            current["submission"].has(field)
        )
    }

    @Test
    fun selfDefaultNeverExpandsWithFinanceGrantsOrSubmissionAssignments() {
        val f = expenseFixture()
        expenseTemplate(f)
        val submission = pendingExpense(f)
        val other = f.copy(claim = UUID.randomUUID(), employee = f.manager)
        body(saveExpense(other, asAdmin = true))
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.read')",
                f.company,
                f.account,
            )
        val path = "${f.path}/${other.claim}/record"
        assertEquals(404, get(f.worker, path).statusCode())
        body(get(f.worker, "$path?scope=AUTHORIZED"))
        body(get(f.worker, "${f.path}/${f.claim}/record"))
        val assigned =
            reviewer(
                f,
                expenseMember(
                    f.company,
                    listOf("company.read", "approvals.read", "expenses.approve"),
                ),
            )
        val approval =
            UUID.fromString(
                body(get(f.worker, "${f.path}/${f.claim}/record"))["submission"]["approval"]["id"]
                    .asString()
            )
        body(reassignExpense(f, approval, setOf(assigned.account)))
        body(
            get(assigned.browser, "/api/v1/companies/${f.company}/expenses/submissions/$submission")
        )
        for (query in listOf("", "?scope=AUTHORIZED", "?scope=AUTHORIZED&historyAfter=2")) {
            val denied = get(assigned.browser, "${f.path}/${f.claim}/record$query")
            assertEquals(404, denied.statusCode(), denied.body())
            assertFalse(denied.body().contains("Travel reimbursement"))
            assertFalse(json.readTree(denied.body()).has("claim"))
        }
        val foreign = company(f.admin, f.adminCsrf)
        assertEquals(
            404,
            get(
                    f.admin,
                    path.replace(f.company.toString(), foreign.toString()) + "?scope=AUTHORIZED",
                )
                .statusCode(),
        )
    }

    @Test
    fun aSharedRecordReadCannotMixClaimVersionsAcrossAConcurrentSave() {
        val f = expenseFixture()
        body(saveExpense(f))
        val path = "${f.path}/${f.claim}/record"
        val barrier = ExpenseReadProbe.Barrier(f.company, "claim-head")
        readProbe.current.set(barrier)
        Executors.newFixedThreadPool(3).use { pool ->
            val pending = pool.submit<HttpResponse<String>> { get(f.worker, path) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val parallel = pool.submit<HttpResponse<String>> { get(f.worker, path) }
                assertEquals(
                    0L,
                    body(parallel.get(3, TimeUnit.SECONDS))["claim"]["version"].asLong(),
                )
                val save =
                    pool.submit<HttpResponse<String>> {
                        saveExpense(f, 0, changes = mapOf("title" to "Revised travel"))
                    }
                assertThrows(TimeoutException::class.java) { save.get(150, TimeUnit.MILLISECONDS) }
                barrier.release.countDown()
                val before = body(pending.get(5, TimeUnit.SECONDS))
                assertEquals(0L, before["claim"]["version"].asLong())
                assertEquals("Travel reimbursement", before["claim"]["draft"]["title"].asString())
                assertEquals(0L, before["history"]["items"][0]["version"].asLong())
                body(save.get(5, TimeUnit.SECONDS))
                val after = body(get(f.worker, path))
                assertEquals(1L, after["claim"]["version"].asLong())
                assertEquals("Revised travel", after["claim"]["draft"]["title"].asString())
                assertEquals(1L, after["history"]["items"][0]["version"].asLong())
            } finally {
                barrier.release.countDown()
                readProbe.current.set(null)
            }
        }
    }

    @Test
    fun approvalReassignmentPublishesAnotherOwnedInvalidationAtTheSameClaimVersion() {
        val f = expenseFixture()
        expenseTemplate(f)
        pendingExpense(f)
        val path = "${f.path}/${f.claim}/record"
        val before = body(get(f.worker, path))
        val approval = UUID.fromString(before["submission"]["approval"]["id"].asString())
        val worker = mobileSyncTestWorker(postgres.jdbcUrl, database())
        assertTrue(worker.maintain.execute() is Result.Success)
        val sync = "/api/v1/companies/${f.company}/sync"
        val selected = "collections=EXPENSE_CLAIMS"
        val initial = body(get(f.worker, "$sync/bootstrap?$selected"))
        val reviewer =
            reviewer(
                f,
                expenseMember(
                    f.company,
                    listOf("company.read", "expenses.approve", "approvals.read"),
                ),
            )
        val barrier = ExpenseReadProbe.Barrier(f.company, "claim-head")
        readProbe.current.set(barrier)
        Executors.newFixedThreadPool(2).use { pool ->
            val reading = pool.submit<HttpResponse<String>> { get(f.worker, path) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val assigning =
                    pool.submit<HttpResponse<String>> {
                        reassignExpense(f, approval, setOf(reviewer.account))
                    }
                assertThrows(TimeoutException::class.java) {
                    assigning.get(150, TimeUnit.MILLISECONDS)
                }
                barrier.release.countDown()
                assertEquals(before, body(reading.get(5, TimeUnit.SECONDS)))
                body(assigning.get(5, TimeUnit.SECONDS))
            } finally {
                barrier.release.countDown()
                readProbe.current.set(null)
            }
        }
        val after = body(get(f.worker, path))
        assertEquals(before["claim"], after["claim"])
        assertEquals(before["history"], after["history"])
        assertEquals(1L, after["submission"]["approval"]["version"].asLong())
        assertEquals(
            reviewer.account.toString(),
            after["submission"]["approval"]["stages"][0][0].asString(),
        )
        assertTrue(worker.maintain.execute() is Result.Success)
        val cursor = URLEncoder.encode(initial["changesCursor"].asString(), Charsets.UTF_8)
        val changed = body(get(f.worker, "$sync/changes?$selected&cursor=$cursor"))["items"]
        assertEquals(1, changed.size())
        assertEquals(f.claim.toString(), changed[0]["id"].asString())
        assertEquals(before["claim"]["version"].asLong(), changed[0]["version"].asLong())
        assertEquals("UPSERT", changed[0]["operation"].asString())
        assertEquals("EXPENSE_CLAIMS", changed[0]["collection"].asString())

        val count =
            database()
                .queryForObject(
                    "select count(*) from mobile_sync_changes where company_id=?",
                    Long::class.java,
                    f.company,
                )!!
        database().dataSource!!.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection
                    .prepareStatement(
                        "update approval_requests set version=version+1 where company_id=? and id=?"
                    )
                    .use { statement ->
                        statement.setObject(1, f.company)
                        statement.setObject(2, approval)
                        assertEquals(1, statement.executeUpdate())
                    }
                connection
                    .prepareStatement("select count(*) from mobile_sync_changes where company_id=?")
                    .use { statement ->
                        statement.setObject(1, f.company)
                        statement.executeQuery().use { rows ->
                            assertTrue(rows.next())
                            assertEquals(count + 1, rows.getLong(1))
                        }
                    }
            } finally {
                connection.rollback()
            }
        }
        assertEquals(
            count,
            database()
                .queryForObject(
                    "select count(*) from mobile_sync_changes where company_id=?",
                    Long::class.java,
                    f.company,
                ),
        )
        assertEquals(after, body(get(f.worker, path)))
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "credentials"])
    fun revocationWhileWaitingForAccessCannotReleaseAnyRecordFields(change: String) {
        val f = expenseFixture()
        body(saveExpense(f))
        val barrier = AccountLockProbe.Barrier(f.account)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> { get(f.worker, "${f.path}/${f.claim}/record") }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                if (change == "permission")
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.self.manage'",
                            f.company,
                            f.account,
                        )
                else
                    database()
                        .update(
                            "update accounts set security_version=security_version+1 where id=?",
                            f.account,
                        )
                barrier.release.countDown()
                val denied = pending.get(10, TimeUnit.SECONDS)
                assertEquals(
                    if (change == "permission") 404 else 401,
                    denied.statusCode(),
                    denied.body(),
                )
                assertFalse(json.readTree(denied.body()).has("claim"))
                assertFalse(denied.body().contains("Travel reimbursement"))
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }
}
