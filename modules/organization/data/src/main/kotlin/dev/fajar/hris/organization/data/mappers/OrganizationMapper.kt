package dev.fajar.hris.organization.data.mappers

import dev.fajar.hris.organization.domain.entities.*
import dev.fajar.hris.schema.tables.records.OrganizationUnitsRecord
import java.util.UUID

fun OrganizationUnitsRecord.toUnit(): OrganizationUnit =
    OrganizationUnit(id, code, name, UnitKind.valueOf(kind), parentId, timezone, active, version)

fun UnitChange.toRow(companyId: UUID): OrganizationUnitsRecord =
    OrganizationUnitsRecord().also {
        it.companyId = companyId
        it.id = id
        it.code = code
        it.name = name
        it.kind = kind.name
        it.parentId = parentId
        it.timezone = timezone
        it.active = active
        it.version = expectedVersion ?: 0
    }
