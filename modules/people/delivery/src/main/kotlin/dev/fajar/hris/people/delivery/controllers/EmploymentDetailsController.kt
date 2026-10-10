package dev.fajar.hris.people.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.people.delivery.mappers.toResponse
import dev.fajar.hris.people.delivery.responses.EmploymentDetailsResponse
import dev.fajar.hris.people.domain.usecases.GetEmploymentDetails
import java.time.LocalDate
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/employees/{id}/employment")
class EmploymentDetailsController(private val get: GetEmploymentDetails) {
    @GetMapping
    fun get(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam asOf: LocalDate,
    ): EmploymentDetailsResponse = get.execute(actor, id, asOf).response().toResponse()
}
