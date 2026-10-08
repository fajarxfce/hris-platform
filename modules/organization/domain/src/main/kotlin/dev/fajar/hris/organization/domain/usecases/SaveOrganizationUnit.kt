package dev.fajar.hris.organization.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.organization.domain.entities.*
import dev.fajar.hris.organization.domain.policies.validateUnit
import dev.fajar.hris.organization.domain.repositories.OrganizationRepository
import java.util.UUID

class SaveOrganizationUnit(
    private val units: OrganizationRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, operationId: UUID, request: UnitChange): Result<MutationReceipt> {
        val access = actor.requirePermission("company.manage")
        if (access is Result.Failed) return access
        val change =
            request.copy(code = request.code.trim().uppercase(), name = request.name.trim())
        val key =
            OperationKey(
                "organization.unit_save",
                operationId,
                listOf(
                    change.id.toString(),
                    change.code,
                    change.name,
                    change.kind.name,
                    change.parentId?.toString(),
                    change.timezone,
                    change.active.toString(),
                    change.expectedVersion?.toString(),
                ),
            )
        val company = requireNotNull(actor.companyId)
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            (replay as Result.Success).value?.let {
                return@run Result.Success(it)
            }
            val lock = units.lockStructure(company)
            if (lock is Result.Failed) return@run lock
            val existing = units.find(company, change.id)
            if (existing is Result.Failed) return@run existing
            val current = (existing as Result.Success).value
            if (current != null && current.kind != change.kind)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "unit_kind_immutable"))
            val parents =
                change.parentId?.let { units.ancestors(company, it) } ?: Result.Success(emptyList())
            if (parents is Result.Failed) return@run parents
            val ancestry = (parents as Result.Success).value
            val valid = validateUnit(change, ancestry.firstOrNull(), ancestry)
            if (valid is Result.Failed) return@run valid
            units.save(company, change).flatMap { receipt ->
                operations
                    .record(actor, key, receipt)
                    .flatMap {
                        journal.record(
                            actor,
                            ChangeRecord("organization_unit", receipt.id, "organization.unit_saved"),
                        )
                    }
                    .map { receipt }
            }
        }
    }
}
