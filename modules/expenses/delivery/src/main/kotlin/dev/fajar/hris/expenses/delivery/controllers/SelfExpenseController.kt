package dev.fajar.hris.expenses.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.response
import dev.fajar.hris.expenses.delivery.responses.*
import dev.fajar.hris.expenses.domain.usecases.ListSelfExpenseEmployments
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/expenses/me/employments")
class SelfExpenseController(private val list: ListSelfExpenseEmployments) {
    @GetMapping
    fun list(
        actor: Actor,
        @Parameter(schema = Schema(minLength = 1, maxLength = 32))
        @RequestParam(required = false)
        after: String?,
        @Parameter(schema = Schema(minimum = "1", maximum = "50"))
        @RequestParam(defaultValue = "20")
        limit: Int,
    ): SelfExpenseDirectoryResponse =
        list.execute(actor, after, limit).response().let { directory ->
            SelfExpenseDirectoryResponse(
                directory.accountId,
                directory.companyId,
                directory.asOf,
                directory.timezone,
                Page(
                    directory.employments.items.map {
                        ExpenseEmploymentResponse(
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
