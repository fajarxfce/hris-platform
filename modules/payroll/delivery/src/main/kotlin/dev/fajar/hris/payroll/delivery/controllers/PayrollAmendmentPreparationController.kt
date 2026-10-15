package dev.fajar.hris.payroll.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.http.response
import dev.fajar.hris.payroll.delivery.mappers.*
import dev.fajar.hris.payroll.delivery.requests.PayrollAmendmentPreviewRequest
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.usecases.*
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/payroll")
class PayrollAmendmentPreparationController(
    private val preparation: GetPayrollAmendmentPreparation,
    private val preview: PreviewPayrollAmendment,
    private val sources: ListPayrollAmendmentSources,
) {
    @GetMapping("/amendment-sources")
    fun list(
        actor: Actor,
        @Parameter(schema = Schema(minimum = "2024", maximum = "2100")) @RequestParam year: Int,
        @RequestParam(required = false) employeeId: UUID?,
        @Parameter(schema = Schema(maxLength = 120)) @RequestParam(defaultValue = "") query: String,
        @RequestParam(required = false) after: UUID?,
        @Parameter(schema = Schema(minimum = "1", maximum = "100"))
        @RequestParam(defaultValue = "50")
        limit: Int,
    ): Page<PayrollAmendmentCandidateResponse> =
        sources.execute(actor, year, employeeId, query, after, limit).response().let {
            Page(it.items.map { entry -> entry.toResponse() }, it.nextCursor)
        }

    @GetMapping("/payslips/{id}/amendment-preparation")
    fun get(actor: Actor, @PathVariable id: UUID): PayrollAmendmentPreparationResponse =
        preparation.execute(actor, id).response().toResponse()

    @PostMapping("/payslips/{id}/amendment-preview")
    fun preview(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestBody body: PayrollAmendmentPreviewRequest,
    ): PayrollAmendmentPreviewResponse =
        preview
            .execute(
                actor,
                id,
                body.sourceVersions.map { it.toReference() },
                body.corrections.map { it.toRemuneration() },
            )
            .response()
            .toResponse()
}
