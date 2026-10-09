package dev.fajar.hris.payroll.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.payroll.delivery.mappers.*
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.usecases.*
import java.time.YearMonth
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/payroll/payslips")
class PayrollPayslipController(
    private val get: GetPayrollPayslip,
    private val list: ListPayrollPayslips,
    private val payments: GetPayrollPayslipPayments,
) {
    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) from: YearMonth?,
        @RequestParam(required = false) until: YearMonth?,
        @RequestParam(required = false) employeeId: UUID?,
        @RequestParam(required = false) after: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<PayrollPayslipSummaryResponse> =
        list.execute(actor, from, until, employeeId, after, limit).response().let {
            Page(it.items.map { item -> item.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}")
    fun get(actor: Actor, @PathVariable id: UUID): PayrollPayslipResponse =
        get.execute(actor, id).response().toResponse()

    @GetMapping("/{id}/payments")
    fun payments(actor: Actor, @PathVariable id: UUID): PayrollPaymentProgressResponse =
        payments.execute(actor, id).response().toResponse()
}
