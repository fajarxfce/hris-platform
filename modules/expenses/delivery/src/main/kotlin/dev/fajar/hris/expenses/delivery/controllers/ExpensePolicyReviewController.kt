package dev.fajar.hris.expenses.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.response
import dev.fajar.hris.expenses.delivery.mappers.*
import dev.fajar.hris.expenses.delivery.responses.*
import dev.fajar.hris.expenses.domain.usecases.GetExpensePolicy
import dev.fajar.hris.expenses.domain.usecases.ListExpensePolicies
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/expenses/policies")
class ExpensePolicyReviewController(
    private val list: ListExpensePolicies,
    private val get: GetExpensePolicy,
) {
    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) after: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<ExpenseCategoryResponse> =
        list.execute(actor, active, after, limit).response().let {
            Page(it.items.map { category -> category.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}")
    fun get(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) historyAfter: Long?,
        @RequestParam(defaultValue = "50") historyLimit: Int,
    ): ExpensePolicyReviewResponse =
        get.execute(actor, id, historyAfter, historyLimit).response().toResponse()
}
