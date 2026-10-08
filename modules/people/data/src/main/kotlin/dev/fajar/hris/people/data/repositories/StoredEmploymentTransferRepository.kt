package dev.fajar.hris.people.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.data.datasources.EmploymentTransferDataSource
import dev.fajar.hris.people.data.mappers.*
import dev.fajar.hris.people.domain.entities.EmploymentTransfer
import dev.fajar.hris.people.domain.repositories.EmploymentTransferRepository
import java.util.UUID

class StoredEmploymentTransferRepository(private val source: EmploymentTransferDataSource) :
    EmploymentTransferRepository {
    override fun record(transfer: EmploymentTransfer): Result<Unit> = safeDatabaseCall {
        source.insert(transfer.toRecord())
    }

    override fun forEmployee(companyId: UUID, id: UUID): Result<List<EmploymentTransfer>> =
        safeDatabaseCall {
            source.forEmployee(companyId, id).map { it.toTransfer() }
        }
}
