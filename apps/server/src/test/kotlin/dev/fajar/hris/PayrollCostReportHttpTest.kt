package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.JobStep
import dev.fajar.hris.reporting.domain.repositories.PayrollCostReportRepository
import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.node.ObjectNode

class PayrollCostReportHttpTest : PayrollCostReportApiFixture() {
    @Autowired private lateinit var reports: PayrollCostReportRepository

    @Test
    fun onlyPublishedAssessmentsContributeAndLaterSalaryEditsDoNotRewriteTheirCosts() {
        val f = calculationFixture()
        val payroll = f.people.payroll
        assertEquals("0.00", report(payroll)["totals"]["employerCost"].asString())
        val publication = approved(f)
        assertEquals(0, report(payroll)["totals"]["assessments"].asLong())
        val lease = beginFinalization(publication)
        assertEquals(0, report(payroll)["totals"]["assessments"].asLong())
        assertEquals(Result.Success(JobStep(1, true)), stepFinalization(publication, lease))
        val published = report(payroll)
        assertEquals("payroll-costs.v1", published["definitionVersion"].asString())
        assertEquals("IDR", published["currency"].asString())
        assertEquals(payroll.company.toString(), published["companyId"].asString())
        assertEquals(1, published["totals"]["assessments"].asLong())
        assertEquals(1, published["totals"]["employments"].asLong())
        assertEquals(0, published["totals"]["revisedAssessments"].asLong())
        assertEquals(5, published["months"].size())
        assertEquals(published["totals"], published["months"][1]["counts"])
        reconcile(payroll, published)
        payrollBody(
            compensation(
                payroll,
                compensationBody(
                    version = 0,
                    changes = mapOf("effectiveFrom" to "2026-10"),
                    termChanges = mapOf("basicSalary" to "20000000", "payBasis" to runPayBasis()),
                ),
            )
        )
        assertEquals(published["totals"], report(payroll)["totals"])
        for (privateField in
            listOf(
                "employeeName",
                "employmentId",
                "personId",
                "accountId",
                "compensation",
                "reason",
            )) assertFalse(published.toString().contains(privateField))
    }

    @Test
    fun eachPublishedAmendmentReplacesTheSelectedRevisionWhileBankResultsAndReplayDoNotChangeCosts() {
        val scene = publicationScene()
        val payroll = scene.source.payroll
        val initial = report(payroll)
        val original = currentPayslip(scene, 0)
        payBank(scene, bankInstruction(scene))
        assertEquals(initial["totals"], report(payroll)["totals"])
        val receipt = payrollBody(admitPublication(scene))
        assertEquals(initial["totals"], report(payroll)["totals"])
        publish(scene)
        val revised = report(payroll)
        assertEquals(1, revised["totals"]["assessments"].asLong())
        assertEquals(1, revised["totals"]["revisedAssessments"].asLong())
        assertNotEquals(initial["totals"]["employerCost"], revised["totals"]["employerCost"])
        assertEquals(original, currentPayslip(scene, 0))
        reconcile(payroll, revised)
        payBank(scene, bankInstruction(scene, observed = true))
        assertEquals(receipt, payrollBody(admitPublication(scene)))
        assertEquals(revised["totals"], report(payroll)["totals"])
        val next = publicationScene(scene.source, "500000", scene.review)
        publish(next)
        val second = report(payroll)
        assertEquals(3, count(payroll.company, "payroll_assessment_revisions"))
        assertEquals(1, second["totals"]["assessments"].asLong())
        assertEquals(1, second["totals"]["revisedAssessments"].asLong())
        reconcile(payroll, second)
    }

    @Test
    fun annualReportReconcilesFourPublishedMonthsAndPreservesAFinalTaxRefund() {
        var f = calculationFixture()
        val people = f.people
        val opening = json.readTree(runOpeningBody(1)) as ObjectNode
        (opening["terms"]["history"] as ObjectNode).put("withheld", "20000000")
        payrollBody(
            command(
                people.preparer.client,
                "/api/v1/companies/${people.payroll.company}/payroll/employees/${people.payroll.employee}/tax-openings/2026",
                json.writeValueAsString(opening),
                people.preparer.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        payrollBody(verifyRunOpening(people, 2))
        reviewTemplate(f)
        publishTaxRun(f, beginTaxRun(f))
        for (month in 10..12) {
            f = followingMonth(f, YearMonth.of(2026, month))
            publishTaxRun(f, beginTaxRun(f))
        }
        val result = report(f.people.payroll, "2026-01", "2026-12")
        assertEquals(4, result["totals"]["assessments"].asLong())
        assertEquals(1, result["totals"]["employments"].asLong())
        assertEquals(12, result["months"].size())
        assertTrue(
            result["months"][11]["counts"]["withheld"].asString().toBigDecimal() < BigDecimal.ZERO
        )
        assertTrue(
            result["months"][11]["counts"]["takeHome"].asString().toBigDecimal() >
                result["months"][11]["counts"]["employerCost"].asString().toBigDecimal()
        )
        reconcile(f.people.payroll, result)
    }

    @Test
    fun sourceAuthorityAnnualBoundsCompanyIsolationAndReportingAvailabilityStayIndependent() {
        val payroll = amendmentFixture().payroll
        val reader = payrollMember(payroll.company, setOf("reports.read", "payroll.read"))
        assertEquals(1, report(payroll, client = reader.client)["totals"]["assessments"].asLong())
        for (permissions in
            listOf(
                setOf("reports.read"),
                setOf("payroll.read"),
                setOf(
                    "reports.read",
                    "payroll.self.read",
                    "payroll.compensation.manage",
                    "payroll.policy.manage",
                    "payroll.pay",
                    "payroll.amend",
                ),
            )) {
            val member = payrollMember(payroll.company, permissions)
            payrollError(get(member.client, reportPath(payroll)), 403, "access_denied")
        }
        for ((from, until) in
            listOf(
                "2023-12" to "2024-01",
                "2026-01" to "2027-01",
                "2100-12" to "2101-01",
                "2026-12" to "2026-01",
            )) {
            val response = get(reader.client, reportPath(payroll, from, until))
            payrollError(response, 422, "invalid_report_month_range")
            val failed = json.readTree(response.body())
            assertEquals("12", failed["parameters"]["maximumMonths"].asString())
        }
        assertEquals(
            400,
            get(reader.client, reportPath(payroll, "2026-01-01", "2026-12")).statusCode(),
        )
        val other = payrollFixture()
        assertEquals(0, report(other)["totals"]["assessments"].asLong())
        val scoped =
            transactions.run(payrollActor(other, other.operator)) {
                reports.aggregate(payroll.company, YearMonth.of(2026, 8), YearMonth.of(2026, 12))
            }
        assertEquals(0, (scoped as Result.Success).value.totals.assessments)
        payrollError(get(reader.client, reportPath(other)), 403, "company_access_denied")
        payrollBody(
            command(
                payroll.admin,
                "/api/v1/companies/${payroll.company}/settings/client-policy",
                """{"expectedVersion":null,"activateAt":null,"disabledModules":["REPORTING"],"minimumBuilds":{"android":0,"ios":0,"web":0},"maintenance":null,"reason":"Pause reporting"}""",
                payroll.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        payrollError(get(reader.client, reportPath(payroll)), 403, "company_module_disabled")
    }
}
