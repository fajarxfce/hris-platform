package dev.fajar.hris.documents.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.documents.delivery.requests.*
import dev.fajar.hris.documents.delivery.responses.*
import dev.fajar.hris.documents.domain.entities.DocumentRetentionAction
import dev.fajar.hris.documents.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/documents")
class DocumentRetentionController(
    private val savePolicy: SaveDocumentRetentionPolicy,
    private val policies: GetDocumentRetentionPolicies,
    private val policyHistory: GetDocumentRetentionPolicyHistory,
    private val change: ChangeDocumentRetention,
    private val get: GetDocumentRetention,
    private val history: GetDocumentRetentionHistory,
) {
    @PostMapping("/retention-policies")
    fun savePolicy(
        actor: Actor,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: SaveDocumentRetentionPolicyRequest,
    ) =
        savePolicy
            .execute(
                actor,
                operationId,
                input.policyId,
                input.classification,
                input.expectedVersion,
                input.retentionDays,
                input.reason,
            )
            .response()
            .toResponse()

    @GetMapping("/retention-policies")
    fun policies(actor: Actor) = policies.execute(actor).response().map { it.toResponse() }

    @GetMapping("/retention-policies/{policyId}/history")
    fun policyHistory(
        actor: Actor,
        @PathVariable policyId: UUID,
        @RequestParam(required = false) after: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<DocumentRetentionPolicyResponse> {
        val page = policyHistory.execute(actor, policyId, after, limit).response()
        return Page(page.items.map { it.toResponse() }, page.nextCursor)
    }

    @GetMapping("/{documentId}/retention")
    fun get(actor: Actor, @PathVariable documentId: UUID) =
        get.execute(actor, documentId).response().toResponse()

    @GetMapping("/{documentId}/retention/history")
    fun history(
        actor: Actor,
        @PathVariable documentId: UUID,
        @RequestParam(required = false) after: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<DocumentRetentionChangeResponse> {
        val page = history.execute(actor, documentId, after, limit).response()
        return Page(page.items.map { it.toResponse() }, page.nextCursor)
    }

    @PostMapping("/{documentId}/retention/archive")
    fun archive(
        actor: Actor,
        @PathVariable documentId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: ChangeDocumentRetentionRequest,
    ) =
        change
            .execute(
                actor,
                operationId,
                documentId,
                DocumentRetentionAction.ARCHIVE,
                input.expectedVersion,
                input.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/{documentId}/retention/restore")
    fun restore(
        actor: Actor,
        @PathVariable documentId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: ChangeDocumentRetentionRequest,
    ) =
        change
            .execute(
                actor,
                operationId,
                documentId,
                DocumentRetentionAction.RESTORE,
                input.expectedVersion,
                input.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/{documentId}/retention/hold")
    fun hold(
        actor: Actor,
        @PathVariable documentId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: ChangeDocumentRetentionRequest,
    ) =
        change
            .execute(
                actor,
                operationId,
                documentId,
                DocumentRetentionAction.PLACE_HOLD,
                input.expectedVersion,
                input.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/{documentId}/retention/release-hold")
    fun releaseHold(
        actor: Actor,
        @PathVariable documentId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: ChangeDocumentRetentionRequest,
    ) =
        change
            .execute(
                actor,
                operationId,
                documentId,
                DocumentRetentionAction.RELEASE_HOLD,
                input.expectedVersion,
                input.reason,
            )
            .response()
            .toResponse()
}
