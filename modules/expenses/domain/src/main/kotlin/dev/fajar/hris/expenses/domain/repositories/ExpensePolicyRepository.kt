package dev.fajar.hris.expenses.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.domain.entities.*
import java.time.LocalDate
import java.util.UUID

interface ExpensePolicyRepository {
    fun lock(companyId: UUID): Result<Unit>

    fun count(companyId: UUID): Result<Int>

    fun find(companyId: UUID, id: UUID): Result<ExpenseCategory?>

    fun effective(companyId: UUID, id: UUID, asOf: LocalDate): Result<ExpenseCategory?>

    fun list(
        companyId: UUID,
        asOf: LocalDate,
        after: String?,
        limit: Int,
    ): Result<Page<ExpenseCategory>>

    fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<ExpenseCategoryRevision>>

    fun save(
        actor: Actor,
        category: ExpenseCategory,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt>
}
