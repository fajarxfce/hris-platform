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
@RequestMapping("/api/v1/companies/{companyId}/payroll/payslips/{assessmentId}/recoveries")
class PayrollPaymentRecoveryController(
    private val confirm: ConfirmPayrollPaymentRecovery,
    private val list: ListPayrollPaymentRecoveries,
    private val review: GetPayrollPaymentRecoveryReview,
) {
    @GetMapping("/review")
    fun review(
        actor: Actor,
        @PathVariable assessmentId: UUID,
    ): PayrollPaymentRecoveryReviewResponse =
        review.execute(actor, assessmentId).response().toResponse()

    @GetMapping
    fun list(actor: Actor, @PathVariable assessmentId: UUID): List<PayrollPaymentRecoveryResponse> =
        list.execute(actor, assessmentId).response().map { it.toResponse() }

    @PutMapping("/{id}")
    fun confirm(
        actor: Actor,
        @PathVariable assessmentId: UUID,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: ConfirmPayrollPaymentRecoveryRequest,
    ): MutationResponse =
        confirm
            .execute(
                actor,
                operationId,
                assessmentId,
                id,
                body.assessmentRevision,
                body.paymentVersion,
                body.amount,
                body.transactionReference,
                body.occurredAt,
                body.reason,
            )
            .response()
            .toResponse()
}
