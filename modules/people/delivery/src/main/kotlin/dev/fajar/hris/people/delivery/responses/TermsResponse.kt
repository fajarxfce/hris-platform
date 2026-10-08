package dev.fajar.hris.people.delivery.responses

import java.time.LocalDate
import java.util.UUID

data class TermsResponse(
    val effectiveFrom: LocalDate,
    val contract: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val status: String,
    val branchId: UUID?,
    val departmentId: UUID?,
    val positionId: UUID?,
    val costCenterId: UUID?,
    val managerId: UUID?,
)
