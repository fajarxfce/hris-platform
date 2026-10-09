package dev.fajar.hris

import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class MembershipAccessHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var probe: AccountLockProbe

    private data class Fixture(
        val account: UUID,
        val browser: HttpClient,
        val csrf: String,
        val company: UUID,
        val target: UUID,
    ) {
        val base
            get() = "/api/v1/companies/$company"
    }

    private fun account(): UUID {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Membership fixture',password_hash from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        return id
    }

    private fun fixture(): Fixture {
        val id = account()
        database()
            .update(
                "insert into platform_permissions(account_id,permission) values(?,'identity.manage'),(?,'companies.create')",
                id,
                id,
            )
        val browser = client()
        val csrf = login(browser, "$id@example.test")
        return Fixture(id, browser, csrf, company(browser, csrf), account())
    }

    private fun waiting(
        account: UUID,
        change: () -> Unit,
        request: () -> HttpResponse<String>,
    ): HttpResponse<String> {
        val barrier = AccountLockProbe.Barrier(account)
        probe.current.set(barrier)
        return Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { request() }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                change()
                barrier.release.countDown()
                pending.get(10, TimeUnit.SECONDS)
            } finally {
                barrier.release.countDown()
                probe.current.set(null)
            }
        }
    }

    private fun roleBody(id: UUID, permissions: Set<String> = setOf("company.read")) =
        json.writeValueAsString(
            mapOf(
                "code" to "R${id.toString().take(8)}",
                "name" to "Operations",
                "permissions" to permissions,
                "reason" to "Reviewed role",
            )
        )

    private fun grantBody(
        permissions: Set<String> = setOf("company.read"),
        role: UUID? = null,
        version: Long? = null,
    ) =
        json.writeValueAsString(
            mapOf(
                "permissions" to permissions,
                "expectedVersion" to version,
                "roleTemplates" to
                    if (role == null) emptyList() else listOf(mapOf("id" to role, "version" to 0)),
                "reason" to "Approved access",
            )
        )

    private fun grant(f: Fixture, body: String = grantBody(), key: UUID = UUID.randomUUID()) =
        command(f.browser, "${f.base}/members/${f.target}", body, f.csrf, key, "PUT")

    @Test
    fun roleAndMembershipCommandsRevalidateBeforeMutationAndReceiptReplay() {
        for (mode in listOf("role", "member")) {
            val f = fixture()
            val id = UUID.randomUUID()
            val key = UUID.randomUUID()
            val body = if (mode == "role") roleBody(id) else grantBody()
            val request = {
                if (mode == "role")
                    command(f.browser, "${f.base}/role-templates/$id", body, f.csrf, key, "PUT")
                else grant(f, body, key)
            }
            val denied =
                waiting(
                    f.account,
                    {
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission='identity.manage'",
                                f.company,
                                f.account,
                            )
                    },
                    request,
                )
            assertEquals(403, denied.statusCode(), "$mode ${denied.body()}")
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'identity.manage')",
                    f.company,
                    f.account,
                )
            val saved = request()
            assertEquals(200, saved.statusCode(), "$mode ${saved.body()}")
            assertEquals(saved.body(), request().body())
            val revoked =
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
            assertEquals(401, revoked.statusCode(), "$mode ${revoked.body()}")
        }
    }

    @Test
    fun companyAccessReadsRecheckPermissionsMembershipCredentialsAndActivation() {
        for (mode in listOf("roles", "members", "grant", "membership", "company", "credentials")) {
            val f = fixture()
            val saved = grant(f)
            assertEquals(200, saved.statusCode(), saved.body())
            val path =
                when (mode) {
                    "roles" -> "${f.base}/role-templates"
                    "members" -> "${f.base}/members"
                    else -> "${f.base}/members/${f.target}"
                }
            val denied =
                waiting(
                    f.account,
                    {
                        when (mode) {
                            "membership" ->
                                database()
                                    .update(
                                        "update company_memberships set active=false where company_id=? and account_id=?",
                                        f.company,
                                        f.account,
                                    )
                            "company" ->
                                database()
                                    .update(
                                        "update companies set active=false where id=?",
                                        f.company,
                                    )
                            "credentials" ->
                                database()
                                    .update(
                                        "update accounts set security_version=security_version+1 where id=?",
                                        f.account,
                                    )
                            else ->
                                database()
                                    .update(
                                        "delete from membership_permissions where company_id=? and account_id=? and permission='identity.manage'",
                                        f.company,
                                        f.account,
                                    )
                        }
                    },
                ) {
                    get(f.browser, path)
                }
            assertEquals(
                if (mode == "credentials") 401 else 403,
                denied.statusCode(),
                "$mode ${denied.body()}",
            )
        }
    }

    @Test
    fun sensitiveGrantsUseOriginalAndLivePlatformAuthorityForDirectAndTemplatePermissions() {
        for (template in listOf(false, true)) {
            for (change in listOf("grant", "remove")) {
                val f = fixture()
                val role = if (template) UUID.randomUUID() else null
                if (role != null) {
                    val saved =
                        command(
                            f.browser,
                            "${f.base}/role-templates/$role",
                            roleBody(role, setOf("payroll.read")),
                            f.csrf,
                            UUID.randomUUID(),
                            "PUT",
                        )
                    assertEquals(200, saved.statusCode(), saved.body())
                }
                val body =
                    grantBody(
                        if (template) setOf("company.read")
                        else setOf("company.read", "payroll.read"),
                        role,
                    )
                val key = UUID.randomUUID()
                if (change == "grant")
                    database()
                        .update(
                            "delete from platform_permissions where account_id=? and permission='identity.manage'",
                            f.account,
                        )
                val denied =
                    waiting(
                        f.account,
                        {
                            if (change == "grant")
                                database()
                                    .update(
                                        "insert into platform_permissions(account_id,permission) values(?,'identity.manage')",
                                        f.account,
                                    )
                            else
                                database()
                                    .update(
                                        "delete from platform_permissions where account_id=? and permission='identity.manage'",
                                        f.account,
                                    )
                        },
                    ) {
                        grant(f, body, key)
                    }
                assertEquals(403, denied.statusCode(), "$template $change ${denied.body()}")
                assertEquals(
                    "platform_administrator_required",
                    json.readTree(denied.body())["code"].asString(),
                )
                assertEquals(
                    0,
                    database()
                        .queryForObject(
                            "select count(*) from company_memberships where company_id=? and account_id=?",
                            Int::class.java,
                            f.company,
                            f.target,
                        ),
                )
                if (change == "remove")
                    database()
                        .update(
                            "insert into platform_permissions(account_id,permission) values(?,'identity.manage')",
                            f.account,
                        )
                val fresh = grant(f, body, key)
                assertEquals(200, fresh.statusCode(), fresh.body())
            }
        }
    }

    @Test
    fun disablingTheTargetWhileWaitingPreventsANewMembershipGrant() {
        val f = fixture()
        val key = UUID.randomUUID()
        val body = grantBody()
        val denied =
            waiting(
                f.target,
                { database().update("update accounts set active=false where id=?", f.target) },
            ) {
                grant(f, body, key)
            }
        assertEquals(422, denied.statusCode(), denied.body())
        assertEquals("account_unavailable", json.readTree(denied.body())["code"].asString())
        database().update("update accounts set active=true where id=?", f.target)
        val saved = grant(f, body, key)
        assertEquals(200, saved.statusCode(), saved.body())
    }

    @Test
    fun memberGrantReadsShareGuardsWithCompanySettingsAndMembershipWriters() {
        val f = fixture()
        val saved = grant(f, grantBody(setOf("company.read", "identity.manage")))
        assertEquals(200, saved.statusCode(), saved.body())
        val second = client()
        login(second, "${f.target}@example.test")
        val path = "${f.base}/members/${f.target}"
        val barrier = AccountLockProbe.Barrier(f.account)
        probe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { get(f.browser, path) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val concurrent = get(second, path)
                assertEquals(200, concurrent.statusCode(), concurrent.body())
                for (key in listOf("company-state:${f.company}", "memberships:${f.company}")) {
                    assertEquals(
                        false,
                        database()
                            .queryForObject(
                                "select pg_try_advisory_xact_lock(hashtextextended(?,0))",
                                Boolean::class.java,
                                key,
                            ),
                    )
                }
                barrier.release.countDown()
                val first = pending.get(10, TimeUnit.SECONDS)
                assertEquals(200, first.statusCode(), first.body())
                assertEquals(concurrent.body(), first.body())
            } finally {
                barrier.release.countDown()
                probe.current.set(null)
            }
        }
        val changed = grant(f, grantBody(version = 0))
        assertEquals(200, changed.statusCode(), changed.body())
        val current = json.readTree(get(f.browser, path).body())
        assertEquals(1, current["member"]["version"].asLong())
        assertEquals(
            listOf("company.read"),
            current["directPermissions"].iterator().asSequence().map { it.asString() }.toList(),
        )
    }
}
