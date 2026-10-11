package dev.fajar.hris.leave.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.response
import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.usecases.ListSelfLeaveEmployments
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/leave/me/employments")
class SelfLeaveController(private val list: ListSelfLeaveEmployments) {
    @GetMapping
    fun list(
        actor: Actor,
        @Parameter(schema = Schema(minLength = 1, maxLength = 32))
        @RequestParam(required = false)
        after: String?,
        @Parameter(schema = Schema(minimum = "1", maximum = "50"))
        @RequestParam(defaultValue = "20")
        limit: Int,
    ): SelfLeaveDirectoryResponse =
        list.execute(actor, after, limit).response().let { directory ->
            SelfLeaveDirectoryResponse(
                directory.accountId,
                directory.companyId,
                directory.asOf,
                directory.timezone,
                Page(
                    directory.employments.items.map {
                        LeaveEmploymentResponse(
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
