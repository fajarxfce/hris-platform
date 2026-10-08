package dev.fajar.hris.people.delivery.controllers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import dev.fajar.hris.people.delivery.mappers.*
import dev.fajar.hris.people.delivery.requests.*
import dev.fajar.hris.people.delivery.responses.*
import dev.fajar.hris.people.domain.usecases.*
import java.time.LocalDate
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies/{companyId}/employees")
class PeopleController(
    private val create: CreateEmployee,
    private val revise: ReviseEmployment,
    private val list: ListEmployees,
    private val get: GetEmployee,
    private val history: GetEmploymentHistory,
) {
    @PostMapping
    fun create(
        actor: Actor,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: CreateEmployeeRequest,
    ): MutationResponse =
        create
            .execute(
                actor,
                operationId,
                body.id,
                body.employeeNumber,
                body.person.toProfile(),
                body.terms.toTerms(),
                body.reason,
            )
            .response()
            .toResponse()

    @PostMapping("/{id}/revisions")
    fun revise(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody body: ReviseEmploymentRequest,
    ): MutationResponse =
        revise
            .execute(actor, operationId, id, body.version, body.terms.toTerms(), body.reason)
            .response()
            .toResponse()

    @GetMapping
    fun list(
        actor: Actor,
        @RequestParam asOf: LocalDate,
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(required = false) after: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<EmployeeResponse> =
        list.execute(actor, asOf, query, after, limit).response().let {
            Page(it.items.map { employee -> employee.toResponse() }, it.nextCursor)
        }

    @GetMapping("/{id}")
    fun get(actor: Actor, @PathVariable id: UUID, @RequestParam asOf: LocalDate): EmployeeResponse =
        get.execute(actor, id, asOf).response().toResponse()

    @GetMapping("/{id}/history")
    fun history(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestParam(required = false) after: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): Page<RevisionResponse> =
        history.execute(actor, id, after, limit).response().let {
            Page(it.items.map { revision -> revision.toResponse() }, it.nextCursor)
        }
}
