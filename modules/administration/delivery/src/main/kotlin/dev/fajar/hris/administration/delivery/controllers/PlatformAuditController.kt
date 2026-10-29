package dev.fajar.hris.administration.delivery.controllers

import dev.fajar.hris.administration.delivery.responses.*
import dev.fajar.hris.administration.domain.entities.AuditSearch
import dev.fajar.hris.administration.domain.usecases.GetPlatformAccountAccessAudit
import dev.fajar.hris.administration.domain.usecases.SearchPlatformAuditEvents
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import java.time.Instant
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/administration/audit-events")
class PlatformAuditController(
    private val search: SearchPlatformAuditEvents,
    private val review: GetPlatformAccountAccessAudit,
) {
    @GetMapping("/{eventId}/account-access")
    fun accountAccess(
        actor: Actor,
        @PathVariable eventId: UUID,
    ): PlatformAccountAccessAuditResponse {
        val result = review.execute(actor, eventId).response()
        return PlatformAccountAccessAuditResponse(
            result.id,
            result.accountId,
            result.active,
            result.permissions,
            result.reason,
        )
    }

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
    ): PlatformAuditPageResponse {
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
        return PlatformAuditPageResponse(
            page.from,
            page.until,
            page.evaluatedAt,
            page.items.map {
                PlatformAuditEventResponse(
                    it.id,
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
