package dev.fajar.hris.leave.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.domain.entities.*
import java.util.UUID

interface LeaveCarryExpiryRepository {
    fun find(companyId: UUID, id: UUID, lock: Boolean = false): Result<LeaveCarryExpiry?>

    fun forJob(companyId: UUID, jobId: UUID, lock: Boolean = false): Result<LeaveCarryExpiry?>

    fun list(companyId: UUID, after: UUID?, limit: Int): Result<Page<LeaveCarryExpiry>>

    fun create(
        companyId: UUID,
        expiry: LeaveCarryExpiry,
        attempt: LeaveCarryExpiryAttempt,
    ): Result<Unit>

    fun next(companyId: UUID, id: UUID, after: Int): Result<LeaveBatchTarget?>

    fun completed(companyId: UUID, id: UUID): Result<Int>

    fun outcome(companyId: UUID, id: UUID, outcome: LeaveCarryExpiryOutcome): Result<Unit>

    fun results(
        companyId: UUID,
        id: UUID,
        after: Int?,
        limit: Int,
    ): Result<Page<LeaveCarryExpiryOutcome>>

    fun attempts(companyId: UUID, id: UUID): Result<List<LeaveCarryExpiryAttempt>>

    fun resume(
        companyId: UUID,
        expiry: LeaveCarryExpiry,
        attempt: LeaveCarryExpiryAttempt,
    ): Result<MutationReceipt>
}
