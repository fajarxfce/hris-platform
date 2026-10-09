package dev.fajar.hris.payroll.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.payroll.delivery.mappers.*
import dev.fajar.hris.payroll.delivery.requests.*
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.usecases.*
import java.time.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/payroll")
class PayrollRunController(
    private val start: StartPayrollCalculation,
    private val resume: ResumePayrollCalculation,
    private val abandon: AbandonPayrollCalculation,
    private val get: GetPayrollRun,
    private val employee: GetPayrollRunEmployee,
    private val list: ListPayrollRuns,
) {
    @PostMapping("/periods/{periodId}/runs")
    fun start(
        actor: Actor,
        @PathVariable periodId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PayrollCalculationStartRequest,
    ): MutationResponse =
        start
            .execute(
                actor,
                operationId,
                body.id,
                periodId,
                body.incomeDueDate,
                body.expectedPeriodVersion,
                body.expectedWorkPeriodVersion,
                body.expectedPolicyVersion,
                body.reviewReference,
                body.reason,
            )
            .response()
            .toResponse()

    @GetMapping("/periods/{periodId}/runs")
    fun list(
        actor: Actor,
        @PathVariable periodId: UUID,
        @RequestParam(required = false) after: Int?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<PayrollRunResponse> =
        list.execute(actor, periodId, after, limit).response().let {
            Page(it.items.map { row -> row.toResponse() }, it.nextCursor)
        }

    @GetMapping("/runs/{id}")
    fun get(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) after: Int?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): PayrollRunDetailsResponse = get.execute(actor, id, after, limit).response().toResponse()

    @GetMapping("/runs/{id}/employees/{employeeId}")
    fun employee(
        actor: Actor,
        @PathVariable id: UUID,
        @PathVariable employeeId: UUID,
    ): PayrollRunResultResponse = employee.execute(actor, id, employeeId).response().toResponse()

    @PostMapping("/runs/{id}/resume")
    fun resume(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PayrollRunActionRequest,
    ): MutationResponse =
        resume
            .execute(actor, operationId, id, body.expectedVersion, body.reason)
            .response()
            .toResponse()

    @PostMapping("/runs/{id}/abandon")
    fun abandon(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PayrollRunAbandonRequest,
    ): MutationResponse =
        abandon
            .execute(
                actor,
                operationId,
                id,
                body.expectedVersion,
                body.expectedPeriodVersion,
                body.reason,
            )
            .response()
            .toResponse()
}
