package dev.fajar.hris.leave.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.response
import dev.fajar.hris.leave.delivery.mappers.*
import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.usecases.GetLeavePolicy
import dev.fajar.hris.leave.domain.usecases.ListLeavePolicies
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/leave/policies")
class LeavePolicyReviewController(
    private val list: ListLeavePolicies,
    private val get: GetLeavePolicy,
) {
    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) after: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<LeaveTypeResponse> =
        list.execute(actor, active, after, limit).response().let {
            Page(it.items.map { type -> type.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}")
    fun get(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) historyAfter: Long?,
        @RequestParam(defaultValue = "50") historyLimit: Int,
    ): LeavePolicyReviewResponse =
        get.execute(actor, id, historyAfter, historyLimit).response().toResponse()
}
