package dev.fajar.hris.payroll.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.payroll.delivery.mappers.*
import dev.fajar.hris.payroll.delivery.requests.*
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.PayrollPeriodStatus
import dev.fajar.hris.payroll.domain.usecases.*
import java.time.YearMonth
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/payroll/periods")
class PayrollPeriodController(
    private val create: CreatePayrollPeriod,
    private val cancel: CancelPayrollPeriod,
    private val get: GetPayrollPeriod,
    private val list: ListPayrollPeriods,
) {
    @PostMapping
    fun create(
        actor: Actor,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PayrollPeriodRequest,
    ): MutationResponse =
        create
            .execute(
                actor,
                operationId,
                body.id,
                body.earningsMonth,
                body.plannedPaymentDate,
                java.util.Set.copyOf(body.employeeIds),
                body.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/{id}/cancel")
    fun cancel(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PayrollPeriodCancellationRequest,
    ): MutationResponse =
        cancel
            .execute(actor, operationId, id, body.expectedVersion, body.reason)
            .response()
            .toResponse()

    @GetMapping("/{id}")
    fun get(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) historyAfter: Long?,
        @RequestParam(defaultValue = "50") historyLimit: Int,
    ): PayrollPeriodDetailsResponse =
        get.execute(actor, id, after, limit, historyAfter, historyLimit).response().toResponse()

    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam from: YearMonth,
        @RequestParam until: YearMonth,
        @RequestParam(required = false) status: PayrollPeriodStatus?,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<PayrollPeriodResponse> =
        list.execute(actor, from, until, status, after, limit).response().let {
            Page(it.items.map { row -> row.toResponse() }, it.nextCursor)
        }
}
