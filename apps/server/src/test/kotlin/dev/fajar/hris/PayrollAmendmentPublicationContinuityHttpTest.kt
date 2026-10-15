package dev.fajar.hris

import java.time.YearMonth
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode

class PayrollAmendmentPublicationContinuityHttpTest : PayrollAmendmentPublicationApiFixture() {
    @Test
    fun followingOrdinaryPayrollUsesTheRevisedAssessmentAndRetainsItsExactHistory() {
        val scene = publicationScene()
        publish(scene)
        val current = currentPayslip(scene)
        val next = followingMonth(scene.source.publication.calculation, YearMonth.of(2026, 10))
        val lease = beginTaxRun(next)
        val (_, assessment) = publishTaxRun(next, lease)
        val view = runView(next, runId(lease))["results"]["items"][0]
        assertEquals(
            scene.source.assessment.toString(),
            view["target"]["previousAssessmentId"].asString(),
        )
        assertEquals(1, view["target"]["previousAssessmentRevision"].asLong())
        val retained =
            payrollBody(
                    get(
                        next.people.payroll.owner.client,
                        "/api/v1/companies/${next.people.payroll.company}/payroll/payslips/$assessment",
                    )
                )["calculation"]["taxInput"]["history"]["taxableGross"]
                .asString()
                .toBigDecimal()
        val prior = current["calculation"]["tax"]["taxableGross"].asString().toBigDecimal()
        val opening =
            current["calculation"]["taxInput"]["history"]["taxableGross"].asString().toBigDecimal()
        assertEquals(0, retained.compareTo(prior + opening))
    }

    @Test
    fun anOrdinaryRunCalculatedBeforeCorrectionCannotFinalizeItsStaleTaxBasis() {
        val original = amendmentFixture()
        val next = followingMonth(original.publication.calculation, YearMonth.of(2026, 10))
        val lease = beginTaxRun(next)
        drainRun(next, lease)
        val run = runId(lease)
        val review = UUID.fromString(payrollBody(submitReview(next, run))["id"].asString())
        payrollBody(decideReview(next, review))
        val source =
            original.copy(
                publication =
                    original.publication.copy(
                        calculation = original.publication.calculation.copy(people = next.people)
                    ),
                maker =
                    original.maker.copy(
                        csrf =
                            login(original.maker.client, "${original.maker.account}@example.test")
                    ),
            )
        publish(publicationScene(source))
        val stale =
            PublicationFixture(
                next,
                run,
                review,
                payrollMember(source.payroll.company, setOf("company.read", "payroll.finalize")),
            )
        payrollError(
            startFinalization(stale, finalizationBody(stale), UUID.randomUUID()),
            409,
            "payroll_tax_history_changed",
        )
        assertEquals(1, count(source.payroll.company, "payroll_assessments"))
    }

    @Test
    fun holidayOwnershipCanMoveWithinOneCorrectedSuffixWhileOriginalStatementsRemainRetained() {
        val original = amendmentFixture()
        val next = followingMonth(original.publication.calculation, YearMonth.of(2026, 10))
        publishTaxRun(next, beginTaxRun(next))
        val source =
            original.copy(
                publication =
                    original.publication.copy(
                        calculation = original.publication.calculation.copy(people = next.people)
                    ),
                maker =
                    original.maker.copy(
                        csrf =
                            login(original.maker.client, "${original.maker.account}@example.test")
                    ),
            )
        val initial = amendmentPreparation(source)
        val first =
            publicationScene(
                source,
                corrections = listOf(holidayCorrection(initial["assessments"][0], "2026-09-20")),
            )
        publish(first)
        val revised = amendmentPreparation(source)
        val moved =
            publicationScene(
                source,
                previousReview = first.review,
                corrections =
                    listOf(
                        holidayCorrection(revised["assessments"][0], null),
                        holidayCorrection(revised["assessments"][1], "2026-10-20"),
                    ),
            )
        publish(moved)
        val current =
            database()
                .queryForList(
                    "select a.tax_month,h.revision,h.holiday_kind from payroll_assessment_holidays h join payroll_assessments a on a.company_id=h.company_id and a.id=h.assessment_id where h.company_id=? order by a.tax_month",
                    source.payroll.company,
                )
        assertEquals(2, current.size)
        assertEquals(listOf(2L, 2L), current.map { it["revision"] })
        assertNull(current[0]["holiday_kind"])
        assertEquals("CHRISTMAS", current[1]["holiday_kind"])
        assertTrue(currentPayslip(moved, 0)["calculation"]["holiday"].isNull)
        assertTrue(
            currentPayslip(moved, 1)["calculation"]["holiday"]["amount"]
                .asString()
                .toBigDecimal()
                .signum() > 0
        )
        assertTrue(currentPayslip(moved)["calculation"]["holiday"].isNull)
    }

    private fun holidayCorrection(entry: JsonNode, date: String?): Map<String, Any?> {
        val input =
            entry["input"]
                .propertyNames()
                .associateWith { entry["input"][it] }
                .toMutableMap<String, Any?>()
        input["holidayAllowance"] =
            date?.let {
                mapOf(
                    "kind" to "CHRISTMAS",
                    "holidayDate" to it,
                    "continuousServiceFrom" to "2026-01-01",
                    "priorPayment" to "NOT_PAID",
                    "reviewReference" to "Fictional reviewed holiday correction",
                )
            }
        return mapOf("assessmentId" to entry["source"]["assessmentId"].asString(), "input" to input)
    }
}
