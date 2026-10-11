package dev.fajar.hris.workforce.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.response
import dev.fajar.hris.workforce.delivery.responses.*
import dev.fajar.hris.workforce.domain.usecases.ListSelfWorkforceEmployments
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/workforce/me/employments")
class SelfWorkforceController(private val list: ListSelfWorkforceEmployments) {
    @GetMapping
    fun list(
        actor: Actor,
        @Parameter(schema = Schema(minLength = 1, maxLength = 32))
        @RequestParam(required = false)
        after: String?,
        @Parameter(schema = Schema(minimum = "1", maximum = "50"))
        @RequestParam(defaultValue = "20")
        limit: Int,
    ): SelfWorkforceDirectoryResponse =
        list.execute(actor, after, limit).response().let { directory ->
            SelfWorkforceDirectoryResponse(
                directory.accountId,
                directory.companyId,
                directory.asOf,
                directory.timezone,
                Page(
                    directory.employments.items.map {
                        WorkforceEmploymentResponse(
                            it.id,
                            it.employeeNumber,
                            it.status.name,
                            it.version,
                        )
                    },
                    directory.employments.nextCursor,
                ),
            )
        }
}
