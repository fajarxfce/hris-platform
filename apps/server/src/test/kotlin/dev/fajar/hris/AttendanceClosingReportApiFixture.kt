package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.jobs.domain.entities.JobStep
import dev.fajar.hris.workforce.domain.usecases.AdvanceWorkPeriodClose
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode

abstract class AttendanceClosingReportApiFixture : AttendanceAdjustmentApiFixture() {
    @Autowired protected lateinit var closeWorker: AdvanceWorkPeriodClose

    protected fun reportPath(f: Fixture, from: String = "2026-08", until: String = "2026-10") =
        "/api/v1/companies/${f.company}/reports/attendance-closings?from=$from&until=$until"

    protected fun reportBody(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    protected fun report(
        f: Fixture,
        from: String = "2026-08",
        until: String = "2026-10",
        client: HttpClient = f.admin,
    ): JsonNode {
        val response = get(client, reportPath(f, from, until))
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        return reportBody(response)
    }

    protected fun finishMonth(f: Fixture, month: String, employees: Int = 1) {
        reportBody(close(f, month = month))
        val lease = claim().single { it.job.request.companyId == f.company }
        for (index in 1..employees) assertEquals(
            Result.Success(JobStep(index, false)),
            closeWorker.execute(f.actor, lease),
        )
        assertEquals(
            Result.Success(JobStep(employees + 1, true)),
            closeWorker.execute(f.actor, lease),
        )
    }

    protected fun reader(f: Fixture, permissions: Set<String>): HttpClient {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Closing report reader',password_hash from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                f.company,
                id,
            )
        permissions.forEach {
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    f.company,
                    id,
                    it,
                )
        }
        return client().also { login(it, "$id@example.test") }
    }

    protected fun mixedCalendar(f: Fixture) {
        val shift = createShift(f)
        for (date in listOf("2026-09-28", "2026-09-29")) reportBody(
            command(
                f.admin,
                "${f.path}/employees/${f.employee}/roster/$date",
                """{"shift":{"id":"$shift","version":0},"reason":"Historical roster"}""",
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        reportBody(
            command(
                f.admin,
                "${f.path}/employees/${f.employee}/attendance/corrections",
                """{"workDate":"2026-09-29","clockIn":"2026-09-29T01:00:00Z","clockOut":"2026-09-29T10:00:00Z","breakMinutes":60,"reason":"Verified work before closing"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        )
        reportBody(
            command(
                f.admin,
                "${f.path}/employees/${f.employee}/attendance/corrections",
                """{"workDate":"2026-09-27","clockIn":null,"clockOut":null,"breakMinutes":0,"reason":"Verified absence before closing"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        )
    }

    protected fun reconcile(f: Fixture, result: JsonNode) {
        val allEmployments = mutableSetOf<String>()
        val months = result["months"]
        for (index in 0 until months.size()) {
            val row = months[index]
            val closing = row["closing"]
            if (closing == null || closing.isNull) continue
            val month = row["month"].asString()
            val employees =
                reportBody(get(f.admin, "${f.path}/periods/$month/employees?limit=50"))["items"]
            val originalDays = mutableListOf<JsonNode>()
            val currentDays = mutableListOf<JsonNode>()
            var adjusted = 0L
            for (employeeIndex in 0 until employees.size()) {
                val employee = employees[employeeIndex]["id"].asString()
                allEmployments += employee
                val original =
                    reportBody(get(f.admin, "${f.path}/periods/$month/employees/$employee"))
                assertEquals(closing["periodId"], original["period"]["id"])
                assertEquals(closing["jobId"], original["period"]["jobId"])
                assertEquals(closing["version"], original["period"]["version"])
                val days = original["days"]
                for (day in 0 until days.size()) originalDays.add(days[day])
                val path = "${f.path}/employees/$employee/attendance/adjustments"
                val history = reportBody(get(f.admin, "$path?month=$month&limit=1"))["items"]
                val latest =
                    if (history.size() == 0) days
                    else {
                        adjusted++
                        reportBody(get(f.admin, "$path/${history[0]["id"].asString()}"))[
                            "snapshot"]["days"]
                    }
                for (day in 0 until latest.size()) currentDays.add(latest[day])
            }
            val counts = row["counts"]
            assertEquals(employees.size().toLong(), counts["employmentMonths"].asLong())
            assertEquals(adjusted, counts["adjustedEmploymentMonths"].asLong())
            assertEquals(originalDays.size.toLong(), counts["days"].asLong())
            val scheduled = originalDays.filter { it["schedule"]["kind"].asString() == "WORK" }
            assertEquals(scheduled.size.toLong(), counts["scheduledDays"].asLong())
            assertEquals(
                scheduled.sumOf { it["schedule"]["plannedMinutes"].asLong() },
                counts["scheduledMinutes"].asLong(),
            )
            for ((name, days) in listOf("original" to originalDays, "current" to currentDays)) {
                val facts = counts[name]
                for ((field, fact) in
                    mapOf(
                        "workedDays" to "WORKED",
                        "absenceDays" to "ABSENCE_RECORDED",
                        "unrecordedDays" to "UNRECORDED",
                        "offDays" to "OFF",
                        "unassignedDays" to "UNASSIGNED",
                    )) assertEquals(
                    days.count { it["fact"].asString() == fact }.toLong(),
                    facts[field].asLong(),
                )
                assertEquals(
                    days.sumOf { it["acceptedMinutes"].asLong() },
                    facts["acceptedMinutes"].asLong(),
                )
            }
        }
        assertEquals(allEmployments.size.toLong(), result["totals"]["employments"].asLong())
    }
}
