package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.domain.entities.*
import java.time.YearMonth
import java.util.UUID

interface PayrollPolicyRepository {
    fun lock(companyId: UUID, shared: Boolean = false): Result<Unit>

    fun current(companyId: UUID): Result<PayrollPolicy?>

    fun effective(companyId: UUID, month: YearMonth): Result<PayrollPolicy?>

    fun history(companyId: UUID, after: Long?, limit: Int): Result<Page<PayrollPolicyRevision>>

    fun save(
        actor: Actor,
        policy: PayrollPolicy,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt>
}
