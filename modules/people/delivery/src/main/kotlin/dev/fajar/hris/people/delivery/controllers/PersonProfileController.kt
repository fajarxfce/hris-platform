package dev.fajar.hris.people.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.people.delivery.requests.PersonProfileRequest
import dev.fajar.hris.people.delivery.responses.*
import dev.fajar.hris.people.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/employees/{employeeId}/profile")
class PersonProfileController(
    private val getProfile: GetPersonProfile,
    private val saveProfile: SavePersonProfile,
    private val history: GetPersonProfileHistory,
) {
    @GetMapping
    fun get(actor: Actor, @PathVariable employeeId: UUID): PersonProfileResponse {
        val value = getProfile.execute(actor, employeeId).response()
        return PersonProfileResponse(
            value.profile.id,
            value.ownerCompanyId,
            value.profile.accountId,
            value.profile.legalName,
            value.profile.birthDate,
            value.profile.nationality,
            value.profile.email,
            value.version,
        )
    }

    @PutMapping
    fun save(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody request: PersonProfileRequest,
    ): MutationResponse =
        saveProfile
            .execute(
                actor,
                operationId,
                employeeId,
                request.expectedVersion,
                request.legalName,
                request.birthDate,
                request.nationality,
                request.email,
                request.reason,
            )
            .response()
            .toResponse()

    @GetMapping("/history")
    fun history(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @RequestParam(required = false) after: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<PersonProfileRevisionResponse> {
        val page = history.execute(actor, employeeId, after, limit).response()
        return Page(
            page.items.map {
                PersonProfileRevisionResponse(
                    it.revision,
                    it.profile.legalName,
                    it.profile.birthDate,
                    it.profile.nationality,
                    it.profile.email,
                    it.actorId,
                    it.reason,
                    it.recordedAt,
                )
            },
            page.nextCursor,
        )
    }
}
