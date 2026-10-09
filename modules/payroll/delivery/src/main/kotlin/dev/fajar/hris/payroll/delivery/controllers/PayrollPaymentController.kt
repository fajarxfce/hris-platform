package dev.fajar.hris.payroll.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.payroll.delivery.mappers.*
import dev.fajar.hris.payroll.delivery.requests.*
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.PayrollPaymentBatchStatus
import dev.fajar.hris.payroll.domain.usecases.*
import java.time.LocalDate
import java.util.UUID
import org.springframework.http.*
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/payroll/payments")
class PayrollPaymentController(
    private val prepare: PreparePayrollPaymentBatch,
    private val release: ReleasePayrollPaymentBatch,
    private val cancel: CancelPayrollPaymentBatch,
    private val reconcile: ReconcilePayrollPaymentBatch,
    private val get: GetPayrollPaymentBatch,
    private val list: ListPayrollPaymentBatches,
    private val payables: ListPayrollPayables,
    private val history: GetPayrollPaymentHistory,
    private val results: GetPayrollPaymentResults,
    private val export: GetPayrollPaymentExport,
) {
    @PutMapping("/{id}")
    fun prepare(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PreparePayrollPaymentBatchRequest,
    ): MutationResponse =
        prepare
            .execute(
                actor,
                operationId,
                id,
                body.title,
                body.items.map { it.toInstruction() },
                body.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/{id}/release")
    fun release(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PayrollPaymentActionRequest,
    ): MutationResponse =
        release
            .execute(actor, operationId, id, body.expectedVersion, body.reason)
            .response()
            .toResponse()

    @PostMapping("/{id}/cancel")
    fun cancel(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PayrollPaymentActionRequest,
    ): MutationResponse =
        cancel
            .execute(actor, operationId, id, body.expectedVersion, body.reason)
            .response()
            .toResponse()

    @PostMapping("/{id}/reconcile")
    fun reconcile(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: ReconcilePayrollPaymentBatchRequest,
    ): MutationResponse =
        reconcile
            .execute(
                actor,
                operationId,
                id,
                body.expectedVersion,
                body.results.map { it.toResult() },
                body.reason,
            )
            .response()
            .toResponse()

    @GetMapping("/payables")
    fun payables(
        actor: Actor,
        @RequestParam from: LocalDate,
        @RequestParam until: LocalDate,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<PayrollPayableResponse> =
        payables.execute(actor, from, until, after, limit).response().let {
            Page(it.items.map { item -> item.toResponse() }, it.nextCursor)
        }

    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam from: LocalDate,
        @RequestParam until: LocalDate,
        @RequestParam(required = false) status: PayrollPaymentBatchStatus?,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<PayrollPaymentSummaryResponse> =
        list.execute(actor, from, until, status, after, limit).response().let {
            Page(it.items.map { item -> item.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}")
    fun get(actor: Actor, @PathVariable id: UUID): PayrollPaymentBatchResponse =
        get.execute(actor, id).response().toResponse()

    @GetMapping("/{id}/history")
    fun history(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) after: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<PayrollPaymentActionResponse> =
        history.execute(actor, id, after, limit).response().let {
            Page(it.items.map { item -> item.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}/results")
    fun results(actor: Actor, @PathVariable id: UUID): List<PayrollPaymentReconciliationResponse> =
        results.execute(actor, id).response().map { it.toResponse() }

    @GetMapping("/{id}/export")
    fun export(actor: Actor, @PathVariable id: UUID): ResponseEntity<ByteArray> =
        ResponseEntity.ok()
            .contentType(MediaType("text", "csv", Charsets.UTF_8))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename("payroll-payments-$id.csv")
                    .build()
                    .toString(),
            )
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header("X-Content-Type-Options", "nosniff")
            .body(export.execute(actor, id).response().toPaymentCsv())
}
