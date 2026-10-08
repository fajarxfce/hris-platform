package dev.fajar.hris.expenses.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import dev.fajar.hris.expenses.delivery.mappers.*
import dev.fajar.hris.expenses.delivery.requests.ExpenseActionRequest
import dev.fajar.hris.expenses.delivery.responses.ExpenseSubmissionResponse
import dev.fajar.hris.expenses.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/expenses/submissions")
class ExpenseSubmissionController(
    private val get: GetExpenseSubmission,
    private val withdraw: WithdrawExpenseSubmission,
) {
    @GetMapping("/{id}")
    fun get(actor: Actor, @PathVariable id: UUID): ExpenseSubmissionResponse =
        get.execute(actor, id).response().toResponse()

    @PostMapping("/{id}/withdraw")
    fun withdraw(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: ExpenseActionRequest,
    ): MutationResponse =
        withdraw
            .execute(actor, operationId, id, body.expectedVersion, body.reason)
            .response()
            .toResponse()
}
