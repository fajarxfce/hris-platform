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
class CompanyProfileAccessHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var accountProbe: AccountLockProbe

    private data class Fixture(
        val account: UUID,
        val browser: HttpClient,
        val csrf: String,
        val company: UUID,
        val employee: UUID,
    )

    private fun fixture(): Fixture {
        val account = UUID.randomUUID()
        val email = "$account@example.test"
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Access fixture',password_hash from accounts where email='admin@example.test'",
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
        val employee = employee(browser, csrf, company)
        return Fixture(account, browser, csrf, company, employee)
    }

    private fun waiting(
        f: Fixture,
        change: () -> Unit,
        request: () -> HttpResponse<String>,
    ): HttpResponse<String> {
        val barrier = AccountLockProbe.Barrier(f.account)
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

    @Test
    fun companyCreationChecksPlatformPermissionAndCredentialsBeforeWriteOrReplay() {
        val f = fixture()
        val key = UUID.randomUUID()
        val code = "C${key.toString().take(8)}"
        val payload =
            json.writeValueAsString(
                mapOf(
                    "code" to code,
                    "name" to "Company access fixture",
                    "timezone" to "Asia/Jakarta",
                )
            )
        val create = { command(f.browser, "/api/v1/companies", payload, f.csrf, key) }
        val denied =
            waiting(
                f,
                {
                    database()
                        .update(
                            "delete from platform_permissions where account_id=? and permission='companies.create'",
                            f.account,
                        )
                },
                create,
            )
        assertEquals(403, denied.statusCode(), denied.body())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from companies where code=?",
                    Int::class.java,
                    code.uppercase(),
                ),
        )
        database()
            .update(
                "insert into platform_permissions(account_id,permission) values(?,'companies.create')",
                f.account,
            )
        val saved = create()
        assertEquals(200, saved.statusCode(), saved.body())
        val revokedReplay =
            waiting(
                f,
                {
                    database()
                        .update(
                            "update accounts set security_version=security_version+1 where id=?",
                            f.account,
                        )
                },
                create,
            )
        assertEquals(401, revokedReplay.statusCode(), revokedReplay.body())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from companies where code=?",
                    Int::class.java,
                    code.uppercase(),
                ),
        )
    }

    @Test
    fun companyUnitAndProfileCommandsRecheckPermissionAndPreserveFailedOperationKeys() {
        for (mode in listOf("company", "unit", "profile")) {
            val f = fixture()
            val permission = if (mode == "profile") "people.profile.manage" else "company.manage"
            val key = UUID.randomUUID()
            val base = "/api/v1/companies/${f.company}"
            val path =
                when (mode) {
                    "company" -> base
                    "unit" -> "$base/organization-units/${UUID.randomUUID()}"
                    else -> "$base/employees/${f.employee}/profile"
                }
            val payload =
                when (mode) {
                    "company" ->
                        mapOf(
                            "version" to 0,
                            "code" to "C${f.company.toString().take(8)}",
                            "name" to "Updated company",
                            "timezone" to "Asia/Jakarta",
                        )
                    "unit" ->
                        mapOf(
                            "code" to "DEPARTMENT",
                            "name" to "Department",
                            "kind" to "DEPARTMENT",
                            "active" to true,
                        )
                    else ->
                        mapOf(
                            "expectedVersion" to 0,
                            "legalName" to "Updated employee",
                            "nationality" to "ID",
                            "reason" to "Verified profile",
                        )
                }
            val request = {
                command(f.browser, path, json.writeValueAsString(payload), f.csrf, key, "PUT")
            }
            val remove = {
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                        f.company,
                        f.account,
                        permission,
                    )
                Unit
            }
            val denied = waiting(f, remove, request)
            assertEquals(403, denied.statusCode(), "$mode ${denied.body()}")
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    f.company,
                    f.account,
                    permission,
                )
            val saved = request()
            assertEquals(200, saved.statusCode(), "$mode ${saved.body()}")
            val replay = waiting(f, remove, request)
            assertEquals(403, replay.statusCode(), "$mode ${replay.body()}")
        }
    }

    @Test
    fun companyAndProfileReadsCannotReturnDataAfterPendingCredentialRevocation() {
        for (mode in listOf("company", "units", "profile", "history")) {
            val f = fixture()
            val base = "/api/v1/companies/${f.company}"
            val path =
                when (mode) {
                    "company" -> base
                    "units" -> "$base/organization-units"
                    "profile" -> "$base/employees/${f.employee}/profile"
                    else -> "$base/employees/${f.employee}/profile/history"
                }
            val revoked =
                waiting(
                    f,
                    {
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.account,
                            )
                    },
                ) {
                    get(f.browser, path)
                }
            assertEquals(401, revoked.statusCode(), "$mode ${revoked.body()}")
            assertFalse(revoked.body().contains("Example employee"))
        }
    }

    @Test
    fun profileScopeAndCompanyMembershipAreRecheckedAfterWaiting() {
        val f = fixture()
        val path = "/api/v1/companies/${f.company}/employees/${f.employee}/profile"
        val denied =
            waiting(
                f,
                {
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='people.profile.read'",
                            f.company,
                            f.account,
                        )
                },
            ) {
                get(f.browser, path)
            }
        assertEquals(404, denied.statusCode(), denied.body())
        val noMembership =
            waiting(
                f,
                {
                    database()
                        .update(
                            "update company_memberships set active=false where company_id=? and account_id=?",
                            f.company,
                            f.account,
                        )
                },
            ) {
                get(f.browser, "/api/v1/companies/${f.company}/organization-units")
            }
        assertEquals(403, noMembership.statusCode(), noMembership.body())
        assertEquals("company_access_denied", json.readTree(noMembership.body())["code"].asString())
    }
}
