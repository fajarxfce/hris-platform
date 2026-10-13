package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.LocalTime
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

@TestPropertySource(properties = [MOBILE_SYNC_TEST_KEYS])
class CalendarSourceSyncHttpTest : WorkforceAccessApiFixture() {
    @Test
    fun frozenRulesSurviveTemplateEditsAndOnlyChangedSourcesAdvance() {
        val f = isolatedFixture()
        val shift = createShift(f)
        assign(f, shift, null)
        roster(f, null, null)
        val holiday = createHoliday(f)
        publish()
        val initial = bootstrap(f)
        assertEquals(4, initial["items"].size())
        val scheduleId = reference(initial, "WORK_SCHEDULES")
        val rosterId = reference(initial, "ROSTER_DAYS")
        val original = canonical(f, "schedules", scheduleId)
        assertEquals(0, original["version"].asLong())
        assertEquals(
            LocalTime.of(8, 0),
            LocalTime.parse(
                original["assignment"]["days"]["FRIDAY"]["details"]["startsAt"].asString()
            ),
        )
        assertTrue(canonical(f, "roster", rosterId)["shift"].isNull)
        assertEquals(403, get(f.employeeClient, "${f.path}/holidays/$holiday").statusCode())
        assertTrue(canonical(f, "holidays", holiday.toString())["active"].asBoolean())

        val changedShift =
            (json.readTree(shiftBody(shift)) as ObjectNode)
                .put("expectedVersion", 0)
                .put("startsAt", "09:00")
                .put("name", "Revised office shift")
        body(
            command(
                f.admin,
                "${f.path}/shifts/$shift",
                json.writeValueAsString(changedShift),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        publish()
        assertEquals(0, changes(f, initial["changesCursor"].asString())["items"].size())
        assertEquals(original, canonical(f, "schedules", scheduleId))

        val operation = UUID.randomUUID()
        assign(f, shift, 0, shiftVersion = 1, operation = operation)
        assign(f, shift, 0, shiftVersion = 1, operation = operation)
        roster(f, shift, 0, shiftVersion = 1)
        body(
            command(
                f.admin,
                "${f.path}/holidays/$holiday",
                """{"workDate":"2026-10-02","name":"Company holiday","active":false,"expectedVersion":0,"reason":"Updated calendar"}""",
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        publish()
        val changed = changes(f, initial["changesCursor"].asString())
        val refs = changed["items"].iterator().asSequence().toList()
        assertEquals(3, refs.size)
        assertEquals(
            setOf("WORK_SCHEDULES", "ROSTER_DAYS", "WORK_HOLIDAYS"),
            refs.map { it["collection"].asString() }.toSet(),
        )
        assertTrue(
            refs.all { it["version"].asLong() == 1L && it["operation"].asString() == "UPSERT" }
        )
        val updated = canonical(f, "schedules", scheduleId)
        assertEquals(1, updated["version"].asLong())
        assertEquals(1, updated["assignment"]["days"]["FRIDAY"]["revision"].asLong())
        assertEquals(1, canonical(f, "roster", rosterId)["version"].asLong())
        assertFalse(canonical(f, "holidays", holiday.toString())["active"].asBoolean())
        val calendar =
            body(
                get(
                    f.employeeClient,
                    "${f.path}/employees/${f.employee}/calendar?from=2026-10-02&until=2026-10-02",
                )
            )
        assertEquals("ROSTER", calendar["days"][0]["origin"].asString())
        assertEquals(updated["assignment"]["days"]["FRIDAY"], calendar["days"][0]["shift"])

        assign(f, shift, 1, from = "2026-10-15", shiftVersion = 1)
        val latest = bootstrap(f)
        val schedules =
            latest["items"]
                .iterator()
                .asSequence()
                .filter { it["collection"].asString() == "WORK_SCHEDULES" }
                .toList()
        assertEquals(2, schedules.size)
        assertEquals(updated, canonical(f, "schedules", scheduleId))
        assertEquals(
            3,
            database()
                .queryForObject(
                    "select count(*) from schedule_assignments where company_id=? and employment_id=?",
                    Int::class.java,
                    f.company,
                    f.employee,
                ),
        )
    }

    @Test
    fun employeeCalendarFeedIncludesSharedHolidaysWithoutExportingAnotherEmployee() {
        val f = isolatedFixture()
        val shift = createShift(f)
        assign(f, shift, null)
        roster(f, null, null)
        val other = anotherEmployee(f)
        assign(f, shift, null, employee = other)
        roster(f, shift, null, employee = other)
        publish()
        val owned = bootstrap(f)
        assertEquals(3, owned["items"].size())
        assertEquals(0, bootstrap(f, f.admin)["items"].size())
        val ownSchedule = reference(owned, "WORK_SCHEDULES")
        val ownRoster = reference(owned, "ROSTER_DAYS")
        val foreignSchedule =
            database()
                .queryForObject(
                    "select id from work_schedule_sync_heads where company_id=? and employment_id=?",
                    UUID::class.java,
                    f.company,
                    other,
                )
                .toString()
        val foreignRoster =
            database()
                .queryForObject(
                    "select id from roster_days where company_id=? and employment_id=?",
                    UUID::class.java,
                    f.company,
                    other,
                )
                .toString()
        for ((resource, id) in
            listOf("schedules" to foreignSchedule, "roster" to foreignRoster)) assertEquals(
            404,
            get(f.employeeClient, "${f.path}/me/$resource/$id").statusCode(),
        )
        for ((resource, id) in
            listOf(
                "schedules" to ownSchedule,
                "roster" to ownRoster,
                "holidays" to f.holiday.toString(),
            )) assertEquals(404, get(f.admin, "${f.path}/me/$resource/$id").statusCode())
        val anotherCompany = isolatedFixture()
        for ((resource, id) in
            listOf(
                "schedules" to ownSchedule,
                "roster" to ownRoster,
                "holidays" to f.holiday.toString(),
            )) assertEquals(
            404,
            get(anotherCompany.employeeClient, "${anotherCompany.path}/me/$resource/$id")
                .statusCode(),
        )

        // Changes to a colleague's pattern do not enter the employee's pending/delta audience.
        assign(f, shift, 0, employee = other)
        publish()
        assertEquals(0, changes(f, owned["changesCursor"].asString())["items"].size())
        database()
            .update(
                "update membership_permissions set permission='workforce.read' where company_id=? and account_id=? and permission='attendance.self.record'",
                f.company,
                f.employeeAccount,
            )
        assertEquals(3, bootstrap(f)["items"].size())
        assertEquals(200, get(f.employeeClient, "${f.path}/me/schedules/$ownSchedule").statusCode())
        assertEquals(
            404,
            get(f.employeeClient, "${f.path}/me/schedules/$foreignSchedule").statusCode(),
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='workforce.read'",
                f.company,
                f.employeeAccount,
            )
        assertEquals(403, get(f.employeeClient, syncPath(f, "bootstrap")).statusCode())
        for ((resource, id) in
            listOf(
                "schedules" to ownSchedule,
                "roster" to ownRoster,
                "holidays" to f.holiday.toString(),
            )) assertEquals(403, get(f.employeeClient, "${f.path}/me/$resource/$id").statusCode())
    }

    private fun assign(
        f: Fixture,
        shift: UUID,
        version: Long?,
        employee: UUID = f.employee,
        from: String = "2026-10-01",
        shiftVersion: Long = 0,
        operation: UUID = UUID.randomUUID(),
    ) {
        body(
            command(
                f.admin,
                "${f.path}/employees/$employee/schedule",
                json.writeValueAsString(
                    mapOf(
                        "effectiveFrom" to from,
                        "days" to
                            listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")
                                .associateWith { mapOf("id" to shift, "version" to shiftVersion) },
                        "expectedVersion" to version,
                        "reason" to "Calendar rule",
                    )
                ),
                f.csrf,
                operation,
                "PUT",
            )
        )
    }

    private fun roster(
        f: Fixture,
        shift: UUID?,
        version: Long?,
        shiftVersion: Long = 0,
        employee: UUID = f.employee,
    ) {
        body(
            command(
                f.admin,
                "${f.path}/employees/$employee/roster/2026-10-02",
                json.writeValueAsString(
                    mapOf(
                        "shift" to shift?.let { mapOf("id" to it, "version" to shiftVersion) },
                        "expectedVersion" to version,
                        "reason" to "Roster override",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
    }

    private fun canonical(f: Fixture, resource: String, id: String) =
        body(get(f.employeeClient, "${f.path}/me/$resource/$id"))

    private fun bootstrap(f: Fixture, client: HttpClient = f.employeeClient) =
        body(get(client, syncPath(f, "bootstrap")))

    private fun changes(f: Fixture, cursor: String) =
        body(
            get(
                f.employeeClient,
                syncPath(f, "changes") + "&cursor=" + URLEncoder.encode(cursor, Charsets.UTF_8),
            )
        )

    private fun syncPath(f: Fixture, phase: String) =
        "/api/v1/companies/${f.company}/sync/$phase?collections=WORK_SCHEDULES,ROSTER_DAYS,WORK_HOLIDAYS&limit=100"

    private fun reference(page: JsonNode, collection: String) =
        page["items"]
            .iterator()
            .asSequence()
            .single { it["collection"].asString() == collection }["id"]
            .asString()

    private fun body(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    private fun publish() {
        val worker = mobileSyncTestWorker(postgres.jdbcUrl, database())
        repeat(4) { assertTrue(worker.maintain.execute() is Result.Success) }
    }
}
