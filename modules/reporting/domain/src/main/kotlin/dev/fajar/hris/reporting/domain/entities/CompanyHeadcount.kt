package dev.fajar.hris.reporting.domain.entities

import java.util.UUID

data class CompanyHeadcount(val companyId: UUID, val counts: HeadcountCounts)
