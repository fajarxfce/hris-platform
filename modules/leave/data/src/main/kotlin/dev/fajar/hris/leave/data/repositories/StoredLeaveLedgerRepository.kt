package dev.fajar.hris.leave.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.data.datasources.LeaveLedgerDataSource
import dev.fajar.hris.leave.data.mappers.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.LeaveLedgerRepository
import java.util.UUID

class StoredLeaveLedgerRepository(private val source: LeaveLedgerDataSource) :
    LeaveLedgerRepository {
    override fun lock(companyId: UUID, employeeId: UUID): Result<Unit> = safeDatabaseCall {
        source.lock(companyId, employeeId)
    }

    override fun balance(
        companyId: UUID,
        employeeId: UUID,
        typeId: UUID,
        year: Int,
    ): Result<LeaveBalance> = safeDatabaseCall {
        val row = source.balance(companyId, employeeId, typeId, year)
        LeaveBalance(
            year,
            Math.toIntExact(row.available),
            Math.toIntExact(row.reserved),
            Math.toIntExact(row.consumed),
        )
    }

    override fun entries(
        companyId: UUID,
        employeeId: UUID,
        typeId: UUID,
        year: Int,
        after: UUID?,
        limit: Int,
    ): Result<Page<LeaveLedgerEntry>> = safeDatabaseCall {
        val rows = source.entries(companyId, employeeId, typeId, year, after, limit + 1)
        Page(
            rows.take(limit).map { it.toEntry() },
            if (rows.size > limit) rows[limit - 1].id.toString() else null,
        )
    }

    override fun append(companyId: UUID, entries: List<LeaveLedgerEntry>): Result<Unit> =
        safeDatabaseCall {
            source.append(entries.map { it.toRow(companyId) })
        }
}
