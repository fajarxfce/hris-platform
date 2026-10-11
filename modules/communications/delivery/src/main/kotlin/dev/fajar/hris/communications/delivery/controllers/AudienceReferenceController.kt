package dev.fajar.hris.communications.delivery.controllers

import dev.fajar.hris.communications.delivery.mappers.toResponse
import dev.fajar.hris.communications.delivery.responses.AudienceReferenceResponse
import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.usecases.ListAudienceReferences
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.response
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/communications/audience-references")
class AudienceReferenceController(private val list: ListAudienceReferences) {
    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam kind: AudienceReferenceKind,
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(required = false) ids: List<UUID>?,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<AudienceReferenceResponse> =
        list
            .execute(actor, AudienceReferenceSearch(kind, query, ids.orEmpty(), after, limit))
            .response()
            .let { Page(it.items.map { row -> row.toResponse() }, it.nextCursor) }
}
