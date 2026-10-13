package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.sync.domain.entities.SyncCollection
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.JsonNode

@TestPropertySource(properties = [MOBILE_SYNC_TEST_KEYS])
class AttendanceSyncHttpTest : WorkforceAccessApiFixture() {
    @Test
    fun companyAvailabilityGatesBothTheFeedAndCanonicalRead() {
        val f = isolatedFixture()
        capture(f)
        val selection = SyncCollection.entries.joinToString(",") { it.name }
        val root = "/api/v1/companies/${f.company}"
        val initial = body(get(f.employeeClient, "$root/sync/bootstrap?collections=$selection"))
        assertEquals(
            setOf("ATTENDANCE_DAYS", "WORK_SCHEDULES", "ROSTER_DAYS", "WORK_HOLIDAYS"),
            initial["collections"].iterator().asSequence().map { it.asString() }.toSet(),
        )
        val id =
            initial["items"]
                .iterator()
                .asSequence()
                .first { it["collection"].asString() == "ATTENDANCE_DAYS" }["id"]
                .asString()
        for ((version, disabled) in listOf(null to listOf("WORKFORCE"), 0L to emptyList())) {
            body(
                command(
                    f.admin,
                    "$root/settings/client-policy",
                    json.writeValueAsString(
                        mapOf(
                            "expectedVersion" to version,
                            "activateAt" to null,
                            "disabledModules" to disabled,
                            "minimumBuilds" to mapOf("android" to 0, "ios" to 0, "web" to 0),
                            "maintenance" to null,
                            "reason" to "Capability configuration",
                        )
                    ),
                    f.csrf,
                    UUID.randomUUID(),
                    "PUT",
                )
            )
            for (path in
                listOf(
                    syncPath(f, "bootstrap"),
                    "$root/sync/changes?collections=$selection" +
                        "&cursor=" +
                        encoded(initial["changesCursor"].asString()),
                    "${f.path}/attendance/days/$id",
                )) {
                val response = get(f.employeeClient, path)
                if (disabled.isNotEmpty()) {
                    assertEquals(403, response.statusCode(), response.body())
                    assertEquals(
                        "company_module_disabled",
                        json.readTree(response.body())["code"].asString(),
                    )
                } else assertEquals(200, response.statusCode(), response.body())
            }
        }
    }

    @Test
    fun captureReviewAndCorrectionAdvanceOneOwnedCanonicalDay() {
        val f = isolatedFixture()
        assertEquals(
            403,
            get(f.employeeClient, "/api/v1/companies/${f.company}/sync/bootstrap").statusCode(),
        )
        assertEquals(0, bootstrap(f)["items"].size())
        val event = capture(f)
        publish()
        val first = bootstrap(f)
        assertEquals(
            listOf("ATTENDANCE_DAYS"),
            first["collections"].iterator().asSequence().map { it.asString() }.toList(),
        )
        assertEquals(1, first["items"].size())
        val id = first["items"][0]["id"].asString()
        assertEquals(0, first["items"][0]["version"].asLong())
        val record = canonical(f, id)
        assertEquals(f.employee.toString(), record["employeeId"].asString())
        assertEquals("PENDING", record["day"]["entries"][0]["status"].asString())
        val original =
            body(
                get(
                    f.employeeClient,
                    "${f.path}/employees/${f.employee}/attendance?from=2026-09-30&until=2026-09-30",
                )
            )
        assertEquals(original[0], record["day"])

        body(reject(f, event))
        val operation = UUID.randomUUID()
        val path = "${f.path}/employees/${f.employee}/attendance/corrections"
        val correction =
            """{"workDate":"2026-09-30","clockIn":null,"clockOut":null,"breakMinutes":0,"reason":"Verified absence"}"""
        body(command(f.admin, path, correction, f.csrf, operation))
        val before = pendingCount(f)
        body(command(f.admin, path, correction, f.csrf, operation))
        assertEquals(before, pendingCount(f))
        publish()
        val changes = changes(f, first["changesCursor"].asString())
        assertEquals(
            listOf(1L, 2L),
            changes["items"].iterator().asSequence().map { it["version"].asLong() }.toList(),
        )
        assertTrue(
            changes["items"].iterator().asSequence().all {
                it["id"].asString() == id && it["operation"].asString() == "UPSERT"
            }
        )
        val updated = canonical(f, id)
        assertEquals(2, updated["version"].asLong())
        assertEquals("REJECTED", updated["day"]["entries"][0]["status"].asString())
        assertEquals(0, updated["day"]["correction"]["version"].asLong())
        assertEquals(
            record["day"]["entries"][0]["schedule"],
            updated["day"]["entries"][0]["schedule"],
        )
        assertEquals(0, changes(f, changes["cursor"].asString())["items"].size())
    }

    @Test
    fun administrativeGrantsDoNotExposeColleaguesAndRevocationStopsBothPaths() {
        val f = isolatedFixture()
        val colleague = colleague(f)
        capture(f)
        capture(colleague)
        publish()
        val owned = bootstrap(f)
        val other = bootstrap(colleague)
        assertEquals(1, owned["items"].size())
        assertEquals(1, other["items"].size())
        val id = owned["items"][0]["id"].asString()
        val foreign = other["items"][0]["id"].asString()
        assertNotEquals(id, foreign)
        assertEquals(404, get(f.employeeClient, "${f.path}/attendance/days/$foreign").statusCode())
        assertEquals(404, get(f.admin, "${f.path}/attendance/days/$id").statusCode())
        assertEquals(0, bootstrap(f, f.admin)["items"].size())
        val differentCompany = isolatedFixture()
        assertEquals(
            404,
            get(differentCompany.employeeClient, "${differentCompany.path}/attendance/days/$id")
                .statusCode(),
        )
        assertEquals(
            422,
            get(
                    colleague.employeeClient,
                    syncPath(f, "changes") + "&cursor=" + encoded(owned["changesCursor"].asString()),
                )
                .statusCode(),
        )
        database()
            .update(
                "update membership_permissions set permission='workforce.read' where company_id=? and account_id=? and permission='attendance.self.record'",
                f.company,
                f.employeeAccount,
            )
        assertEquals(200, get(f.employeeClient, "${f.path}/attendance/days/$id").statusCode())
        assertEquals(1, bootstrap(f)["items"].size())
        assertEquals(404, get(f.employeeClient, "${f.path}/attendance/days/$foreign").statusCode())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='workforce.read'",
                f.company,
                f.employeeAccount,
            )
        assertEquals(403, get(f.employeeClient, syncPath(f, "bootstrap")).statusCode())
        assertEquals(403, get(f.employeeClient, "${f.path}/attendance/days/$id").statusCode())
    }

    private fun colleague(f: Fixture): Fixture {
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Sync colleague',password_hash from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                f.company,
                account,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'attendance.self.record')",
                f.company,
                account,
            )
        val employee = anotherEmployee(f, account)
        val client = client()
        val csrf = login(client, "$account@example.test")
        return f.copy(
            employee = employee,
            employeeAccount = account,
            employeeClient = client,
            employeeCsrf = csrf,
        )
    }

    private fun canonical(f: Fixture, id: String) =
        body(get(f.employeeClient, "${f.path}/attendance/days/$id"))

    private fun bootstrap(f: Fixture, client: HttpClient = f.employeeClient) =
        body(get(client, syncPath(f, "bootstrap")))

    private fun changes(f: Fixture, cursor: String) =
        body(get(f.employeeClient, syncPath(f, "changes") + "&cursor=" + encoded(cursor)))

    private fun syncPath(f: Fixture, phase: String) =
        "/api/v1/companies/${f.company}/sync/$phase?collections=ATTENDANCE_DAYS&limit=100"

    private fun encoded(cursor: String) = URLEncoder.encode(cursor, Charsets.UTF_8)

    private fun body(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    private fun pendingCount(f: Fixture) =
        database()
            .queryForObject(
                "select count(*) from mobile_sync_changes where company_id=? and collection='ATTENDANCE_DAYS'",
                Int::class.java,
                f.company,
            )

    private fun publish() {
        val worker = mobileSyncTestWorker(postgres.jdbcUrl, database())
        repeat(4) { assertTrue(worker.maintain.execute() is Result.Success) }
    }
}
