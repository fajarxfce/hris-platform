package dev.fajar.hris.workforce.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.workforce.delivery.mappers.*
import dev.fajar.hris.workforce.delivery.requests.*
import dev.fajar.hris.workforce.delivery.responses.*
import dev.fajar.hris.workforce.domain.entities.OvertimeStatus
import dev.fajar.hris.workforce.domain.usecases.*
import java.time.LocalDate
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/workforce/overtime")
class OvertimeController(
    private val plan: PlanOvertimeRequest,
    private val actual: SubmitOvertimeActual,
    private val decide: DecideOvertimeRequest,
    private val withdraw: WithdrawOvertimeRequest,
    private val details: GetOvertimeRequest,
    private val list: ListOvertimeRequests,
) {
    @PostMapping
    fun plan(
        actor: Actor,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: OvertimePlanRequest,
    ): MutationResponse =
        plan
            .execute(
                actor,
                operationId,
                body.id,
                body.employeeId,
                body.expectedEmploymentVersion,
                body.workDate,
                body.requested.toInterval(),
                body.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/{id}/actual")
    fun actual(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: OvertimeActualRequest,
    ): MutationResponse =
        actual
            .execute(
                actor,
                operationId,
                id,
                body.expectedVersion,
                body.actual.toInterval(),
                body.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/{id}/decisions")
    fun decide(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: OvertimeDecisionRequest,
    ): MutationResponse =
        decide
            .execute(actor, operationId, id, body.expectedVersion, body.decision, body.reason)
            .response()
            .toResponse()

    @PostMapping("/{id}/withdraw")
    fun withdraw(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: OvertimeWithdrawalRequest,
    ): MutationResponse =
        withdraw
            .execute(actor, operationId, id, body.expectedVersion, body.reason)
            .response()
            .toResponse()

    @GetMapping("/{id}")
    fun details(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) historyAfter: Long?,
        @RequestParam(defaultValue = "50") historyLimit: Int,
    ): OvertimeDetailsResponse =
        details.execute(actor, id, historyAfter, historyLimit).response().toResponse()

    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) employeeId: UUID?,
        @RequestParam from: LocalDate,
        @RequestParam until: LocalDate,
        @RequestParam(required = false) status: OvertimeStatus?,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<OvertimeResponse> =
        list.execute(actor, employeeId, from, until, status, after, limit).response().let {
            Page(it.items.map { row -> row.toResponse() }, it.nextCursor)
        }
}
