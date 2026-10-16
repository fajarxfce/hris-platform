package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.repositories.PayrollCalculationSourceRepository
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.jooq.JSONB
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class PayrollAttendanceCalculationHttpTest : PayrollAttendanceSourceApiFixture() {
    @Autowired private lateinit var sources: PayrollCalculationSourceRepository

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun laterCorrectionsCannotReplaceTheOriginalOrAdjustedSourceOfAnActiveRun(adjusted: Boolean) {
        val f = calculationFixture()
        val selected =
            if (adjusted) lateAttendance(f.people).also { selectedInput(f, it) } else null
        val original =
            database()
                .queryForObject(
                    "select payload::text from work_period_snapshots where company_id=? and job_id=? and employment_id=?",
                    String::class.java,
                    f.people.payroll.company,
                    f.work.job,
                    f.people.payroll.employee,
                )
        val lease = beginRun(f)
        val later = lateAttendance(f.people, absence = adjusted)
        drainRun(f, lease)
        val result = result(f, runId(lease))
        assertTrue(result["item"]["failure"].isNull, result.toString())
        assertEquals(
            selected?.toString(),
            result["item"]["target"]["workAdjustmentId"].takeUnless { it.isNull }?.asString(),
        )
        assertEquals(
            selected?.toString(),
            result["facts"]["workAdjustmentId"].takeUnless { it.isNull }?.asString(),
        )
        assertEquals(
            if (adjusted) "WORKED" else "UNASSIGNED",
            result["facts"]["workDays"][0]["attendance"].asString(),
        )
        assertEquals(later.toString(), workSource(f.people)["adjustment"]["id"].asString())
        assertEquals(
            original,
            database()
                .queryForObject(
                    "select payload::text from work_period_snapshots where company_id=? and job_id=? and employment_id=?",
                    String::class.java,
                    f.people.payroll.company,
                    f.work.job,
                    f.people.payroll.employee,
                ),
        )
        payrollError(
            inputSave(
                f.people,
                f.work,
                inputBody(
                    f.work,
                    if (adjusted) 3 else 1,
                    changes = mapOf("workAdjustmentId" to later),
                ),
            ),
            409,
            "payroll_period_frozen",
        )
    }

    @Test
    fun aCorrectionAfterInputVerificationMustBeReviewedBeforeSuccessfulCalculation() {
        val f = calculationFixture()
        val late = lateAttendance(f.people)
        val first = beginRun(f)
        drainRun(f, first)
        val failed = result(f, runId(first))
        assertEquals("stale_workforce_version", failed["item"]["failure"]["code"].asString())
        assertEquals(late.toString(), failed["item"]["target"]["workAdjustmentId"].asString())
        assertTrue(failed["facts"].isNull)
        payrollBody(abandonRun(f, runId(first)))
        selectedInput(f, late)
        val run = UUID.randomUUID()
        payrollBody(createRun(f, runBody(f, run, mapOf("expectedPeriodVersion" to 3))))
        val lease =
            claimPayrollJobs(dev.fajar.hris.jobs.domain.entities.JobKind.PAYROLL_CALCULATE).single {
                runId(it) == run
            }
        drainRun(f, lease)
        assertTrue(result(f, run)["item"]["failure"].isNull)
        assertEquals(late.toString(), result(f, run)["facts"]["workAdjustmentId"].asString())
    }

    @Test
    fun targetAndResultConstraintsRejectForgedSourceReferencesWithoutAdvancingProgress() {
        val f = calculationFixture()
        val adjustment = lateAttendance(f.people)
        selectedInput(f, adjustment)
        val key = UUID.randomUUID()
        val body = runBody(f)
        runProbe.targetSnapshot = { it.first().workAdjustmentId = null }
        assertEquals(409, createRun(f, body, key).statusCode())
        runProbe.clear()
        assertEquals(0, count(f.people.payroll.company, "payroll_runs"))
        assertEquals(0, count(f.people.payroll.company, "payroll_run_targets"))
        val run = UUID.fromString(payrollBody(createRun(f, body, key))["id"].asString())
        val lease =
            claimPayrollJobs(dev.fajar.hris.jobs.domain.entities.JobKind.PAYROLL_CALCULATE).single {
                runId(it) == run
            }
        runProbe.beforeResult = { row ->
            row.facts =
                JSONB.valueOf(
                    row.facts.data().replace(adjustment.toString(), UUID.randomUUID().toString())
                )
        }
        assertInstanceOf(Result.Failed::class.java, stepRun(f, lease))
        assertEquals(0, count(f.people.payroll.company, "payroll_run_results"))
        assertEquals(0, runView(f, run)["run"]["processed"].asInt())
        runProbe.clear()
        drainRun(f, lease)
        assertTrue(result(f, run)["item"]["failure"].isNull)
    }

    @Test
    fun exactSourceReadsNeverFallBackAcrossCompanyEmployeeOrClosingJob() {
        val f = calculationFixture()
        val adjustment = lateAttendance(f.people)
        val other = calculationFixture()
        val foreign = lateAttendance(other.people)
        val actor = payrollActor(f.people.payroll, f.people.reviewer)
        val month = YearMonth.of(2026, 9)
        for ((job, employee, id) in
            listOf(
                Triple(f.work.job, f.people.payroll.employee, foreign),
                Triple(f.work.job, other.people.payroll.employee, adjustment),
                Triple(other.work.job, f.people.payroll.employee, adjustment),
                Triple(f.work.job, f.people.payroll.employee, UUID.randomUUID()),
            )) assertEquals(
            Result.Success(null),
            transactions.run(actor) {
                sources.workDays(f.people.payroll.company, job, employee, month, id)
            },
        )
        val exact =
            transactions.run(actor) {
                sources.workDays(
                    f.people.payroll.company,
                    f.work.job,
                    f.people.payroll.employee,
                    month,
                    adjustment,
                )
            }
        assertTrue(exact is Result.Success, exact.toString())
        assertEquals("WORKED", (exact as Result.Success).value!!.first().attendance.name)
    }

    @Test
    fun invalidSnapshotMappingAndInterruptedReadsReleaseTheOwnedWorkerTransaction() {
        val f = calculationFixture()
        val adjustment = lateAttendance(f.people)
        selectedInput(f, adjustment)
        val lease = beginRun(f)
        runProbe.snapshot = { raw -> raw!!.replace("\"2026-09-01\"", "\"2026-09-02\"") }
        assertInstanceOf(Result.Failed::class.java, stepRun(f, lease))
        assertEquals(0, count(f.people.payroll.company, "payroll_run_results"))
        runProbe.snapshot = { throw InterruptedException("Cancelled evidence read") }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<Result<*>> { stepRun(f, lease) }
            assertInstanceOf(
                InterruptedException::class.java,
                assertThrows(ExecutionException::class.java) { pending.get(10, TimeUnit.SECONDS) }
                    .cause,
            )
        }
        runProbe.clear()
        assertEquals(0, runView(f, runId(lease))["run"]["processed"].asInt())
        drainRun(f, lease)
        assertTrue(result(f, runId(lease))["item"]["failure"].isNull)
    }
}
