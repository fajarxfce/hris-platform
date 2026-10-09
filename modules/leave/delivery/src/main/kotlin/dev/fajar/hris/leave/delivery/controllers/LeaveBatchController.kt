package dev.fajar.hris.leave.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.leave.delivery.mappers.*
import dev.fajar.hris.leave.delivery.requests.*
import dev.fajar.hris.leave.delivery.responses.*
import dev.fajar.hris.leave.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/leave")
class LeaveBatchController(
    private val accrual: StartLeaveAccrualBatch,
    private val close: StartLeaveYearCloseBatch,
    private val resume: ResumeLeaveBatch,
    private val get: GetLeaveBatch,
    private val list: ListLeaveBatches,
) {
    @PostMapping("/accrual-batches")
    fun accrue(
        actor: Actor,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: LeaveAccrualBatchRequest,
    ): MutationResponse =
        accrual
            .execute(
                actor,
                operationId,
                body.id,
                body.typeId,
                body.month,
                body.expectedPolicyVersion,
                body.employeeIds,
                body.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/year-close-batches")
    fun close(
        actor: Actor,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: LeaveYearCloseBatchRequest,
    ): MutationResponse =
        close
            .execute(
                actor,
                operationId,
                body.id,
                body.typeId,
                body.year,
                body.expectedPolicyVersion,
                body.employeeIds,
                body.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/batches/{id}/resume")
    fun resume(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: LeaveBatchResumeRequest,
    ): MutationResponse =
        resume
            .execute(actor, operationId, id, body.expectedVersion, body.reason)
            .response()
            .toResponse()

    @GetMapping("/batches/{id}")
    fun get(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) after: Int?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): LeaveBatchDetailsResponse = get.execute(actor, id, after, limit).response().toResponse()

    @GetMapping("/batches")
    fun list(
        actor: Actor,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<LeaveBatchResponse> =
        list.execute(actor, after, limit).response().let {
            Page(it.items.map { batch -> batch.toResponse() }, it.nextCursor)
        }
}
