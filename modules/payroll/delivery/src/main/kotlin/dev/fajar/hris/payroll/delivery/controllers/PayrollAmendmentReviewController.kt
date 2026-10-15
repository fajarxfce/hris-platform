package dev.fajar.hris.payroll.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.payroll.delivery.mappers.*
import dev.fajar.hris.payroll.delivery.requests.*
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/payroll")
class PayrollAmendmentReviewController(
    private val submit: SubmitPayrollAmendmentReview,
    private val decide: DecidePayrollAmendmentReview,
    private val withdraw: WithdrawPayrollAmendmentReview,
    private val get: GetPayrollAmendmentReview,
    private val list: ListPayrollAmendmentReviews,
) {
    @PutMapping("/amendments/{amendmentId}/reviews/{id}")
    fun submit(
        actor: Actor,
        @PathVariable amendmentId: UUID,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: SubmitPayrollAmendmentReviewRequest,
    ): MutationResponse =
        submit
            .execute(
                actor,
                operationId,
                amendmentId,
                id,
                body.expectedVersion,
                body.calculationId,
                body.reason,
            )
            .response()
            .toResponse()

    @GetMapping("/amendments/{amendmentId}/reviews")
    fun list(actor: Actor, @PathVariable amendmentId: UUID): List<PayrollAmendmentReviewResponse> =
        list.execute(actor, amendmentId).response().map { it.toResponse() }

    @GetMapping("/amendment-reviews/{id}")
    fun get(actor: Actor, @PathVariable id: UUID): PayrollAmendmentReviewDetailsResponse =
        get.execute(actor, id).response().toResponse()

    @PostMapping("/amendments/{amendmentId}/reviews/{id}/decisions")
    fun decide(
        actor: Actor,
        @PathVariable amendmentId: UUID,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: DecidePayrollAmendmentReviewRequest,
    ): MutationResponse =
        decide
            .execute(
                actor,
                operationId,
                amendmentId,
                id,
                body.expectedVersion,
                body.expectedReviewVersion,
                body.expectedApprovalVersion,
                body.decision,
                body.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/amendments/{amendmentId}/reviews/{id}/withdraw")
    fun withdraw(
        actor: Actor,
        @PathVariable amendmentId: UUID,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: WithdrawPayrollAmendmentReviewRequest,
    ): MutationResponse =
        withdraw
            .execute(
                actor,
                operationId,
                amendmentId,
                id,
                body.expectedVersion,
                body.expectedReviewVersion,
                body.expectedApprovalVersion,
                body.reason,
            )
            .response()
            .toResponse()
}
