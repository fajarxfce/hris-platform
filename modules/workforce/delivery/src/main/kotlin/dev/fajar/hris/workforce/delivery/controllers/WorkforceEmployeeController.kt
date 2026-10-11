package dev.fajar.hris.workforce.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.response
import dev.fajar.hris.workforce.delivery.mappers.toResponse
import dev.fajar.hris.workforce.delivery.responses.*
import dev.fajar.hris.workforce.domain.usecases.GetEmployeeCalendarReview
import dev.fajar.hris.workforce.domain.usecases.ListWorkforceEmployees
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/workforce/employees")
class WorkforceEmployeeController(
    private val list: ListWorkforceEmployees,
    private val review: GetEmployeeCalendarReview,
) {
    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam asOf: LocalDate,
        @RequestParam(defaultValue = "") query: String,
        @Parameter(schema = Schema(minLength = 1, maxLength = 32))
        @RequestParam(required = false)
        after: String?,
        @Parameter(schema = Schema(minimum = "1", maximum = "50"))
        @RequestParam(defaultValue = "50")
        limit: Int,
    ): Page<WorkforceEmployeeResponse> =
        list.execute(actor, asOf, query, after, limit).response().let { page ->
            Page(page.items.map { it.toResponse() }, page.nextCursor)
        }

    @GetMapping("/{id}/calendar/review")
    fun review(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam from: LocalDate,
        @RequestParam until: LocalDate,
    ): EmployeeCalendarReviewResponse =
        review.execute(actor, id, from, until).response().toResponse()
}
