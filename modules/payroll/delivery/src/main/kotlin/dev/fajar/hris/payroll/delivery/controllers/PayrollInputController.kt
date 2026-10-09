package dev.fajar.hris.payroll.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.payroll.delivery.mappers.*
import dev.fajar.hris.payroll.delivery.requests.*
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.PayrollInputStatus
import dev.fajar.hris.payroll.domain.usecases.*
import java.time.YearMonth
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/payroll")
class PayrollInputController(
    private val save: SavePayrollInput,
    private val verify: VerifyPayrollInput,
    private val get: GetPayrollInput,
    private val history: GetPayrollInputHistory,
    private val list: ListPayrollInputs,
    private val source: GetPayrollWorkSource,
) {
    @PutMapping("/employees/{employeeId}/inputs/{month}")
    fun save(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @PathVariable month: YearMonth,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PayrollInputRequest,
    ): MutationResponse =
        save
            .execute(
                actor,
                operationId,
                employeeId,
                month,
                body.workJobId,
                body.expectedWorkPeriodVersion,
                body.expectedEmploymentVersion,
                body.terms.toTerms(),
                body.expectedVersion,
                body.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/employees/{employeeId}/inputs/{month}/verify")
    fun verify(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @PathVariable month: YearMonth,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PayrollInputVerificationRequest,
    ): MutationResponse =
        verify
            .execute(actor, operationId, employeeId, month, body.expectedVersion, body.reason)
            .response()
            .toResponse()

    @GetMapping("/employees/{employeeId}/inputs/{month}")
    fun get(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @PathVariable month: YearMonth,
        @RequestParam(required = false) revision: Long?,
    ): PayrollInputResponse =
        get.execute(actor, employeeId, month, revision).response().toResponse()

    @GetMapping("/employees/{employeeId}/inputs/{month}/history")
    fun history(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @PathVariable month: YearMonth,
        @RequestParam(required = false) after: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<PayrollInputSummaryResponse> =
        history.execute(actor, employeeId, month, after, limit).response().let {
            Page(it.items.map { row -> row.toResponse() }, it.nextCursor)
        }

    @GetMapping("/inputs")
    fun list(
        actor: Actor,
        @RequestParam month: YearMonth,
        @RequestParam(required = false) status: PayrollInputStatus?,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<PayrollInputSummaryResponse> =
        list.execute(actor, month, status, after, limit).response().let {
            Page(it.items.map { row -> row.toResponse() }, it.nextCursor)
        }

    @GetMapping("/employees/{employeeId}/work-source")
    fun source(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @RequestParam month: YearMonth,
    ): PayrollWorkSourceResponse = source.execute(actor, employeeId, month).response().toResponse()
}
