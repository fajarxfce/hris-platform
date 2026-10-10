package dev.fajar.hris.leave.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.leave.delivery.mappers.*
import dev.fajar.hris.leave.delivery.requests.*
import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.usecases.*
import java.time.LocalDate
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/leave")
class LeavePolicyController(private val save: SaveLeaveType, private val types: ListLeaveTypes) {
    @PutMapping("/types/{id}")
    fun save(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: LeaveTypeRequest,
    ): MutationResponse =
        save
            .execute(actor, operationId, body.toType(id), body.expectedVersion, body.reason)
            .response()
            .toResponse()

    @GetMapping("/types")
    fun types(
        actor: Actor,
        @RequestParam asOf: LocalDate,
        @RequestParam(required = false) after: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<LeaveTypeResponse> =
        types.execute(actor, asOf, after, limit).response().let {
            Page(it.items.map { type -> type.toResponse() }, it.nextCursor)
        }
}
