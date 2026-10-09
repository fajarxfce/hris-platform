package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.calculateMonthlyPayroll
import dev.fajar.hris.payroll.domain.usecases.GetPayrollRunEmployee
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PayrollRunSnapshotHttpTest : PayrollRunApiFixture() {
    @Autowired private lateinit var readEmployee: GetPayrollRunEmployee

    @Test
    fun overtimeAndHolidaySnapshotsRetainEveryInputAndCalculationAfterDatabaseRoundTrip() {
        val f = calculationFixture()
        payrollBody(
            inputSave(
                f.people,
                f.work,
                inputBody(
                    f.work,
                    1,
                    termChanges =
                        mapOf(
                            "holidayAllowance" to
                                mapOf(
                                    "kind" to "CHRISTMAS",
                                    "holidayDate" to "2026-09-20",
                                    "continuousServiceFrom" to "2026-01-01",
                                    "priorPayment" to "NOT_PAID",
                                    "reviewReference" to
                                        "Fictional date for snapshot serialization coverage",
                                )
                        ),
                ),
            )
        )
        payrollBody(inputVerify(f.people, 2))
        val overtimeId = UUID.randomUUID()
        runProbe.snapshot = {
            json.writeValueAsString(
                mapOf(
                    "employeeId" to f.people.payroll.employee,
                    "month" to "2026-09",
                    "days" to
                        (1..30).map { n ->
                            val date = LocalDate.of(2026, 9, n)
                            mapOf(
                                "workDate" to date,
                                "fact" to if (n == 1) "WORKED" else "OFF",
                                "schedule" to
                                    mapOf(
                                        "workDate" to date,
                                        "kind" to if (n == 1) "WORK" else "OFF",
                                        "holidayId" to null,
                                    ),
                                "overtime" to
                                    if (n == 1)
                                        listOf(
                                            mapOf(
                                                "requestId" to overtimeId,
                                                "revision" to 3,
                                                "approvedMinutes" to 120,
                                            )
                                        )
                                    else emptyList<Any>(),
                            )
                        },
                )
            )
        }
        val lease = beginRun(f)
        drainRun(f, lease)
        val result =
            readEmployee.execute(
                payrollActor(f.people.payroll, f.people.reviewer),
                runId(lease),
                f.people.payroll.employee,
            )
        assertTrue(result is Result.Success, result.toString())
        val record = (result as Result.Success).value
        assertNull(record.item.failure)
        val facts = requireNotNull(record.facts)
        val calculated = requireNotNull(record.calculation)
        assertEquals(Result.Success(calculated), calculateMonthlyPayroll(facts))
        assertEquals(120, facts.workDays.first().overtime.single().minutes)
        assertEquals(overtimeId, facts.workDays.first().overtime.single().requestId)
        assertEquals(2, calculated.overtime.single().calculation.segments.size)
        assertNotNull(calculated.holiday)
        val response =
            payrollBody(
                get(
                    f.people.reviewer.client,
                    runPath(f, runId(lease)) + "/employees/${f.people.payroll.employee}",
                )
            )
        assertEquals(120, response["facts"]["workDays"][0]["overtime"][0]["minutes"].asInt())
        assertEquals(
            "1.5",
            response["calculation"]["overtime"][0]["calculation"]["segments"][0]["multiplier"]
                .asString(),
        )
        assertTrue(response["calculation"]["holiday"].isObject)
    }

    @Test
    fun aFiveThousandEmployeeRunCapturesOnlyBoundedReferencesAtStart() {
        val f = processingFixture()
        payrollBody(policy(f.payroll))
        val work = closeWork(f)
        val employees = largeRoster(f, 4999) + f.payroll.employee
        val period =
            UUID.fromString(
                payrollBody(periodCreate(f, periodBody(f, employees = employees)))["id"].asString()
            )
        val prepared = CalculationFixture(f, work, period)
        val id = UUID.fromString(payrollBody(createRun(prepared))["id"].asString())
        val view = runView(prepared, id)
        assertEquals(5000, view["run"]["totalEmployees"].asInt())
        assertEquals(5001, view["job"]["totalItems"].asInt())
        assertEquals(0, view["results"]["items"].size())
        assertEquals(5000, count(f.payroll.company, "payroll_run_targets"))
        assertEquals(0, count(f.payroll.company, "payroll_run_results"))
        val lease = claimPayrollJobs(JobKind.PAYROLL_CALCULATE).single()
        assertEquals(Result.Success(JobStep(1, false)), stepRun(prepared, lease))
        assertEquals(1, count(f.payroll.company, "payroll_run_results"))
    }
}
