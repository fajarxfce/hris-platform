package dev.fajar.hris.expenses.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.response
import dev.fajar.hris.expenses.delivery.mappers.toResponse
import dev.fajar.hris.expenses.delivery.responses.*
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimReadScope
import dev.fajar.hris.expenses.domain.entities.ExpenseDraftReferenceQuery
import dev.fajar.hris.expenses.domain.usecases.*
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/expenses/drafting/employees")
class ExpenseDraftingController(
    private val employees: ListExpenseDraftEmployees,
    private val receipts: ListExpenseDraftReceipts,
    private val references: GetExpenseDraftReferences,
) {
    @GetMapping
    fun employees(
        actor: Actor,
        @Parameter(schema = Schema(maxLength = 120)) @RequestParam(defaultValue = "") query: String,
        @Parameter(schema = Schema(minLength = 1, maxLength = 32))
        @RequestParam(required = false)
        after: String?,
        @Parameter(schema = Schema(minimum = "1", maximum = "50"))
        @RequestParam(defaultValue = "20")
        limit: Int,
    ): ExpenseDraftDirectoryResponse =
        employees.execute(actor, query, after, limit).response().toResponse()

    @GetMapping("/{employmentId}/receipts")
    fun receipts(
        actor: Actor,
        @PathVariable employmentId: UUID,
        @RequestParam(required = false) after: UUID?,
        @Parameter(schema = Schema(minimum = "1", maximum = "50"))
        @RequestParam(defaultValue = "20")
        limit: Int,
    ): Page<ExpenseDraftReceiptOptionResponse> =
        receipts.execute(actor, employmentId, after, limit).response().let {
            Page(it.items.map { receipt -> receipt.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{employmentId}/references")
    fun references(
        actor: Actor,
        @PathVariable employmentId: UUID,
        @Parameter(
            array =
                ArraySchema(
                    maxItems = 20,
                    uniqueItems = true,
                    schema = Schema(type = "string", format = "uuid"),
                )
        )
        @RequestParam(required = false)
        categoryIds: Set<UUID>?,
        @Parameter(
            array =
                ArraySchema(
                    maxItems = 20,
                    uniqueItems = true,
                    schema = Schema(type = "string", format = "uuid"),
                )
        )
        @RequestParam(required = false)
        costCenterIds: Set<UUID>?,
        @Parameter(
            array =
                ArraySchema(
                    maxItems = 60,
                    uniqueItems = true,
                    schema = Schema(type = "string", format = "uuid"),
                )
        )
        @RequestParam(required = false)
        receiptRevisionIds: Set<UUID>?,
    ): ExpenseDraftReferencesResponse =
        references
            .execute(
                actor,
                employmentId,
                ExpenseDraftReferenceQuery(
                    categoryIds.orEmpty(),
                    costCenterIds.orEmpty(),
                    receiptRevisionIds.orEmpty(),
                ),
                ExpenseClaimReadScope.AUTHORIZED,
            )
            .response()
            .toResponse()
}
