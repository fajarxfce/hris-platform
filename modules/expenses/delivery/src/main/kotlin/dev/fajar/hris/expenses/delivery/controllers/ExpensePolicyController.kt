package dev.fajar.hris.expenses.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.expenses.delivery.mappers.*
import dev.fajar.hris.expenses.delivery.requests.ExpenseCategoryRequest
import dev.fajar.hris.expenses.delivery.responses.*
import dev.fajar.hris.expenses.domain.usecases.*
import java.time.LocalDate
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/expenses/categories")
class ExpensePolicyController(
    private val save: SaveExpenseCategory,
    private val categories: ListExpenseCategories,
    private val history: GetExpenseCategoryHistory,
) {
    @PutMapping("/{id}")
    fun save(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: ExpenseCategoryRequest,
    ): MutationResponse =
        save
            .execute(actor, operationId, body.toCategory(id), body.expectedVersion, body.reason)
            .response()
            .toResponse()

    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam asOf: LocalDate,
        @RequestParam(required = false) after: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<ExpenseCategoryResponse> =
        categories.execute(actor, asOf, after, limit).response().let {
            Page(it.items.map { category -> category.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}/history")
    fun history(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) after: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<ExpenseCategoryRevisionResponse> =
        history.execute(actor, id, after, limit).response().let {
            Page(it.items.map { revision -> revision.toResponse() }, it.nextCursor)
        }
}
