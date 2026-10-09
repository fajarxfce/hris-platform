package dev.fajar.hris

import dev.fajar.hris.payroll.domain.usecases.*
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(PayrollReviewProbeConfiguration::class)
abstract class PayrollReviewApiFixture : PayrollRunApiFixture() {
    @Autowired protected lateinit var reviewProbe: PayrollReviewProbe
    @Autowired protected lateinit var submitReviewUseCase: SubmitPayrollReview

    @AfterEach
    fun resetReviewProbe() {
        reviewProbe.clear()
    }

    protected fun calculated(f: CalculationFixture): UUID {
        val lease = beginRun(f)
        drainRun(f, lease)
        return runId(lease)
    }

    protected fun reviewsPath(f: CalculationFixture) =
        "/api/v1/companies/${f.people.payroll.company}/payroll/reviews"

    protected fun reviewTemplate(
        f: CalculationFixture,
        id: UUID = UUID.randomUUID(),
        changes: Map<String, Any?> = emptyMap(),
    ): UUID {
        payrollBody(
            command(
                f.people.payroll.admin,
                "/api/v1/companies/${f.people.payroll.company}/approvals/templates/$id",
                json.writeValueAsString(
                    mapOf(
                        "name" to "Payroll review",
                        "kind" to "PAYROLL",
                        "effectiveFrom" to "2026-01-01",
                        "minimumAmount" to "0",
                        "stages" to
                            listOf(
                                mapOf(
                                    "assignment" to "NAMED",
                                    "accountIds" to listOf(f.people.reviewer.account),
                                )
                            ),
                        "reason" to "Configure independent payroll review",
                    ) + changes
                ),
                f.people.payroll.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        return id
    }

    protected fun submitReview(
        f: CalculationFixture,
        run: UUID,
        id: UUID = UUID.randomUUID(),
        version: Long = 1,
        key: UUID = UUID.randomUUID(),
        member: PayrollMember = f.people.preparer,
    ) =
        command(
            member.client,
            runPath(f, run) + "/reviews",
            json.writeValueAsString(
                mapOf(
                    "id" to id,
                    "expectedRunVersion" to version,
                    "reason" to "Submit calculated September payroll",
                )
            ),
            member.csrf,
            key,
        )

    protected fun reviewView(
        f: CalculationFixture,
        id: UUID,
        member: PayrollMember = f.people.reviewer,
    ) = payrollBody(get(member.client, reviewsPath(f) + "/$id"))

    protected fun decideReview(
        f: CalculationFixture,
        id: UUID,
        member: PayrollMember = f.people.reviewer,
        version: Long = 0,
        approvalVersion: Long = 0,
        decision: String = "APPROVE",
        reason: String = "Independent review",
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            member.client,
            reviewsPath(f) + "/$id/decisions",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to version,
                    "expectedApprovalVersion" to approvalVersion,
                    "decision" to decision,
                    "reason" to reason,
                )
            ),
            member.csrf,
            key,
        )

    protected fun withdrawReview(
        f: CalculationFixture,
        id: UUID,
        version: Long = 0,
        approvalVersion: Long = 0,
        key: UUID = UUID.randomUUID(),
        member: PayrollMember = f.people.preparer,
    ) =
        command(
            member.client,
            reviewsPath(f) + "/$id/withdraw",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to version,
                    "expectedApprovalVersion" to approvalVersion,
                    "reason" to "Withdraw for payroll correction",
                )
            ),
            member.csrf,
            key,
        )

    protected fun reassignReview(
        f: CalculationFixture,
        approval: UUID,
        assignees: Set<UUID>,
        version: Long = 0,
    ) =
        command(
            f.people.payroll.admin,
            "/api/v1/companies/${f.people.payroll.company}/approvals/$approval/reassign",
            json.writeValueAsString(
                mapOf(
                    "version" to version,
                    "assignees" to assignees,
                    "reason" to "Assign available payroll reviewer",
                )
            ),
            f.people.payroll.adminCsrf,
            UUID.randomUUID(),
        )

    protected fun pendingReview(f: CalculationFixture, run: UUID = calculated(f)): UUID {
        reviewTemplate(f)
        return UUID.fromString(payrollBody(submitReview(f, run))["id"].asString())
    }
}
