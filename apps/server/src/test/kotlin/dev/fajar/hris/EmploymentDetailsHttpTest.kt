package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.EmploymentDetails
import dev.fajar.hris.people.domain.usecases.GetEmploymentDetails
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class, OrganizationReadProbeConfiguration::class)
class EmploymentDetailsHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var details: GetEmploymentDetails
    @Autowired private lateinit var accounts: AccountLockProbe
    @Autowired private lateinit var references: OrganizationReadProbe

    private data class Fixture(
        val admin: HttpClient,
        val csrf: String,
        val company: UUID,
        val employee: UUID,
        val manager: UUID,
        val branch: UUID,
        val department: UUID,
        val reader: HttpClient,
        val account: UUID,
    ) {
        val path
            get() = "/api/v1/companies/$company/employees/$employee/employment?asOf=2026-10-01"
    }

    @AfterEach
    fun releaseProbes() {
        accounts.current.getAndSet(null)?.release?.countDown()
        references.current.getAndSet(null)?.release?.countDown()
    }

    private fun fixture(): Fixture {
        val admin = client()
        val csrf = login(admin)
        val company = company(admin, csrf)
        val branch = UUID.randomUUID()
        val department = UUID.randomUUID()
        for ((id, values) in
            listOf(
                branch to
                    mapOf(
                        "code" to "HQ",
                        "name" to "Main office",
                        "kind" to "BRANCH",
                        "timezone" to "Asia/Jakarta",
                    ),
                department to
                    mapOf("code" to "HR", "name" to "Human resources", "kind" to "DEPARTMENT"),
            )) {
            val created =
                command(
                    admin,
                    "/api/v1/companies/$company/organization-units/$id",
                    json.writeValueAsString(values + mapOf("active" to true)),
                    csrf,
                    UUID.randomUUID(),
                    "PUT",
                )
            assertEquals(200, created.statusCode(), created.body())
        }
        val manager = employee(admin, csrf, company)
        val employee = UUID.randomUUID()
        val created =
            command(
                admin,
                "/api/v1/companies/$company/employees",
                json.writeValueAsString(
                    mapOf(
                        "id" to employee,
                        "employeeNumber" to "E${employee.toString().take(8)}",
                        "person" to
                            mapOf(
                                "id" to UUID.randomUUID(),
                                "legalName" to "Employment detail fixture",
                                "nationality" to "ID",
                                "birthDate" to "1990-03-04",
                            ),
                        "terms" to
                            (terms(manager = manager) +
                                mapOf("branchId" to branch, "departmentId" to department)),
                        "reason" to "Onboarding",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, created.statusCode(), created.body())
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Employment reader',password_hash from accounts where email='admin@example.test'",
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
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'people.read')",
                company,
                account,
            )
        val reader = client()
        login(reader, "$account@example.test")
        return Fixture(admin, csrf, company, employee, manager, branch, department, reader, account)
    }

    @Test
    fun detailsRetainEffectiveEmploymentAndCurrentReferenceLabelsWithoutSensitiveProfiles() {
        val f = fixture()
        val scheduled =
            revise(
                f.admin,
                f.csrf,
                f.company,
                f.employee,
                0,
                terms("2026-11-01", f.manager, "SUSPENDED") +
                    mapOf("branchId" to f.branch, "departmentId" to f.department),
            )
        assertEquals(200, scheduled.statusCode(), scheduled.body())
        val renamed =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/organization-units/${f.branch}",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to 0,
                        "code" to "HQ",
                        "name" to "Renamed office",
                        "kind" to "BRANCH",
                        "timezone" to "Asia/Jakarta",
                        "active" to false,
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, renamed.statusCode(), renamed.body())
        val response = get(f.reader, f.path)
        assertEquals(200, response.statusCode(), response.body())
        val body = json.readTree(response.body())
        assertEquals("2026-10-01", body["asOf"].asString())
        assertEquals(1, body["employee"]["version"].asInt())
        assertEquals(0, body["employee"]["appliedRevision"].asInt())
        assertEquals("ACTIVE", body["employee"]["terms"]["status"].asString())
        assertEquals(f.branch.toString(), body["branch"]["id"].asString())
        assertEquals("Renamed office", body["branch"]["name"].asString())
        assertFalse(body["branch"]["active"].asBoolean())
        assertEquals("Human resources", body["department"]["name"].asString())
        assertTrue(body["position"].isNull)
        assertTrue(body["costCenter"].isNull)
        assertEquals(f.manager.toString(), body["manager"]["id"].asString())
        assertEquals("Example employee", body["manager"]["legalName"].asString())
        assertTrue(body["manager"]["working"].asBoolean())
        assertFalse(response.body().contains("birthDate"))
        assertFalse(response.body().contains("nationality"))
        assertFalse(body["manager"].has("email"))
        assertFalse(body["manager"].has("accountId"))
        val later = get(f.reader, f.path.replace("2026-10-01", "2026-11-01"))
        assertEquals(200, later.statusCode(), later.body())
        assertEquals(
            "SUSPENDED",
            json.readTree(later.body())["employee"]["terms"]["status"].asString(),
        )
    }

    @Test
    fun companyWideReadIsRequiredAndForeignEmployeesAndScopesCannotBeResolved() {
        val f = fixture()
        val otherCompany = company(f.admin, f.csrf)
        val otherEmployee = employee(f.admin, f.csrf, otherCompany)
        val foreign = get(f.reader, f.path.replace(f.employee.toString(), otherEmployee.toString()))
        assertEquals(404, foreign.statusCode(), foreign.body())
        val foreignScope =
            get(f.reader, f.path.replace(f.company.toString(), otherCompany.toString()))
        assertEquals(403, foreignScope.statusCode(), foreignScope.body())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=?",
                f.company,
                f.account,
            )
        for (permission in
            listOf(
                "people.team.read",
                "people.self.read",
                "people.manage",
                "people.profile.read",
            )) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                f.company,
                f.account,
                permission,
            )
        val denied = get(f.reader, f.path)
        assertEquals(403, denied.statusCode(), denied.body())
        assertFalse(denied.body().contains("Main office"))
        assertFalse(denied.body().contains("Employment detail fixture"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "credential"])
    fun pendingDetailReadsRecheckLiveAuthority(change: String) {
        val f = fixture()
        val gate = AccountLockProbe.Barrier(f.account)
        accounts.current.set(gate)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending = executor.submit<HttpResponse<String>> { get(f.reader, f.path) }
            try {
                assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
                if (change == "credential")
                    database()
                        .update(
                            "update accounts set security_version=security_version+1 where id=?",
                            f.account,
                        )
                else
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission='people.read'",
                            f.company,
                            f.account,
                        )
                gate.release.countDown()
                val denied = pending.get(10, TimeUnit.SECONDS)
                assertEquals(
                    if (change == "credential") 401 else 403,
                    denied.statusCode(),
                    denied.body(),
                )
                assertFalse(denied.body().contains("Human resources"))
            } finally {
                gate.release.countDown()
            }
        }
    }

    @Test
    fun cancellationBetweenReferenceReadsReleasesTheSharedStructureAndPeopleGuards() {
        val f = fixture()
        val gate = OrganizationReadProbe.Barrier(f.company, f.branch)
        references.current.set(gate)
        val actor =
            Actor(
                f.account,
                f.company,
                setOf("people.read"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<Result<EmploymentDetails>> {
                    details.execute(actor, f.employee, LocalDate.parse("2026-10-01"))
                }
            try {
                assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertTrue(gate.exited.await(5, TimeUnit.SECONDS))
                assertThrows(CancellationException::class.java) { pending.get(5, TimeUnit.SECONDS) }
            } finally {
                gate.release.countDown()
            }
        }
        val renamed =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/organization-units/${f.branch}",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to 0,
                        "code" to "HQ",
                        "name" to "After cancellation",
                        "kind" to "BRANCH",
                        "timezone" to "Asia/Jakarta",
                        "active" to true,
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, renamed.statusCode(), renamed.body())
        val revised =
            revise(
                f.admin,
                f.csrf,
                f.company,
                f.employee,
                0,
                terms("2026-10-01", f.manager) + mapOf("branchId" to f.branch),
            )
        assertEquals(200, revised.statusCode(), revised.body())
        val fresh = get(f.reader, f.path)
        assertEquals(200, fresh.statusCode(), fresh.body())
        assertEquals("After cancellation", json.readTree(fresh.body())["branch"]["name"].asString())
        assertEquals(1, json.readTree(fresh.body())["employee"]["version"].asInt())
    }
}
