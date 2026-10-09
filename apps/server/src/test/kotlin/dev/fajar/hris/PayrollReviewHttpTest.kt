package dev.fajar.hris

import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollReviewHttpTest : PayrollReviewApiFixture() {
    @Test
    fun independentStagedApprovalFreezesTotalsAndAllowsAReviewedPayrollBeneficiary() {
        val f = calculationFixture()
        val run = calculated(f)
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'payroll.review')",
                f.people.payroll.company,
                f.people.payroll.owner.account,
            )
        reviewTemplate(
            f,
            changes =
                mapOf(
                    "stages" to
                        listOf(
                            mapOf(
                                "assignment" to "NAMED",
                                "accountIds" to listOf(f.people.reviewer.account),
                            ),
                            mapOf(
                                "assignment" to "NAMED",
                                "accountIds" to listOf(f.people.payroll.owner.account),
                            ),
                        )
                ),
        )
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val response = payrollBody(submitReview(f, run, id, key = key))
        assertEquals(response, payrollBody(submitReview(f, run, id, key = key)))
        val before = reviewView(f, id)
        val totals = before["review"]["totals"]
        assertEquals(1, totals["employeeCount"].asInt())
        assertEquals("IDR", totals["currency"].asString())
        assertEquals(BigDecimal("11976700.00"), BigDecimal(totals["taxableGross"].asString()))
        assertEquals(BigDecimal("479068.00"), BigDecimal(totals["withheld"].asString()))
        assertEquals(BigDecimal("10605932.00"), BigDecimal(totals["takeHome"].asString()))
        payrollBody(decideReview(f, id))
        assertEquals("PENDING", reviewView(f, id)["review"]["status"].asString())
        payrollBody(
            decideReview(f, id, member = f.people.payroll.owner, version = 1, approvalVersion = 1)
        )
        val approved = reviewView(f, id)
        assertEquals("APPROVED", approved["review"]["status"].asString())
        assertEquals(3, approved["changes"].size())
        assertEquals(totals, approved["review"]["totals"])
        assertEquals("CALCULATED", runView(f, run)["run"]["status"].asString())
        payrollError(abandonRun(f, run), 409, "payroll_review_withdrawal_required")
        payrollBody(withdrawReview(f, id, 2, 2))
        val withdrawn = reviewView(f, id)
        assertEquals("WITHDRAWN", withdrawn["review"]["status"].asString())
        assertEquals("CANCELLED", withdrawn["approval"]["status"].asString())
        assertEquals(4, withdrawn["changes"].size())
        assertEquals(2, count(f.people.payroll.company, "approval_decisions"))
        payrollBody(abandonRun(f, run))
        assertEquals(response, payrollBody(submitReview(f, run, id, key = key)))
    }

    @Test
    fun anotherSubmitterCannotReassignTheCalculationMakerAsAnApprover() {
        val f = calculationFixture()
        val run = calculated(f)
        val submitter =
            payrollMember(
                f.people.payroll.company,
                setOf("company.read", "payroll.calculate", "payroll.review"),
            )
        reviewTemplate(
            f,
            changes =
                mapOf(
                    "stages" to
                        listOf(
                            mapOf(
                                "assignment" to "NAMED",
                                "accountIds" to
                                    listOf(
                                        f.people.preparer.account,
                                        submitter.account,
                                        f.people.reviewer.account,
                                    ),
                            )
                        )
                ),
        )
        val id =
            UUID.fromString(payrollBody(submitReview(f, run, member = submitter))["id"].asString())
        val view = reviewView(f, id)
        val approval = UUID.fromString(view["approval"]["id"].asString())
        assertEquals(
            setOf(f.people.reviewer.account.toString()),
            view["approval"]["stages"][0].iterator().asSequence().map { it.asString() }.toSet(),
        )
        assertEquals(
            setOf(f.people.preparer.account.toString()),
            view["approval"]["excludedAccountIds"]
                .iterator()
                .asSequence()
                .map { it.asString() }
                .toSet(),
        )
        payrollError(decideReview(f, id, member = f.people.preparer), 403, "self_approval_denied")
        payrollError(decideReview(f, id, member = submitter), 403, "self_approval_denied")
        payrollError(
            reassignReview(f, approval, setOf(f.people.preparer.account)),
            422,
            "approver_unavailable",
        )
        payrollBody(decideReview(f, id))
    }

    @Test
    fun blockedAssignmentsCanBeRepairedAndARejectedResultRequiresRecalculation() {
        val f = calculationFixture()
        val run = calculated(f)
        reviewTemplate(
            f,
            changes =
                mapOf(
                    "stages" to
                        listOf(
                            mapOf(
                                "assignment" to "NAMED",
                                "accountIds" to listOf(f.people.preparer.account),
                            )
                        )
                ),
        )
        val id = UUID.fromString(payrollBody(submitReview(f, run))["id"].asString())
        val first = reviewView(f, id)
        assertEquals("BLOCKED", first["approval"]["status"].asString())
        val approval = UUID.fromString(first["approval"]["id"].asString())
        payrollBody(reassignReview(f, approval, setOf(f.people.reviewer.account)))
        payrollError(decideReview(f, id), 409, "approval_changed")
        payrollError(
            decideReview(f, id, approvalVersion = 1, decision = "REJECT", reason = ""),
            422,
            "decision_reason_required",
        )
        payrollBody(
            decideReview(
                f,
                id,
                approvalVersion = 1,
                decision = "REJECT",
                reason = "Attendance resolution needs correction",
            )
        )
        assertEquals("REJECTED", reviewView(f, id)["review"]["status"].asString())
        payrollError(submitReview(f, run), 409, "payroll_recalculation_required")
        payrollError(withdrawReview(f, id, 1, 2), 409, "payroll_review_not_active")
        payrollBody(abandonRun(f, run))
    }

    @Test
    fun failedCalculationsPolicyAndObservedVersionsAreValidatedWithoutOrphans() {
        val failed = calculationFixture(extraEmployees = 1)
        val badRun = calculated(failed)
        payrollError(submitReview(failed, badRun), 409, "payroll_calculation_has_failures")
        val f = calculationFixture()
        val lease = beginRun(f)
        val run = runId(lease)
        payrollError(submitReview(f, run, version = 0), 409, "payroll_calculation_required")
        drainRun(f, lease)
        payrollError(submitReview(f, run, version = 0), 409, "stale_version")
        payrollError(submitReview(f, run), 422, "approval_policy_missing")
        val invalid =
            command(
                f.people.payroll.admin,
                "/api/v1/companies/${f.people.payroll.company}/approvals/templates/${UUID.randomUUID()}",
                json.writeValueAsString(
                    mapOf(
                        "name" to "Invalid payroll manager",
                        "kind" to "PAYROLL",
                        "effectiveFrom" to "2026-01-01",
                        "stages" to listOf(mapOf("assignment" to "MANAGER")),
                        "reason" to "No single payroll beneficiary",
                    )
                ),
                f.people.payroll.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        payrollError(invalid, 422, "payroll_approval_assignment_invalid")
        assertEquals(0, count(f.people.payroll.company, "payroll_reviews"))
        assertEquals(0, count(f.people.payroll.company, "approval_requests"))
    }
}
