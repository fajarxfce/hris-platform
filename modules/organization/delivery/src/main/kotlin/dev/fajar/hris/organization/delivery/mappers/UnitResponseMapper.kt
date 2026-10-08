package dev.fajar.hris.organization.delivery.mappers

import dev.fajar.hris.organization.delivery.responses.UnitResponse
import dev.fajar.hris.organization.domain.entities.OrganizationUnit

fun OrganizationUnit.toResponse(): UnitResponse =
    UnitResponse(id, code, name, kind.name, parentId, timezone, active, version)
