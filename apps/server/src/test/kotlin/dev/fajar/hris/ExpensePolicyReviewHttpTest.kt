package dev.fajar.hris

import java.net.http.HttpClient
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
class ExpensePolicyReviewHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var readProbe: ExpenseReadProbe
    @Autowired private lateinit var accountProbe: AccountLockProbe

    private data class Fixture(
        val browser: HttpClient,
        val csrf: String,
        val company: UUID,
        val category: UUID = UUID.randomUUID(),
    ) {
        val path
            get() = "/api/v1/companies/$company/expenses/policies"
    }

    private fun fixture(): Fixture {
        val browser = client()
        val csrf = login(browser)
        return Fixture(browser, csrf, company(browser, csrf))
    }

    private fun save(f: Fixture, version: Long? = null, changes: Map<String, Any?> = emptyMap()) =
        command(
            f.browser,
            "/api/v1/companies/${f.company}/expenses/categories/${f.category}",
            json.writeValueAsString(
                mapOf(
                    "code" to "TRAVEL",
                    "name" to "Business travel",
                    "effectiveFrom" to "2026-01-01",
                    "maximumLineAmount" to "500000.00",
                    "maximumClaimAmount" to "2000000.00",
                    "receiptRequired" to true,
                    "costCenterRequired" to true,
                    "maximumAgeDays" to 30,
                    "allowedContracts" to listOf("PERMANENT", "FIXED_TERM"),
                    "expectedVersion" to version,
                    "reason" to "Expense policy configuration",
                ) + changes
            ),
            f.csrf,
            UUID.randomUUID(),
            "PUT",
        )

    private fun body(response: HttpResponse<String>) =
        json.readTree(response.body()).also {
            assertEquals(200, response.statusCode(), response.body())
        }

    private fun member(company: UUID, permission: String): Pair<UUID, HttpClient> {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Expense review fixture',password_hash from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                id,
            )
        for (grant in listOf("company.read", permission)) {
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    company,
                    id,
                    grant,
                )
        }
        val browser = client()
        login(browser, "$id@example.test")
        return id to browser
    }

    @AfterEach
    fun releaseProbes() {
        readProbe.current.getAndSet(null)?.release?.countDown()
        accountProbe.current.getAndSet(null)?.release?.countDown()
    }

    @Test
    fun catalogIncludesFutureAndArchivedHeadsWithoutChangingEffectiveSelection() {
        val f = fixture()
        body(save(f))
        body(save(f, 0, mapOf("effectiveFrom" to "2027-01-01", "active" to false)))
        body(
            save(
                f.copy(category = UUID.randomUUID()),
                changes = mapOf("code" to "MEALS", "effectiveFrom" to "2028-01-01"),
            )
        )
        val first = body(get(f.browser, "${f.path}?limit=1"))
        assertEquals("MEALS", first["items"][0]["code"].asString())
        assertEquals("MEALS", first["nextCursor"].asString())
        val last = body(get(f.browser, "${f.path}?limit=1&after=MEALS"))
        assertEquals("TRAVEL", last["items"][0]["code"].asString())
        assertEquals(1, last["items"][0]["version"].asLong())
        assertEquals(1, last["items"][0]["appliedRevision"].asLong())
        assertFalse(last["items"][0]["active"].asBoolean())
        assertTrue(last["nextCursor"].isNull)
        assertEquals(
            "MEALS",
            body(get(f.browser, "${f.path}?active=true"))["items"][0]["code"].asString(),
        )
        assertEquals(
            "TRAVEL",
            body(get(f.browser, "${f.path}?active=false"))["items"][0]["code"].asString(),
        )
        val effective =
            body(
                get(f.browser, "/api/v1/companies/${f.company}/expenses/categories?asOf=2026-10-01")
            )["items"]
        assertEquals(1, effective.size())
        assertEquals(0, effective[0]["appliedRevision"].asLong())
        assertTrue(effective[0]["active"].asBoolean())
        for (query in
            listOf("limit=0", "limit=201", "after=%20", "after=a", "after=" + "A".repeat(33))) {
            val invalid = get(f.browser, "${f.path}?$query")
            assertEquals(422, invalid.statusCode(), invalid.body())
            assertEquals("invalid_page", json.readTree(invalid.body())["code"].asString())
        }
    }

    @Test
    fun reviewKeepsTheSavedHeadWhilePagingAscendingImmutableRevisions() {
        val f = fixture()
        body(save(f))
        body(save(f, 0, mapOf("effectiveFrom" to "2027-01-01", "active" to false)))
        body(save(f, 1, mapOf("effectiveFrom" to "2026-03-01", "name" to "Corrected travel")))
        val first = body(get(f.browser, "${f.path}/${f.category}?historyLimit=2"))
        assertEquals(2, first["current"]["version"].asLong())
        assertEquals("Corrected travel", first["current"]["name"].asString())
        assertEquals(
            listOf(0L, 1L),
            first["history"]["items"]
                .iterator()
                .asSequence()
                .map { it["category"]["appliedRevision"].asLong() }
                .toList(),
        )
        assertEquals("1", first["history"]["nextCursor"].asString())
        assertEquals(
            "Expense policy configuration",
            first["history"]["items"][0]["reason"].asString(),
        )
        assertNotNull(UUID.fromString(first["history"]["items"][0]["actorId"].asString()))
        assertFalse(first["history"]["items"][1]["category"]["active"].asBoolean())
        val last = body(get(f.browser, "${f.path}/${f.category}?historyLimit=2&historyAfter=1"))
        assertEquals(first["current"], last["current"])
        assertEquals(
            listOf(2L),
            last["history"]["items"]
                .iterator()
                .asSequence()
                .map { it["category"]["appliedRevision"].asLong() }
                .toList(),
        )
        assertTrue(last["history"]["nextCursor"].isNull)
        assertEquals(
            0,
            body(get(f.browser, "${f.path}/${f.category}?historyAfter=2"))["history"]["items"]
                .size(),
        )
        assertEquals(
            1,
            body(
                    get(
                        f.browser,
                        "/api/v1/companies/${f.company}/expenses/categories?asOf=2027-02-01",
                    )
                )["items"][0]["appliedRevision"]
                .asLong(),
        )
        for (query in
            listOf(
                "historyLimit=0",
                "historyLimit=201",
                "historyAfter=-1",
                "historyAfter=3",
                "historyAfter=1000",
            )) {
            val invalid = get(f.browser, "${f.path}/${f.category}?$query")
            assertEquals(422, invalid.statusCode(), invalid.body())
            assertEquals("invalid_page", json.readTree(invalid.body())["code"].asString())
        }
    }

    @Test
    fun policyReadersNeedAdministrationAndCannotDiscoverForeignResources() {
        val f = fixture()
        body(save(f))
        for (permission in listOf("expenses.self.manage", "expenses.read")) {
            val (_, reader) = member(f.company, permission)
            assertEquals(403, get(reader, f.path).statusCode())
            assertEquals(403, get(reader, "${f.path}/${f.category}").statusCode())
            body(get(reader, "/api/v1/companies/${f.company}/expenses/categories?asOf=2026-10-01"))
        }
        val (_, admin) = member(f.company, "expenses.policy.manage")
        body(get(admin, f.path))
        body(get(admin, "${f.path}/${f.category}"))
        val foreign = company(f.browser, f.csrf)
        assertEquals(
            0,
            body(get(f.browser, "/api/v1/companies/$foreign/expenses/policies"))["items"].size(),
        )
        for (path in
            listOf(
                "${f.path}/${UUID.randomUUID()}",
                "/api/v1/companies/$foreign/expenses/policies/${f.category}",
            )) {
            val missing = get(f.browser, path)
            assertEquals(404, missing.statusCode())
            assertEquals(
                "expense_category_not_found",
                json.readTree(missing.body())["code"].asString(),
            )
            assertFalse(missing.body().contains("Business travel"))
            assertFalse(json.readTree(missing.body()).has("current"))
        }
    }

    @Test
    fun concurrentReadersShareTheGuardAndReviewCannotMixHeadAndHistoryAcrossASave() {
        val f = fixture()
        body(save(f))
        val barrier = ExpenseReadProbe.Barrier(f.company, "policy-head")
        readProbe.current.set(barrier)
        Executors.newFixedThreadPool(3).use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> { get(f.browser, "${f.path}/${f.category}") }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val parallel =
                    pool.submit<HttpResponse<String>> { get(f.browser, "${f.path}/${f.category}") }
                assertEquals(
                    0,
                    body(parallel.get(3, TimeUnit.SECONDS))["current"]["version"].asLong(),
                )
                val saving =
                    pool.submit<HttpResponse<String>> { save(f, 0, mapOf("active" to false)) }
                assertThrows(TimeoutException::class.java) {
                    saving.get(150, TimeUnit.MILLISECONDS)
                }
                barrier.release.countDown()
                val previous = body(pending.get(5, TimeUnit.SECONDS))
                assertEquals(0, previous["current"]["version"].asLong())
                assertEquals(
                    listOf(0L),
                    previous["history"]["items"]
                        .iterator()
                        .asSequence()
                        .map { it["category"]["appliedRevision"].asLong() }
                        .toList(),
                )
                body(saving.get(5, TimeUnit.SECONDS))
                val next = body(get(f.browser, "${f.path}/${f.category}"))
                assertEquals(1, next["current"]["version"].asLong())
                assertEquals(
                    listOf(0L, 1L),
                    next["history"]["items"]
                        .iterator()
                        .asSequence()
                        .map { it["category"]["appliedRevision"].asLong() }
                        .toList(),
                )
            } finally {
                barrier.release.countDown()
                readProbe.current.set(null)
            }
        }
    }

    @Test
    fun revocationDuringAccountAcquisitionRejectsBothAdministrativeReads() {
        val f = fixture()
        body(save(f))
        for (path in listOf(f.path, "${f.path}/${f.category}")) {
            val (id, browser) = member(f.company, "expenses.policy.manage")
            val barrier = AccountLockProbe.Barrier(id)
            accountProbe.current.set(barrier)
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<HttpResponse<String>> { get(browser, path) }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.policy.manage'",
                            f.company,
                            id,
                        )
                    barrier.release.countDown()
                    val denied = pending.get(10, TimeUnit.SECONDS)
                    assertEquals(403, denied.statusCode(), denied.body())
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
        }
    }
}
