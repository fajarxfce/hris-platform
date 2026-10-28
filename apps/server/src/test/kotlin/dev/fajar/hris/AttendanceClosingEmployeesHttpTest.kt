package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.repositories.AttendanceClosingReportRepository
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.YearMonth
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode

class AttendanceClosingEmployeesHttpTest : AttendanceClosingReportApiFixture() {
    @Autowired private lateinit var reports: AttendanceClosingReportRepository
    @Autowired private lateinit var transactions: TransactionRunner

    private fun path(f: Fixture, month: String = "2026-09") =
        "/api/v1/companies/${f.company}/reports/attendance-closings/employees?month=$month"

    private fun page(f: Fixture, after: String? = null, limit: Int = 50): JsonNode {
        val cursor = after?.let { "&after=${URLEncoder.encode(it,StandardCharsets.UTF_8)}" } ?: ""
        val response = get(f.admin, "${path(f)}&limit=$limit$cursor")
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        return reportBody(response)
    }

    @Test
    fun exactSelectedRevisionRemainsReadableAfterANewerCorrection() {
        val f = isolatedFixture()
        mixedCalendar(f)
        finishMonth(f, "2026-09")
        val original = page(f)["items"][0]
        assertTrue(original["adjustment"].isNull)
        assertEquals(480, original["counts"]["original"]["acceptedMinutes"].asLong())
        adjusted(f, adjustmentBody(adjustmentReview(f)))
        val selected = page(f)["items"][0]
        val id = selected["adjustment"]["id"].asString()
        assertEquals(1, selected["adjustment"]["revision"].asLong())
        assertEquals(900, selected["counts"]["current"]["acceptedMinutes"].asLong())
        val detail = reportBody(get(f.admin, "${adjustmentPath(f)}/$id"))
        assertEquals(selected["closing"]["jobId"], detail["snapshot"]["period"]["jobId"])
        val date = "2026-09-29"
        adjusted(f, adjustmentBody(adjustmentReview(f, date), date, absence = true))
        val current = page(f)["items"][0]
        assertNotEquals(id, current["adjustment"]["id"].asString())
        assertEquals(2, current["adjustment"]["revision"].asLong())
        assertEquals(420, current["counts"]["current"]["acceptedMinutes"].asLong())
        assertEquals(original["closing"], current["closing"])
        assertEquals(original["counts"]["original"], current["counts"]["original"])
        assertEquals(detail, reportBody(get(f.admin, "${adjustmentPath(f)}/$id")))
        val aggregate = report(f, "2026-09", "2026-09")
        assertEquals(aggregate["totals"], current["counts"])
        assertEquals(f.employee.toString(), current["employeeId"].asString())
        assertFalse(current["employeeNumber"].asString().isBlank())
        assertFalse(current["employeeName"].asString().isBlank())
        for (private in
            listOf(
                "personId",
                "accountId",
                "reason",
                "evidenceIds",
                "birthDate",
                "email",
            )) assertFalse(current.has(private))
    }

    @Test
    fun boundedPagesReconcileBothFactSetsAndKeepTheirOrderAcrossLaterCorrectionsAndHires() {
        val f = isolatedFixture()
        val ids =
            listOf(f.employee, anotherEmployee(f), anotherEmployee(f)).map(UUID::toString).sorted()
        mixedCalendar(f)
        finishMonth(f, "2026-09", 3)
        val rows = mutableListOf<JsonNode>()
        var after: String? = null
        for (index in 0..2) {
            val result = page(f, after, 1)
            assertEquals(f.company.toString(), result["companyId"].asString())
            assertEquals("2026-09", result["month"].asString())
            assertEquals("attendance-closings.v1", result["definitionVersion"].asString())
            assertEquals(1, result["items"].size())
            rows.add(result["items"][0])
            assertEquals(ids[index], rows.last()["employeeId"].asString())
            after = result["nextCursor"].takeUnless { it.isNull }?.asString()
            if (index < 2) assertNotNull(after) else assertNull(after)
        }
        val counts = report(f, "2026-09", "2026-09")["totals"]
        for (field in
            listOf(
                "employmentMonths",
                "employments",
                "adjustedEmploymentMonths",
                "days",
                "scheduledDays",
                "scheduledMinutes",
            )) assertEquals(counts[field].asLong(), rows.sumOf { it["counts"][field].asLong() })
        for (source in listOf("original", "current")) for (field in
            listOf(
                "workedDays",
                "absenceDays",
                "unrecordedDays",
                "offDays",
                "unassignedDays",
                "acceptedMinutes",
            )) assertEquals(
            counts[source][field].asLong(),
            rows.sumOf { it["counts"][source][field].asLong() },
        )
        val cursor = page(f, limit = 1)["nextCursor"].asString()
        adjusted(f, adjustmentBody(adjustmentReview(f)))
        anotherEmployee(f)
        val continuation = page(f, cursor)
        assertEquals(2, continuation["items"].size())
        assertEquals(ids.drop(1), (0..1).map { continuation["items"][it]["employeeId"].asString() })
        assertEquals(3, page(f)["items"].size())
        assertEquals(0, reportBody(get(f.admin, path(f, "2026-10")))["items"].size())
    }

    @Test
    fun currentReportAndSourceGrantsRlsAndScopedContinuationProtectPrivateRows() {
        val f = closedFixture()
        for (permissions in
            listOf(
                emptySet(),
                setOf("reports.read"),
                setOf("workforce.read"),
                setOf(
                    "reports.read",
                    "workforce.team.read",
                    "attendance.correct",
                    "workforce.close",
                ),
            )) {
            val response = get(reader(f, permissions), path(f))
            assertEquals(403, response.statusCode(), response.body())
            assertFalse(response.body().contains("employeeName"))
        }
        val viewer = reader(f, setOf("reports.read", "workforce.read"))
        assertEquals(1, reportBody(get(viewer, path(f)))["items"].size())
        val foreign = isolatedFixture()
        assertEquals(403, get(viewer, path(foreign)).statusCode())
        val isolated =
            transactions.run(foreign.actor) {
                reports.employees(f.company, YearMonth.parse("2026-09"), null, 50)
            }
        assertTrue((isolated as Result.Success).value.items.isEmpty())
        for (suffix in
            listOf(
                "&limit=0",
                "&limit=51",
                "&after=bad",
                "&after=attendance-closings.v1%7C${foreign.company}%7C2026-09%7C${f.employee}",
                "&after=attendance-closings.v1%7C${f.company}%7C2026-10%7C${f.employee}",
            )) assertEquals(422, get(viewer, path(f) + suffix).statusCode())
        assertEquals(422, get(viewer, path(f, "1999-12")).statusCode())
        assertEquals(400, get(viewer, path(f, "2026-09-01")).statusCode())
        reportBody(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/settings/client-policy",
                """{"expectedVersion":null,"activateAt":null,"disabledModules":["REPORTING"],"minimumBuilds":{"android":0,"ios":0,"web":0},"maintenance":null,"reason":"Pause closing reports"}""",
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        assertEquals(403, get(viewer, path(f)).statusCode())
    }
}
