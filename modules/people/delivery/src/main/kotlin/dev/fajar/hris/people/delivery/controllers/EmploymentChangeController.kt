package dev.fajar.hris.people.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import dev.fajar.hris.people.delivery.mappers.toResponse
import dev.fajar.hris.people.delivery.requests.EmploymentCancellationRequest
import dev.fajar.hris.people.delivery.responses.EmploymentRevisionDetailsResponse
import dev.fajar.hris.people.domain.usecases.CancelEmploymentRevision
import dev.fajar.hris.people.domain.usecases.GetEmploymentRevision
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/employees/{id}/revisions")
class EmploymentChangeController(
    private val cancel: CancelEmploymentRevision,
    private val get: GetEmploymentRevision,
) {
    @GetMapping("/{revision}")
    fun get(
        actor: Actor,
        @PathVariable id: UUID,
        @PathVariable revision: Long,
    ): EmploymentRevisionDetailsResponse = get.execute(actor, id, revision).response().toResponse()

    @PostMapping("/{revision}/cancel")
    fun cancel(
        actor: Actor,
        @PathVariable id: UUID,
        @PathVariable revision: Long,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: EmploymentCancellationRequest,
    ): MutationResponse =
        cancel
            .execute(actor, key, id, body.expectedVersion, revision, body.reason)
            .response()
            .toResponse()
}
