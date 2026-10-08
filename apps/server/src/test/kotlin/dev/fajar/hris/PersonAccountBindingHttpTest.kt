package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.people.domain.usecases.BindPersonAccount
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
class PersonAccountBindingHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var binding: BindPersonAccount
    @Autowired private lateinit var probe: AccountLockProbe

    private data class Fixture(
        val browser: HttpClient,
        val csrf: String,
        val company: UUID,
        val employee: UUID,
        val person: UUID,
        val account: UUID,
    ) {
        val path: String
            get() = "/api/v1/companies/$company/employees/$employee/account-link"
    }

    private fun member(company: UUID): UUID {
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Employee account',password_hash from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'people.self.read')",
                company,
                account,
            )
        return account
    }

    private fun fixture(): Fixture {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        val employee = employee(browser, csrf, company)
        val person =
            database()
                .queryForObject(
                    "select person_id from employments where company_id=? and id=?",
                    UUID::class.java,
                    company,
                    employee,
                )!!
        return Fixture(browser, csrf, company, employee, person, member(company))
    }

    private fun bind(
        f: Fixture,
        account: UUID = f.account,
        version: Long = 0,
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            f.browser,
            f.path,
            json.writeValueAsString(
                mapOf(
                    "accountId" to account,
                    "expectedVersion" to version,
                    "reason" to "Identity verified independently",
                )
            ),
            f.csrf,
            key,
        )

    private fun count(f: Fixture, table: String) =
        database()
            .queryForObject(
                "select count(*) from $table where person_id=?",
                Int::class.java,
                f.person,
            )

    @Test
    fun bindingPublishesSelfAccessWithImmutableIdentityHistoryAndOriginalReplay() {
        val f = fixture()
        val target = client()
        login(target, "${f.account}@example.test")
        val profile = "/api/v1/companies/${f.company}/employees/${f.employee}/profile"
        assertEquals(404, get(target, profile).statusCode())
        val key = UUID.randomUUID()
        val first = bind(f, key = key)
        assertEquals(200, first.statusCode(), first.body())
        assertEquals(200, get(target, profile).statusCode())
        assertEquals(first.body(), bind(f, key = key).body())
        assertEquals(409, bind(f, version = 1).statusCode())
        assertEquals(409, bind(f, version = 1, key = key).statusCode())
        val history = json.readTree(get(f.browser, "$profile/history").body()).get("items")
        assertEquals(2, history.size())
        assertTrue(history.get(0).get("accountId").isNull)
        assertEquals(f.account.toString(), history.get(1).get("accountId").asString())
        assertEquals(1, count(f, "person_account_links"))
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select version from employments where company_id=? and id=?",
                    Int::class.java,
                    f.company,
                    f.employee,
                ),
        )
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update person_account_links set reason='Altered' where person_id=?",
                    f.person,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update("update persons set account_id=null,version=version+1 where id=?", f.person)
        }
        database().update("update accounts set active=false where id=?", f.account)
        assertEquals(first.body(), bind(f, key = key).body())
        assertFalse(
            database()
                .queryForObject(
                    "select active from accounts where id=?",
                    Boolean::class.java,
                    f.account,
                )!!
        )
    }

    @Test
    fun independentBindingRequiresAnActiveMemberAndTheOwningCompany() {
        val f = fixture()
        val admin =
            database()
                .queryForObject(
                    "select id from accounts where email='admin@example.test'",
                    UUID::class.java,
                )!!
        assertEquals(403, bind(f, account = admin).statusCode())
        assertEquals(422, bind(f, account = UUID.randomUUID()).statusCode())
        database()
            .update(
                "update company_memberships set active=false where company_id=? and account_id=?",
                f.company,
                f.account,
            )
        assertEquals(422, bind(f).statusCode())
        database()
            .update(
                "update company_memberships set active=true where company_id=? and account_id=?",
                f.company,
                f.account,
            )
        val other = company(f.browser, f.csrf)
        val shared = UUID.randomUUID()
        database()
            .update(
                "insert into employments(company_id,id,person_id,employee_number) values(?,?,?,'SHARED')",
                other,
                shared,
                f.person,
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                other,
                f.account,
            )
        assertEquals(403, bind(f.copy(company = other, employee = shared)).statusCode())
        assertEquals(404, bind(f.copy(company = other)).statusCode())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='people.account.link'",
                f.company,
                admin,
            )
        assertEquals(403, bind(f).statusCode())
        assertEquals(0, count(f, "person_account_links"))
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update persons set account_id=?,version=version+1 where id=?",
                    f.account,
                    f.person,
                )
        }
    }

    @Test
    fun competingPeopleCannotBindTheSameAccountOrLeaveAnOrphanAuthorization() {
        val f = fixture()
        val other = employee(f.browser, f.csrf, f.company)
        val second =
            f.copy(
                employee = other,
                person =
                    database()
                        .queryForObject(
                            "select person_id from employments where company_id=? and id=?",
                            UUID::class.java,
                            f.company,
                            other,
                        )!!,
            )
        val ready = CountDownLatch(2)
        val go = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val tasks =
                listOf(f, second).map { value ->
                    pool.submit<Int> {
                        ready.countDown()
                        check(go.await(5, TimeUnit.SECONDS))
                        bind(value).statusCode()
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            go.countDown()
            assertEquals(listOf(200, 409), tasks.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from person_account_links where account_id=?",
                    Int::class.java,
                    f.account,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from persons where account_id=?",
                    Int::class.java,
                    f.account,
                ),
        )
        assertEquals(
            3,
            database()
                .queryForObject(
                    "select count(*) from person_profile_revisions where owner_company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun aConcurrentProfileEditCannotBeOverwrittenByAccountBinding() {
        val f = fixture()
        val go = CountDownLatch(1)
        val ready = CountDownLatch(2)
        Executors.newFixedThreadPool(2).use { pool ->
            val link =
                pool.submit<Int> {
                    ready.countDown()
                    check(go.await(5, TimeUnit.SECONDS))
                    bind(f).statusCode()
                }
            val edit =
                pool.submit<Int> {
                    ready.countDown()
                    check(go.await(5, TimeUnit.SECONDS))
                    command(
                            f.browser,
                            "/api/v1/companies/${f.company}/employees/${f.employee}/profile",
                            json.writeValueAsString(
                                mapOf(
                                    "expectedVersion" to 0,
                                    "legalName" to "Corrected name",
                                    "nationality" to "ID",
                                    "reason" to "Verified correction",
                                )
                            ),
                            f.csrf,
                            UUID.randomUUID(),
                            "PUT",
                        )
                        .statusCode()
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            go.countDown()
            val results = listOf(link.get(15, TimeUnit.SECONDS), edit.get(15, TimeUnit.SECONDS))
            assertEquals(listOf(200, 409), results.sorted())
            assertEquals(if (results[0] == 200) 1 else 0, count(f, "person_account_links"))
        }
        assertEquals(2, count(f, "person_profile_revisions"))
        assertEquals(
            1,
            database()
                .queryForObject("select version from persons where id=?", Int::class.java, f.person),
        )
    }

    @Test
    fun revokedOrStaleAuthenticationCannotPublishThePendingBinding() {
        val f = fixture()
        val id =
            database()
                .queryForObject(
                    "select id from accounts where email='admin@example.test'",
                    UUID::class.java,
                )!!
        val version =
            database()
                .queryForObject(
                    "select security_version from accounts where id=?",
                    Long::class.java,
                    id,
                )!!
        val actor =
            Actor(
                id,
                f.company,
                PermissionCatalog.companyAdministrator,
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = version,
            )
        val barrier = AccountLockProbe.Barrier(id)
        probe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val task =
                    pool.submit<Result<MutationReceipt>> {
                        binding.execute(
                            actor,
                            UUID.randomUUID(),
                            f.employee,
                            f.account,
                            0,
                            "Verified binding",
                        )
                    }
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "update accounts set security_version=security_version+1 where id=?",
                        id,
                    )
                barrier.release.countDown()
                assertEquals(
                    Result.Failed(Failure(FailureKind.UNAUTHENTICATED, "session_revoked")),
                    task.get(15, TimeUnit.SECONDS),
                )
            }
        } finally {
            barrier.release.countDown()
            probe.current.set(null)
        }
        assertEquals(0, count(f, "person_account_links"))
        val fresh = f.copy(csrf = login(f.browser))
        clock.set(clock.instant().plusSeconds(601))
        assertEquals(403, bind(fresh).statusCode())
        assertEquals(0, count(f, "person_account_links"))
    }

    @Test
    fun failedAuditRollsBackAccountLinkProfileAndReceiptTogether() {
        val f = fixture()
        val key = UUID.randomUUID()
        database()
            .execute(
                """create function fail_account_link_audit() returns trigger language plpgsql as ${'$'}${'$'} begin if new.resource_id='${f.person}'::uuid and new.action='people.account_bound' then raise exception 'Fixture failure' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
            )
        database()
            .execute(
                "create trigger account_link_audit_probe before insert on audit_entries for each row execute function fail_account_link_audit()"
            )
        try {
            assertEquals(409, bind(f, key = key).statusCode())
            assertEquals(0, count(f, "person_account_links"))
            assertEquals(1, count(f, "person_profile_revisions"))
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
                0,
                database()
                    .queryForObject(
                        "select count(*) from operation_receipts where operation_id=?",
                        Int::class.java,
                        key,
                    ),
            )
        } finally {
            database().execute("drop trigger account_link_audit_probe on audit_entries")
            database().execute("drop function fail_account_link_audit()")
        }
        assertEquals(200, bind(f, key = key).statusCode())
    }
}
