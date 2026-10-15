package dev.fajar.hris.leave.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.leave.delivery.mappers.toResponse
import dev.fajar.hris.leave.delivery.requests.LeaveCarryExpiryResumeRequest
import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.usecases.*
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/leave/carry-expiries")
class LeaveCarryExpiryController(
    private val list: ListLeaveCarryExpiries,
    private val get: GetLeaveCarryExpiry,
    private val resume: ResumeLeaveCarryExpiry,
) {
    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) after: UUID?,
        @Parameter(schema = Schema(minimum = "1", maximum = "100"))
        @RequestParam(defaultValue = "50")
        limit: Int,
    ): Page<LeaveCarryExpiryResponse> =
        list.execute(actor, after, limit).response().let {
            Page(it.items.map { value -> value.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}")
    fun get(
        actor: Actor,
        @PathVariable id: UUID,
        @Parameter(schema = Schema(minimum = "1", maximum = "5000"))
        @RequestParam(required = false)
        after: Int?,
        @Parameter(schema = Schema(minimum = "1", maximum = "100"))
        @RequestParam(defaultValue = "50")
        limit: Int,
    ): LeaveCarryExpiryDetailsResponse =
        get.execute(actor, id, after, limit).response().toResponse()

    @PostMapping("/{id}/resume")
    fun resume(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: LeaveCarryExpiryResumeRequest,
    ): MutationResponse =
        resume
            .execute(actor, operationId, id, body.expectedVersion, body.reason)
            .response()
            .toResponse()
}
