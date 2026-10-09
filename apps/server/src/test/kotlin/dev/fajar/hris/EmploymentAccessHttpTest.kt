package dev.fajar.hris

import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class EmploymentAccessHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var accountProbe: AccountLockProbe

    private data class Fixture(
        val account: UUID,
        val browser: HttpClient,
        val csrf: String,
        val company: UUID,
        val employee: UUID,
    ) {
        val path
            get() = "/api/v1/companies/$company/employees"
    }

    private fun fixture(): Fixture {
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Employment access fixture',password_hash from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into platform_permissions(account_id,permission) select ?,permission from platform_permissions where account_id=(select id from accounts where email='admin@example.test')",
                account,
            )
        val browser = client()
        val csrf = login(browser, "$account@example.test")
        val company = company(browser, csrf)
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'people.self.read') on conflict do nothing",
                company,
                account,
            )
        return Fixture(
            account,
            browser,
            csrf,
            company,
            employee(browser, csrf, company, account = account),
        )
    }

    private fun waiting(
        account: UUID,
        change: () -> Unit,
        request: () -> HttpResponse<String>,
    ): HttpResponse<String> {
        val barrier = AccountLockProbe.Barrier(account)
        accountProbe.current.set(barrier)
        return Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { request() }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                change()
                barrier.release.countDown()
                pending.get(10, TimeUnit.SECONDS)
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    private fun creation(id: UUID, account: UUID? = null) =
        mapOf(
            "id" to id,
            "employeeNumber" to "E${id.toString().take(8)}",
            "person" to
                mapOf(
                    "id" to UUID.randomUUID(),
                    "legalName" to "Employee fixture",
                    "nationality" to "ID",
                    "accountId" to account,
                ),
            "terms" to terms(),
            "reason" to "Verified employment",
        )

    @Test
    fun employmentCommandsRejectPendingRevocationBeforeWriteAndReceiptReplay() {
        for (mode in listOf("create", "revise", "cancel")) {
            val f = fixture()
            if (mode == "cancel") {
                val scheduled =
                    revise(f.browser, f.csrf, f.company, f.employee, 0, terms("2026-11-01"))
                assertEquals(200, scheduled.statusCode(), scheduled.body())
            }
            val id = UUID.randomUUID()
            val key = UUID.randomUUID()
            val path =
                when (mode) {
                    "create" -> f.path
                    "revise" -> "${f.path}/${f.employee}/revisions"
                    else -> "${f.path}/${f.employee}/revisions/1/cancel"
                }
            val input =
                when (mode) {
                    "create" -> creation(id)
                    "revise" ->
                        mapOf(
                            "version" to 0,
                            "terms" to terms("2026-11-01"),
                            "reason" to "Scheduled revision",
                        )
                    else -> mapOf("expectedVersion" to 1, "reason" to "Cancelled revision")
                }
            val payload = json.writeValueAsString(input)
            val request = { command(f.browser, path, payload, f.csrf, key) }
            val denied =
                waiting(
                    f.account,
                    {
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission='people.manage'",
                                f.company,
                                f.account,
                            )
                    },
                    request,
                )
            assertEquals(403, denied.statusCode(), "$mode ${denied.body()}")
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'people.manage')",
                    f.company,
                    f.account,
                )
            val saved = request()
            assertEquals(200, saved.statusCode(), "$mode ${saved.body()}")
            val revokedReplay =
                waiting(
                    f.account,
                    {
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.account,
                            )
                    },
                    request,
                )
            assertEquals(401, revokedReplay.statusCode(), "$mode ${revokedReplay.body()}")
        }
    }

    @Test
    fun directoryRecomputesVisibilityWhenCompanyReadIsRemovedButSelfAccessRemains() {
        val f = fixture()
        employee(f.browser, f.csrf, f.company)
        val before = get(f.browser, "${f.path}?asOf=2026-10-01")
        assertEquals(200, before.statusCode(), before.body())
        assertEquals(2, json.readTree(before.body())["items"].size())
        val narrowed =
            waiting(
                f.account,
                {
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='people.read'",
                            f.company,
                            f.account,
                        )
                },
            ) {
                get(f.browser, "${f.path}?asOf=2026-10-01")
            }
        assertEquals(200, narrowed.statusCode(), narrowed.body())
        val items = json.readTree(narrowed.body())["items"]
        assertEquals(1, items.size())
        assertEquals(f.employee.toString(), items[0]["id"].asString())
    }

    @Test
    fun employeeHistoryAndTransferReadsRecheckTheirCurrentScope() {
        for (mode in listOf("employee", "history", "transfers")) {
            val f = fixture()
            val other = employee(f.browser, f.csrf, f.company)
            val path =
                "${f.path}/$other" +
                    when (mode) {
                        "history" -> "/history"
                        "transfers" -> "/transfers"
                        else -> "?asOf=2026-10-01"
                    }
            val denied =
                waiting(
                    f.account,
                    {
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission='people.read'",
                                f.company,
                                f.account,
                            )
                    },
                ) {
                    get(f.browser, path)
                }
            assertEquals(
                if (mode == "employee") 404 else 403,
                denied.statusCode(),
                "$mode ${denied.body()}",
            )
        }
    }

    @Test
    fun creatingAnEmploymentLocksAndRechecksItsLinkedAccount() {
        val f = fixture()
        val target = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name) values(?,?,'Linked account')",
                target,
                "$target@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                f.company,
                target,
            )
        val id = UUID.randomUUID()
        val payload = json.writeValueAsString(creation(id, target))
        val denied =
            waiting(
                target,
                { database().update("update accounts set active=false where id=?", target) },
            ) {
                command(f.browser, f.path, payload, f.csrf, UUID.randomUUID())
            }
        assertEquals(422, denied.statusCode(), denied.body())
        assertEquals("account_membership_required", json.readTree(denied.body())["code"].asString())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=? and id=?",
                    Int::class.java,
                    f.company,
                    id,
                ),
        )
    }
}
