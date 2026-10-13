package dev.fajar.hris.expenses.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.response
import dev.fajar.hris.expenses.delivery.responses.ExpenseCostCenterSnapshotResponse
import dev.fajar.hris.expenses.domain.usecases.ListExpenseCostCenters
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/expenses/cost-centers")
class ExpenseCostCenterController(private val list: ListExpenseCostCenters) {
    @GetMapping
    fun list(
        actor: Actor,
        @Parameter(schema = Schema(maxLength = 120)) @RequestParam(defaultValue = "") query: String,
        @Parameter(
            schema = Schema(maxLength = 44, pattern = "COST_CENTER:[A-Z0-9][A-Z0-9_-]{1,31}")
        )
        @RequestParam(required = false)
        after: String?,
        @Parameter(schema = Schema(minimum = "1", maximum = "50"))
        @RequestParam(defaultValue = "20")
        limit: Int,
    ): Page<ExpenseCostCenterSnapshotResponse> =
        list.execute(actor, query, after, limit).response().let { page ->
            Page(
                page.items.map {
                    ExpenseCostCenterSnapshotResponse(it.id, it.code, it.name, it.version)
                },
                page.nextCursor,
            )
        }
}
