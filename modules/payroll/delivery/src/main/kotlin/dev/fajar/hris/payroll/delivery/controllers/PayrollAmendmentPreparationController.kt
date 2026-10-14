package dev.fajar.hris.payroll.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.payroll.delivery.mappers.*
import dev.fajar.hris.payroll.delivery.requests.PayrollAmendmentPreviewRequest
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/payroll/payslips/{id}")
class PayrollAmendmentPreparationController(
    private val preparation: GetPayrollAmendmentPreparation,
    private val preview: PreviewPayrollAmendment,
) {
    @GetMapping("/amendment-preparation")
    fun get(actor: Actor, @PathVariable id: UUID): PayrollAmendmentPreparationResponse =
        preparation.execute(actor, id).response().toResponse()

    @PostMapping("/amendment-preview")
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
