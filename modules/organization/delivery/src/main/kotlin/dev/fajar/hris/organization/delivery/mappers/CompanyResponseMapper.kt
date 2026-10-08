package dev.fajar.hris.organization.delivery.mappers

import dev.fajar.hris.organization.delivery.responses.CompanyResponse
import dev.fajar.hris.organization.domain.entities.Company

fun Company.toResponse(): CompanyResponse =
    CompanyResponse(id, code, name, timezone, active, version)
