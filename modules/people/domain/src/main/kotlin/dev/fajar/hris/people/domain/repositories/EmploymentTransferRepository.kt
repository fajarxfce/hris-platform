package dev.fajar.hris.people.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.EmploymentTransfer
import java.util.UUID

interface EmploymentTransferRepository {
    fun record(transfer: EmploymentTransfer): Result<Unit>

    fun forEmployee(companyId: UUID, id: UUID): Result<List<EmploymentTransfer>>
}
