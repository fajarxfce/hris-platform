package dev.fajar.hris.leave.data.datasources

import dev.fajar.hris.schema.tables.records.LeaveAccountsRecord
import dev.fajar.hris.schema.tables.records.LeaveLedgerRecord
import java.util.UUID

interface LeaveLedgerDataSource {
    fun lock(company: UUID, employee: UUID, shared: Boolean)

    fun account(company: UUID, id: UUID): LeaveAccountsRecord?

    fun balance(company: UUID, employee: UUID, type: UUID, year: Int): LeaveAccountsRecord?

    fun entries(
        company: UUID,
        employee: UUID,
        type: UUID,
        year: Int,
        after: UUID?,
        limit: Int,
    ): List<LeaveLedgerRecord>

    fun append(rows: List<LeaveLedgerRecord>)
}
