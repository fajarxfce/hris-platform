package dev.fajar.hris.collaboration.delivery.controllers

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.usecases.SyncNativeCollaborationSummaries
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.identity.delivery.security.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@SessionTransport(SessionKind.NATIVE)
@RequestMapping("/api/v1/companies/{companyId}/collaboration/direct-conversations/summary-sync")
class NativeCollaborationSummarySyncController(private val sync: SyncNativeCollaborationSummaries) {
    @GetMapping("/bootstrap")
    fun bootstrap(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "30") limit: Int,
    ): CollaborationSummaryBootstrap =
        sync
            .bootstrap(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                client.version,
                cursor,
                limit,
            )
            .response()

    @GetMapping("/changes")
    fun changes(
        actor: Actor,
        @AuthenticationPrincipal identity: NativeIdentity,
        client: ClientRequest,
        @RequestParam cursor: String,
        @RequestParam(defaultValue = "30") limit: Int,
    ): CollaborationSummaryChanges =
        sync
            .changes(
                actor,
                identity.sessionId,
                identity.sessionVersion,
                client.version,
                cursor,
                limit,
            )
            .response()
}
