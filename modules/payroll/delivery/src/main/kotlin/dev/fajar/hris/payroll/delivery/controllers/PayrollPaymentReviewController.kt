package dev.fajar.hris.payroll.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.payroll.delivery.mappers.toResponse
import dev.fajar.hris.payroll.delivery.responses.PayrollPaymentReviewResponse
import dev.fajar.hris.payroll.domain.usecases.GetPayrollPaymentReview
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/payroll/payments/{id}/review")
class PayrollPaymentReviewController(private val review: GetPayrollPaymentReview) {
    @GetMapping
    fun get(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) historyAfter: Long?,
        @RequestParam(defaultValue = "50") historyLimit: Int,
    ): PayrollPaymentReviewResponse =
        review.execute(actor, id, historyAfter, historyLimit).response().toResponse()
}
