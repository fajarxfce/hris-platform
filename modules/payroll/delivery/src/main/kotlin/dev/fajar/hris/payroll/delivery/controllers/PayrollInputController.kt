package dev.fajar.hris.payroll.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.payroll.delivery.mappers.*
import dev.fajar.hris.payroll.delivery.requests.*
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.PayrollInputEmployeeStatus
import dev.fajar.hris.payroll.domain.entities.PayrollInputStatus
import dev.fajar.hris.payroll.domain.usecases.*
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
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
    private val review: GetPayrollInputReview,
    private val employees: ListPayrollInputEmployees,
) {
    @GetMapping("/inputs/employees")
    fun employees(
        actor: Actor,
        @RequestParam month: YearMonth,
        @Parameter(schema = Schema(maxLength = 120)) @RequestParam(defaultValue = "") query: String,
        @RequestParam(required = false) status: PayrollInputEmployeeStatus?,
        @Parameter(schema = Schema(minLength = 1, maxLength = 32))
        @RequestParam(required = false)
        after: String?,
        @Parameter(schema = Schema(minimum = "1", maximum = "50"))
        @RequestParam(defaultValue = "50")
        limit: Int,
    ): Page<PayrollInputEmployeeResponse> =
        employees.execute(actor, month, query, status, after, limit).response().let {
            Page(it.items.map { row -> row.toResponse() }, it.nextCursor)
        }

    @GetMapping("/employees/{employeeId}/inputs/{month}/review")
    fun review(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @PathVariable month: YearMonth,
        @Parameter(schema = Schema(minimum = "0", maximum = "999"))
        @RequestParam(required = false)
        historyAfter: Long?,
        @Parameter(schema = Schema(minimum = "1", maximum = "200"))
        @RequestParam(defaultValue = "50")
        historyLimit: Int,
    ): PayrollInputReviewResponse =
        review.execute(actor, employeeId, month, historyAfter, historyLimit).response().toResponse()

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
                body.workAdjustmentId,
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
