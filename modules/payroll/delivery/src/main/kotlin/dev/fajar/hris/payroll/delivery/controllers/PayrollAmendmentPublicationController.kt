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
@RequestMapping("/api/v1/companies/{companyId}/payroll/amendments/{amendmentId}/publications")
class PayrollAmendmentPublicationController(
    private val start: StartPayrollAmendmentPublication,
    private val list: ListPayrollAmendmentPublications,
    private val recover: RecoverPayrollAmendmentPublication,
) {
    @GetMapping
    fun list(
        actor: Actor,
        @PathVariable amendmentId: UUID,
    ): List<PayrollAmendmentPublicationDetailsResponse> =
        list.execute(actor, amendmentId).response().map { it.toResponse() }

    @PutMapping("/{id}")
    fun start(
        actor: Actor,
        @PathVariable amendmentId: UUID,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: StartPayrollAmendmentPublicationRequest,
    ): MutationResponse =
        start
            .execute(
                actor,
                operationId,
                amendmentId,
                id,
                body.expectedVersion,
                body.reviewId,
                body.expectedReviewVersion,
                body.expectedApprovalVersion,
                body.calculationId,
                body.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/{id}/recover")
    fun recover(
        actor: Actor,
        @PathVariable amendmentId: UUID,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: RecoverPayrollAmendmentPublicationRequest,
    ): MutationResponse =
        recover
            .execute(
                actor,
                operationId,
                amendmentId,
                id,
                body.expectedVersion,
                body.expectedJobVersion,
                body.reason,
            )
            .response()
            .toResponse()
}
