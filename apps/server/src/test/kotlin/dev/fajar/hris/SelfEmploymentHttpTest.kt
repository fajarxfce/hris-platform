package dev.fajar.hris

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
class SelfEmploymentHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var accounts: AccountLockProbe

    private data class Member(val id: UUID, val client: HttpClient)

    @AfterEach
    fun releaseProbe() {
        accounts.current.getAndSet(null)?.release?.countDown()
    }

    private fun member(company: UUID, permission: String = "people.self.read"): Member {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Self profile fixture',password_hash from accounts where email='admin@example.test'",
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
    fun discoveryUsesCompanyDateAndOnlyTheLinkedPersonEvenWithDirectoryAccess() {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf, "Pacific/Honolulu")
        val person = member(company, "people.read")
        val own = employee(admin, csrf, company, account = person.id)
        val other = employee(admin, csrf, company)
        val path = "/api/v1/companies/$company/me/employments"
        val result = get(person.client, path)
        assertEquals(200, result.statusCode(), result.body())
        val body = json.readTree(result.body())
        assertEquals("2026-09-30", body["asOf"].asString())
        assertEquals("Pacific/Honolulu", body["timezone"].asString())
        assertEquals(1, body["employments"]["items"].size())
        assertEquals(own.toString(), body["employments"]["items"][0]["id"].asString())
        assertTrue(body["employments"]["nextCursor"].isNull)
        assertFalse(result.body().contains(other.toString()))
        assertFalse(result.body().contains("nationality"))
        assertFalse(result.body().contains("birthDate"))
        val unlinked = get(admin, path)
        assertEquals(200, unlinked.statusCode(), unlinked.body())
        assertEquals(0, json.readTree(unlinked.body())["employments"]["items"].size())
        for (query in listOf("limit=0", "limit=51", "after=${"E".repeat(33)}")) {
            val invalid = get(person.client, "$path?$query")
            assertEquals(422, invalid.statusCode(), invalid.body())
            assertEquals("invalid_page", json.readTree(invalid.body())["code"].asString())
        }
    }

    @Test
    fun selfReadIncludesAssignmentLabelsButDoesNotExpandOtherPeopleOrCompanyAccess() {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val member = member(company)
        val manager = employee(admin, csrf, company)
        val own = employee(admin, csrf, company, manager = manager, account = member.id)
        val base = "/api/v1/companies/$company"
        val details = get(member.client, "$base/employees/$own/employment?asOf=2026-10-01")
        assertEquals(200, details.statusCode(), details.body())
        val body = json.readTree(details.body())
        assertEquals(own.toString(), body["employee"]["id"].asString())
        assertEquals(manager.toString(), body["manager"]["id"].asString())
        assertEquals("Example employee", body["manager"]["legalName"].asString())
        assertFalse(body["manager"].has("accountId"))
        assertEquals(200, get(member.client, "$base/employees/$own/profile").statusCode())
        assertEquals(
            404,
            get(member.client, "$base/employees/$manager/employment?asOf=2026-10-01").statusCode(),
        )
        val foreign = company(admin, csrf)
        assertEquals(
            403,
            get(member.client, "/api/v1/companies/$foreign/me/employments").statusCode(),
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=?",
                company,
                member.id,
            )
        assertEquals(403, get(member.client, "$base/me/employments").statusCode())
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "credential", "membership"])
    fun discoveryRevalidatesAuthorityAfterPendingAccessAcquisition(change: String) {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val member = member(company)
        employee(admin, csrf, company, account = member.id)
        val barrier = AccountLockProbe.Barrier(member.id)
        accounts.current.set(barrier)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<HttpResponse<String>> {
                    get(member.client, "/api/v1/companies/$company/me/employments")
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
}
