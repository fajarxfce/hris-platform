package dev.fajar.hris.people.delivery.requests

import dev.fajar.hris.people.domain.entities.ContractKind
import dev.fajar.hris.people.domain.entities.EmploymentStatus
import java.time.LocalDate
import java.util.UUID

data class TermsRequest(
    val effectiveFrom: LocalDate,
    val contract: ContractKind,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val status: EmploymentStatus,
    val branchId: UUID? = null,
    val departmentId: UUID? = null,
    val positionId: UUID? = null,
    val costCenterId: UUID? = null,
    val managerId: UUID? = null,
)
