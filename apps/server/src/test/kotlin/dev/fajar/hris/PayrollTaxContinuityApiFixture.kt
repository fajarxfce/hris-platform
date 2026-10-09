package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*

abstract class PayrollTaxContinuityApiFixture : PayrollFinalizationApiFixture() {
    protected fun renewProcessing(f: ProcessingFixture): ProcessingFixture {
        fun renew(member: PayrollMember) =
            member.copy(csrf = login(member.client, "${member.account}@example.test"))
        return f.copy(
            payroll =
                f.payroll.copy(
                    adminCsrf = login(f.payroll.admin),
                    operator = renew(f.payroll.operator),
                    owner = renew(f.payroll.owner),
                ),
            preparer = renew(f.preparer),
            reviewer = renew(f.reviewer),
            actor = f.actor.copy(authenticatedAt = clock.instant()),
        )
    }

    protected fun followingMonth(
        prior: CalculationFixture,
        month: YearMonth,
        configure: (ProcessingFixture) -> Unit = {},
    ): CalculationFixture {
        clock.set(month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant())
        val f = renewProcessing(prior.people)
        configure(f)
        val company = f.payroll.company
        val closed =
            payrollBody(
                command(
                    f.payroll.admin,
                    "/api/v1/companies/$company/workforce/periods/$month/close",
                    """{"expectedVersion":0,"reason":"Verified monthly workforce"}""",
                    f.payroll.adminCsrf,
                    UUID.randomUUID(),
                )
            )
        val id = UUID.fromString(closed["id"].asString())
        val lease = claimPayrollJobs(JobKind.WORKFORCE_CLOSE).single { it.job.request.id == id }
        var done = false
        repeat(lease.job.request.totalItems) {
            if (!done) {
                val step = advanceWork.execute(f.actor, lease)
                assertTrue(step is Result.Success, step.toString())
                done = (step as Result.Success).value.finished
            }
        }
        assertTrue(done)
        val source =
            payrollBody(
                get(
                    f.preparer.client,
                    "/api/v1/companies/$company/payroll/employees/${f.payroll.employee}/work-source?month=$month",
                )
            )
        val work = WorkSource(id, source["version"].asLong())
        val path =
            "/api/v1/companies/$company/payroll/employees/${f.payroll.employee}/inputs/$month"
        val days =
            (1..month.lengthOfMonth()).map { day ->
                mapOf(
                    "workDate" to month.atDay(day).toString(),
                    "portion" to "FULL",
                    "disposition" to "PAID",
                    "reference" to "Reviewed unassigned calendar day",
                )
            }
        payrollBody(
            command(
                f.preparer.client,
                path,
                inputBody(work, termChanges = mapOf("dayResolutions" to days)),
                f.preparer.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        payrollBody(
            command(
                f.reviewer.client,
                "$path/verify",
                """{"expectedVersion":0,"reason":"Independent monthly input review"}""",
                f.reviewer.csrf,
                UUID.randomUUID(),
            )
        )
        val period =
            payrollBody(
                periodCreate(
                    f,
                    periodBody(
                        f,
                        changes =
                            mapOf(
                                "earningsMonth" to month.toString(),
                                "plannedPaymentDate" to month.plusMonths(1).atDay(2).toString(),
                            ),
                    ),
                )
            )
        return CalculationFixture(f, work, UUID.fromString(period["id"].asString()))
    }

    protected fun beginTaxRun(f: CalculationFixture): JobLease {
        val month =
            YearMonth.from(
                database()
                    .queryForObject(
                        "select earnings_month from payroll_periods where company_id=? and id=?",
                        LocalDate::class.java,
                        f.people.payroll.company,
                        f.period,
                    )!!
            )
        val version =
            database()
                .queryForObject(
                    "select version from payroll_periods where company_id=? and id=?",
                    Long::class.java,
                    f.people.payroll.company,
                    f.period,
                )!!
        val policy =
            database()
                .queryForObject(
                    "select revision from payroll_policy_revisions where company_id=? and effective_from<=? order by effective_from desc,revision desc limit 1",
                    Long::class.java,
                    f.people.payroll.company,
                    month.atDay(1),
                )!!
        val id =
            UUID.fromString(
                payrollBody(
                        createRun(
                            f,
                            runBody(
                                f,
                                changes =
                                    mapOf(
                                        "incomeDueDate" to month.atEndOfMonth().toString(),
                                        "expectedPeriodVersion" to version,
                                        "expectedPolicyVersion" to policy,
                                    ),
                            ),
                        )
                    )["id"]
                    .asString()
            )
        return claimPayrollJobs(JobKind.PAYROLL_CALCULATE).single {
            it.job.request.values["runId"] == id.toString()
        }
    }

    protected fun publishTaxRun(
        f: CalculationFixture,
        lease: JobLease,
    ): Pair<PublicationFixture, UUID> {
        drainRun(f, lease)
        val run = runId(lease)
        val view = runView(f, run)
        assertEquals(0, view["run"]["failed"].asInt(), view.toString())
        val review = UUID.fromString(payrollBody(submitReview(f, run))["id"].asString())
        payrollBody(decideReview(f, review))
        val publication =
            PublicationFixture(
                f,
                run,
                review,
                payrollMember(f.people.payroll.company, setOf("company.read", "payroll.finalize")),
            )
        val finalize = beginFinalization(publication)
        assertEquals(Result.Success(JobStep(1, true)), stepFinalization(publication, finalize))
        val assessment =
            database()
                .queryForObject(
                    "select id from payroll_assessments where company_id=? and run_id=?",
                    UUID::class.java,
                    f.people.payroll.company,
                    run,
                )!!
        return publication to assessment
    }
}
