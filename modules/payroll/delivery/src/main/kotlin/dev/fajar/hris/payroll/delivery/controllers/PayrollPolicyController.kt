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
class PayrollPolicyController(
    private val save: SavePayrollPolicy,
    private val get: GetPayrollPolicy,
    private val history: GetPayrollPolicyHistory,
    private val rules: ListPayrollIncomeTaxRules,
) {
    @PutMapping("/policy")
    fun save(
        actor: Actor,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PayrollPolicyRequest,
    ): MutationResponse =
        save
            .execute(actor, operationId, body.toPolicy(), body.expectedVersion, body.reason)
            .response()
            .toResponse()

    @GetMapping("/policy")
    fun get(actor: Actor, @RequestParam asOf: YearMonth): PayrollPolicyResponse =
        get.execute(actor, asOf).response().toResponse()

    @GetMapping("/policy/history")
    fun history(
        actor: Actor,
        @RequestParam(required = false) after: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<PayrollPolicyRevisionResponse> =
        history.execute(actor, after, limit).response().let {
            Page(it.items.map { revision -> revision.toResponse() }, it.nextCursor)
        }

    @GetMapping("/income-tax-rules")
    fun rules(actor: Actor): List<IncomeTaxRuleResponse> =
        rules.execute(actor).response().map { it.toResponse() }
}
