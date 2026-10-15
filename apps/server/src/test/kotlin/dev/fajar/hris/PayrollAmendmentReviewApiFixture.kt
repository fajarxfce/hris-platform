package dev.fajar.hris

import dev.fajar.hris.payroll.domain.repositories.PayrollAmendmentReviewRepository
import dev.fajar.hris.payroll.domain.usecases.*
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(PayrollAmendmentReviewProbeConfiguration::class)
abstract class PayrollAmendmentReviewApiFixture : PayrollAmendmentDraftApiFixture() {
    @Autowired protected lateinit var amendmentReviewProbe: PayrollAmendmentReviewProbe
    @Autowired protected lateinit var amendmentReviews: PayrollAmendmentReviewRepository
    @Autowired protected lateinit var submitAmendmentReviewUseCase: SubmitPayrollAmendmentReview
    @Autowired protected lateinit var decideAmendmentReviewUseCase: DecidePayrollAmendmentReview
    @Autowired protected lateinit var withdrawAmendmentReviewUseCase: WithdrawPayrollAmendmentReview

    protected data class AmendmentReviewScene(
        val draft: DraftFixture,
        val reviewer: PayrollMember,
        val secondReviewer: PayrollMember,
        val id: UUID,
        val calculationId: UUID,
        val operation: UUID = UUID.randomUUID(),
    )

    @AfterEach fun clearAmendmentReviewProbe() = amendmentReviewProbe.clear()

    protected fun amendmentReviewScene(
        draft: DraftFixture = draftFixture(),
        stages: Int = 1,
        submitted: Boolean = true,
        configureTemplate: Boolean = true,
    ): AmendmentReviewScene {
        val company = draft.source.payroll.company
        val permissions = setOf("company.read", "payroll.amend.review", "approvals.read")
        val reviewer = payrollMember(company, permissions)
        val second = payrollMember(company, permissions)
        payrollBody(draftCommand(draft, "calculate", 0))
        val calculation = UUID.fromString(draftView(draft)["latestCalculation"]["id"].asString())
        if (configureTemplate)
            amendmentReviewTemplate(
                draft,
                if (stages == 1) listOf(reviewer.account)
                else listOf(reviewer.account, second.account),
            )
        val scene = AmendmentReviewScene(draft, reviewer, second, UUID.randomUUID(), calculation)
        if (submitted) payrollBody(submitAmendmentReview(scene))
        return scene
    }

    protected fun amendmentReviewTemplate(
        draft: DraftFixture,
        stages: List<UUID>,
        id: UUID = UUID.randomUUID(),
        changes: Map<String, Any?> = emptyMap(),
    ): UUID {
        val f = draft.source.payroll
        payrollBody(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/approvals/templates/$id",
                json.writeValueAsString(
                    mapOf(
                        "name" to "Amendment review",
                        "kind" to "PAYROLL_AMENDMENT",
                        "effectiveFrom" to "2026-01-01",
                        "minimumAmount" to "0",
                        "stages" to
                            stages.map {
                                mapOf("assignment" to "NAMED", "accountIds" to listOf(it))
                            },
                        "reason" to "Independent correction review",
                    ) + changes
                ),
                f.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        return id
    }

    protected fun amendmentReviewsPath(scene: AmendmentReviewScene) =
        draftsPath(scene.draft.source) + "/${scene.draft.id}/reviews"

    protected fun amendmentReviewPath(scene: AmendmentReviewScene) =
        "/api/v1/companies/${scene.draft.source.payroll.company}/payroll/amendment-reviews/${scene.id}"

    protected fun submitAmendmentReview(
        scene: AmendmentReviewScene,
        version: Long = 1,
        member: PayrollMember = scene.draft.source.maker,
        key: UUID = scene.operation,
    ) =
        command(
            member.client,
            amendmentReviewsPath(scene) + "/${scene.id}",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to version,
                    "calculationId" to scene.calculationId,
                    "reason" to "Submit retained correction",
                )
            ),
            member.csrf,
            key,
            "PUT",
        )

    protected fun decideAmendmentReview(
        scene: AmendmentReviewScene,
        version: Long = 2,
        reviewVersion: Long = 0,
        approvalVersion: Long = 0,
        member: PayrollMember = scene.reviewer,
        decision: String = "APPROVE",
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            member.client,
            amendmentReviewsPath(scene) + "/${scene.id}/decisions",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to version,
                    "expectedReviewVersion" to reviewVersion,
                    "expectedApprovalVersion" to approvalVersion,
                    "decision" to decision,
                    "reason" to "Independent correction review",
                )
            ),
            member.csrf,
            key,
        )

    protected fun withdrawAmendmentReview(
        scene: AmendmentReviewScene,
        version: Long = 2,
        reviewVersion: Long = 0,
        approvalVersion: Long = 0,
        member: PayrollMember = scene.draft.source.maker,
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            member.client,
            amendmentReviewsPath(scene) + "/${scene.id}/withdraw",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to version,
                    "expectedReviewVersion" to reviewVersion,
                    "expectedApprovalVersion" to approvalVersion,
                    "reason" to "Withdraw retained correction",
                )
            ),
            member.csrf,
            key,
        )

    protected fun amendmentReviewView(
        scene: AmendmentReviewScene,
        member: PayrollMember = scene.reviewer,
    ) = payrollBody(get(member.client, amendmentReviewPath(scene)))

    protected fun amendmentReviewCounts(scene: AmendmentReviewScene) =
        draftCounts(scene.draft.source) +
            listOf(
                    "payroll_amendment_reviews",
                    "payroll_amendment_review_changes",
                    "approval_requests",
                    "approval_decisions",
                    "approval_assignment_overrides",
                )
                .associateWith { count(scene.draft.source.payroll.company, it) }
}
