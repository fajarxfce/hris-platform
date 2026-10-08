package dev.fajar.hris.leave.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import java.time.LocalDate
import java.util.UUID

interface LeavePolicyRepository {
    fun lock(companyId: UUID): Result<Unit>

    fun find(companyId: UUID, id: UUID): Result<LeaveType?>

    fun effective(companyId: UUID, id: UUID, asOf: LocalDate): Result<LeaveType?>

    fun list(companyId: UUID, asOf: LocalDate, after: String?, limit: Int): Result<Page<LeaveType>>

    fun save(
        actor: Actor,
        type: LeaveType,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt>
}
