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
class PayrollReviewController(
    private val submit: SubmitPayrollReview,
    private val decide: DecidePayrollReview,
    private val withdraw: WithdrawPayrollReview,
    private val get: GetPayrollReview,
    private val list: ListPayrollReviews,
) {
    @PostMapping("/runs/{runId}/reviews")
    fun submit(
        actor: Actor,
        @PathVariable runId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PayrollReviewSubmitRequest,
    ): MutationResponse =
        submit
            .execute(actor, operationId, body.id, runId, body.expectedRunVersion, body.reason)
            .response()
            .toResponse()

    @GetMapping("/runs/{runId}/reviews")
    fun list(
        actor: Actor,
        @PathVariable runId: UUID,
        @RequestParam(required = false) after: Int?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<PayrollReviewResponse> =
        list.execute(actor, runId, after, limit).response().let {
            Page(it.items.map { row -> row.toResponse() }, it.nextCursor)
        }

    @GetMapping("/reviews/{id}")
    fun get(actor: Actor, @PathVariable id: UUID): PayrollReviewDetailsResponse =
        get.execute(actor, id).response().toResponse()

    @PostMapping("/reviews/{id}/decisions")
    fun decide(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PayrollReviewDecisionRequest,
    ): MutationResponse =
        decide
            .execute(
                actor,
                operationId,
                id,
                body.expectedVersion,
                body.expectedApprovalVersion,
                body.decision,
                body.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/reviews/{id}/withdraw")
    fun withdraw(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PayrollReviewWithdrawRequest,
    ): MutationResponse =
        withdraw
            .execute(
                actor,
                operationId,
                id,
                body.expectedVersion,
                body.expectedApprovalVersion,
                body.reason,
            )
            .response()
            .toResponse()
}
