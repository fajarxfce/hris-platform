package dev.fajar.hris.expenses.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.expenses.delivery.mappers.toResponse
import dev.fajar.hris.expenses.delivery.responses.ExpensePaymentReviewResponse
import dev.fajar.hris.expenses.domain.usecases.GetExpensePaymentReview
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/expenses/payments/{id}/review")
class ExpensePaymentReviewController(private val review: GetExpensePaymentReview) {
    @GetMapping
    fun get(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) historyAfter: Long?,
        @RequestParam(defaultValue = "50") historyLimit: Int,
    ): ExpensePaymentReviewResponse =
        review.execute(actor, id, historyAfter, historyLimit).response().toResponse()
}
