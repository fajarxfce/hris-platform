package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.payroll.domain.usecases.*
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(PayrollRunProbeConfiguration::class)
abstract class PayrollRunApiFixture : PayrollPeriodApiFixture() {
    @Autowired protected lateinit var runProbe: PayrollRunProbe
    @Autowired protected lateinit var advanceRun: AdvancePayrollCalculation
    @Autowired protected lateinit var abortRun: AbortPayrollCalculation
    @Autowired protected lateinit var startRun: StartPayrollCalculation

    protected data class CalculationFixture(
        val people: ProcessingFixture,
        val work: WorkSource,
        val period: UUID,
    )

    @AfterEach
    fun clearRunHooks() {
        runProbe.clear()
    }

    protected fun calculationFixture(
        extraEmployees: Int = 0,
        prepare: Boolean = true,
    ): CalculationFixture {
        val f = processingFixture()
        val extra = if (extraEmployees == 0) emptySet() else largeRoster(f, extraEmployees)
        payrollBody(policy(f.payroll))
        if (prepare)
            payrollBody(
                compensation(
                    f.payroll,
                    compensationBody(termChanges = mapOf("payBasis" to runPayBasis())),
                )
            )
        val work = closeWork(f)
        if (prepare) {
            payrollBody(
                inputSave(
                    f,
                    work,
                    inputBody(work, termChanges = mapOf("dayResolutions" to runPaidDays())),
                )
            )
            payrollBody(inputVerify(f))
            payrollBody(saveRunOpening(f))
            payrollBody(verifyRunOpening(f))
        }
        val period =
            UUID.fromString(
                payrollBody(periodCreate(f, periodBody(f, employees = extra + f.payroll.employee)))[
                        "id"]
                    .asString()
            )
        return CalculationFixture(f, work, period)
    }

    protected fun runPayBasis(): Map<String, Any?> =
        mapOf(
            "overtimeRuleId" to "ID-PP35-2021-v1",
            "holidayAllowanceRuleId" to "ID-PERMENAKER6-2016-v1",
            "proration" to "CALENDAR_DAYS",
            "workWeek" to "FIVE_DAYS",
            "shortestWorkDay" to null,
            "overtimeEligibility" to "ELIGIBLE",
            "overtimeExemptionReference" to null,
            "regularNonFixedWage" to "0",
            "serviceMonthConvention" to "CALENDAR_FRACTION",
            "rounding" to "HALF_UP",
            "reviewReference" to "Reviewed earning conventions",
        )

    protected fun runPaidDays() =
        (1..30).map { day ->
            mapOf(
                "workDate" to java.time.LocalDate.of(2026, 9, day).toString(),
                "portion" to "FULL",
                "disposition" to "PAID",
                "reference" to "Reviewed unassigned calendar day",
            )
        }

    protected fun runOpeningBody(version: Long? = null) =
        json.writeValueAsString(
            mapOf(
                "expectedVersion" to version,
                "expectedEmploymentVersion" to 0,
                "reason" to "Review opening history for September",
                "terms" to
                    mapOf(
                        "throughMonth" to 8,
                        "residency" to "RESIDENT",
                        "ptkp" to "K0",
                        "reference" to "Fictional reviewed August records",
                        "history" to
                            mapOf(
                                "taxableGross" to "80000000",
                                "retirementContributions" to "800000",
                                "qualifiedDonations" to "0",
                                "withheld" to "2000000",
                                "employmentMonths" to 8,
                                "previousEmployerNet" to "0",
                                "previousEmployerWithheld" to "0",
                            ),
                    ),
            )
        )

    protected fun saveRunOpening(f: ProcessingFixture, version: Long? = null) =
        command(
            f.preparer.client,
            "/api/v1/companies/${f.payroll.company}/payroll/employees/${f.payroll.employee}/tax-openings/2026",
            runOpeningBody(version),
            f.preparer.csrf,
            UUID.randomUUID(),
            "PUT",
        )

    protected fun verifyRunOpening(f: ProcessingFixture, version: Long = 0) =
        command(
            f.reviewer.client,
            "/api/v1/companies/${f.payroll.company}/payroll/employees/${f.payroll.employee}/tax-openings/2026/verify",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Independent historical review")
            ),
            f.reviewer.csrf,
            UUID.randomUUID(),
        )

    protected fun runPath(f: CalculationFixture, id: UUID) =
        "/api/v1/companies/${f.people.payroll.company}/payroll/runs/$id"

    protected fun runBody(
        f: CalculationFixture,
        id: UUID = UUID.randomUUID(),
        changes: Map<String, Any?> = emptyMap(),
    ) =
        json.writeValueAsString(
            mapOf(
                "id" to id,
                "incomeDueDate" to "2026-09-30",
                "expectedPeriodVersion" to 0,
                "expectedWorkPeriodVersion" to f.work.version,
                "expectedPolicyVersion" to 0,
                "reviewReference" to "Reviewed September salary liability",
                "reason" to "Calculate monthly payroll",
            ) + changes
        )

    protected fun createRun(
        f: CalculationFixture,
        body: String = runBody(f),
        key: UUID = UUID.randomUUID(),
        member: PayrollMember = f.people.preparer,
    ) = command(member.client, periodsPath(f.people) + "/${f.period}/runs", body, member.csrf, key)

    protected fun beginRun(f: CalculationFixture): JobLease {
        val id = UUID.fromString(payrollBody(createRun(f))["id"].asString())
        return claimPayrollJobs(JobKind.PAYROLL_CALCULATE).single {
            it.job.request.values["runId"] == id.toString()
        }
    }

    protected fun runId(lease: JobLease) =
        UUID.fromString(lease.job.request.values.getValue("runId"))

    protected fun runView(f: CalculationFixture, id: UUID, suffix: String = "") =
        payrollBody(get(f.people.reviewer.client, runPath(f, id) + suffix))

    protected fun stepRun(
        f: CalculationFixture,
        lease: JobLease,
        actor: Actor = payrollActor(f.people.payroll, f.people.preparer),
    ) = advanceRun.execute(actor, lease)

    protected fun drainRun(f: CalculationFixture, lease: JobLease) {
        var finished = false
        repeat(lease.job.request.totalItems) {
            if (!finished) {
                val result = stepRun(f, lease)
                assertTrue(result is Result.Success, result.toString())
                finished = (result as Result.Success).value.finished
            }
        }
        assertTrue(finished, "A run must terminate within its fixed target count")
    }

    protected fun stopRun(f: CalculationFixture, lease: JobLease) {
        val job =
            payrollBody(
                get(
                    f.people.payroll.admin,
                    "/api/v1/companies/${f.people.payroll.company}/jobs/${lease.job.request.id}",
                )
            )
        val cancelled =
            command(
                f.people.payroll.admin,
                "/api/v1/companies/${f.people.payroll.company}/jobs/${lease.job.request.id}/cancel",
                json.writeValueAsString(mapOf("expectedVersion" to job["version"].asLong())),
                f.people.payroll.adminCsrf,
                UUID.randomUUID(),
            )
        payrollBody(cancelled)
        assertEquals(
            Result.Success(Unit),
            abortRun.execute(lease, Failure(FailureKind.CONFLICT, "job_cancellation_requested")),
        )
    }

    protected fun resumeRun(
        f: CalculationFixture,
        id: UUID,
        version: Long = 1,
        key: UUID = UUID.randomUUID(),
        member: PayrollMember = f.people.preparer,
    ) =
        command(
            member.client,
            runPath(f, id) + "/resume",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Resume retained payroll results")
            ),
            member.csrf,
            key,
        )

    protected fun abandonRun(
        f: CalculationFixture,
        id: UUID,
        version: Long = 1,
        periodVersion: Long = 2,
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            f.people.preparer.client,
            runPath(f, id) + "/abandon",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to version,
                    "expectedPeriodVersion" to periodVersion,
                    "reason" to "Correct payroll sources",
                )
            ),
            f.people.preparer.csrf,
            key,
        )
}
