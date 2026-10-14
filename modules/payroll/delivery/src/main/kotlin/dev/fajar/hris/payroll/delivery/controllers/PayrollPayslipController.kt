package dev.fajar.hris.payroll.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.payroll.delivery.mappers.*
import dev.fajar.hris.payroll.delivery.pdf.*
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.PayrollPayslipReadScope
import dev.fajar.hris.payroll.domain.usecases.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.security.MessageDigest
import java.time.YearMonth
import java.util.HexFormat
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/payroll/payslips")
class PayrollPayslipController(
    private val get: GetPayrollPayslip,
    private val list: ListPayrollPayslips,
    private val payments: GetPayrollPayslipPayments,
    private val pdf: PayrollPayslipPdfWriter,
    private val downloads: BinaryResponseWriter,
) {
    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) from: YearMonth?,
        @RequestParam(required = false) until: YearMonth?,
        @RequestParam(required = false) employeeId: UUID?,
        @RequestParam(required = false) after: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "AUTHORIZED") scope: PayrollPayslipReadScope,
        @RequestParam(defaultValue = "") query: String,
    ): Page<PayrollPayslipSummaryResponse> =
        list.execute(actor, from, until, employeeId, after, limit, scope, query).response().let {
            Page(it.items.map { item -> item.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}")
    fun get(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "AUTHORIZED") scope: PayrollPayslipReadScope,
    ): PayrollPayslipResponse = get.execute(actor, id, scope).response().toResponse()

    @GetMapping("/{id}/pdf", produces = ["application/pdf"])
    fun pdf(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "ID") language: PayrollDocumentLanguage,
        @RequestParam(defaultValue = "AUTHORIZED") scope: PayrollPayslipReadScope,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val payslip = get.execute(actor, id, scope).response()
        val bytes = pdf.write(payslip, language)
        get.execute(actor, id, scope).response()
        val digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        writeBinaryDownloadResponse(
            request,
            response,
            BinaryDownloadMetadata(
                "payslip-$id-${language.name.lowercase()}.pdf",
                "application/pdf",
                bytes.size.toLong(),
                "\"$digest\"",
            ),
            downloads,
            PAYSLIP_PDF_MAXIMUM_BYTES,
        ) { offset, length ->
            get.execute(actor, id, scope).map {
                bytes.copyOfRange(offset.toInt(), offset.toInt() + length)
            }
        }
    }

    @GetMapping("/{id}/payments")
    fun payments(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "AUTHORIZED") scope: PayrollPayslipReadScope,
    ): PayrollPaymentProgressResponse = payments.execute(actor, id, scope).response().toResponse()
}
