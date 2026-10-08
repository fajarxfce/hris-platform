package dev.fajar.hris

import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(TestClockConfiguration::class)
class ReportingAccessHttpTest : ApiIntegrationTest() {
    @Autowired private lateinit var clock: MutableTestClock

    @Test
    fun historicalFiltersDoNotRestoreFormerTeamAccessAndCombinedScopeIncludesSelf() {
        clock.set(Instant.parse("2026-10-01T16:59:00Z"))
        val admin = client()
        val csrf = login(admin)
        val created =
            command(
                admin,
                "/api/v1/companies",
                json.writeValueAsString(
                    mapOf(
                        "code" to "T${UUID.randomUUID().toString().take(8)}",
                        "name" to "Reporting Test",
                        "timezone" to "Asia/Jakarta",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, created.statusCode(), created.body())
        val company = UUID.fromString(json.readTree(created.body()).get("id").asString())
        val accounts = List(2) { UUID.randomUUID() }
        for (account in accounts) {
            database()
                .update(
                    "insert into accounts(id,email,display_name,password_hash) select ?,?,'Example Manager',password_hash from accounts where email='admin@example.test'",
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
                listOf("people.team.read", "people.self.read", "workforce.team.read")) {
                database()
                    .update(
                        "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                        company,
                        account,
                        permission,
                    )
            }
        }
        val managers = List(2) { UUID.randomUUID() }
        val worker = UUID.randomUUID()
        for ((index, id) in (managers + worker).withIndex()) {
            val body =
                mapOf(
                    "id" to id,
                    "employeeNumber" to "EMP$index",
                    "person" to
                        mapOf(
                            "id" to UUID.randomUUID(),
                            "accountId" to accounts.getOrNull(index),
                            "legalName" to "Employee $index",
                            "nationality" to "ID",
                        ),
                    "terms" to
                        mapOf(
                            "effectiveFrom" to "2026-01-01",
                            "startDate" to "2026-01-01",
                            "status" to "ACTIVE",
                            "contract" to "PERMANENT",
                            "managerId" to if (id == worker) managers[0] else null,
                        ),
                    "reason" to "Onboarding",
                )
            val response =
                command(
                    admin,
                    "/api/v1/companies/$company/employees",
                    json.writeValueAsString(body),
                    csrf,
                    UUID.randomUUID(),
                )
            assertEquals(200, response.statusCode(), response.body())
        }
        val revision =
            command(
                admin,
                "/api/v1/companies/$company/employees/$worker/revisions",
                json.writeValueAsString(
                    mapOf(
                        "version" to 0,
                        "terms" to
                            mapOf(
                                "effectiveFrom" to "2026-10-02",
                                "startDate" to "2026-01-01",
                                "status" to "ACTIVE",
                                "contract" to "PERMANENT",
                                "managerId" to managers[1],
                            ),
                        "reason" to "Reporting transfer",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, revision.statusCode(), revision.body())
        val first = client()
        login(first, "${accounts[0]}@example.test")
        val second = client()
        login(second, "${accounts[1]}@example.test")
        val historicalEmployee = "/api/v1/companies/$company/employees/$worker?asOf=2026-10-01"
        val historicalCalendar =
            "/api/v1/companies/$company/workforce/employees/$worker/calendar?from=2026-10-01&until=2026-10-01"
        assertEquals(200, get(first, historicalEmployee).statusCode())
        assertEquals(404, get(second, historicalEmployee).statusCode())
        assertEquals(200, get(first, historicalCalendar).statusCode())
        clock.set(Instant.parse("2026-10-01T17:01:00Z"))
        assertEquals(404, get(first, historicalEmployee).statusCode())
        assertEquals(200, get(second, historicalEmployee).statusCode())
        assertEquals(404, get(first, historicalCalendar).statusCode())
        assertEquals(200, get(second, historicalCalendar).statusCode())
        val firstItems =
            json
                .readTree(get(first, "/api/v1/companies/$company/employees?asOf=2026-10-01").body())
                .get("items")
        assertEquals(
            listOf(managers[0].toString()),
            (0 until firstItems.size()).map { firstItems[it].get("id").asString() },
        )
        val secondItems =
            json
                .readTree(
                    get(second, "/api/v1/companies/$company/employees?asOf=2026-10-01").body()
                )
                .get("items")
        assertEquals(
            setOf(managers[1].toString(), worker.toString()),
            (0 until secondItems.size()).map { secondItems[it].get("id").asString() }.toSet(),
        )
    }
}
