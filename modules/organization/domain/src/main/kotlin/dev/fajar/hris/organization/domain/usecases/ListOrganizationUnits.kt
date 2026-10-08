package dev.fajar.hris.organization.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.organization.domain.entities.*
import dev.fajar.hris.organization.domain.repositories.OrganizationRepository

class ListOrganizationUnits(
    private val units: OrganizationRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(
        actor: Actor,
        kind: UnitKind?,
        after: String?,
        limit: Int,
    ): Result<Page<OrganizationUnit>> =
        actor.requirePermission("company.read").flatMap {
            if (limit !in 1..200 || (after?.length ?: 0) > 80)
                Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
            else
                transactions.run(actor) {
                    units.list(requireNotNull(actor.companyId), kind, after, limit)
                }
        }
}
