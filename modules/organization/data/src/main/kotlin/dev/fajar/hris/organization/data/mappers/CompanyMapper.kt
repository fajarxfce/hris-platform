package dev.fajar.hris.organization.data.mappers

import dev.fajar.hris.organization.domain.entities.Company
import dev.fajar.hris.schema.tables.records.CompaniesRecord

fun CompaniesRecord.toCompany(): Company = Company(id, code, name, timezone, active, version)
