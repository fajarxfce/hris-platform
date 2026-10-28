package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.repositories.AttendanceExceptionReportRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AttendanceReportHttpTest : AttendanceExceptionApiFixture() {
    @Autowired private lateinit var reports: AttendanceExceptionReportRepository
    @Autowired private lateinit var transactions: TransactionRunner

    private fun path(f: Fixture) =
        "/api/v1/companies/${f.company}/reports/attendance-exceptions?from=2026-09-01&until=2026-10-01"

    private fun report(f: Fixture): tools.jackson.databind.JsonNode {
        val response = get(f.admin, path(f))
        assertEquals(200, response.statusCode(), response.body())
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        return json.readTree(response.body())
    }

    @Test
    fun countsReconcileWithExceptionRowsWithoutDoubleCountingOverlappingKindsOrEmployments() {
        val f = isolatedFixture()
        val empty = report(f)
        assertEquals("attendance-exceptions.v1", empty["definitionVersion"].asString())
        assertEquals(31, empty["days"].size())
        assertEquals(0, empty["totals"]["observedDays"].asLong())
        val start = recordOfflineEvent(f)
        reviewEvent(f, start, "ACCEPT")
        recordOfflineEvent(f, kind = "CLOCK_OUT", at = Instant.parse("2026-09-30T02:00:00Z"))
        recordOfflineEvent(f, date = "2026-09-29")
        recordOfflineEvent(f, date = "2026-08-31", at = Instant.parse("2026-08-31T03:00:00Z"))
        val value = report(f)
        val total = value["totals"]
        assertEquals(2, total["observedDays"].asLong())
        assertEquals(2, total["affectedDays"].asLong())
        assertEquals(1, total["affectedEmployments"].asLong())
        assertEquals(2, total["pendingDays"].asLong())
        assertEquals(1, total["incompleteDays"].asLong())
        assertEquals(2, total["pendingEvents"].asLong())
        assertEquals(exceptionPage(f)["items"].size().toLong(), total["affectedDays"].asLong())
        assertEquals(
            exceptionPage(f, "PENDING")["items"].size().toLong(),
            total["pendingDays"].asLong(),
        )
        assertEquals(
            exceptionPage(f, "INCOMPLETE")["items"].size().toLong(),
            total["incompleteDays"].asLong(),
        )
        val daily = value["days"][29]["counts"]
        assertEquals(1, daily["affectedDays"].asLong())
        assertEquals(1, daily["pendingDays"].asLong())
        assertEquals(1, daily["incompleteDays"].asLong())
        assertFalse(value.toString().contains("employeeName"))
        assertFalse(value.toString().contains("employeeId"))
        assertFalse(value.toString().contains("capturedAt"))
    }

    @Test
    fun correctionsResolveIncompletenessWhileSubsequentEvidenceStillNeedsReview() {
        val f = isolatedFixture()
        val start = recordOfflineEvent(f)
        reviewEvent(f, start, "ACCEPT")
        assertEquals(1, report(f)["totals"]["incompleteDays"].asLong())
        val corrected =
            command(
                f.admin,
                "${f.path}/employees/${f.employee}/attendance/corrections",
                """{"workDate":"2026-09-30","breakMinutes":0,"reason":"Verified absence"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, corrected.statusCode(), corrected.body())
        assertEquals(0, report(f)["totals"]["affectedDays"].asLong())
        val late = recordOfflineEvent(f, at = Instant.parse("2026-09-30T02:00:00Z"))
        val pending = report(f)["totals"]
        assertEquals(1, pending["pendingEvents"].asLong())
        assertEquals(0, pending["incompleteDays"].asLong())
        reviewEvent(f, late, "REJECT")
        val cleared = report(f)["totals"]
        assertEquals(0, cleared["affectedDays"].asLong())
        assertEquals(1, cleared["observedDays"].asLong())
    }

    @Test
    fun scopeBoundsAndOriginalGrantsCannotExposeAnotherCompany() {
        val f = isolatedFixture()
        recordOfflineEvent(f)
        val other = isolatedFixture()
        assertEquals(403, get(other.admin, path(f)).statusCode())
        assertEquals(403, get(f.employeeClient, path(f)).statusCode())
        val isolated =
            transactions.run(other.actor) {
                reports.aggregate(
                    f.company,
                    LocalDate.parse("2026-09-01"),
                    LocalDate.parse("2026-10-01"),
                )
            } as Result.Success
        assertEquals(0, isolated.value.totals.observedDays)
        assertEquals(0, report(other)["totals"]["affectedDays"].asLong())
        for (from in listOf("2026-08-31", "2026-10-02", "1899-12-31")) {
            val response = get(f.admin, path(f).replace("from=2026-09-01", "from=$from"))
            assertEquals(422, response.statusCode(), response.body())
            assertEquals("invalid_report_range", json.readTree(response.body())["code"].asString())
        }
        for (permission in listOf("reports.read", "workforce.read")) {
            database()
                .update(
                    "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                    f.company,
                    f.actor.accountId,
                    permission,
                )
            assertEquals(403, get(f.admin, path(f)).statusCode())
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    f.company,
                    f.actor.accountId,
                    permission,
                )
        }
        assertEquals(1, report(f)["totals"]["affectedDays"].asLong())
    }

    @Test
    fun reportingAvailabilityAppliesToTheNewReadRoute() {
        val f = isolatedFixture()
        val changed =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/settings/client-policy",
                """{"expectedVersion":null,"activateAt":null,"disabledModules":["REPORTING"],"minimumBuilds":{"android":0,"ios":0,"web":0},"maintenance":null,"reason":"Pause reporting"}""",
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, changed.statusCode(), changed.body())
        val response = get(f.admin, path(f))
        assertEquals(403, response.statusCode(), response.body())
        assertEquals("company_module_disabled", json.readTree(response.body())["code"].asString())
    }
}
