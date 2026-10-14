package dev.fajar.hris.payroll.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.payroll.delivery.mappers.*
import dev.fajar.hris.payroll.delivery.requests.*
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.PayrollAmendmentStatus
import dev.fajar.hris.payroll.domain.usecases.*
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/payroll/amendments")
class PayrollAmendmentController(
    private val list: ListPayrollAmendments,
    private val get: GetPayrollAmendment,
    private val history: GetPayrollAmendmentHistory,
    private val revision: GetPayrollAmendmentRevision,
    private val calculation: GetPayrollAmendmentCalculation,
    private val save: SavePayrollAmendment,
    private val calculate: CalculatePayrollAmendment,
    private val cancel: CancelPayrollAmendment,
) {
    @GetMapping
    fun list(
        actor: Actor,
        @Parameter(schema = Schema(minimum = "2024", maximum = "2100")) @RequestParam year: Int,
        @RequestParam(required = false) employeeId: UUID?,
        @RequestParam(required = false) status: PayrollAmendmentStatus?,
        @Parameter(schema = Schema(maxLength = 120)) @RequestParam(defaultValue = "") query: String,
        @RequestParam(required = false) after: UUID?,
        @Parameter(schema = Schema(minimum = "1", maximum = "100"))
        @RequestParam(defaultValue = "50")
        limit: Int,
    ): Page<PayrollAmendmentSummaryResponse> =
        list.execute(actor, year, employeeId, status, query, after, limit).response().let {
            Page(it.items.map { row -> row.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}")
    fun get(actor: Actor, @PathVariable id: UUID): PayrollAmendmentDetailsResponse =
        get.execute(actor, id).response().toResponse()

    @GetMapping("/{id}/history")
    fun history(
        actor: Actor,
        @PathVariable id: UUID,
        @Parameter(schema = Schema(minimum = "0", maximum = "999"))
        @RequestParam(required = false)
        after: Long?,
        @Parameter(schema = Schema(minimum = "1", maximum = "100"))
        @RequestParam(defaultValue = "50")
        limit: Int,
    ): Page<PayrollAmendmentSummaryResponse> =
        history.execute(actor, id, after, limit).response().let {
            Page(it.items.map { row -> row.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}/revisions/{version}")
    fun revision(
        actor: Actor,
        @PathVariable id: UUID,
        @Parameter(schema = Schema(minimum = "0", maximum = "999")) @PathVariable version: Long,
    ): PayrollAmendmentResponse = revision.execute(actor, id, version).response().toResponse()

    @GetMapping("/{id}/calculations/{calculationId}")
    fun calculation(
        actor: Actor,
        @PathVariable id: UUID,
        @PathVariable calculationId: UUID,
    ): PayrollAmendmentCalculationAttemptResponse =
        calculation.execute(actor, id, calculationId).response().toResponse()

    @PutMapping("/{id}")
    fun save(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: SavePayrollAmendmentRequest,
    ): MutationResponse =
        save
            .execute(
                actor,
                operationId,
                id,
                body.firstAssessmentId,
                body.expectedVersion,
                body.toTerms(),
                body.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/{id}/calculate")
    fun calculate(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PayrollAmendmentCommandRequest,
    ): MutationResponse =
        calculate
            .execute(actor, operationId, id, body.expectedVersion, body.reason)
            .response()
            .toResponse()

    @PostMapping("/{id}/cancel")
    fun cancel(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PayrollAmendmentCommandRequest,
    ): MutationResponse =
        cancel
            .execute(actor, operationId, id, body.expectedVersion, body.reason)
            .response()
            .toResponse()
}
