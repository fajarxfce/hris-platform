package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.usecases.ListSelfWorkforceEmployments
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class, OvertimePlanningProbeConfiguration::class)
class SelfWorkforceHttpTest : ClientPolicyApiFixture() {
    @Autowired private lateinit var accounts: AccountLockProbe
    @Autowired private lateinit var list: ListSelfWorkforceEmployments
    @Autowired private lateinit var acquisition: OvertimePlanningProbe
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner

    private data class Member(val id: UUID, val browser: HttpClient, val csrf: String)

    private fun member(company: UUID, permission: String = "attendance.self.record"): Member {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Workforce self fixture',password_hash from accounts where email='admin@example.test'",
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
        acquisition.current.getAndSet(null)?.release?.countDown()
    }

    @Test
    fun directoryUsesCompanyDateAndReturnsOnlyOwnedEmploymentReferences() {
        val f = fixture()
        val company = company(f.browser, f.csrf, "Pacific/Honolulu")
        val owner = member(company, "workforce.read")
        val own = employee(f.browser, f.csrf, company, account = owner.id)
        val other = employee(f.browser, f.csrf, company)
        val path = "/api/v1/companies/$company/workforce/me/employments"
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
        assertEquals(403, get(owner.browser, "${f.root}/workforce/me/employments").statusCode())
        val profileOnly = member(company, "people.self.read")
        failure(get(profileOnly.browser, path), 403, "access_denied")
    }

    @ParameterizedTest
    @ValueSource(strings = ["attendance.self.record", "overtime.self.manage"])
    fun workforceHasIndependentModuleAdmissionAndNativeBuildRequirements(permission: String) {
        val f = fixture()
        val owner = member(f.company, permission)
        val own = employee(f.browser, f.csrf, f.company, account = owner.id)
        val path = "${f.root}/workforce/me/employments"
        assertEquals(
            200,
            save(f, changes = mapOf("disabledModules" to listOf("PEOPLE"))).statusCode(),
        )
        failure(get(owner.browser, "${f.root}/me/employments"), 403, "company_module_disabled")
        assertEquals(200, get(owner.browser, path).statusCode())
        val directory = "${f.root}/workforce/employees?asOf=2026-10-01"
        val review =
            "${f.root}/workforce/employees/$own/calendar/review?from=2026-10-01&until=2026-10-07"
        assertEquals(200, get(f.browser, directory).statusCode())
        assertEquals(200, get(f.browser, review).statusCode())
        assertEquals(
            if (permission == "attendance.self.record") 200 else 404,
            get(
                    owner.browser,
                    "${f.root}/workforce/employees/$own/calendar?from=2026-10-01&until=2026-10-07",
                )
                .statusCode(),
        )
        assertEquals(200, save(f, 0, mapOf("disabledModules" to listOf("WORKFORCE"))).statusCode())
        val blocked = get(owner.browser, path)
        failure(get(f.browser, directory), 403, "company_module_disabled")
        failure(get(f.browser, review), 403, "company_module_disabled")
        failure(blocked, 403, "company_module_disabled")
        assertEquals("WORKFORCE", json.readTree(blocked.body())["parameters"]["module"].asString())
        assertEquals(
            200,
            save(f, 1, mapOf("minimumBuilds" to mapOf("android" to 12, "ios" to 0, "web" to 0)))
                .statusCode(),
        )
        val exchange =
            command(
                owner.browser,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"Workforce fixture"}""",
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
                    get(owner.browser, "${f.root}/workforce/me/employments")
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
                                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,'workforce.read')",
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
                setOf("attendance.self.record"),
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
        assertEquals(200, get(owner.browser, "${f.root}/workforce/me/employments").statusCode())
    }

    @ParameterizedTest
    @ValueSource(strings = ["overtime.self.manage", "overtime.read", "overtime.manage"])
    fun overtimeDiscoveryGrantsOnlyTheOwnersMinimalReferences(permission: String) {
        val f = fixture()
        val owner = member(f.company, permission)
        val own = employee(f.browser, f.csrf, f.company, account = owner.id)
        val other = employee(f.browser, f.csrf, f.company)
        val response = get(owner.browser, "${f.root}/workforce/me/employments")
        assertEquals(200, response.statusCode(), response.body())
        val body = json.readTree(response.body())
        val items = body["employments"]["items"]
        assertEquals(1, items.size())
        assertEquals(own.toString(), items[0]["id"].asString())
        assertEquals(
            setOf("id", "employeeNumber", "status", "version"),
            items[0].properties().map { it.key }.toSet(),
        )
        assertFalse(response.body().contains(other.toString()))
        val calendar =
            "${f.root}/workforce/employees/$own/calendar?from=2026-10-01&until=2026-10-07"
        failure(get(owner.browser, calendar), 404, "employee_not_found")
        failure(get(owner.browser, "${f.root}/me/employments"), 403, "access_denied")
        val requests = "${f.root}/workforce/overtime?from=2026-10-01&until=2026-10-07"
        assertEquals(200, get(owner.browser, "$requests&employeeId=$own").statusCode())
        if (permission == "overtime.self.manage") {
            failure(get(owner.browser, "$requests&employeeId=$other"), 404, "employee_not_found")
            failure(get(owner.browser, requests), 403, "employee_scope_required")
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun lateExpiryAndCancellationDoNotReleaseAnAcquiredDirectory(cancel: Boolean) {
        val f = fixture()
        val reader =
            ListSelfWorkforceEmployments(
                people,
                companies,
                members,
                identities,
                transactions,
                IdentitySecurityPolicy(),
                clock,
            )
        val owner = member(f.company, "overtime.manage")
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
                setOf("overtime.manage"),
                clock.instant(),
                UUID.randomUUID(),
                mfaVerifiedAt =
                    clock.instant().minus(IdentitySecurityPolicy().maximumMfaAge).plusSeconds(1),
                credentialVersion = 0,
            )
        val barrier = OvertimePlanningProbe.Barrier(f.company, review = false)
        val finished = CountDownLatch(1)
        acquisition.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    try {
                        reader.execute(actor, null, 20)
                    } finally {
                        finished.countDown()
                    }
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                if (cancel) {
                    assertTrue(pending.cancel(true))
                    assertTrue(finished.await(5, TimeUnit.SECONDS))
                } else {
                    clock.set(clock.instant().plusSeconds(2))
                    barrier.release.countDown()
                    assertEquals(
                        "mfa_required",
                        (pending.get(10, TimeUnit.SECONDS) as Result.Failed).failure.code,
                    )
                }
            } finally {
                barrier.release.countDown()
                acquisition.current.set(null)
            }
            val recovered =
                pool.submit<Result<*>> {
                    reader.execute(actor.copy(mfaVerifiedAt = clock.instant()), null, 20)
                }
            assertTrue(recovered.get(10, TimeUnit.SECONDS) is Result.Success)
        }
        // A writer proves the interrupted reader released its shared people guard.
        employee(f.browser, f.csrf, f.company)
    }
}
