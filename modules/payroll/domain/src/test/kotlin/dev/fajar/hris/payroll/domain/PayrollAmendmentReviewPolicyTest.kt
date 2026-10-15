package dev.fajar.hris.payroll.domain

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.policies.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollAmendmentReviewPolicyTest : PayrollAmendmentFixture() {
    private val now = Instant.parse("2026-10-01T00:00:00Z")

    private fun calculated(): Pair<PayrollAmendmentSummary, PayrollAmendmentCalculationSummary> {
        val source = sources(9..9).single()
        val actor = UUID.randomUUID()
        val id = UUID.randomUUID()
        val calculation =
            PayrollAmendmentCalculationSummary(
                UUID.randomUUID(),
                id,
                1,
                0,
                PayrollAmendmentCalculationStatus.SUCCEEDED,
                1,
                PayrollAmendmentDifference(takeHome = BigDecimal.TEN),
                null,
                actor,
                now,
                "Retained calculation",
            )
        val amendment =
            PayrollAmendmentSummary(
                id,
                source.assessmentId,
                source.facts.month,
                source.personId,
                PayrollEmployee(source.target.employeeId, "E001", "Fictional employee", 0),
                1,
                PayrollAmendmentStatus.CALCULATED,
                PayrollAmendmentAction.CALCULATED,
                calculation.id,
                now,
                actor,
                now,
                "Retained calculation",
            )
        return amendment to calculation
    }

    private fun review(amendment: PayrollAmendmentSummary) =
        PayrollAmendmentReview(
            UUID.randomUUID(),
            amendment.id,
            1,
            amendment.version,
            amendment.latestCalculationId!!,
            UUID.randomUUID(),
            LocalDate.of(2026, 10, 1),
            "Asia/Jakarta",
            BigDecimal.TEN,
            amendment.actorId,
            now,
            "Retained review",
            PayrollAmendmentReviewStatus.WITHDRAWN,
            1,
        )

    @Test
    fun opposingMonthsRemainMaterialAndDifferentMoneyDimensionsDoNotDoubleCountOneMonth() {
        val year = sources(9..10)
        val differences =
            listOf(
                PayrollAmendmentDifference(
                    taxableGross = BigDecimal("1000"),
                    takeHome = BigDecimal("900"),
                    withheld = BigDecimal("100"),
                ),
                PayrollAmendmentDifference(
                    taxableGross = BigDecimal("-1000"),
                    takeHome = BigDecimal("-900"),
                    withheld = BigDecimal("-100"),
                ),
            )
        val preview =
            PayrollAmendmentPreview(
                year.first().assessmentId,
                year.map { PayrollAmendmentSourceReference(it.assessmentId, 0) },
                year.mapIndexed { index, value ->
                    PayrollAmendmentCalculation(
                        PayrollAmendmentSourceReference(value.assessmentId, 0),
                        value.target,
                        value.calculation,
                        value.facts,
                        value.calculation,
                        differences[index],
                    )
                },
                PayrollAmendmentDifference(),
            )
        assertEquals(0, payrollAmendmentReviewExposure(preview).compareTo(BigDecimal("2000")))
        val employerCorrection =
            preview.copy(
                calculations =
                    preview.calculations.map {
                        it.copy(
                            difference =
                                PayrollAmendmentDifference(
                                    employeeDeductions = BigDecimal("-1200"),
                                    employerCost = BigDecimal("-1500"),
                                )
                        )
                    }
            )
        assertEquals(
            0,
            payrollAmendmentReviewExposure(employerCorrection).compareTo(BigDecimal("3000")),
        )
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedException::class.java) {
                payrollAmendmentReviewExposure(preview)
            }
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun submissionReservesEnoughRevisionsForEightDecisionsAndRecovery() {
        val (amendment, calculation) = calculated()
        assertEquals(
            Result.Success(Unit),
            validatePayrollAmendmentSubmission(amendment.copy(version = 987), calculation, null),
        )
        assertEquals(
            "payroll_amendment_revision_limit",
            (validatePayrollAmendmentSubmission(amendment.copy(version = 988), calculation, null)
                    as Result.Failed)
                .failure
                .code,
        )
        val previous = review(amendment)
        assertEquals(
            Result.Success(Unit),
            validatePayrollAmendmentSubmission(amendment, calculation, previous),
        )
        assertEquals(
            "payroll_amendment_review_limit",
            (validatePayrollAmendmentSubmission(amendment, calculation, previous.copy(number = 8))
                    as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            "payroll_amendment_review_active",
            (validatePayrollAmendmentSubmission(
                    amendment,
                    calculation,
                    previous.copy(status = PayrollAmendmentReviewStatus.APPROVED),
                )
                    as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun rejectionRequiresANewCalculationAndEverySubmissionPinsItsCalculationOwner() {
        val (amendment, calculation) = calculated()
        val rejected = review(amendment).copy(status = PayrollAmendmentReviewStatus.REJECTED)
        assertEquals(
            "payroll_amendment_recalculation_required",
            (validatePayrollAmendmentSubmission(amendment, calculation, rejected) as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            Result.Success(Unit),
            validatePayrollAmendmentSubmission(
                amendment,
                calculation,
                rejected.copy(calculationId = UUID.randomUUID()),
            ),
        )
        for (candidate in
            listOf(
                calculation.copy(id = UUID.randomUUID()),
                calculation.copy(amendmentId = UUID.randomUUID()),
                calculation.copy(status = PayrollAmendmentCalculationStatus.FAILED),
            )) assertEquals(
            "payroll_amendment_calculation_required",
            (validatePayrollAmendmentSubmission(amendment, candidate, null) as Result.Failed)
                .failure
                .code,
        )
    }

    @Test
    fun reviewingAndPreparingRemainIndependentExplicitGrants() {
        val base =
            Actor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                setOf("payroll.review"),
                now,
                UUID.randomUUID(),
            )
        assertTrue(requirePayrollAmendmentRead(base) is Result.Failed)
        assertEquals(
            Result.Success(Unit),
            requirePayrollAmendmentRead(base.copy(permissions = setOf("payroll.amend.review"))),
        )
        assertEquals(
            Result.Success(Unit),
            requirePayrollAmendmentRead(base.copy(permissions = setOf("payroll.amend"))),
        )
        assertEquals(
            setOf("payroll.amend.review"),
            approvalPermissions(ApprovalKind.PAYROLL_AMENDMENT),
        )
        assertTrue("payroll.amend.review" in PermissionCatalog.assignable)
        for (role in
            listOf(
                PermissionCatalog.companyAdministrator,
                PermissionCatalog.humanResources,
                PermissionCatalog.finance,
                PermissionCatalog.manager,
                PermissionCatalog.employee,
            )) assertFalse("payroll.amend.review" in role)
    }

    @Test
    fun liveBeneficiaryExclusionDoesNotRewriteTheRetainedSnapshot() {
        val maker = UUID.randomUUID()
        val beneficiary = UUID.randomUUID()
        val approval =
            ApprovalRequest(
                UUID.randomUUID(),
                ApprovalKind.PAYROLL_AMENDMENT,
                UUID.randomUUID(),
                maker,
                null,
                UUID.randomUUID(),
                0,
                listOf(ApprovalStage(setOf(beneficiary))),
                0,
                ApprovalStatus.PENDING,
                0,
                now,
                setOf(maker),
            )
        val scoped = payrollAmendmentDecisionScope(approval, beneficiary)
        assertEquals(setOf(maker), approval.excludedAccountIds)
        assertEquals(setOf(maker, beneficiary), scoped.excludedAccountIds)
        val actor =
            Actor(
                beneficiary,
                UUID.randomUUID(),
                setOf("payroll.amend.review"),
                now,
                UUID.randomUUID(),
            )
        assertEquals(
            "self_approval_denied",
            (decideApproval(
                    scoped,
                    actor,
                    ApprovalDecision.APPROVE,
                    "Reviewed",
                    emptyList(),
                    emptyList(),
                    now,
                )
                    as Result.Failed)
                .failure
                .code,
        )
    }
}
