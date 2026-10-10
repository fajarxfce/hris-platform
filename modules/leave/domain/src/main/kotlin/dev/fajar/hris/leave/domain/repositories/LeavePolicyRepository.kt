package dev.fajar.hris.leave.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import java.time.LocalDate
import java.util.UUID

interface LeavePolicyRepository {
    fun lock(companyId: UUID, shared: Boolean = false): Result<Unit>

    fun find(companyId: UUID, id: UUID): Result<LeaveType?>

    fun effective(companyId: UUID, id: UUID, asOf: LocalDate): Result<LeaveType?>

    fun list(companyId: UUID, asOf: LocalDate, after: String?, limit: Int): Result<Page<LeaveType>>

    fun catalog(
        companyId: UUID,
        active: Boolean?,
        after: String?,
        limit: Int,
    ): Result<Page<LeaveType>>

    fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<LeavePolicyRevision>>

    fun save(
        actor: Actor,
        type: LeaveType,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt>
}
