package dev.fajar.hris.workforce.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.response
import dev.fajar.hris.workforce.delivery.mappers.toResponse
import dev.fajar.hris.workforce.delivery.responses.*
import dev.fajar.hris.workforce.domain.usecases.GetOvertimePlanningReview
import dev.fajar.hris.workforce.domain.usecases.ListOvertimePlanningEmployees
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/workforce/overtime/planning/employees")
class OvertimePlanningController(
    private val employees: ListOvertimePlanningEmployees,
    private val review: GetOvertimePlanningReview,
) {
    @GetMapping
    fun employees(
        actor: Actor,
        @RequestParam workDate: LocalDate,
        @Parameter(schema = Schema(maxLength = 120)) @RequestParam(defaultValue = "") query: String,
        @Parameter(schema = Schema(minLength = 1, maxLength = 32))
        @RequestParam(required = false)
        after: String?,
        @Parameter(schema = Schema(minimum = "1", maximum = "50"))
        @RequestParam(defaultValue = "50")
        limit: Int,
    ): Page<WorkforceEmployeeResponse> =
        employees.execute(actor, workDate, query, after, limit).response().let { page ->
            Page(page.items.map { it.toResponse() }, page.nextCursor)
        }

    @GetMapping("/{employeeId}")
    fun review(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @RequestParam workDate: LocalDate,
    ): OvertimePlanningReviewResponse =
        review.execute(actor, employeeId, workDate).response().toResponse()
}
