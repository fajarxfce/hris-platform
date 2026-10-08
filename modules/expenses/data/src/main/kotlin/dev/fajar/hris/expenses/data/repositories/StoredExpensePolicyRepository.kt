package dev.fajar.hris.expenses.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.expenses.data.datasources.ExpensePolicyDataSource
import dev.fajar.hris.expenses.data.mappers.*
import dev.fajar.hris.expenses.domain.entities.*
import dev.fajar.hris.expenses.domain.repositories.ExpensePolicyRepository
import dev.fajar.hris.schema.tables.records.*
import java.time.LocalDate
import java.util.UUID

class StoredExpensePolicyRepository(private val source: ExpensePolicyDataSource) :
    ExpensePolicyRepository {
    override fun lock(companyId: UUID): Result<Unit> = safeDatabaseCall { source.lock(companyId) }

    override fun count(companyId: UUID): Result<Int> = safeDatabaseCall { source.count(companyId) }

    override fun find(companyId: UUID, id: UUID): Result<ExpenseCategory?> = safeDatabaseCall {
        source.find(companyId, id)?.toCategory()
    }

    override fun effective(companyId: UUID, id: UUID, asOf: LocalDate): Result<ExpenseCategory?> =
        safeDatabaseCall {
            source.effective(companyId, id, asOf)?.toCategory()
        }

    override fun list(
        companyId: UUID,
        asOf: LocalDate,
        after: String?,
        limit: Int,
    ): Result<Page<ExpenseCategory>> = safeDatabaseCall {
        val rows = source.list(companyId, asOf, after, limit + 1)
        Page(
            rows.take(limit).map { it.toCategory() },
            if (rows.size > limit) rows[limit - 1].code else null,
        )
    }

    override fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<ExpenseCategoryRevision>> = safeDatabaseCall {
        val rows = source.history(companyId, id, after, limit + 1)
        Page(
            rows.take(limit).map { it.toHistory() },
            if (rows.size > limit) rows[limit - 1].revision.revision.toString() else null,
        )
    }

    override fun save(
        actor: Actor,
        category: ExpenseCategory,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                val company = requireNotNull(actor.companyId)
                if (expectedVersion == null) {
                    source.insert(
                        ExpenseCategoriesRecord().also {
                            it.companyId = company
                            it.id = category.id
                            it.code = category.code
                            it.version = 0
                        }
                    )
                    0L
                } else source.advance(company, category.id, expectedVersion)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.append(
                        ExpenseCategoryRevisionsRecord().also {
                            it.companyId = requireNotNull(actor.companyId)
                            it.categoryId = category.id
                            it.revision = version
                            it.effectiveFrom = category.effectiveFrom
                            it.name = category.policy.name
                            it.maximumLineAmount = category.policy.maximumLineAmount
                            it.maximumClaimAmount = category.policy.maximumClaimAmount
                            it.receiptRequired = category.policy.receiptRequired
                            it.costCenterRequired = category.policy.costCenterRequired
                            it.maximumAgeDays = category.policy.maximumAgeDays
                            it.allowedContracts =
                                category.policy.allowedContracts
                                    .map { contract -> contract.name }
                                    .sorted()
                                    .toTypedArray()
                            it.active = category.active
                            it.actorId = actor.accountId
                            it.reason = reason
                        }
                    )
                    MutationReceipt(category.id, version)
                }
            }
}
