package dev.fajar.hris

import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException

class WorkCalendarHttpTest : ApiIntegrationTest() {
    private fun company(client: HttpClient, csrf: String): UUID {
        val response =
            command(
                client,
                "/api/v1/companies",
                json.writeValueAsString(
                    mapOf(
                        "code" to "W${UUID.randomUUID().toString().take(8)}",
                        "name" to "Work Calendar Test",
                        "timezone" to "Asia/Jakarta",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, response.statusCode(), response.body())
        return UUID.fromString(json.readTree(response.body()).get("id").asString())
    }

    private fun employee(client: HttpClient, csrf: String, company: UUID): UUID {
        val id = UUID.randomUUID()
        val body =
            mapOf(
                "id" to id,
                "employeeNumber" to "E${id.toString().take(8)}",
                "person" to
                    mapOf(
                        "id" to UUID.randomUUID(),
                        "legalName" to "Example Employee",
                        "nationality" to "ID",
                    ),
                "terms" to
                    mapOf(
                        "effectiveFrom" to "2026-01-01",
                        "startDate" to "2026-01-01",
                        "contract" to "PERMANENT",
                        "status" to "ACTIVE",
                    ),
                "reason" to "Administrative onboarding",
            )
        val response =
            command(
                client,
                "/api/v1/companies/$company/employees",
                json.writeValueAsString(body),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, response.statusCode(), response.body())
        return id
    }

    private fun shift(
        client: HttpClient,
        csrf: String,
        company: UUID,
        id: UUID,
        version: Long? = null,
        start: String = "22:00",
        end: String = "06:00",
    ): HttpResponse<String> =
        command(
            client,
            "/api/v1/companies/$company/workforce/shifts/$id",
            json.writeValueAsString(
                mapOf(
                    "code" to "S${id.toString().take(8)}",
                    "name" to "Night",
                    "startsAt" to start,
                    "endsAt" to end,
                    "breakMinutes" to 30,
                    "timezone" to "Asia/Jakarta",
                    "mode" to "REMOTE",
                    "expectedVersion" to version,
                    "reason" to "Shift configuration",
                )
            ),
            csrf,
            UUID.randomUUID(),
            "PUT",
        )

    private fun scheduleBody(
        shift: UUID,
        shiftVersion: Long = 0,
        version: Long? = null,
        from: String = "2026-01-01",
    ): String =
        json.writeValueAsString(
            mapOf(
                "effectiveFrom" to from,
                "days" to
                    java.time.DayOfWeek.entries.associate {
                        it.name to mapOf("id" to shift, "version" to shiftVersion)
                    },
                "expectedVersion" to version,
                "reason" to "Schedule assignment",
            )
        )

    @Test
    fun publishedSchedulesKeepShiftSnapshotsAndEffectiveHistory() {
        val client = client()
        val csrf = login(client)
        val company = company(client, csrf)
        val employee = employee(client, csrf, company)
        val shift = UUID.randomUUID()
        assertEquals(200, shift(client, csrf, company, shift).statusCode())
        val assigned =
            command(
                client,
                "/api/v1/companies/$company/workforce/employees/$employee/schedule",
                scheduleBody(shift),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, assigned.statusCode(), assigned.body())
        val changed = shift(client, csrf, company, shift, 0, "20:00", "04:00")
        assertEquals(200, changed.statusCode(), changed.body())
        val existing =
            get(
                client,
                "/api/v1/companies/$company/workforce/employees/$employee/calendar?from=2026-10-08&until=2026-10-08",
            )
        assertEquals(200, existing.statusCode(), existing.body())
        assertEquals(
            "2026-10-08T15:00:00Z",
            json.readTree(existing.body()).get("days")[0].get("startsAt").asString(),
        )
        assertEquals(
            0,
            json.readTree(existing.body()).get("days")[0].get("shift").get("revision").asLong(),
        )
        val scheduled =
            command(
                client,
                "/api/v1/companies/$company/workforce/employees/$employee/schedule",
                scheduleBody(shift, 1, 0, "2026-10-15"),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, scheduled.statusCode(), scheduled.body())
        val dates =
            get(
                client,
                "/api/v1/companies/$company/workforce/employees/$employee/calendar?from=2026-10-14&until=2026-10-15",
            )
        val days = json.readTree(dates.body()).get("days")
        assertEquals("2026-10-14T15:00:00Z", days[0].get("startsAt").asString())
        assertEquals("2026-10-15T13:00:00Z", days[1].get("startsAt").asString())
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update schedule_assignments set reason='overwrite' where company_id=? and employment_id=?",
                    company,
                    employee,
                )
        }
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from shift_revisions where company_id=? and shift_id=?",
                    Int::class.java,
                    company,
                    shift,
                ),
        )
    }

    @Test
    fun rosterOverridesHolidayAndCanExplicitlySetADayOff() {
        val client = client()
        val csrf = login(client)
        val company = company(client, csrf)
        val employee = employee(client, csrf, company)
        val shift = UUID.randomUUID()
        assertEquals(200, shift(client, csrf, company, shift).statusCode())
        assertEquals(
            200,
            command(
                    client,
                    "/api/v1/companies/$company/workforce/employees/$employee/schedule",
                    scheduleBody(shift),
                    csrf,
                    UUID.randomUUID(),
                    "PUT",
                )
                .statusCode(),
        )
        val holiday =
            command(
                client,
                "/api/v1/companies/$company/workforce/holidays/${UUID.randomUUID()}",
                json.writeValueAsString(
                    mapOf(
                        "workDate" to "2026-10-08",
                        "name" to "Company holiday",
                        "reason" to "Company calendar",
                    )
                ),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, holiday.statusCode(), holiday.body())
        val path =
            "/api/v1/companies/$company/workforce/employees/$employee/calendar?from=2026-10-08&until=2026-10-08"
        assertEquals(
            "HOLIDAY",
            json.readTree(get(client, path).body()).get("days")[0].get("origin").asString(),
        )
        val work =
            command(
                client,
                "/api/v1/companies/$company/workforce/employees/$employee/roster/2026-10-08",
                json.writeValueAsString(
                    mapOf(
                        "shift" to mapOf("id" to shift, "version" to 0),
                        "reason" to "Holiday coverage",
                    )
                ),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, work.statusCode(), work.body())
        assertEquals(
            "WORK",
            json.readTree(get(client, path).body()).get("days")[0].get("kind").asString(),
        )
        val offBody =
            json.writeValueAsString(mapOf("expectedVersion" to 0, "reason" to "Coverage cancelled"))
        val operation = UUID.randomUUID()
        val off =
            command(
                client,
                "/api/v1/companies/$company/workforce/employees/$employee/roster/2026-10-08",
                offBody,
                csrf,
                operation,
                "PUT",
            )
        assertEquals(200, off.statusCode(), off.body())
        assertEquals(
            off.body(),
            command(
                    client,
                    "/api/v1/companies/$company/workforce/employees/$employee/roster/2026-10-08",
                    offBody,
                    csrf,
                    operation,
                    "PUT",
                )
                .body(),
        )
        val result = json.readTree(get(client, path).body()).get("days")[0]
        assertEquals("OFF", result.get("kind").asString())
        assertEquals("ROSTER", result.get("origin").asString())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from roster_revisions where company_id=? and employment_id=?",
                    Int::class.java,
                    company,
                    employee,
                ),
        )
    }

    @Test
    fun staleShiftReferencesAndForeignCompanyShiftsCannotBePublished() {
        val client = client()
        val csrf = login(client)
        val first = company(client, csrf)
        val second = company(client, csrf)
        val employee = employee(client, csrf, first)
        val shift = UUID.randomUUID()
        assertEquals(200, shift(client, csrf, second, shift).statusCode())
        val foreign =
            command(
                client,
                "/api/v1/companies/$first/workforce/employees/$employee/schedule",
                scheduleBody(shift),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(422, foreign.statusCode(), foreign.body())
        val own = UUID.randomUUID()
        assertEquals(200, shift(client, csrf, first, own).statusCode())
        assertEquals(200, shift(client, csrf, first, own, 0, "21:00", "05:00").statusCode())
        val stale =
            command(
                client,
                "/api/v1/companies/$first/workforce/employees/$employee/schedule",
                scheduleBody(own),
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(409, stale.statusCode(), stale.body())
        assertEquals("shift_changed", json.readTree(stale.body()).get("code").asString())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from schedule_versions where company_id=? and employment_id=?",
                    Int::class.java,
                    first,
                    employee,
                ),
        )
    }

    @Test
    fun calendarRangeAndEmployeeScopeAreEnforced() {
        val client = client()
        val csrf = login(client)
        val first = company(client, csrf)
        val second = company(client, csrf)
        val employee = employee(client, csrf, first)
        val excessive =
            get(
                client,
                "/api/v1/companies/$first/workforce/employees/$employee/calendar?from=2026-01-01&until=2026-12-31",
            )
        assertEquals(422, excessive.statusCode(), excessive.body())
        assertEquals(
            404,
            get(
                    client,
                    "/api/v1/companies/$second/workforce/employees/$employee/calendar?from=2026-10-08&until=2026-10-09",
                )
                .statusCode(),
        )
        val empty =
            get(
                client,
                "/api/v1/companies/$first/workforce/employees/$employee/calendar?from=2026-10-08&until=2026-10-08",
            )
        assertEquals(
            "UNASSIGNED",
            json.readTree(empty.body()).get("days")[0].get("kind").asString(),
        )
    }
}
