package dev.fajar.hris.expenses.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.expenses.delivery.mappers.*
import dev.fajar.hris.expenses.delivery.requests.*
import dev.fajar.hris.expenses.delivery.responses.*
import dev.fajar.hris.expenses.domain.entities.ExpensePaymentBatchStatus
import dev.fajar.hris.expenses.domain.usecases.*
import java.time.LocalDate
import java.util.UUID
import org.springframework.http.*
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/expenses/payments")
class ExpensePaymentController(
    private val prepare: PrepareExpensePaymentBatch,
    private val release: ReleaseExpensePaymentBatch,
    private val cancel: CancelExpensePaymentBatch,
    private val reconcile: ReconcileExpensePaymentBatch,
    private val get: GetExpensePaymentBatch,
    private val list: ListExpensePaymentBatches,
    private val payables: ListExpensePayables,
    private val history: GetExpensePaymentHistory,
    private val results: GetExpensePaymentResults,
    private val export: GetExpensePaymentExport,
) {
    @PutMapping("/{id}")
    fun prepare(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: PrepareExpensePaymentBatchRequest,
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
        @RequestBody body: ExpenseActionRequest,
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
        @RequestBody body: ExpenseActionRequest,
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
        @RequestBody body: ReconcileExpensePaymentBatchRequest,
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
    ): Page<ExpensePayableResponse> =
        payables.execute(actor, from, until, after, limit).response().let {
            Page(it.items.map { item -> item.toResponse() }, it.nextCursor)
        }

    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam from: LocalDate,
        @RequestParam until: LocalDate,
        @RequestParam(required = false) status: ExpensePaymentBatchStatus?,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<ExpensePaymentSummaryResponse> =
        list.execute(actor, from, until, status, after, limit).response().let {
            Page(it.items.map { item -> item.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}")
    fun get(actor: Actor, @PathVariable id: UUID): ExpensePaymentBatchResponse =
        get.execute(actor, id).response().toResponse()

    @GetMapping("/{id}/history")
    fun history(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) after: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<ExpensePaymentActionResponse> =
        history.execute(actor, id, after, limit).response().let {
            Page(it.items.map { item -> item.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}/results")
    fun results(actor: Actor, @PathVariable id: UUID): List<ExpensePaymentReconciliationResponse> =
        results.execute(actor, id).response().map { it.toResponse() }

    @GetMapping("/{id}/export")
    fun export(actor: Actor, @PathVariable id: UUID): ResponseEntity<ByteArray> =
        ResponseEntity.ok()
            .contentType(MediaType("text", "csv", Charsets.UTF_8))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename("expense-payments-$id.csv")
                    .build()
                    .toString(),
            )
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header("X-Content-Type-Options", "nosniff")
            .body(export.execute(actor, id).response().toPaymentCsv())
}
