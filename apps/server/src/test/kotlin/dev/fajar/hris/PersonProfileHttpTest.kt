package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.repositories.PersonProfileRepository
import java.net.http.HttpClient
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException

class PersonProfileHttpTest : ApiIntegrationTest() {
    @Autowired private lateinit var profiles: PersonProfileRepository
    @Autowired private lateinit var transactions: TransactionRunner

    private data class Fixture(
        val browser: HttpClient,
        val csrf: String,
        val company: UUID,
        val employee: UUID,
        val person: UUID,
        val account: UUID,
    ) {
        val path
            get() = "/api/v1/companies/$company/employees/$employee/profile"
    }

    private fun company(browser: HttpClient, csrf: String): UUID {
        val response =
            command(
                browser,
                "/api/v1/companies",
                json.writeValueAsString(
                    mapOf(
                        "code" to "P${UUID.randomUUID().toString().take(8)}",
                        "name" to "Profile Test",
                        "timezone" to "Asia/Jakarta",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, response.statusCode(), response.body())
        return UUID.fromString(json.readTree(response.body()).get("id").asString())
    }

    private fun fixture(): Fixture {
        val account = UUID.randomUUID()
        val email = "$account@example.test"
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Profile administrator',password_hash from accounts where email='admin@example.test'",
                account,
                email,
            )
        database()
            .update(
                "insert into platform_permissions(account_id,permission) select ?,permission from platform_permissions where account_id=(select id from accounts where email='admin@example.test')",
                account,
            )
        val browser = client()
        val csrf = login(browser, email)
        val company = company(browser, csrf)
        val employee = UUID.randomUUID()
        val person = UUID.randomUUID()
        val created =
            command(
                browser,
                "/api/v1/companies/$company/employees",
                json.writeValueAsString(
                    mapOf(
                        "id" to employee,
                        "employeeNumber" to "EMP001",
                        "person" to
                            mapOf(
                                "id" to person,
                                "accountId" to account,
                                "legalName" to "Initial Employee",
                                "nationality" to "ID",
                                "birthDate" to "1990-01-01",
                            ),
                        "terms" to
                            mapOf(
                                "effectiveFrom" to "2026-01-01",
                                "startDate" to "2026-01-01",
                                "contract" to "PERMANENT",
                                "status" to "ACTIVE",
                            ),
                        "reason" to "Administrative onboarding",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, created.statusCode(), created.body())
        return Fixture(browser, csrf, company, employee, person, account)
    }

    private fun body(
        name: String = "Updated Employee",
        version: Long = 0,
        nationality: String = "ID",
        birthDate: String = "1990-01-01",
    ) =
        json.writeValueAsString(
            mapOf(
                "expectedVersion" to version,
                "legalName" to name,
                "nationality" to nationality,
                "birthDate" to birthDate,
                "email" to "person@example.test",
                "reason" to "Verified personnel record",
            )
        )

    private fun save(f: Fixture, body: String = body(), key: UUID = UUID.randomUUID()) =
        command(f.browser, f.path, body, f.csrf, key, "PUT")

    @Test
    fun profileChangesKeepImmutableHistoryAndAnIndependentVersionWithReplay() {
        val f = fixture()
        val key = UUID.randomUUID()
        val payload = body()
        val first = save(f, payload, key)
        assertEquals(200, first.statusCode(), first.body())
        assertEquals(f.person.toString(), json.readTree(first.body()).get("id").asString())
        val current = json.readTree(get(f.browser, f.path).body())
        assertEquals(1, current.get("version").asLong())
        assertEquals("1990-01-01", current.get("birthDate").asString())
        assertEquals(200, save(f, body("Latest Employee", 1)).statusCode())
        assertEquals(first.body(), save(f, payload, key).body())
        assertEquals(409, save(f, body("Different operation payload"), key).statusCode())
        val directory =
            get(f.browser, "/api/v1/companies/${f.company}/employees/${f.employee}?asOf=2026-10-01")
        assertEquals(200, directory.statusCode(), directory.body())
        val projection = json.readTree(directory.body())
        assertEquals(0, projection.get("version").asLong())
        assertEquals("Latest Employee", projection.get("person").get("legalName").asString())
        assertFalse(projection.get("person").has("birthDate"))
        assertFalse(projection.get("person").has("nationality"))
        val firstPage = json.readTree(get(f.browser, "${f.path}/history?limit=1").body())
        assertEquals("Initial Employee", firstPage.get("items").get(0).get("legalName").asString())
        assertEquals("0", firstPage.get("nextCursor").asString())
        val rest = json.readTree(get(f.browser, "${f.path}/history?after=0").body()).get("items")
        assertEquals(2, rest.size())
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update person_profile_revisions set legal_name='Altered' where person_id=?",
                    f.person,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update persons set owner_company_id=?,version=version+1 where id=?",
                    UUID.randomUUID(),
                    f.person,
                )
        }
    }

    @Test
    fun concurrentProfileSavesDoNotOverwriteEachOther() {
        val f = fixture()
        val ready = CountDownLatch(2)
        val go = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val requests =
                (1..2).map { i ->
                    executor.submit<Int> {
                        ready.countDown()
                        check(go.await(5, TimeUnit.SECONDS))
                        save(f, body("Revision $i")).statusCode()
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            go.countDown()
            assertEquals(listOf(200, 409), requests.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        assertEquals(
            1,
            database()
                .queryForObject("select version from persons where id=?", Int::class.java, f.person),
        )
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from person_profile_revisions where person_id=?",
                    Int::class.java,
                    f.person,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='people.profile_saved'",
                    Int::class.java,
                    f.person,
                ),
        )
    }

    @Test
    fun failedAuditRollsBackTheProfileHistoryAndReceipt() {
        val f = fixture()
        val key = UUID.randomUUID()
        database()
            .execute(
                """create function fail_profile_audit() returns trigger language plpgsql as ${'$'}${'$'}
            begin if new.resource_id='${f.person}'::uuid and new.action='people.profile_saved' then
                raise exception 'Fixture write failure' using errcode='23514'; end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger profile_audit_probe before insert on audit_entries for each row execute function fail_profile_audit()"
            )
        try {
            assertEquals(409, save(f, key = key).statusCode())
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select version from persons where id=?",
                        Int::class.java,
                        f.person,
                    ),
            )
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select count(*) from person_profile_revisions where person_id=?",
                        Int::class.java,
                        f.person,
                    ),
            )
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from operation_receipts where operation_id=?",
                        Int::class.java,
                        key,
                    ),
            )
        } finally {
            database().execute("drop trigger profile_audit_probe on audit_entries")
            database().execute("drop function fail_profile_audit()")
        }
        assertEquals(200, save(f, key = key).statusCode())
    }

    @Test
    fun sensitiveProfilesRequireExplicitScopeAndAnOwningCompanyForChanges() {
        val f = fixture()
        val other = company(f.browser, f.csrf)
        val otherEmployee = UUID.randomUUID()
        database()
            .update(
                "insert into employments(company_id,id,person_id,employee_number) values(?,?,?,'SHARED001')",
                other,
                otherEmployee,
                f.person,
            )
        val otherPath = "/api/v1/companies/$other/employees/$otherEmployee/profile"
        assertEquals(200, get(f.browser, otherPath).statusCode())
        assertEquals(
            403,
            command(f.browser, otherPath, body(), f.csrf, UUID.randomUUID(), "PUT").statusCode(),
        )
        val unrelated = company(f.browser, f.csrf)
        assertEquals(
            404,
            get(f.browser, "/api/v1/companies/$unrelated/employees/${f.employee}/profile")
                .statusCode(),
        )
        val hidden =
            transactions.run(
                Actor(f.account, unrelated, emptySet(), Instant.now(), UUID.randomUUID())
            ) {
                profiles.history(f.person, null, 50)
            }
        assertTrue(hidden is Result.Success)
        assertTrue((hidden as Result.Success).value.items.isEmpty())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission like 'people.profile.%'",
                f.company,
                f.account,
            )
        assertEquals(404, get(f.browser, f.path).statusCode())
        assertEquals(403, save(f).statusCode())
        database()
            .update(
                "insert into membership_permissions values(?,?,'people.self.read')",
                f.company,
                f.account,
            )
        assertEquals(200, get(f.browser, f.path).statusCode())
        assertEquals(403, get(f.browser, "${f.path}/history").statusCode())
        database()
            .update(
                "insert into membership_permissions values(?,?,'people.profile.read'),(?,?,'people.profile.manage')",
                f.company,
                f.account,
                f.company,
                f.account,
            )
        assertEquals(422, save(f, body(nationality = "ZZ")).statusCode())
        assertEquals(422, save(f, body(birthDate = "2999-01-01")).statusCode())
        assertEquals(422, get(f.browser, "${f.path}/history?limit=201").statusCode())
    }
}
