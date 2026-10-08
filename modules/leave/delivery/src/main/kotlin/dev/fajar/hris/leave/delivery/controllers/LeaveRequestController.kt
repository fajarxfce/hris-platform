package dev.fajar.hris.leave.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.leave.delivery.mappers.*
import dev.fajar.hris.leave.delivery.requests.*
import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/leave/requests")
class LeaveRequestController(
    private val submit: SubmitLeaveRequest,
    private val decide: DecideLeaveRequest,
    private val withdraw: WithdrawLeaveRequest,
    private val cancel: RequestLeaveCancellation,
    private val details: GetLeaveRequest,
    private val list: ListLeaveRequests,
) {
    @PostMapping
    fun submit(
        actor: Actor,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: LeaveSubmissionRequest,
    ): MutationResponse =
        submit
            .execute(
                actor,
                operationId,
                body.id,
                body.employeeId,
                body.typeId,
                body.days.map { RequestedLeaveDay(it.workDate, it.portion) },
                body.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/{id}/decisions")
    fun decide(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: LeaveDecisionRequest,
    ): MutationResponse =
        decide
            .execute(actor, operationId, id, body.version, body.decision, body.reason)
            .response()
            .toResponse()

    @PostMapping("/{id}/withdraw")
    fun withdraw(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: LeaveRequestActionRequest,
    ): MutationResponse =
        withdraw.execute(actor, operationId, id, body.version, body.reason).response().toResponse()

    @PostMapping("/{id}/cancellation")
    fun cancellation(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: LeaveRequestActionRequest,
    ): MutationResponse =
        cancel.execute(actor, operationId, id, body.version, body.reason).response().toResponse()

    @GetMapping("/{id}")
    fun details(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) historyAfter: Long?,
        @RequestParam(defaultValue = "50") historyLimit: Int,
    ): LeaveRequestResponse =
        details.execute(actor, id, historyAfter, historyLimit).response().toResponse()

    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) employeeId: UUID?,
        @RequestParam(required = false) status: LeaveStatus?,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<LeaveRequestSummaryResponse> =
        list.execute(actor, employeeId, status, after, limit).response().let {
            Page(it.items.map { request -> request.toResponse() }, it.nextCursor)
        }
}
