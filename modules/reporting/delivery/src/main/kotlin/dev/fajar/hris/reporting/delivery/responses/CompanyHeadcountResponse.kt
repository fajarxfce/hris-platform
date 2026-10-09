package dev.fajar.hris.reporting.delivery.responses

import java.util.UUID

data class CompanyHeadcountResponse(val companyId: UUID, val counts: HeadcountCountsResponse)
