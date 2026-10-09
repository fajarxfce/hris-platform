package dev.fajar.hris.payroll.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.payroll.delivery.mappers.*
import dev.fajar.hris.payroll.delivery.requests.*
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.usecases.*
import java.time.YearMonth
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/payroll")
class CompensationController(
    private val save: SaveEmployeeCompensation,
    private val get: GetEmployeeCompensation,
    private val history: GetCompensationHistory,
    private val list: ListEmployeeCompensations,
) {
    @PutMapping("/employees/{employeeId}/compensation")
    fun save(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: CompensationRequest,
    ): MutationResponse =
        save
            .execute(
                actor,
                operationId,
                employeeId,
                body.effectiveFrom,
                body.terms.toTerms(),
                body.expectedVersion,
                body.expectedEmploymentVersion,
                body.reason,
            )
            .response()
            .toResponse()

    @GetMapping("/employees/{employeeId}/compensation")
    fun get(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @RequestParam asOf: YearMonth,
    ): CompensationResponse = get.execute(actor, employeeId, asOf).response().toResponse()

    @GetMapping("/employees/{employeeId}/compensation/history")
    fun history(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @RequestParam(required = false) after: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<CompensationRevisionResponse> =
        history.execute(actor, employeeId, after, limit).response().let {
            Page(it.items.map { revision -> revision.toResponse() }, it.nextCursor)
        }

    @GetMapping("/compensations")
    fun list(
        actor: Actor,
        @RequestParam asOf: YearMonth,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<CompensationResponse> =
        list.execute(actor, asOf, after, limit).response().let {
            Page(it.items.map { compensation -> compensation.toResponse() }, it.nextCursor)
        }
}
