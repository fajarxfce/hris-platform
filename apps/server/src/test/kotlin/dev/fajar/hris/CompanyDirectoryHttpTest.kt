package dev.fajar.hris

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.core.domain.TransactionRunner
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.*
import dev.fajar.hris.people.domain.usecases.ListCompanyDirectory
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
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class CompanyDirectoryHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var accounts: AccountLockProbe
    @Autowired private lateinit var context: ApplicationContext

    private data class Member(val id: UUID, val client: HttpClient)

    @AfterEach
    fun releaseProbe() {
        accounts.current.getAndSet(null)?.release?.countDown()
    }

    private fun member(company: UUID, permission: String = "people.directory.read"): Member {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Directory fixture',password_hash from accounts where email='admin@example.test'",
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
        val client = client()
        login(client, "$id@example.test")
        return Member(id, client)
    }

    @Test
    fun directoryReturnsMinimalCurrentColleaguesAndAssignmentLabelsWithoutProfileAccess() {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val member = member(company)
        val active = employee(admin, csrf, company)
        val suspended = employee(admin, csrf, company)
        val future = employee(admin, csrf, company, start = "2027-01-01")
        val department = UUID.randomUUID()
        val position = UUID.randomUUID()
        for ((id, kind, name) in
            listOf(
                Triple(department, "DEPARTMENT", "Engineering"),
                Triple(position, "POSITION", "Mobile engineer"),
            )) {
            val created =
                command(
                    admin,
                    "/api/v1/companies/$company/organization-units/$id",
                    json.writeValueAsString(
                        mapOf("code" to kind, "kind" to kind, "name" to name, "active" to true)
                    ),
                    csrf,
                    UUID.randomUUID(),
                    "PUT",
                )
            assertEquals(200, created.statusCode(), created.body())
        }
        assertEquals(
            200,
            revise(
                    admin,
                    csrf,
                    company,
                    active,
                    0,
                    terms("2026-09-01") +
                        mapOf("departmentId" to department, "positionId" to position),
                )
                .statusCode(),
        )
        assertEquals(
            200,
            revise(admin, csrf, company, suspended, 0, terms("2026-09-01", status = "SUSPENDED"))
                .statusCode(),
        )
        val result = get(member.client, "/api/v1/companies/$company/employees/directory")
        assertEquals(200, result.statusCode(), result.body())
        val body = json.readTree(result.body())
        assertEquals("2026-10-01", body["asOf"].asString())
        assertEquals("Asia/Jakarta", body["timezone"].asString())
        val items = body["employees"]["items"]
        assertEquals(1, items.size())
        assertEquals(active.toString(), items[0]["id"].asString())
        assertEquals("Engineering", items[0]["department"].asString())
        assertEquals("Mobile engineer", items[0]["position"].asString())
        assertEquals(5, items[0].size())
        for (privateField in
            listOf("birthDate", "nationality", "email", "accountId", "contract", "salary")) {
            assertFalse(result.body().contains(privateField), privateField)
        }
        assertFalse(result.body().contains(suspended.toString()))
        assertFalse(result.body().contains(future.toString()))
        assertEquals(
            404,
            get(member.client, "/api/v1/companies/$company/employees/$active/profile").statusCode(),
        )
        assertEquals(
            403,
            get(member.client, "/api/v1/companies/$company/employees?asOf=2026-10-01").statusCode(),
        )
        val foreign = company(admin, csrf)
        employee(admin, csrf, foreign)
        assertEquals(
            403,
            get(member.client, "/api/v1/companies/$foreign/employees/directory").statusCode(),
        )
        val selfOnly = member(company, "people.self.read")
        assertEquals(
            403,
            get(selfOnly.client, "/api/v1/companies/$company/employees/directory").statusCode(),
        )
    }

    @Test
    fun paginationAndLiteralSearchStayBoundedAndUseTheCompanyDate() {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf, "Pacific/Honolulu")
        val member = member(company)
        val expected = List(3) { employee(admin, csrf, company).toString() }.toSet()
        val path = "/api/v1/companies/$company/employees/directory"
        val seen = mutableSetOf<String>()
        var cursor: String? = null
        repeat(3) { index ->
            val response =
                get(member.client, "$path?limit=1${cursor?.let { "&after=$it" }.orEmpty()}")
            assertEquals(200, response.statusCode(), response.body())
            val body = json.readTree(response.body())
            assertEquals("2026-09-30", body["asOf"].asString())
            val page = body["employees"]
            assertEquals(1, page["items"].size())
            assertTrue(seen.add(page["items"][0]["id"].asString()))
            val next = page["nextCursor"].takeUnless { it.isNull }?.asString()
            if (index < 2) assertNotNull(next) else assertNull(next)
            assertNotEquals(cursor, next ?: "finished")
            cursor = next
        }
        assertEquals(expected, seen)
        for (literal in listOf("%25", "_", "not-an-employee")) {
            val empty = get(member.client, "$path?query=$literal")
            assertEquals(200, empty.statusCode(), empty.body())
            assertEquals(0, json.readTree(empty.body())["employees"]["items"].size())
        }
        for (invalid in
            listOf(
                "limit=0",
                "limit=51",
                "query=${"x".repeat(121)}",
                "after=",
                "after=${"E".repeat(33)}",
            )) {
            val result = get(member.client, "$path?$invalid")
            assertEquals(422, result.statusCode(), result.body())
            assertEquals("invalid_page", json.readTree(result.body())["code"].asString())
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "credential", "membership"])
    fun directoryRechecksAccessAfterWaiting(change: String) {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val member = member(company)
        employee(admin, csrf, company)
        val barrier = AccountLockProbe.Barrier(member.id)
        accounts.current.set(barrier)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<HttpResponse<String>> {
                    get(member.client, "/api/v1/companies/$company/employees/directory")
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                when (change) {
                    "credential" ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                member.id,
                            )
                    "membership" ->
                        database()
                            .update(
                                "update company_memberships set active=false where company_id=? and account_id=?",
                                company,
                                member.id,
                            )
                    else ->
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=?",
                                company,
                                member.id,
                            )
                }
                barrier.release.countDown()
                val denied = pending.get(10, TimeUnit.SECONDS)
                assertEquals(
                    if (change == "credential") 401 else 403,
                    denied.statusCode(),
                    denied.body(),
                )
                assertFalse(denied.body().contains("Example employee"))
            } finally {
                barrier.release.countDown()
            }
        }
    }

    @Test
    fun directoryRejectsMfaThatExpiresDuringAccessAcquisition() {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val member = member(company, "people.read")
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                member.id,
            )
        employee(admin, csrf, company)
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val target =
            ListCompanyDirectory(
                context.getBean(CompanyDirectoryRepository::class.java),
                context.getBean(PeopleRepository::class.java),
                context.getBean(CompanyRepository::class.java),
                context.getBean(MembershipRepository::class.java),
                context.getBean(IdentityRepository::class.java),
                context.getBean(TransactionRunner::class.java),
                security,
                clock,
            )
        val actor =
            Actor(
                member.id,
                company,
                setOf("people.read"),
                clock.instant(),
                UUID.randomUUID(),
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1),
                credentialVersion = 0,
            )
        val barrier = AccountLockProbe.Barrier(member.id)
        accounts.current.set(barrier)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending = executor.submit<Result<*>> { target.execute(actor, "", null, 20) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(2))
                barrier.release.countDown()
                assertEquals(
                    "mfa_required",
                    (pending.get(10, TimeUnit.SECONDS) as? Result.Failed)?.failure?.code,
                )
            } finally {
                barrier.release.countDown()
            }
        }
        assertTrue(
            target.execute(actor.copy(mfaVerifiedAt = clock.instant()), "", null, 20)
                is Result.Success
        )
    }
}
