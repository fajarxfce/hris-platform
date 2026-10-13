package dev.fajar.hris.expenses.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.expenses.delivery.mappers.toResponse
import dev.fajar.hris.expenses.delivery.responses.SelfExpenseDraftReferencesResponse
import dev.fajar.hris.expenses.domain.entities.ExpenseDraftReferenceQuery
import dev.fajar.hris.expenses.domain.usecases.GetSelfExpenseDraftReferences
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(
    "/api/v1/companies/{companyId}/expenses/me/employments/{employmentId}/draft-references"
)
class SelfExpenseDraftReferencesController(private val references: GetSelfExpenseDraftReferences) {
    @GetMapping
    fun get(
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
    ): SelfExpenseDraftReferencesResponse =
        references
            .execute(
                actor,
                employmentId,
                ExpenseDraftReferenceQuery(
                    categoryIds.orEmpty(),
                    costCenterIds.orEmpty(),
                    receiptRevisionIds.orEmpty(),
                ),
            )
            .response()
            .toResponse()
}
