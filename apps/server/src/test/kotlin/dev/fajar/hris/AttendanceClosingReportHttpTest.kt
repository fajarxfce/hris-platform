package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.JobStep
import dev.fajar.hris.reporting.domain.repositories.AttendanceClosingReportRepository
import dev.fajar.hris.workforce.domain.usecases.AbortWorkPeriodClose
import java.time.YearMonth
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AttendanceClosingReportHttpTest : AttendanceClosingReportApiFixture() {
    @Autowired private lateinit var reports: AttendanceClosingReportRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var abortClosing: AbortWorkPeriodClose

    @Test
    fun cancelledAttemptSnapshotsRemainExcludedAfterAReplacementClosingIsPublished() {
        val f = isolatedFixture()
        val first = begin(f)
        assertEquals(Result.Success(JobStep(1, false)), closeWorker.execute(f.actor, first))
        cancel(f, first)
        assertEquals(
            Result.Success(Unit),
            abortClosing.execute(first, Failure(FailureKind.CONFLICT, "job_cancellation_requested")),
        )
        assertEquals("REVIEW_REQUIRED", period(f)["status"].asString())
        assertEquals(0, report(f)["totals"]["employmentMonths"].asLong())
        val second = begin(f, period(f)["version"].asLong())
        assertEquals(Result.Success(JobStep(1, false)), closeWorker.execute(f.actor, second))
        assertEquals(Result.Success(JobStep(2, true)), closeWorker.execute(f.actor, second))
        val result = report(f)
        assertEquals(1, result["totals"]["employmentMonths"].asLong())
        assertEquals(30, result["totals"]["days"].asLong())
        assertEquals(
            second.job.request.id.toString(),
            result["months"][1]["closing"]["jobId"].asString(),
        )
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from work_period_snapshots where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        reconcile(f, result)
    }

    @Test
    fun partialClosingDoesNotCountUntilItsFinalPublicationAndLaterHiresCannotRewriteIt() {
        val f = isolatedFixture()
        anotherEmployee(f)
        assertEquals(0, report(f)["totals"]["closedMonths"].asLong())
        val lease = begin(f)
        assertEquals(Result.Success(JobStep(1, false)), closeWorker.execute(f.actor, lease))
        assertEquals(0, report(f)["totals"]["employmentMonths"].asLong())
        assertEquals(Result.Success(JobStep(2, false)), closeWorker.execute(f.actor, lease))
        assertEquals(0, report(f)["totals"]["closedMonths"].asLong())
        assertEquals(Result.Success(JobStep(3, true)), closeWorker.execute(f.actor, lease))
        val closed = report(f)
        assertEquals("attendance-closings.v1", closed["definitionVersion"].asString())
        assertEquals(f.company.toString(), closed["companyId"].asString())
        assertEquals(1, closed["totals"]["closedMonths"].asLong())
        assertEquals(2, closed["totals"]["employmentMonths"].asLong())
        assertEquals(60, closed["totals"]["days"].asLong())
        assertEquals(closed["totals"]["original"], closed["totals"]["current"])
        assertTrue(closed["months"][0]["closing"].isNull)
        assertEquals("Asia/Jakarta", closed["months"][1]["closing"]["timezone"].asString())
        anotherEmployee(f)
        assertEquals(closed["totals"], report(f)["totals"])
        reconcile(f, closed)
        for (field in
            listOf(
                "employeeName",
                "employeeNumber",
                "personId",
                "accountId",
                "reason",
                "evidenceIds",
            )) assertFalse(closed.toString().contains(field))
    }

    @Test
    fun lateCaptureAndSuccessiveCorrectionsRetainOriginalClosingAndSelectOnlyTheLastAdjustedSnapshot() {
        val f = isolatedFixture()
        mixedCalendar(f)
        finishMonth(f, "2026-08")
        finishMonth(f, "2026-09")
        val original = report(f)
        assertEquals(2, original["totals"]["closedMonths"].asLong())
        assertEquals(2, original["totals"]["employmentMonths"].asLong())
        assertEquals(1, original["totals"]["employments"].asLong())
        assertEquals(61, original["totals"]["days"].asLong())
        assertEquals(960, original["totals"]["scheduledMinutes"].asLong())
        assertEquals(480, original["totals"]["original"]["acceptedMinutes"].asLong())
        reconcile(f, original)
        capture(f)
        assertEquals(original["totals"], report(f)["totals"])
        val body = adjustmentBody(adjustmentReview(f))
        val operation = UUID.randomUUID()
        val first = adjusted(f, body, operation)
        val changed = report(f)
        assertEquals(1, changed["totals"]["adjustedEmploymentMonths"].asLong())
        assertEquals(900, changed["totals"]["current"]["acceptedMinutes"].asLong())
        assertEquals(original["totals"]["original"], changed["totals"]["original"])
        val date = "2026-09-29"
        adjusted(f, adjustmentBody(adjustmentReview(f, date), date, absence = true))
        val latest = report(f)
        assertEquals(1, latest["totals"]["adjustedEmploymentMonths"].asLong())
        assertEquals(420, latest["totals"]["current"]["acceptedMinutes"].asLong())
        assertEquals(2, latest["totals"]["current"]["absenceDays"].asLong())
        assertEquals(960, latest["totals"]["scheduledMinutes"].asLong())
        assertEquals(original["totals"]["original"], latest["totals"]["original"])
        assertEquals(first, adjusted(f, body, operation))
        assertEquals(latest["totals"], report(f)["totals"])
        reconcile(f, latest)
    }

    @Test
    fun zeroEmployeeClosingRetainsItsPublishedIdentityAndCompleteAnnualBuckets() {
        val f = isolatedFixture()
        val created =
            reportBody(
                command(
                    f.admin,
                    "/api/v1/companies",
                    """{"code":"E${UUID.randomUUID().toString().take(8)}","name":"Empty closing","timezone":"Asia/Jakarta"}""",
                    f.csrf,
                    UUID.randomUUID(),
                )
            )
        val company = UUID.fromString(created["id"].asString())
        val empty = f.copy(company = company, actor = f.actor.copy(companyId = company))
        finishMonth(empty, "2026-09", 0)
        val result = report(empty, "2026-01", "2026-12")
        assertEquals(12, result["months"].size())
        assertEquals(1, result["totals"]["closedMonths"].asLong())
        assertEquals(0, result["totals"]["employmentMonths"].asLong())
        assertEquals(0, result["totals"]["days"].asLong())
        assertFalse(result["months"][8]["closing"].isNull)
        assertTrue(result["months"][9]["closing"].isNull)
        reconcile(empty, result)
    }

    @Test
    fun currentSourceAndReportGrantsAnnualBoundsRlsAndModuleAdmissionProtectBothSnapshots() {
        val f = closedFixture()
        for (permissions in
            listOf(
                emptySet(),
                setOf("reports.read"),
                setOf("workforce.read"),
                setOf(
                    "reports.read",
                    "workforce.team.read",
                    "attendance.self.record",
                    "attendance.correct",
                    "attendance.verify",
                    "workforce.close",
                ),
            )) {
            val response = get(reader(f, permissions), reportPath(f))
            assertEquals(403, response.statusCode(), response.body())
        }
        val viewer = reader(f, setOf("reports.read", "workforce.read"))
        assertEquals(1, report(f, client = viewer)["totals"]["employmentMonths"].asLong())
        for ((from, until) in
            listOf(
                "1999-12" to "2000-01",
                "2100-12" to "2101-01",
                "2026-01" to "2027-01",
                "2026-02" to "2026-01",
            )) {
            val response = get(viewer, reportPath(f, from, until))
            assertEquals(422, response.statusCode(), response.body())
            assertEquals(
                "invalid_report_month_range",
                json.readTree(response.body())["code"].asString(),
            )
            assertEquals(
                "2000",
                json.readTree(response.body())["parameters"]["minimumYear"].asString(),
            )
        }
        assertEquals(400, get(viewer, reportPath(f, "2026-01-01", "2026-12")).statusCode())
        val other = isolatedFixture()
        assertEquals(0, report(other)["totals"]["closedMonths"].asLong())
        assertEquals(403, get(viewer, reportPath(other)).statusCode())
        val raw =
            transactions.run(other.actor) {
                reports.aggregate(f.company, YearMonth.parse("2026-08"), YearMonth.parse("2026-10"))
            }
        assertEquals(0, (raw as Result.Success).value.totals.closedMonths)
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
        val disabled = get(viewer, reportPath(f))
        assertEquals(403, disabled.statusCode(), disabled.body())
        assertEquals("company_module_disabled", json.readTree(disabled.body())["code"].asString())
    }
}
