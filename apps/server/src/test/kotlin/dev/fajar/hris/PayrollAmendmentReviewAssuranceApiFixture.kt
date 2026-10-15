package dev.fajar.hris

import dev.fajar.hris.approvals.domain.entities.ApprovalDecision
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.payroll.domain.usecases.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.util.UUID
import org.springframework.beans.factory.annotation.Autowired

abstract class PayrollAmendmentReviewAssuranceApiFixture : PayrollAmendmentReviewApiFixture() {
    @Autowired private lateinit var policies: PayrollPolicyRepository
    @Autowired private lateinit var sources: PayrollAmendmentSourceRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var approvals: ApprovalRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    protected val security = IdentitySecurityPolicy()

    protected data class ReviewAssuranceScene(
        val review: AmendmentReviewScene,
        val actor: Actor,
        val operation: UUID = UUID.randomUUID(),
    )

    protected fun reviewAssuranceScene(action: String): ReviewAssuranceScene {
        val scene = amendmentReviewScene(submitted = action != "submit")
        val member =
            if (action in setOf("approve", "reject")) scene.reviewer else scene.draft.source.maker
        val actor =
            payrollActor(scene.draft.source.payroll, member).copy(mfaVerifiedAt = clock.instant())
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                actor.accountId,
            )
        return ReviewAssuranceScene(scene, actor)
    }

    protected fun protectedReviewCommand(
        scene: ReviewAssuranceScene,
        action: String,
        actor: Actor = scene.actor,
    ): Result<MutationReceipt> =
        when (action) {
            "submit" ->
                SubmitPayrollAmendmentReview(
                        amendments,
                        amendmentReviews,
                        sources,
                        policies,
                        people,
                        approvals,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(
                        actor,
                        scene.operation,
                        scene.review.draft.id,
                        scene.review.id,
                        1,
                        scene.review.calculationId,
                        "Submit retained correction",
                    )
            "approve",
            "reject" ->
                DecidePayrollAmendmentReview(
                        amendments,
                        amendmentReviews,
                        sources,
                        policies,
                        people,
                        approvals,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(
                        actor,
                        scene.operation,
                        scene.review.draft.id,
                        scene.review.id,
                        2,
                        0,
                        0,
                        if (action == "approve") ApprovalDecision.APPROVE
                        else ApprovalDecision.REJECT,
                        "Independent correction review",
                    )
            "withdraw" ->
                WithdrawPayrollAmendmentReview(
                        amendments,
                        amendmentReviews,
                        policies,
                        people,
                        approvals,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(
                        actor,
                        scene.operation,
                        scene.review.draft.id,
                        scene.review.id,
                        2,
                        0,
                        0,
                        "Withdraw retained correction",
                    )
            else -> error("Unknown amendment review action")
        }

    protected fun protectedReviewRead(
        scene: ReviewAssuranceScene,
        action: String,
        actor: Actor = scene.actor,
    ): Result<*> =
        when (action) {
            "review" ->
                GetPayrollAmendmentReview(
                        amendments,
                        amendmentReviews,
                        policies,
                        people,
                        approvals,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.review.id)
            "reviews" ->
                ListPayrollAmendmentReviews(
                        amendments,
                        policies,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                        amendmentReviews,
                    )
                    .execute(actor, scene.review.draft.id)
            "draft" ->
                GetPayrollAmendment(
                        amendments,
                        policies,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                        amendmentReviews,
                    )
                    .execute(actor, scene.review.draft.id)
            else -> error("Unknown amendment review read")
        }
}
