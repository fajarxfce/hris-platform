package dev.fajar.hris.expenses.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.expenses.delivery.mappers.toResponse
import dev.fajar.hris.expenses.delivery.responses.ExpenseClaimReviewResponse
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimReadScope
import dev.fajar.hris.expenses.domain.usecases.GetExpenseClaimReview
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/expenses/claims/{id}/review")
class ExpenseClaimReviewController(private val review: GetExpenseClaimReview) {
    @GetMapping
    fun get(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) historyAfter: Long?,
        @RequestParam(defaultValue = "50") historyLimit: Int,
        @RequestParam(required = false) submissionsAfter: Int?,
        @RequestParam(defaultValue = "20") submissionsLimit: Int,
        @RequestParam(defaultValue = "AUTHORIZED") scope: ExpenseClaimReadScope,
    ): ExpenseClaimReviewResponse =
        review
            .execute(
                actor,
                id,
                historyAfter,
                historyLimit,
                submissionsAfter,
                submissionsLimit,
                scope,
            )
            .response()
            .toResponse()
}
