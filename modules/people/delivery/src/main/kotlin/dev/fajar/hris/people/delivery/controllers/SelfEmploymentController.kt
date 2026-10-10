package dev.fajar.hris.people.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.response
import dev.fajar.hris.people.delivery.mappers.toResponse
import dev.fajar.hris.people.delivery.responses.SelfEmploymentDirectoryResponse
import dev.fajar.hris.people.domain.usecases.ListSelfEmployments
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/me/employments")
class SelfEmploymentController(private val list: ListSelfEmployments) {
    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) after: String?,
        @RequestParam(defaultValue = "20") limit: Int,
    ): SelfEmploymentDirectoryResponse =
        list.execute(actor, after, limit).response().let {
            SelfEmploymentDirectoryResponse(
                it.asOf,
                it.timezone,
                Page(
                    it.employments.items.map { employee -> employee.toResponse() },
                    it.employments.nextCursor,
                ),
            )
        }
}
