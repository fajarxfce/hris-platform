package dev.fajar.hris.people.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import dev.fajar.hris.people.delivery.mappers.*
import dev.fajar.hris.people.delivery.requests.EmploymentTransferRequest
import dev.fajar.hris.people.delivery.responses.EmploymentTransferResponse
import dev.fajar.hris.people.domain.entities.EmploymentTransferCommand
import dev.fajar.hris.people.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/employees/{id}")
class EmploymentTransferController(
    private val transfer: TransferEmployee,
    private val history: GetEmploymentTransfers,
) {
    @PostMapping("/transfer")
    fun transfer(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") key: UUID,
        @RequestBody body: EmploymentTransferRequest,
    ): MutationResponse =
        transfer
            .execute(
                actor,
                key,
                id,
                EmploymentTransferCommand(
                    body.targetCompanyId,
                    body.targetEmploymentId,
                    body.expectedVersion,
                    body.employeeNumber,
                    body.terms.toTerms(),
                    body.reason,
                ),
            )
            .response()
            .toResponse()

    @GetMapping("/transfers")
    fun history(actor: Actor, @PathVariable id: UUID): List<EmploymentTransferResponse> =
        history.execute(actor, id).response().map { it.toResponse() }
}
