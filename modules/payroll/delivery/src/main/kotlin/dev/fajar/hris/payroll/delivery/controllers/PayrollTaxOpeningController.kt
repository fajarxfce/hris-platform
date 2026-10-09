package dev.fajar.hris.payroll.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.payroll.delivery.mappers.*
import dev.fajar.hris.payroll.delivery.requests.*
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.PayrollTaxOpeningStatus
import dev.fajar.hris.payroll.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/payroll")
class PayrollTaxOpeningController(
    private val save: SavePayrollTaxOpening,
    private val verify: VerifyPayrollTaxOpening,
    private val get: GetPayrollTaxOpening,
    private val history: GetPayrollTaxOpeningHistory,
    private val list: ListPayrollTaxOpenings,
) {
    @PutMapping("/employees/{employeeId}/tax-openings/{year}")
    fun save(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @PathVariable year: Int,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PayrollTaxOpeningRequest,
    ): MutationResponse =
        save
            .execute(
                actor,
                operationId,
                employeeId,
                year,
                body.terms.toTerms(),
                body.expectedVersion,
                body.expectedEmploymentVersion,
                body.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/employees/{employeeId}/tax-openings/{year}/verify")
    fun verify(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @PathVariable year: Int,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PayrollTaxOpeningVerificationRequest,
    ): MutationResponse =
        verify
            .execute(actor, operationId, employeeId, year, body.expectedVersion, body.reason)
            .response()
            .toResponse()

    @GetMapping("/employees/{employeeId}/tax-openings/{year}")
    fun get(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @PathVariable year: Int,
    ): PayrollTaxOpeningResponse = get.execute(actor, employeeId, year).response().toResponse()

    @GetMapping("/employees/{employeeId}/tax-openings/{year}/history")
    fun history(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @PathVariable year: Int,
        @RequestParam(required = false) after: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<PayrollTaxOpeningResponse> =
        history.execute(actor, employeeId, year, after, limit).response().let {
            Page(it.items.map { row -> row.toResponse() }, it.nextCursor)
        }

    @GetMapping("/tax-openings")
    fun list(
        actor: Actor,
        @RequestParam year: Int,
        @RequestParam(required = false) status: PayrollTaxOpeningStatus?,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<PayrollTaxOpeningResponse> =
        list.execute(actor, year, status, after, limit).response().let {
            Page(it.items.map { row -> row.toResponse() }, it.nextCursor)
        }
}
