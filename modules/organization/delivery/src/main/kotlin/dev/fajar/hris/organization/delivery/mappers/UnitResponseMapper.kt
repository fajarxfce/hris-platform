package dev.fajar.hris.organization.delivery.mappers

import dev.fajar.hris.organization.delivery.responses.UnitDetailsResponse
import dev.fajar.hris.organization.delivery.responses.UnitResponse
import dev.fajar.hris.organization.domain.entities.OrganizationUnit
import dev.fajar.hris.organization.domain.entities.OrganizationUnitDetails

fun OrganizationUnit.toResponse(): UnitResponse =
    UnitResponse(id, code, name, kind.name, parentId, timezone, active, version)

fun OrganizationUnitDetails.toResponse(): UnitDetailsResponse =
    UnitDetailsResponse(companyId, unit.toResponse(), parent?.toResponse())
