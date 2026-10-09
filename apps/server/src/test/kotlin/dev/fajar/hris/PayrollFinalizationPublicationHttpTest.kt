package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@org.springframework.context.annotation.Import(PayrollPublicationBudgetConfiguration::class)
class PayrollFinalizationPublicationHttpTest : PayrollFinalizationApiFixture() {
    // Seeds already calculated outcomes to measure publication independently of payroll arithmetic.
    // Only this isolated fixture transaction bypasses source triggers; the API and publication use
    // the restricted runtime connection with every constraint and trigger enabled.
    private fun retainedRun(size: Int, duplicatePerson: Boolean = false): PublicationFixture {
        require(size in 2..5000)
        val donor = calculationFixture()
        val donorRun = calculated(donor)
        val people = processingFixture()
        payrollBody(policy(people.payroll))
        val work = closeWork(people)
        val employees = largeRoster(people, size - 1) + people.payroll.employee
        val period =
            UUID.fromString(
                payrollBody(periodCreate(people, periodBody(people, employees = employees)))["id"]
                    .asString()
            )
        val c = CalculationFixture(people, work, period)
        val lease = beginRun(c)
        val run = runId(lease)
        val sql = database()
        TransactionTemplate(JdbcTransactionManager(sql.dataSource!!)).executeWithoutResult {
            sql.execute("set local session_replication_role=replica")
            if (duplicatePerson)
                sql.update(
                    "update employments set person_id=(select person_id from employments where company_id=? and id=?) where company_id=?",
                    people.payroll.company,
                    people.payroll.employee,
                    people.payroll.company,
                )
            sql.update(
                """
                insert into payroll_run_results(company_id,run_id,ordinal,job_id,status,completed_at,facts,calculation,taxable_gross,withheld,take_home)
                select t.company_id,t.run_id,t.ordinal,?,'SUCCEEDED',o.completed_at,o.facts,o.calculation,o.taxable_gross,o.withheld,o.take_home
                from payroll_run_targets t cross join payroll_run_results o where t.company_id=? and t.run_id=? and o.company_id=? and o.run_id=?
                """
                    .trimIndent(),
                lease.job.request.id,
                people.payroll.company,
                run,
                donor.people.payroll.company,
                donorRun,
            )
            sql.update(
                "update payroll_runs set status='CALCULATED',version=1,processed=total_employees,succeeded=total_employees where company_id=? and id=?",
                people.payroll.company,
                run,
            )
            sql.update(
                "update background_jobs set status='SUCCEEDED',completed_items=total_items,finished_at=now(),lease_owner=null,lease_token=null,lease_until=null,version=version+1 where id=?",
                lease.job.request.id,
            )
            sql.update(
                "update payroll_periods set status='CALCULATED',version=2 where company_id=? and id=?",
                people.payroll.company,
                period,
            )
            sql.update(
                "insert into payroll_period_changes(company_id,period_id,revision,status,actor_id,recorded_at,reason,run_id) values(?,?,2,'CALCULATED',?,now(),'Retained publication load fixture',?)",
                people.payroll.company,
                period,
                people.preparer.account,
                run,
            )
        }
        val review = pendingReview(c, run)
        payrollBody(decideReview(c, review))
        return PublicationFixture(
            c,
            run,
            review,
            payrollMember(people.payroll.company, setOf("company.read", "payroll.finalize")),
        )
    }

    @Test
    fun fiveThousandThinAssessmentsPublishWithinTheExistingTransactionBudget() {
        val f = retainedRun(5000)
        val lease = beginFinalization(f)
        assertEquals(
            Result.Success("10s"),
            transactions.run(payrollActor(f.calculation.people.payroll, f.finalizer)) {
                Result.Success(
                    runtimeJdbc.queryForObject("show statement_timeout", String::class.java)
                )
            },
        )

        val started = System.nanoTime()
        var referencesAt = started
        var joinSetting: String? = null
        finalizationProbe.afterAssessments = {
            referencesAt = System.nanoTime()
            joinSetting = runtimeJdbc.queryForObject("show enable_nestloop", String::class.java)
        }
        val result = stepFinalization(f, lease)
        val elapsed = Duration.ofNanos(System.nanoTime() - started)
        assertEquals(Result.Success(JobStep(1, true)), result)
        assertEquals(
            "on",
            joinSetting,
            "Publication settings must not leak into the caller transaction",
        )
        assertTrue(elapsed < Duration.ofSeconds(30), elapsed.toString())
        assertEquals(5000, count(f.calculation.people.payroll.company, "payroll_assessments"))
        assertEquals(5000, count(f.calculation.people.payroll.company, "mobile_sync_changes"))
        assertEquals("FINALIZED", runView(f.calculation, f.run)["run"]["status"].asString())
        println(
            "Published 5000 retained payroll references in ${elapsed.toMillis()} ms; references acquired at ${Duration.ofNanos(referencesAt-started).toMillis()} ms"
        )
        val payer =
            payrollMember(
                f.calculation.people.payroll.company,
                setOf("company.read", "payroll.pay"),
            )
        val day = LocalDate.now(clock.withZone(ZoneId.of("Asia/Jakarta")))
        val listingStarted = System.nanoTime()
        val payables =
            payrollBody(
                get(
                    payer.client,
                    "/api/v1/companies/${f.calculation.people.payroll.company}/payroll/payments/payables?from=$day&until=$day&limit=200",
                )
            )
        assertEquals(200, payables["items"].size())
        assertFalse(payables["nextCursor"].isNull)
        println(
            "Listed 200 payable references from 5000 published assessments in ${Duration.ofNanos(System.nanoTime()-listingStarted).toMillis()} ms"
        )
    }

    @Test
    fun twoEmploymentIdsCannotAssessTheSamePersonTwice() {
        val f = retainedRun(2, duplicatePerson = true)
        payrollError(startFinalization(f), 409, "payroll_person_duplicated")
        assertEquals(0, count(f.calculation.people.payroll.company, "payroll_finalizations"))
        assertEquals(0, count(f.calculation.people.payroll.company, "payroll_assessments"))
    }

    @Test
    fun positiveHolidayAssessmentRetainsTheReviewedKindAndYear() {
        val c = calculationFixture()
        payrollBody(
            inputSave(
                c.people,
                c.work,
                inputBody(
                    c.work,
                    1,
                    termChanges =
                        mapOf(
                            "dayResolutions" to runPaidDays(),
                            "holidayAllowance" to
                                mapOf(
                                    "kind" to "CHRISTMAS",
                                    "holidayDate" to "2026-09-20",
                                    "continuousServiceFrom" to "2026-01-01",
                                    "priorPayment" to "NOT_PAID",
                                    "reviewReference" to
                                        "Fictional holiday date for publication verification",
                                ),
                        ),
                ),
            )
        )
        payrollBody(inputVerify(c.people, 2))
        val f = approved(c)
        val lease = beginFinalization(f)
        assertEquals(Result.Success(JobStep(1, true)), stepFinalization(f, lease))
        val row =
            database()
                .queryForMap(
                    "select holiday_kind,holiday_year from payroll_assessments where company_id=?",
                    c.people.payroll.company,
                )
        assertEquals("CHRISTMAS", row["holiday_kind"])
        assertEquals(2026, row["holiday_year"])
    }
}
