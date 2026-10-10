package dev.fajar.hris.organization.delivery.responses

import java.util.UUID

data class UnitDetailsResponse(
    val companyId: UUID,
    val unit: UnitResponse,
    val parent: UnitResponse?,
)
