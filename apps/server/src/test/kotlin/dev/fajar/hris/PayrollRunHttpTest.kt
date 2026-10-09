package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollRunHttpTest : PayrollRunApiFixture() {
    @Test
    fun workerRetainsReviewedFactsDetailedAmountsAndPagedLocalizableOutcomes() {
        val f = calculationFixture(extraEmployees = 2)
        val lease = beginRun(f)
        val id = runId(lease)
        assertEquals(4, lease.job.request.totalItems)
        drainRun(f, lease)
        val view = runView(f, id, "?limit=1")
        assertEquals("CALCULATED", view["run"]["status"].asString())
        assertEquals(3, view["run"]["processed"].asInt())
        assertEquals(1, view["run"]["succeeded"].asInt())
        assertEquals(2, view["run"]["failed"].asInt())
        assertEquals("SUCCEEDED", view["job"]["status"].asString())
        assertEquals(4, view["job"]["completedItems"].asInt())
        assertEquals("2026-09", view["run"]["taxMonth"].asString())
        assertEquals("2026-10-02", view["run"]["plannedPaymentDate"].asString())
        var page = view["results"]
        val seen = mutableSetOf<String>()
        repeat(3) {
            assertEquals(1, page["items"].size())
            val row = page["items"][0]
            assertFalse(row.has("facts"))
            assertFalse(row.has("calculation"))
            assertTrue(seen.add(row["target"]["employeeId"].asString()))
            if (!row["failure"].isNull) {
                assertEquals("payroll_compensation_required", row["failure"]["code"].asString())
                assertEquals("CONFLICT", row["failure"]["kind"].asString())
                assertTrue(row["failure"]["parameters"].isObject)
            }
            val after = page["nextCursor"].takeUnless { it.isNull }?.asString()
            if (after != null) page = runView(f, id, "?limit=1&after=$after")["results"]
        }
        assertTrue(page["nextCursor"].isNull)
        val detail =
            payrollBody(
                get(
                    f.people.reviewer.client,
                    runPath(f, id) + "/employees/${f.people.payroll.employee}",
                )
            )
        assertTrue(detail["item"]["failure"].isNull)
        assertEquals(30, detail["facts"]["workDays"].size())
        assertEquals(8, detail["facts"]["taxHistory"]["throughMonth"].asInt())
        val calculation = detail["calculation"]
        assertEquals(3, calculation["earnings"].size())
        assertEquals(
            BigDecimal("11500000"),
            calculation["earnings"]
                .iterator()
                .asSequence()
                .map { BigDecimal(it["amount"].asString()) }
                .reduce(BigDecimal::add)
                .stripTrailingZeros()
                .setScale(0),
        )
        assertEquals(
            BigDecimal("11976700"),
            BigDecimal(calculation["tax"]["taxableGross"].asString()).setScale(0),
        )
        assertEquals(
            BigDecimal("479068"),
            BigDecimal(calculation["tax"]["withheld"].asString()).setScale(0),
        )
        assertEquals(
            BigDecimal("10605932"),
            BigDecimal(calculation["tax"]["takeHome"].asString()).setScale(0),
        )
        assertEquals(
            BigDecimal(detail["item"]["takeHome"].asString()),
            BigDecimal(calculation["tax"]["takeHome"].asString()).setScale(2),
        )
        val period =
            payrollBody(get(f.people.reviewer.client, periodsPath(f.people) + "/${f.period}"))
        assertEquals("CALCULATED", period["period"]["status"].asString())
        assertEquals(id.toString(), period["period"]["currentRunId"].asString())
        assertEquals(3, period["history"]["items"].size())
        assertEquals(
            1,
            payrollBody(get(f.people.reviewer.client, periodsPath(f.people) + "/${f.period}/runs"))[
                    "items"]
                .size(),
        )
    }

    @Test
    fun competingCommandsReplayExactlyAndCompetingStepsNeverDuplicateResults() {
        val f = calculationFixture()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val body = runBody(f, id)
        Executors.newFixedThreadPool(3).use { pool ->
            val responses =
                (1..3)
                    .map {
                        pool.submit<java.net.http.HttpResponse<String>> { createRun(f, body, key) }
                    }
                    .map { payrollBody(it.get(10, TimeUnit.SECONDS)) }
            assertEquals(1, responses.toSet().size)
        }
        assertEquals(1, count(f.people.payroll.company, "payroll_runs"))
        payrollError(
            createRun(f, runBody(f, changes = mapOf("expectedPeriodVersion" to 1))),
            409,
            "payroll_period_not_draft",
        )
        val lease = claimPayrollJobs(JobKind.PAYROLL_CALCULATE).single()
        Executors.newFixedThreadPool(2).use { pool ->
            val results =
                (1..2)
                    .map { pool.submit<Result<JobStep>> { stepRun(f, lease) } }
                    .map { it.get(10, TimeUnit.SECONDS) }
            assertTrue(results.all { it is Result.Success }, results.toString())
        }
        assertEquals(1, count(f.people.payroll.company, "payroll_run_results"))
        assertEquals("CALCULATED", runView(f, id)["run"]["status"].asString())
        payrollBody(createRun(f, body, key))
        payrollError(
            createRun(f, runBody(f, id, mapOf("reason" to "Changed payload")), key),
            409,
            "operation_payload_mismatch",
        )
    }

    @Test
    fun observationsDateBoundsAndPermissionFailuresLeaveNoRunOrCheckpoint() {
        val f = calculationFixture()
        val key = UUID.randomUUID()
        for ((change, code) in
            listOf(
                mapOf("expectedPeriodVersion" to 1) to "stale_version",
                mapOf("expectedWorkPeriodVersion" to f.work.version + 1) to
                    "stale_workforce_version",
                mapOf("expectedPolicyVersion" to 1) to "stale_policy_version",
                mapOf("incomeDueDate" to "2026-10-02") to "payroll_tax_month_review_required",
            )) {
            payrollError(createRun(f, runBody(f, changes = change), key), 409, code)
        }
        payrollError(createRun(f, member = f.people.reviewer), 403, "access_denied")
        assertEquals(0, count(f.people.payroll.company, "payroll_runs"))
        payrollBody(createRun(f, key = key))
        val id = UUID.fromString(runViewId(f))
        payrollError(
            get(f.people.reviewer.client, runPath(f, id) + "?limit=201"),
            422,
            "invalid_page",
        )
    }

    private fun runViewId(f: CalculationFixture): String =
        payrollBody(get(f.people.reviewer.client, periodsPath(f.people) + "/${f.period}/runs"))[
                "items"][0]["id"]
            .asString()

    @Test
    fun malformedStoredEvidenceStopsTheStepWithoutCreatingABusinessFailure() {
        val f = calculationFixture()
        val lease = beginRun(f)
        runProbe.snapshot = { """{"month":"bad-date","employeeId":"invalid","days":[]}""" }
        val result = stepRun(f, lease)
        assertTrue(result is Result.Failed, result.toString())
        assertEquals(0, count(f.people.payroll.company, "payroll_run_results"))
        assertEquals(0, runView(f, runId(lease))["job"]["completedItems"].asInt())
        runProbe.clear()
        drainRun(f, lease)
    }
}
