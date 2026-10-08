package dev.fajar.hris

import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import tools.jackson.databind.JsonNode

@Import(TestClockConfiguration::class)
abstract class LeaveApiFixture : ApiIntegrationTest() {
    @Autowired protected lateinit var clock: MutableTestClock

    protected data class Fixture(
        val company: UUID,
        val employee: UUID,
        val account: UUID,
        val manager: UUID,
        val managerAccount: UUID,
        val type: UUID,
        val admin: HttpClient,
        val adminCsrf: String,
        val worker: HttpClient,
        val workerCsrf: String,
        val supervisor: HttpClient,
        val supervisorCsrf: String,
    )

    protected fun fixture(): Fixture {
        clock.set(Instant.parse("2026-10-01T15:00:00Z"))
        val admin = client()
        val csrf = login(admin)
        val response =
            command(
                admin,
                "/api/v1/companies",
                json.writeValueAsString(
                    mapOf(
                        "code" to "L${UUID.randomUUID().toString().take(8)}",
                        "name" to "Leave Test",
                        "timezone" to "Asia/Jakarta",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, response.statusCode(), response.body())
        val company = UUID.fromString(json.readTree(response.body()).get("id").asString())
        val account = UUID.randomUUID()
        val managerAccount = UUID.randomUUID()
        for ((member, permissions) in
            listOf(
                account to listOf("company.read", "leave.self.manage"),
                managerAccount to
                    listOf(
                        "company.read",
                        "leave.team.read",
                        "leave.team.approve",
                        "approvals.read",
                    ),
            )) {
            database()
                .update(
                    "insert into accounts(id,email,display_name,password_hash) select ?,?,'Example Member',password_hash from accounts where email='admin@example.test'",
                    member,
                    "$member@example.test",
                )
            database()
                .update(
                    "insert into company_memberships(company_id,account_id) values(?,?)",
                    company,
                    member,
                )
            for (permission in permissions) database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    company,
                    member,
                    permission,
                )
        }
        val employee = UUID.randomUUID()
        val manager = UUID.randomUUID()
        for ((id, member, managerId) in
            listOf(Triple(manager, managerAccount, null), Triple(employee, account, manager))) {
            val created =
                command(
                    admin,
                    "/api/v1/companies/$company/employees",
                    json.writeValueAsString(
                        mapOf(
                            "id" to id,
                            "employeeNumber" to "E${id.toString().take(8)}",
                            "person" to
                                mapOf(
                                    "id" to UUID.randomUUID(),
                                    "accountId" to member,
                                    "legalName" to "Example Employee",
                                    "nationality" to "ID",
                                ),
                            "terms" to
                                mapOf(
                                    "effectiveFrom" to "2025-01-01",
                                    "startDate" to "2025-01-01",
                                    "status" to "ACTIVE",
                                    "contract" to "PERMANENT",
                                    "managerId" to managerId,
                                ),
                            "reason" to "Onboarding",
                        )
                    ),
                    csrf,
                    UUID.randomUUID(),
                )
            assertEquals(200, created.statusCode(), created.body())
        }
        val worker = client()
        val workerCsrf = login(worker, "$account@example.test")
        val supervisor = client()
        val supervisorCsrf = login(supervisor, "$managerAccount@example.test")
        val f =
            Fixture(
                company,
                employee,
                account,
                manager,
                managerAccount,
                UUID.randomUUID(),
                admin,
                csrf,
                worker,
                workerCsrf,
                supervisor,
                supervisorCsrf,
            )
        val policy = policy(f)
        assertEquals(200, policy.statusCode(), policy.body())
        return f
    }

    protected fun policy(
        f: Fixture,
        version: Long? = null,
        from: String = "2026-01-01",
        paid: Boolean = true,
        code: String = "ANNUAL",
        active: Boolean = true,
    ): HttpResponse<String> =
        command(
            f.admin,
            "/api/v1/companies/${f.company}/leave/types/${f.type}",
            json.writeValueAsString(
                mapOf(
                    "code" to code,
                    "name" to "Annual leave",
                    "effectiveFrom" to from,
                    "paid" to paid,
                    "allowPartialDays" to true,
                    "minServiceMonths" to 12,
                    "maxRequestDays" to 30,
                    "active" to active,
                    "expectedVersion" to version,
                    "reason" to "Leave policy configuration",
                )
            ),
            f.adminCsrf,
            UUID.randomUUID(),
            "PUT",
        )

    protected fun adjust(
        f: Fixture,
        days: String,
        key: UUID = UUID.randomUUID(),
    ): HttpResponse<String> =
        command(
            f.admin,
            "/api/v1/companies/${f.company}/leave/employees/${f.employee}/balances/${f.type}/2026/adjustments",
            json.writeValueAsString(
                mapOf("days" to days, "reason" to "Annual entitlement adjustment")
            ),
            f.adminCsrf,
            key,
        )

    protected fun ledger(
        f: Fixture,
        client: HttpClient = f.worker,
        suffix: String = "",
    ): HttpResponse<String> =
        get(
            client,
            "/api/v1/companies/${f.company}/leave/employees/${f.employee}/balances/${f.type}/2026$suffix",
        )

    protected fun balance(f: Fixture): JsonNode {
        val result = ledger(f)
        assertEquals(200, result.statusCode(), result.body())
        return json.readTree(result.body()).get("balance")
    }
}
