package dev.fajar.hris

import java.net.http.HttpClient
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException

@Import(AccountLockProbeConfiguration::class)
class ExpensePolicyHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var probe: AccountLockProbe

    private data class Fixture(
        val browser: HttpClient,
        val csrf: String,
        val company: UUID,
        val category: UUID,
    ) {
        val path
            get() = "/api/v1/companies/$company/expenses/categories"
    }

    private fun fixture(): Fixture {
        val browser = client()
        val csrf = login(browser)
        return Fixture(browser, csrf, company(browser, csrf), UUID.randomUUID())
    }

    private fun body(version: Long? = null, changes: Map<String, Any?> = emptyMap()): String =
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
        )

    private fun save(
        f: Fixture,
        version: Long? = null,
        changes: Map<String, Any?> = emptyMap(),
        key: UUID = UUID.randomUUID(),
    ) = command(f.browser, "${f.path}/${f.category}", body(version, changes), f.csrf, key, "PUT")

    private fun member(company: UUID, permissions: List<String>): Pair<UUID, HttpClient> {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Expense fixture',password_hash from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                id,
            )
        permissions.forEach {
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    company,
                    id,
                    it,
                )
        }
        val browser = client()
        login(browser, "$id@example.test")
        return id to browser
    }

    @Test
    fun effectivePoliciesKeepImmutableHistoryStableCodesAndBoundedPages() {
        val f = fixture()
        val first = save(f)
        assertEquals(200, first.statusCode(), first.body())
        assertEquals(
            200,
            save(
                    f,
                    0,
                    mapOf(
                        "effectiveFrom" to "2026-11-01",
                        "maximumLineAmount" to "600000.00",
                        "active" to false,
                    ),
                )
                .statusCode(),
        )
        val past = get(f.browser, "${f.path}?asOf=2026-10-01")
        assertEquals(200, past.statusCode(), past.body())
        val original = json.readTree(past.body()).get("items")[0]
        assertEquals("500000.00", original.get("maximumLineAmount").asString())
        assertEquals("IDR", original.get("currency").asString())
        assertEquals(1, original.get("version").asLong())
        assertEquals(0, original.get("appliedRevision").asLong())
        assertTrue(original.get("active").asBoolean())
        val future =
            json.readTree(get(f.browser, "${f.path}?asOf=2026-11-01").body()).get("items")[0]
        assertFalse(future.get("active").asBoolean())
        assertEquals("600000.00", future.get("maximumLineAmount").asString())
        assertEquals(
            200,
            save(f, 1, mapOf("effectiveFrom" to "2026-02-01", "name" to "Corrected policy"))
                .statusCode(),
        )
        assertEquals(
            1,
            json
                .readTree(get(f.browser, "${f.path}?asOf=2026-11-01").body())
                .get("items")[0]
                .get("appliedRevision")
                .asLong(),
        )
        val history =
            json.readTree(get(f.browser, "${f.path}/${f.category}/history?limit=1").body())
        assertEquals("0", history.get("nextCursor").asString())
        val second =
            json.readTree(get(f.browser, "${f.path}/${f.category}/history?limit=1&after=0").body())
        assertEquals(1, second.get("items")[0].get("category").get("appliedRevision").asLong())
        assertEquals(422, save(f, 2, mapOf("code" to "RENAMED")).statusCode())
        assertEquals(409, save(f, 1).statusCode())
        val other = f.copy(category = UUID.randomUUID())
        assertEquals(200, save(other, changes = mapOf("code" to "MEALS")).statusCode())
        val page = json.readTree(get(f.browser, "${f.path}?asOf=2026-10-01&limit=1").body())
        assertEquals("MEALS", page.get("nextCursor").asString())
        val last =
            json.readTree(get(f.browser, "${f.path}?asOf=2026-10-01&limit=1&after=MEALS").body())
        assertEquals("TRAVEL", last.get("items")[0].get("code").asString())
        assertTrue(last.get("nextCursor").isNull)
        assertThrows(DataAccessException::class.java) {
            database()
                .update("delete from expense_category_revisions where category_id=?", f.category)
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update expense_categories set code='OTHER',version=version+1 where id=?",
                    f.category,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database().update("delete from expense_categories where id=?", f.category)
        }
    }

    @Test
    fun competingCategoryCodesAndVersionsProduceOneCommittedChange() {
        val f = fixture()
        val second = f.copy(category = UUID.randomUUID())
        val go = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val tasks =
                listOf(f, second).map { value ->
                    pool.submit<Int> {
                        check(go.await(5, TimeUnit.SECONDS))
                        save(value).statusCode()
                    }
                }
            go.countDown()
            assertEquals(listOf(200, 409), tasks.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        val id =
            database()
                .queryForObject(
                    "select id from expense_categories where company_id=?",
                    UUID::class.java,
                    f.company,
                )!!
        val stored = f.copy(category = id)
        val update = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val tasks =
                (1..2).map { index ->
                    pool.submit<Int> {
                        check(update.await(5, TimeUnit.SECONDS))
                        save(stored, 0, mapOf("name" to "Updated $index")).statusCode()
                    }
                }
            update.countDown()
            assertEquals(listOf(200, 409), tasks.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from expense_categories where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from expense_category_revisions where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where company_id=? and action='expenses.category_saved'",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun receiptsReplayAfterLaterRevisionsWithoutGrantingRevokedAccess() {
        val f = fixture()
        val key = UUID.randomUUID()
        val first = save(f, key = key)
        assertEquals(200, first.statusCode(), first.body())
        assertEquals(200, save(f, 0, mapOf("active" to false)).statusCode())
        assertEquals(first.body(), save(f, key = key).body())
        assertEquals(
            409,
            save(f, changes = mapOf("name" to "Different request"), key = key).statusCode(),
        )
        val admin =
            database()
                .queryForObject(
                    "select id from accounts where email='admin@example.test'",
                    UUID::class.java,
                )!!
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='expenses.policy.manage'",
                f.company,
                admin,
            )
        assertEquals(403, save(f, key = key).statusCode())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from expense_category_revisions where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun policyAccessIsScopedToCompanyAndHistoryRequiresAdministration() {
        val f = fixture()
        assertEquals(200, save(f).statusCode())
        val (_, worker) = member(f.company, listOf("expenses.self.manage"))
        assertEquals(200, get(worker, "${f.path}?asOf=2026-10-01").statusCode())
        assertEquals(403, get(worker, "${f.path}/${f.category}/history").statusCode())
        val (_, other) = member(f.company, listOf("company.read"))
        assertEquals(403, get(other, "${f.path}?asOf=2026-10-01").statusCode())
        val foreign = company(f.browser, f.csrf)
        assertEquals(
            404,
            get(f.browser, "/api/v1/companies/$foreign/expenses/categories/${f.category}/history")
                .statusCode(),
        )
        assertEquals(
            0,
            json
                .readTree(
                    get(f.browser, "/api/v1/companies/$foreign/expenses/categories?asOf=2026-10-01")
                        .body()
                )
                .get("items")
                .size(),
        )
        assertEquals(
            403,
            get(worker, "/api/v1/companies/$foreign/expenses/categories?asOf=2026-10-01")
                .statusCode(),
        )
    }

    @Test
    fun invalidMoneyDatesAndDefinitionsNeverPersistPartialPolicies() {
        val f = fixture()
        val cases =
            listOf(
                mapOf("maximumLineAmount" to "0"),
                mapOf("maximumLineAmount" to "1e100000"),
                mapOf("maximumLineAmount" to "1.001"),
                mapOf("maximumLineAmount" to "1000000000000"),
                mapOf("maximumClaimAmount" to "1"),
                mapOf("maximumAgeDays" to 367),
                mapOf("allowedContracts" to emptyList<String>()),
                mapOf("code" to "bad code"),
                mapOf("effectiveFrom" to "2201-01-01"),
                mapOf("reason" to " "),
                mapOf("name" to " "),
            )
        for (changes in cases) {
            val result = save(f, changes = changes)
            assertEquals(422, result.statusCode(), result.body())
        }
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from expense_categories where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(422, get(f.browser, "${f.path}?asOf=2026-10-01&limit=201").statusCode())
        assertEquals(422, get(f.browser, "${f.path}?asOf=2201-01-01").statusCode())
        assertEquals(422, get(f.browser, "${f.path}/${f.category}/history?after=-1").statusCode())
    }

    @Test
    fun policyWriteRechecksCredentialsAfterWaitingAndRollsBackAuditFailure() {
        val f = fixture()
        val (account, browser) = member(f.company, listOf("expenses.policy.manage"))
        val csrf = json.readTree(get(browser, "/api/v1/auth/csrf").body()).get("token").asString()
        val barrier = AccountLockProbe.Barrier(account)
        probe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<Int> { save(f.copy(browser = browser, csrf = csrf)).statusCode() }
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "update accounts set security_version=security_version+1 where id=?",
                        account,
                    )
                barrier.release.countDown()
                assertEquals(401, pending.get(15, TimeUnit.SECONDS))
            }
        } finally {
            barrier.release.countDown()
            probe.current.set(null)
        }
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from expense_categories where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        val key = UUID.randomUUID()
        database()
            .execute(
                """create function fail_expense_policy_audit() returns trigger language plpgsql as ${'$'}${'$'} begin if new.resource_id='${f.category}'::uuid and new.action='expenses.category_saved' then raise exception 'Fixture failure' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger expense_policy_audit_probe before insert on audit_entries for each row execute function fail_expense_policy_audit()"
            )
        try {
            val failed = save(f, key = key)
            assertEquals(409, failed.statusCode(), failed.body())
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from expense_categories where company_id=?",
                        Int::class.java,
                        f.company,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from expense_category_revisions where company_id=?",
                        Int::class.java,
                        f.company,
                    ),
            )
        } finally {
            database().execute("drop trigger expense_policy_audit_probe on audit_entries")
            database().execute("drop function fail_expense_policy_audit()")
        }
        assertEquals(200, save(f, key = key).statusCode())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from expense_category_revisions where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun categoryCapacityCannotBeOversubscribedByCompetingCreates() {
        val f = fixture()
        val admin =
            database()
                .queryForObject(
                    "select id from accounts where email='admin@example.test'",
                    UUID::class.java,
                )!!
        database()
            .execute(
                """do ${'$'}${'$'} declare identifier uuid;begin for n in 1..499 loop identifier:=gen_random_uuid();
            insert into expense_categories(company_id,id,code) values('${f.company}',identifier,'FIXTURE_'||lpad(n::text,3,'0'));
            insert into expense_category_revisions(company_id,category_id,revision,effective_from,name,maximum_line_amount,maximum_claim_amount,receipt_required,cost_center_required,maximum_age_days,allowed_contracts,active,actor_id,reason)
            values('${f.company}',identifier,0,'2026-01-01','Fixture category',1,1,true,true,30,ARRAY['PERMANENT'],true,'$admin','Capacity fixture');
            end loop;end ${'$'}${'$'}"""
            )
        val go = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val tasks =
                (1..2).map { index ->
                    pool.submit<Int> {
                        check(go.await(5, TimeUnit.SECONDS))
                        save(
                                f.copy(category = UUID.randomUUID()),
                                changes = mapOf("code" to "LAST_$index"),
                            )
                            .statusCode()
                    }
                }
            go.countDown()
            assertEquals(listOf(200, 409), tasks.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        assertEquals(
            500,
            database()
                .queryForObject(
                    "select count(*) from expense_categories where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }
}
