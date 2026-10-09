package dev.fajar.hris.identity.delivery.responses

import java.util.UUID

data class CompanyAccessResponse(val companyId: UUID, val permissions: List<String>)
