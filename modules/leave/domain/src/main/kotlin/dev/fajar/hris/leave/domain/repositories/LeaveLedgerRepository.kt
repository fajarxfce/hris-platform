package dev.fajar.hris.leave.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import java.util.UUID

interface LeaveLedgerRepository {
    fun lock(companyId: UUID, employeeId: UUID, shared: Boolean = false): Result<Unit>

    fun account(companyId: UUID, id: UUID): Result<LeaveAccount?>

    fun balance(companyId: UUID, employeeId: UUID, typeId: UUID, year: Int): Result<LeaveBalance>

    fun entries(
        companyId: UUID,
        employeeId: UUID,
        typeId: UUID,
        year: Int,
        after: UUID?,
        limit: Int,
    ): Result<Page<LeaveLedgerEntry>>

    fun append(companyId: UUID, entries: List<LeaveLedgerEntry>): Result<Unit>
}
