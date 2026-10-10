package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.EmploymentRevisionDetails
import dev.fajar.hris.people.domain.usecases.GetEmploymentRevision
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class EmploymentRevisionHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var getRevision: GetEmploymentRevision
    @Autowired private lateinit var accounts: AccountLockProbe

    private data class Fixture(
        val admin: HttpClient,
        val csrf: String,
        val company: UUID,
        val employee: UUID,
        val reader: HttpClient,
        val account: UUID,
    ) {
        val path
            get() = "/api/v1/companies/$company/employees/$employee/revisions"
    }

    @AfterEach
    fun releaseProbe() {
        accounts.current.getAndSet(null)?.release?.countDown()
    }

    private fun fixture(manage: Boolean = true): Fixture {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val employee = employee(admin, csrf, company)
        val revision =
            revise(admin, csrf, company, employee, 0, terms("2026-11-01", status = "SUSPENDED"))
        assertEquals(200, revision.statusCode(), revision.body())
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Revision reader',password_hash from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
        for (permission in
            if (manage) listOf("people.read", "people.manage")
            else listOf("people.read")) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                company,
                account,
                permission,
            )
        val reader = client()
        login(reader, "$account@example.test")
        return Fixture(admin, csrf, company, employee, reader, account)
    }

    @Test
    fun revisionDetailsShareTheCurrentVersionAndRetainCancellationEvidence() {
        val f = fixture()
        val scheduled = get(f.reader, "${f.path}/1")
        assertEquals(200, scheduled.statusCode(), scheduled.body())
        val body = json.readTree(scheduled.body())
        assertEquals(f.employee.toString(), body["employeeId"].asString())
        assertEquals(1, body["version"].asInt())
        assertEquals("2026-10-01", body["companyDate"].asString())
        assertEquals("SUSPENDED", body["revision"]["terms"]["status"].asString())
        assertEquals(1, body["revision"]["revision"].asInt())
        assertTrue(body["canCancel"].asBoolean())
        assertFalse(body.has("person"))
        val original = get(f.reader, "${f.path}/0")
        assertEquals(200, original.statusCode(), original.body())
        assertFalse(json.readTree(original.body())["canCancel"].asBoolean())
        val cancelled = cancellation(f.admin, f.csrf, f.company, f.employee, 1, 1)
        assertEquals(200, cancelled.statusCode(), cancelled.body())
        val retained = get(f.reader, "${f.path}/1")
        assertEquals(200, retained.statusCode(), retained.body())
        val evidence = json.readTree(retained.body())
        assertEquals(2, evidence["version"].asInt())
        assertEquals(
            "Cancelled scheduled change",
            evidence["revision"]["cancellation"]["reason"].asString(),
        )
        assertFalse(evidence["canCancel"].asBoolean())
    }

    @Test
    fun cancellationAvailabilityUsesTheOwningCompanyDateAndDoesNotGrantManagementToReaders() {
        val f = fixture(manage = false)
        assertFalse(json.readTree(get(f.reader, "${f.path}/1").body())["canCancel"].asBoolean())
        clock.set(Instant.parse("2026-10-31T16:59:59Z"))
        val before = get(f.admin, "${f.path}/1")
        assertEquals(200, before.statusCode(), before.body())
        assertTrue(json.readTree(before.body())["canCancel"].asBoolean())
        clock.set(Instant.parse("2026-10-31T17:00:00Z"))
        val effective = get(f.admin, "${f.path}/1")
        assertEquals(200, effective.statusCode(), effective.body())
        assertEquals("2026-11-01", json.readTree(effective.body())["companyDate"].asString())
        assertFalse(json.readTree(effective.body())["canCancel"].asBoolean())
    }

    @Test
    fun fullReadAndCompanyScopeProtectDirectRevisionLinks() {
        val f = fixture()
        assertEquals(422, get(f.reader, "${f.path}/-1").statusCode())
        assertEquals(404, get(f.reader, "${f.path}/99").statusCode())
        val other = company(f.admin, f.csrf)
        val otherEmployee = employee(f.admin, f.csrf, other)
        assertEquals(
            404,
            get(f.reader, f.path.replace(f.employee.toString(), otherEmployee.toString()) + "/0")
                .statusCode(),
        )
        assertEquals(
            403,
            get(f.reader, f.path.replace(f.company.toString(), other.toString()) + "/1")
                .statusCode(),
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='people.read'",
                f.company,
                f.account,
            )
        for (permission in
            listOf("people.self.read", "people.team.read", "people.profile.read")) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                f.company,
                f.account,
                permission,
            )
        val denied = get(f.reader, "${f.path}/1")
        assertEquals(403, denied.statusCode(), denied.body())
        assertFalse(denied.body().contains("Scheduled change"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["read", "credential", "remove-management", "add-management"])
    fun pendingRevisionReadsUseTheOriginalAndLiveScope(change: String) {
        val f = fixture(manage = change != "add-management")
        val gate = AccountLockProbe.Barrier(f.account)
        accounts.current.set(gate)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending = executor.submit<HttpResponse<String>> { get(f.reader, "${f.path}/1") }
            try {
                assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
                when (change) {
                    "credential" ->
                        database()
                            .update(
                                "update accounts set security_version=security_version+1 where id=?",
                                f.account,
                            )
                    "add-management" ->
                        database()
                            .update(
                                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'people.manage')",
                                f.company,
                                f.account,
                            )
                    else ->
                        database()
                            .update(
                                "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                                f.company,
                                f.account,
                                if (change == "read") "people.read" else "people.manage",
                            )
                }
                gate.release.countDown()
                val response = pending.get(10, TimeUnit.SECONDS)
                assertEquals(
                    when (change) {
                        "credential" -> 401
                        "read" -> 403
                        else -> 200
                    },
                    response.statusCode(),
                    response.body(),
                )
                if (response.statusCode() == 200)
                    assertFalse(json.readTree(response.body())["canCancel"].asBoolean())
                else assertFalse(response.body().contains("Scheduled change"))
            } finally {
                gate.release.countDown()
            }
        }
    }

    @Test
    fun cancellingAPendingRevisionReadReleasesItsGuardsBeforeAWriterContinues() {
        val f = fixture()
        val actor =
            Actor(
                f.account,
                f.company,
                setOf("people.read", "people.manage"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val gate = AccountLockProbe.Barrier(f.account)
        val finished = CountDownLatch(1)
        accounts.current.set(gate)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<Result<EmploymentRevisionDetails>> {
                    try {
                        getRevision.execute(actor, f.employee, 1)
                    } finally {
                        finished.countDown()
                    }
                }
            try {
                assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                assertThrows(CancellationException::class.java) { pending.get(5, TimeUnit.SECONDS) }
            } finally {
                gate.release.countDown()
                accounts.current.set(null)
            }
        }
        val cancelled = cancellation(f.admin, f.csrf, f.company, f.employee, 1, 1)
        assertEquals(200, cancelled.statusCode(), cancelled.body())
        assertEquals(2, json.readTree(get(f.reader, "${f.path}/1").body())["version"].asInt())
    }
}
