package dev.fajar.hris.documents.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.documents.delivery.responses.*
import dev.fajar.hris.documents.domain.entities.DocumentClassification
import dev.fajar.hris.documents.domain.usecases.GetSelfDocumentRecord
import dev.fajar.hris.documents.domain.usecases.ListSelfDocumentEmployments
import dev.fajar.hris.documents.domain.usecases.ListSelfDocumentEvidence
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/documents/me")
class SelfDocumentController(
    private val employments: ListSelfDocumentEmployments,
    private val evidence: ListSelfDocumentEvidence,
    private val record: GetSelfDocumentRecord,
) {
    @GetMapping("/evidence/{documentId}")
    fun record(actor: Actor, @PathVariable documentId: UUID): DocumentCatalogRecordResponse =
        record.execute(actor, documentId).response().toResponse()

    @GetMapping("/employments")
    fun employments(
        actor: Actor,
        @Parameter(schema = Schema(minLength = 1, maxLength = 32))
        @RequestParam(required = false)
        after: String?,
        @Parameter(
            schema =
                Schema(
                    type = "integer",
                    format = "int32",
                    minimum = "1",
                    maximum = "50",
                    defaultValue = "20",
                )
        )
        @RequestParam(defaultValue = "20")
        limit: Int,
    ): SelfDocumentDirectoryResponse =
        employments.execute(actor, after, limit).response().toResponse()

    @GetMapping("/evidence")
    fun evidence(
        actor: Actor,
        @RequestParam employmentId: UUID,
        @Parameter(
            description =
                "Published evidence owned by this account. This read does not authorize a later attachment or content download.",
            schema = Schema(allowableValues = ["PERSONAL", "RECEIPT"], defaultValue = "PERSONAL"),
        )
        @RequestParam(defaultValue = "PERSONAL")
        classification: DocumentClassification,
        @RequestParam(required = false) after: UUID?,
        @Parameter(
            schema =
                Schema(
                    type = "integer",
                    format = "int32",
                    minimum = "1",
                    maximum = "50",
                    defaultValue = "20",
                )
        )
        @RequestParam(defaultValue = "20")
        limit: Int,
    ): SelfDocumentEvidenceResponse =
        evidence.execute(actor, employmentId, classification, after, limit).response().toResponse()
}
