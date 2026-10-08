package dev.fajar.hris.storage.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.storage.delivery.requests.RetryObjectCleanupRequest
import dev.fajar.hris.storage.delivery.responses.ObjectCleanupResponse
import dev.fajar.hris.storage.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/storage-cleanup")
class ObjectCleanupController(
    private val list: ListObjectCleanup,
    private val retry: RetryObjectCleanup,
) {
    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<ObjectCleanupResponse> =
        list.execute(actor, after, limit).response().let { page ->
            Page(
                page.items.map {
                    ObjectCleanupResponse(
                        it.request.id,
                        it.request.resourceId,
                        it.status.name,
                        it.attempts,
                        it.request.deleteAfter,
                        it.failureCode,
                        it.version,
                    )
                },
                page.nextCursor,
            )
        }

    @PostMapping("/{id}/retry")
    fun retry(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: RetryObjectCleanupRequest,
    ): MutationResponse =
        retry.execute(actor, key, id, body.expectedVersion, body.reason).response().toResponse()
}
