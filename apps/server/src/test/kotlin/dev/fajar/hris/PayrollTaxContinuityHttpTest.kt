package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.PayrollTaxAssessment
import dev.fajar.hris.payroll.domain.repositories.PayrollAssessmentRepository
import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID
import org.jooq.JSONB
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.node.ObjectNode

class PayrollTaxContinuityHttpTest : PayrollTaxContinuityApiFixture() {
    @Autowired private lateinit var assessments: PayrollAssessmentRepository

    private fun history(f: CalculationFixture, id: UUID): PayrollTaxAssessment {
        val result =
            transactions.run(payrollActor(f.people.payroll, f.people.reviewer)) {
                assessments.taxHistory(f.people.payroll.company, id)
            }
        assertTrue(result is Result.Success, result.toString())
        return requireNotNull((result as Result.Success).value)
    }

    @Test
    fun publishedMonthlyResultsCarryForwardWithoutEditingVerifiedOpeningHistory() {
        var f = calculationFixture()
        reviewTemplate(f)
        val (_, firstId) = publishTaxRun(f, beginTaxRun(f))
        var previous = history(f, firstId)
        val opening =
            database()
                .queryForObject(
                    "select terms::text from payroll_tax_opening_revisions where company_id=? and opening_id=? and revision=?",
                    String::class.java,
                    f.people.payroll.company,
                    previous.openingId,
                    previous.openingRevision,
                )
        for (month in 10..12) {
            f = followingMonth(f, YearMonth.of(2026, month))
            val (published, id) = publishTaxRun(f, beginTaxRun(f))
            val current = history(f, id)
            val target = runView(f, published.run)["results"]["items"][0]["target"]
            assertEquals(previous.id.toString(), target["previousAssessmentId"].asString())
            assertEquals(previous.openingId, current.openingId)
            assertEquals(previous.openingRevision, current.openingRevision)
            assertEquals(
                0,
                (previous.input.history.taxableGross + previous.calculation.taxableGross).compareTo(
                    current.input.history.taxableGross
                ),
            )
            assertEquals(
                0,
                (previous.input.history.withheld + previous.calculation.withheld).compareTo(
                    current.input.history.withheld
                ),
            )
            assertEquals(
                previous.input.history.employmentMonths + 1,
                current.input.history.employmentMonths,
            )
            assertEquals(month == 12, current.input.finalPeriod)
            previous = current
        }
        assertEquals(
            opening,
            database()
                .queryForObject(
                    "select terms::text from payroll_tax_opening_revisions where company_id=? and opening_id=? and revision=?",
                    String::class.java,
                    f.people.payroll.company,
                    previous.openingId,
                    previous.openingRevision,
                ),
        )
        assertEquals(4, count(f.people.payroll.company, "payroll_assessments"))
        assertEquals(2, count(f.people.payroll.company, "payroll_tax_opening_revisions"))
    }

    @Test
    fun missingAndOutOfOrderMonthsAreRetainedAsEmployeeFailures() {
        for (month in listOf(11, 8)) {
            val initial = calculationFixture()
            reviewTemplate(initial)
            val (_, id) = publishTaxRun(initial, beginTaxRun(initial))
            val next = followingMonth(initial, YearMonth.of(2026, month))
            val lease = beginTaxRun(next)
            drainRun(next, lease)
            val view = runView(next, runId(lease))
            assertEquals(1, view["run"]["failed"].asInt(), view.toString())
            val item = view["results"]["items"][0]
            assertEquals(id.toString(), item["target"]["previousAssessmentId"].asString())
            assertEquals(
                if (month == 11) "payroll_tax_history_incomplete"
                else "payroll_tax_history_out_of_order",
                item["failure"]["code"].asString(),
            )
            payrollError(submitReview(next, runId(lease)), 409, "payroll_calculation_has_failures")
            assertEquals(1, count(initial.people.payroll.company, "payroll_assessments"))
        }
    }

    @Test
    fun aNewYearDoesNotSelectThePreviousYearAssessmentOrOpening() {
        val initial = calculationFixture()
        reviewTemplate(initial)
        publishTaxRun(initial, beginTaxRun(initial))
        val next =
            followingMonth(initial, YearMonth.of(2027, 1)) { f ->
                payrollBody(
                    policy(
                        f.payroll,
                        policyBody(
                            0,
                            mapOf("effectiveFrom" to "2027-01", "effectiveUntil" to "2027-12"),
                        ),
                    )
                )
            }
        val lease = beginTaxRun(next)
        drainRun(next, lease)
        val view = runView(next, runId(lease))
        val item = view["results"]["items"][0]
        assertTrue(item["target"]["previousAssessmentId"].isNull)
        assertTrue(item["target"]["taxOpeningId"].isNull)
        assertEquals("payroll_tax_opening_required", item["failure"]["code"].asString())
    }

    @Test
    fun aTargetCannotOmitItsCurrentPublishedReference() {
        val initial = calculationFixture()
        reviewTemplate(initial)
        publishTaxRun(initial, beginTaxRun(initial))
        val next = followingMonth(initial, YearMonth.of(2026, 10))
        runProbe.targetSnapshot = { it.single().previousAssessmentId = null }
        try {
            val response =
                createRun(next, runBody(next, changes = mapOf("incomeDueDate" to "2026-10-31")))
            assertEquals(409, response.statusCode(), response.body())
        } finally {
            runProbe.targetSnapshot = null
        }
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from payroll_runs where company_id=? and period_id=?",
                    Int::class.java,
                    next.people.payroll.company,
                    next.period,
                ),
        )
        val lease = beginTaxRun(next)
        drainRun(next, lease)
        assertEquals(0, runView(next, runId(lease))["run"]["failed"].asInt())
    }

    @Test
    fun tamperedOpeningOrContinuationArithmeticRollsBackTheWholeCheckpoint() {
        for (continued in listOf(false, true)) {
            val initial = calculationFixture()
            val f =
                if (continued) {
                    reviewTemplate(initial)
                    publishTaxRun(initial, beginTaxRun(initial))
                    followingMonth(initial, YearMonth.of(2026, 10))
                } else initial
            val lease = beginTaxRun(f)
            runProbe.beforeResult = { row ->
                val facts = json.readTree(row.facts!!.data())
                (facts["taxHistory"]["history"] as ObjectNode).put("withheld", "0")
                row.facts = JSONB.valueOf(json.writeValueAsString(facts))
            }
            try {
                val result = stepRun(f, lease)
                assertTrue(result is Result.Failed, result.toString())
                assertEquals(FailureKind.CONFLICT, (result as Result.Failed).failure.kind)
            } finally {
                runProbe.beforeResult = null
            }
            val before = runView(f, runId(lease))
            assertEquals(0, before["run"]["processed"].asInt())
            assertEquals(0, before["results"]["items"].size())
            drainRun(f, lease)
            val details = runView(f, runId(lease), "/employees/${f.people.payroll.employee}")
            assertTrue(
                BigDecimal(details["calculation"]["taxInput"]["history"]["withheld"].asString())
                    .signum() > 0
            )
        }
    }

    @Test
    fun assessmentReadsRemainCompanyScopedAndOnlyPublishedResultsExist() {
        val first = calculationFixture()
        reviewTemplate(first)
        val lease = beginTaxRun(first)
        assertTrue(stepRun(first, lease) is Result.Success)
        assertEquals(0, count(first.people.payroll.company, "payroll_assessments"))
        val (_, id) = publishTaxRun(first, lease)
        val other = calculationFixture()
        val isolated =
            transactions.run(payrollActor(other.people.payroll, other.people.preparer)) {
                assessments.taxHistory(first.people.payroll.company, id)
            }
        assertEquals(Result.Success<PayrollTaxAssessment?>(null), isolated)
        assertEquals(id, history(first, id).id)
    }

    @Test
    fun aLaterMonthlyRunCannotPublishTheSameHolidayAllowanceAgain() {
        fun holiday(month: Int) =
            mapOf(
                "kind" to "CHRISTMAS",
                "holidayDate" to YearMonth.of(2026, month).atDay(20).toString(),
                "continuousServiceFrom" to "2026-01-01",
                "priorPayment" to "NOT_PAID",
                "reviewReference" to
                    "Fictional reviewed holiday date for duplicate assessment verification",
            )
        val first = calculationFixture()
        payrollBody(
            inputSave(
                first.people,
                first.work,
                inputBody(
                    first.work,
                    1,
                    termChanges =
                        mapOf("dayResolutions" to runPaidDays(), "holidayAllowance" to holiday(9)),
                ),
            )
        )
        payrollBody(inputVerify(first.people, 2))
        reviewTemplate(first)
        publishTaxRun(first, beginTaxRun(first))
        val next = followingMonth(first, YearMonth.of(2026, 10))
        val path =
            "/api/v1/companies/${next.people.payroll.company}/payroll/employees/${next.people.payroll.employee}/inputs/2026-10"
        val days =
            (1..31).map { day ->
                mapOf(
                    "workDate" to YearMonth.of(2026, 10).atDay(day).toString(),
                    "portion" to "FULL",
                    "disposition" to "PAID",
                    "reference" to "Reviewed unassigned day",
                )
            }
        payrollBody(
            command(
                next.people.preparer.client,
                path,
                inputBody(
                    next.work,
                    1,
                    termChanges = mapOf("dayResolutions" to days, "holidayAllowance" to holiday(10)),
                ),
                next.people.preparer.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        payrollBody(
            command(
                next.people.reviewer.client,
                "$path/verify",
                """{"expectedVersion":2,"reason":"Independent review"}""",
                next.people.reviewer.csrf,
                UUID.randomUUID(),
            )
        )
        val lease = beginTaxRun(next)
        drainRun(next, lease)
        val run = runId(lease)
        assertEquals(0, runView(next, run)["run"]["failed"].asInt())
        val review = UUID.fromString(payrollBody(submitReview(next, run))["id"].asString())
        payrollBody(decideReview(next, review))
        val publication =
            PublicationFixture(
                next,
                run,
                review,
                payrollMember(
                    next.people.payroll.company,
                    setOf("company.read", "payroll.finalize"),
                ),
            )
        payrollError(startFinalization(publication), 409, "payroll_holiday_already_assessed")
        assertEquals(1, count(next.people.payroll.company, "payroll_assessments"))
    }
}
