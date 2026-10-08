package dev.fajar.hris.documents.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.documents.delivery.requests.*
import dev.fajar.hris.documents.delivery.responses.*
import dev.fajar.hris.documents.domain.entities.StartDocumentUploadCommand
import dev.fajar.hris.documents.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/documents")
class DocumentController(
    private val start: StartDocumentUpload,
    private val validate: StartDocumentValidation,
    private val upload: UploadDocumentChunk,
    private val cancel: CancelDocumentUpload,
    private val get: GetDocument,
    private val getRevision: GetDocumentRevision,
    private val list: ListDocuments,
    private val history: GetDocumentRevisions,
    private val validationHistory: GetDocumentValidationAttempts,
) {
    @PostMapping("/uploads")
    fun start(
        actor: Actor,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: StartDocumentUploadRequest,
    ): MutationResponse =
        start
            .execute(
                actor,
                operationId,
                StartDocumentUploadCommand(
                    input.documentId,
                    input.revisionId,
                    input.employmentId,
                    input.title,
                    input.classification,
                    input.expectedDocumentVersion,
                    input.fileName,
                    input.mediaType,
                    input.size,
                    input.sha256,
                    input.reason,
                ),
            )
            .response()
            .toResponse()

    @PostMapping("/revisions/{revisionId}/chunks", consumes = ["application/octet-stream"])
    fun chunk(
        actor: Actor,
        @PathVariable revisionId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestHeader("Upload-Offset") offset: Long,
        @RequestHeader("Upload-Checksum-Sha256") sha256: String,
        @RequestBody bytes: ByteArray,
    ): MutationResponse =
        upload
            .execute(actor, operationId, revisionId, offset, sha256, bytes)
            .response()
            .toResponse()

    @PostMapping("/revisions/{revisionId}/cancel")
    fun cancel(
        actor: Actor,
        @PathVariable revisionId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: CancelDocumentUploadRequest,
    ): MutationResponse =
        cancel
            .execute(actor, operationId, revisionId, input.expectedVersion, input.reason)
            .response()
            .toResponse()

    @PostMapping("/revisions/{revisionId}/validate")
    fun validate(
        actor: Actor,
        @PathVariable revisionId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody input: ValidateDocumentRequest,
    ): MutationResponse =
        validate
            .execute(actor, operationId, revisionId, input.expectedVersion, input.reason)
            .response()
            .toResponse()

    @GetMapping("/{documentId}")
    fun get(actor: Actor, @PathVariable documentId: UUID) =
        get.execute(actor, documentId).response().toResponse()

    @GetMapping("/revisions/{revisionId}")
    fun revision(actor: Actor, @PathVariable revisionId: UUID) =
        getRevision.execute(actor, revisionId).response().toResponse()

    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam employmentId: UUID,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<DocumentResponse> {
        val page = list.execute(actor, employmentId, after, limit).response()
        return Page(page.items.map { it.toResponse() }, page.nextCursor)
    }

    @GetMapping("/revisions/{revisionId}/validation-attempts")
    fun validationAttempts(actor: Actor, @PathVariable revisionId: UUID) =
        validationHistory.execute(actor, revisionId).response().map { it.toResponse() }

    @GetMapping("/{documentId}/revisions")
    fun history(
        actor: Actor,
        @PathVariable documentId: UUID,
        @RequestParam(required = false) after: Int?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<DocumentRevisionResponse> {
        val page = history.execute(actor, documentId, after, limit).response()
        return Page(page.items.map { it.toResponse() }, page.nextCursor)
    }
}
