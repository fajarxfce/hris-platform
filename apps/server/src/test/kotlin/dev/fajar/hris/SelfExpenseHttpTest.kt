package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.usecases.ListSelfExpenseEmployments
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class SelfExpenseHttpTest : ClientPolicyApiFixture() {
    @Autowired private lateinit var accounts: AccountLockProbe
    @Autowired private lateinit var list: ListSelfExpenseEmployments
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner

    private data class Member(val id: UUID, val browser: HttpClient, val csrf: String)

    private fun member(company: UUID, permission: String = "expenses.self.manage"): Member {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Expense self fixture',password_hash from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                id,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                company,
                id,
                permission,
            )
        val browser = client()
        return Member(id, browser, login(browser, "$id@example.test"))
    }

    @AfterEach
    fun releaseAccountProbe() {
        accounts.current.getAndSet(null)?.release?.countDown()
    }

    @Test
    fun directoryUsesCompanyDateAndReturnsOnlyOwnedEmploymentReferences() {
        val f = fixture()
        val company = company(f.browser, f.csrf, "Pacific/Honolulu")
        val owner = member(company, "expenses.read")
        val own = employee(f.browser, f.csrf, company, account = owner.id)
        val other = employee(f.browser, f.csrf, company)
        val path = "/api/v1/companies/$company/expenses/me/employments"
        val response = get(owner.browser, path)
        assertEquals(200, response.statusCode(), response.body())
        val body = json.readTree(response.body())
        assertEquals(owner.id.toString(), body["accountId"].asString())
        assertEquals(company.toString(), body["companyId"].asString())
        assertEquals("2026-09-30", body["asOf"].asString())
        assertEquals("Pacific/Honolulu", body["timezone"].asString())
        val items = body["employments"]["items"]
        assertEquals(1, items.size())
        assertEquals(own.toString(), items[0]["id"].asString())
        assertEquals(
            setOf("id", "employeeNumber", "status", "version"),
            items[0].properties().map { it.key }.toSet(),
        )
        assertTrue(body["employments"]["nextCursor"].isNull)
        assertFalse(response.body().contains(other.toString()))
        assertFalse(response.body().contains("Example employee"))
        assertEquals(0, json.readTree(get(f.browser, path).body())["employments"]["items"].size())
        for (query in listOf("limit=0", "limit=51", "after=${"E".repeat(33)}", "after=")) {
            failure(get(owner.browser, "$path?$query"), 422, "invalid_page")
        }
        assertEquals(200, get(owner.browser, "$path?limit=1").statusCode())
        val number = items[0]["employeeNumber"].asString()
        val tail = get(owner.browser, "$path?after=$number&limit=1")
        assertEquals(200, tail.statusCode(), tail.body())
        assertEquals(0, json.readTree(tail.body())["employments"]["items"].size())
        assertEquals(403, get(owner.browser, "${f.root}/expenses/me/employments").statusCode())
        val profileOnly = member(company, "people.self.read")
        failure(get(profileOnly.browser, path), 403, "access_denied")
    }

    @Test
    fun expensesHaveIndependentModuleAdmissionAndNativeBuildRequirements() {
        val f = fixture()
        val owner = member(f.company)
        val own = employee(f.browser, f.csrf, f.company, account = owner.id)
        val path = "${f.root}/expenses/me/employments"
        assertEquals(
            200,
            save(f, changes = mapOf("disabledModules" to listOf("PEOPLE", "WORKFORCE")))
                .statusCode(),
        )
        failure(get(owner.browser, "${f.root}/me/employments"), 403, "company_module_disabled")
        assertEquals(200, get(owner.browser, path).statusCode())
        assertEquals(200, save(f, 0, mapOf("disabledModules" to listOf("EXPENSES"))).statusCode())
        val blocked = get(owner.browser, path)
        failure(blocked, 403, "company_module_disabled")
        assertEquals("EXPENSES", json.readTree(blocked.body())["parameters"]["module"].asString())
        assertEquals(
            200,
            save(f, 1, mapOf("minimumBuilds" to mapOf("android" to 12, "ios" to 0, "web" to 0)))
                .statusCode(),
        )
        val exchange =
            command(
                owner.browser,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"Expense fixture"}""",
                owner.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, exchange.statusCode(), exchange.body())
        val auth =
            "Authorization" to "Bearer ${json.readTree(exchange.body())["accessToken"].asString()}"
        failure(read(f, path, listOf(auth)), 403, "client_version_required")
        failure(
            read(
                f,
                path,
                listOf(auth, "X-HRIS-Client-Platform" to "ANDROID", "X-HRIS-Client-Build" to "11"),
            ),
            403,
            "client_update_required",
        )
        val accepted =
            read(
                f,
                path,
                listOf(auth, "X-HRIS-Client-Platform" to "ANDROID", "X-HRIS-Client-Build" to "12"),
            )
        assertEquals(200, accepted.statusCode(), accepted.body())
        assertEquals(
            own.toString(),
            json.readTree(accepted.body())["employments"]["items"][0]["id"].asString(),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "credential", "membership", "replacement-grant"])
    fun authorityIsCheckedAgainAfterAdmissionAndThePendingResourceGuard(change: String) {
        val f = fixture()
        val owner = member(f.company)
        employee(f.browser, f.csrf, f.company, account = owner.id)
        val barrier = AccountLockProbe.Barrier(owner.id, occurrence = 2)
        accounts.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> {
                    get(owner.browser, "${f.root}/expenses/me/employments")
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                when (change) {
                    "credential" ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                owner.id,
                            )
                    "membership" ->
                        database()
                            .update(
                                "update company_memberships set active=false where company_id=? and account_id=?",
                                f.company,
                                owner.id,
                            )
                    else -> {
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=?",
                                f.company,
                                owner.id,
                            )
                        if (change == "replacement-grant")
                            database()
                                .update(
                                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'expenses.read')",
                                    f.company,
                                    owner.id,
                                )
                    }
                }
                barrier.release.countDown()
                val response = pending.get(10, TimeUnit.SECONDS)
                assertEquals(
                    if (change == "credential") 401 else 403,
                    response.statusCode(),
                    response.body(),
                )
                assertFalse(response.body().contains("employeeNumber"))
            } finally {
                barrier.release.countDown()
            }
        }
    }

    @Test
    fun cancellingDiscoveryReleasesPeopleAndAccessGuardsForTheNextRequest() {
        val f = fixture()
        val owner = member(f.company)
        val actor =
            Actor(
                owner.id,
                f.company,
                setOf("expenses.self.manage"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val barrier = AccountLockProbe.Barrier(owner.id)
        accounts.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { list.execute(actor, null, 20) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
            } finally {
                accounts.current.set(null)
                barrier.release.countDown()
            }
        }
        // A writer needs the exclusive people guard held by the interrupted read.
        employee(f.browser, f.csrf, f.company, account = owner.id)
        assertEquals(200, get(owner.browser, "${f.root}/expenses/me/employments").statusCode())
    }

    @Test
    fun mfaExpiryWhileWaitingForAccessCannotReleaseEmploymentReferences() {
        val security = IdentitySecurityPolicy()
        val protectedRead =
            ListSelfExpenseEmployments(
                people,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
            )
        val f = fixture()
        val owner = member(f.company)
        employee(f.browser, f.csrf, f.company, account = owner.id)
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                owner.id,
            )
        val actor =
            Actor(
                owner.id,
                f.company,
                setOf("expenses.self.manage"),
                clock.instant(),
                UUID.randomUUID(),
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1),
                credentialVersion = 0,
            )
        val barrier = AccountLockProbe.Barrier(owner.id)
        accounts.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { protectedRead.execute(actor, null, 20) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(2))
                barrier.release.countDown()
                val result = pending.get(10, TimeUnit.SECONDS)
                assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code)
            } finally {
                accounts.current.set(null)
                barrier.release.countDown()
            }
        }
        val renewed = protectedRead.execute(actor.copy(mfaVerifiedAt = clock.instant()), null, 20)
        assertTrue(renewed is Result.Success, renewed.toString())
        assertEquals(1, (renewed as Result.Success).value.employments.items.size)
    }
}
