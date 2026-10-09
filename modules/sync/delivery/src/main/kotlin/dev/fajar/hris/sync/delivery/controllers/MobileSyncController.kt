package dev.fajar.hris.sync.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.sync.delivery.mappers.toResponse
import dev.fajar.hris.sync.delivery.responses.*
import dev.fajar.hris.sync.domain.usecases.GetMobileSyncBootstrap
import dev.fajar.hris.sync.domain.usecases.GetMobileSyncChanges
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/sync")
class MobileSyncController(
    private val bootstrap: GetMobileSyncBootstrap,
    private val changes: GetMobileSyncChanges,
) {
    @GetMapping("/bootstrap")
    fun bootstrap(
        actor: Actor,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "100") limit: Int,
    ): SyncBootstrapResponse = bootstrap.execute(actor, cursor, limit).response().toResponse()

    @GetMapping("/changes")
    fun changes(
        actor: Actor,
        @RequestParam cursor: String,
        @RequestParam(defaultValue = "100") limit: Int,
    ): SyncChangesResponse = changes.execute(actor, cursor, limit).response().toResponse()
}
