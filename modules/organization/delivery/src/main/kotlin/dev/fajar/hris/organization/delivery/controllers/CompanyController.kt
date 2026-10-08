package dev.fajar.hris.organization.delivery.controllers

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.http.*
import dev.fajar.hris.organization.delivery.mappers.toResponse
import dev.fajar.hris.organization.delivery.requests.*
import dev.fajar.hris.organization.delivery.responses.CompanyResponse
import dev.fajar.hris.organization.domain.usecases.*
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
class CompanyController(
    private val create: CreateCompany,
    private val get: GetCompany,
    private val update: UpdateCompany,
) {
    @PostMapping("/api/v1/companies")
    fun create(
        actor: Actor,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody request: CreateCompanyRequest,
    ): MutationResponse =
        create
            .execute(actor, operationId, request.code, request.name, request.timezone)
            .response()
            .toResponse()

    @GetMapping("/api/v1/companies/{companyId}")
    fun get(actor: Actor): CompanyResponse = get.execute(actor).response().toResponse()

    @PutMapping("/api/v1/companies/{companyId}")
    fun update(
        actor: Actor,
        @RequestHeader("Idempotency-Key") operationId: UUID,
        @RequestBody request: UpdateCompanyRequest,
    ): MutationResponse =
        update
            .execute(
                actor,
                operationId,
                request.code,
                request.name,
                request.timezone,
                request.version,
            )
            .response()
            .toResponse()
}
