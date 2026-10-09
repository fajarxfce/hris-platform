package dev.fajar.hris.administration.delivery.controllers

import dev.fajar.hris.administration.delivery.responses.*
import dev.fajar.hris.administration.domain.entities.AuditSearch
import dev.fajar.hris.administration.domain.usecases.SearchAuditEvents
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import java.time.Instant
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/audit-events")
class AuditController(private val search: SearchAuditEvents) {
    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) until: Instant?,
        @RequestParam(required = false) cursor: UUID?,
        @RequestParam(required = false) actorId: UUID?,
        @RequestParam(required = false) resourceType: String?,
        @RequestParam(required = false) resourceId: UUID?,
        @RequestParam(required = false) action: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): AuditPageResponse {
        val page =
            search
                .execute(
                    actor,
                    AuditSearch(
                        from,
                        until,
                        cursor,
                        actorId,
                        resourceType,
                        resourceId,
                        action,
                        limit,
                    ),
                )
                .response()
        return AuditPageResponse(
            page.companyId,
            page.from,
            page.until,
            page.evaluatedAt,
            page.items.map {
                AuditEventResponse(
                    it.id,
                    it.companyId,
                    it.actorId,
                    it.resourceType,
                    it.resourceId,
                    it.action,
                    it.correlationId,
                    it.recordedAt,
                )
            },
            page.nextCursor,
        )
    }
}
