package dev.fajar.hris.payroll.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.payroll.delivery.mappers.*
import dev.fajar.hris.payroll.delivery.requests.PayrollFinalizationRequest
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.PayrollRunStatus
import dev.fajar.hris.payroll.domain.usecases.*
import java.time.YearMonth
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/payroll")
class PayrollFinalizationController(
    private val start: StartPayrollFinalization,
    private val get: GetPayrollFinalization,
    private val list: ListPayrollFinalizations,
    private val preparation: GetPayrollFinalizationPreparation,
    private val runs: ListPayrollPublicationRuns,
) {
    @GetMapping("/publication-runs")
    fun runs(
        actor: Actor,
        @RequestParam from: YearMonth,
        @RequestParam until: YearMonth,
        @RequestParam(required = false) status: PayrollRunStatus?,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<PayrollRunResponse> =
        runs.execute(actor, from, until, status, after, limit).response().let {
            Page(it.items.map { row -> row.toResponse() }, it.nextCursor)
        }

    @GetMapping("/runs/{runId}/finalization-preparation")
    fun preparation(
        actor: Actor,
        @PathVariable runId: UUID,
    ): PayrollFinalizationPreparationResponse =
        preparation.execute(actor, runId).response().toResponse()

    @PostMapping("/runs/{runId}/finalizations")
    fun start(
        actor: Actor,
        @PathVariable runId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PayrollFinalizationRequest,
    ): MutationResponse =
        start
            .execute(
                actor,
                operationId,
                body.id,
                runId,
                body.reviewId,
                body.expectedRunVersion,
                body.expectedReviewVersion,
                body.expectedApprovalVersion,
                body.reason,
            )
            .response()
            .toResponse()

    @GetMapping("/runs/{runId}/finalizations")
    fun list(
        actor: Actor,
        @PathVariable runId: UUID,
        @RequestParam(required = false) after: Int?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<PayrollFinalizationResponse> =
        list.execute(actor, runId, after, limit).response().let {
            Page(it.items.map { row -> row.toResponse() }, it.nextCursor)
        }

    @GetMapping("/finalizations/{id}")
    fun get(actor: Actor, @PathVariable id: UUID): PayrollFinalizationDetailsResponse =
        get.execute(actor, id).response().toResponse()
}
