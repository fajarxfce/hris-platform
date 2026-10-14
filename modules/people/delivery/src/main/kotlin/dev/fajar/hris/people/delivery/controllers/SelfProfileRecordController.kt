package dev.fajar.hris.people.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.response
import dev.fajar.hris.people.delivery.mappers.toResponse
import dev.fajar.hris.people.delivery.responses.*
import dev.fajar.hris.people.domain.usecases.GetSelfProfileRecord
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/employees/me/profiles")
class SelfProfileRecordController(private val get: GetSelfProfileRecord) {
    @GetMapping("/{employeeId}")
    fun record(actor: Actor, @PathVariable employeeId: UUID): SelfProfileRecordResponse =
        get.execute(actor, employeeId).response().let { record ->
            val snapshot = requireNotNull(record.snapshot)
            val profile = snapshot.details.employee.person
            SelfProfileRecordResponse(
                record.id,
                record.version,
                snapshot.timezone,
                snapshot.details.toResponse(),
                PersonProfileResponse(
                    profile.id,
                    snapshot.ownerCompanyId,
                    profile.accountId,
                    profile.legalName,
                    profile.birthDate,
                    profile.nationality,
                    profile.email,
                    snapshot.profileVersion,
                ),
            )
        }
}
