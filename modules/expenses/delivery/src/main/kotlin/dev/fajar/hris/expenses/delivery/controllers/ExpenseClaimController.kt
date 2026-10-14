package dev.fajar.hris.expenses.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.expenses.delivery.mappers.*
import dev.fajar.hris.expenses.delivery.requests.*
import dev.fajar.hris.expenses.delivery.responses.*
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimReadScope
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimStatus
import dev.fajar.hris.expenses.domain.usecases.*
import java.time.LocalDate
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/expenses/claims")
class ExpenseClaimController(
    private val payments: GetExpenseClaimPayments,
    private val save: SaveExpenseDraft,
    private val submit: SubmitExpenseClaim,
    private val submissions: ListExpenseSubmissions,
    private val cancel: CancelExpenseDraft,
    private val get: GetExpenseClaim,
    private val list: ListExpenseClaims,
    private val drafts: GetExpenseDrafts,
    private val history: GetExpenseClaimHistory,
) {
    @GetMapping("/{id}/payments")
    fun payments(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "AUTHORIZED") scope: ExpenseClaimReadScope,
    ): List<ExpensePaymentProgressResponse> =
        payments.execute(actor, id, scope).response().attempts.map { it.toResponse() }

    @GetMapping("/{id}/payment-progress")
    fun paymentProgress(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "SELF") scope: ExpenseClaimReadScope,
    ): ExpenseClaimPaymentsResponse = payments.execute(actor, id, scope).response().toResponse()

    @PutMapping("/{id}/draft")
    fun save(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: SaveExpenseDraftRequest,
    ): MutationResponse =
        save.execute(actor, operationId, body.toCommand(id)).response().toResponse()

    @PostMapping("/{id}/submit")
    fun submit(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: SubmitExpenseClaimRequest,
    ): MutationResponse =
        submit
            .execute(actor, operationId, id, body.submissionId, body.expectedVersion, body.reason)
            .response()
            .toResponse()

    @GetMapping("/{id}/submissions")
    fun submissions(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) after: Int?,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "AUTHORIZED") scope: ExpenseClaimReadScope,
    ): Page<ExpenseSubmissionSummaryResponse> =
        submissions.execute(actor, id, after, limit, scope).response().let {
            Page(it.items.map { submission -> submission.toResponse() }, it.nextCursor)
        }

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

    @GetMapping("/{id}")
    fun get(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "AUTHORIZED") scope: ExpenseClaimReadScope,
    ): ExpenseClaimResponse = get.execute(actor, id, scope).response().toResponse()

    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) employmentId: UUID?,
        @RequestParam(required = false) status: ExpenseClaimStatus?,
        @RequestParam from: LocalDate,
        @RequestParam until: LocalDate,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "AUTHORIZED") scope: ExpenseClaimReadScope,
    ): Page<ExpenseClaimSummaryResponse> =
        list.execute(actor, employmentId, status, from, until, after, limit, scope).response().let {
            Page(it.items.map { claim -> claim.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}/drafts")
    fun drafts(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) after: Int?,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "AUTHORIZED") scope: ExpenseClaimReadScope,
    ): Page<ExpenseDraftResponse> =
        drafts.execute(actor, id, after, limit, scope).response().let {
            Page(it.items.map { draft -> draft.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}/history")
    fun history(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) after: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "AUTHORIZED") scope: ExpenseClaimReadScope,
    ): Page<ExpenseClaimChangeResponse> =
        history.execute(actor, id, after, limit, scope).response().let {
            Page(it.items.map { change -> change.toResponse() }, it.nextCursor)
        }
}
