package dev.fajar.hris.workforce.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import dev.fajar.hris.workforce.delivery.mappers.*
import dev.fajar.hris.workforce.delivery.requests.*
import dev.fajar.hris.workforce.delivery.responses.RosterBulkReviewResponse
import dev.fajar.hris.workforce.domain.usecases.ApplyRosterBulk
import dev.fajar.hris.workforce.domain.usecases.ReviewRosterBulk
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/workforce/roster/bulk")
class RosterBulkController(
    private val review: ReviewRosterBulk,
    private val apply: ApplyRosterBulk,
) {
    @PostMapping("/review")
    fun review(actor: Actor, @RequestBody body: RosterBulkReviewRequest): RosterBulkReviewResponse =
        review.execute(actor, body.toSelection()).response().toResponse()

    @PostMapping
    fun apply(
        actor: Actor,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: RosterBulkApplyRequest,
    ): MutationResponse =
        apply
            .execute(
                actor,
                operationId,
                body.selection.toSelection(),
                body.reviewRevision,
                body.reason,
            )
            .response()
            .toResponse()
}
