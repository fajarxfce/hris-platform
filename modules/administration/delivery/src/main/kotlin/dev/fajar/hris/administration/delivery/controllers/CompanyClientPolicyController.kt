package dev.fajar.hris.administration.delivery.controllers

import dev.fajar.hris.administration.delivery.mappers.toResponse
import dev.fajar.hris.administration.delivery.requests.SaveCompanyClientPolicyRequest
import dev.fajar.hris.administration.delivery.responses.*
import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.usecases.*
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}")
class CompanyClientPolicyController(
    private val current: GetClientPolicy,
    private val revision: GetCompanyClientPolicyRevision,
    private val save: SaveCompanyClientPolicy,
) {
    @GetMapping("/client-policy")
    fun current(actor: Actor): ClientPolicyResponse = current.execute(actor).response().toResponse()

    @GetMapping("/settings/client-policy")
    fun settings(actor: Actor): CompanyClientPolicySettingsResponse =
        CompanyClientPolicySettingsResponse(revision.execute(actor).response()?.toResponse())

    @GetMapping("/settings/client-policy/revisions/{version}")
    fun revision(actor: Actor, @PathVariable version: Long): CompanyClientPolicyRevisionResponse =
        requireNotNull(revision.execute(actor, version).response()).toResponse()

    @PutMapping("/settings/client-policy")
    fun save(
        actor: Actor,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: SaveCompanyClientPolicyRequest,
    ): MutationResponse =
        save
            .execute(
                actor,
                operationId,
                SaveCompanyClientPolicyCommand(
                    body.expectedVersion,
                    body.activateAt,
                    ClientPolicy(
                        body.disabledModules,
                        MinimumClientBuilds(
                            body.minimumBuilds.android,
                            body.minimumBuilds.ios,
                            body.minimumBuilds.web,
                        ),
                        body.maintenance?.let { MaintenanceWindow(it.startsAt, it.endsAt) },
                    ),
                    body.reason,
                ),
            )
            .response()
            .toResponse()
}
