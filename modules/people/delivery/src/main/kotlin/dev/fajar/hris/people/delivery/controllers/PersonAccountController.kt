package dev.fajar.hris.people.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.people.delivery.requests.PersonAccountBindingRequest
import dev.fajar.hris.people.domain.usecases.BindPersonAccount
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/employees/{employeeId}/account-link")
class PersonAccountController(private val bind: BindPersonAccount) {
    @PostMapping
    fun bind(
        actor: Actor,
        @PathVariable employeeId: UUID,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: PersonAccountBindingRequest,
    ): MutationResponse =
        bind
            .execute(actor, key, employeeId, body.accountId, body.expectedVersion, body.reason)
            .response()
            .toResponse()
}
