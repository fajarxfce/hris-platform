package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.CompanyRoleTemplate
import dev.fajar.hris.identity.domain.repositories.RoleTemplateRepository
import java.util.UUID

class ListRoleTemplates(
    private val roles: RoleTemplateRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor, after: UUID?, limit: Int): Result<Page<CompanyRoleTemplate>> {
        val access = actor.requirePermission("identity.manage")
        if (access is Result.Failed) return access
        if (limit !in 1..200)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_pagination"))
        return transactions.run(actor) { roles.list(requireNotNull(actor.companyId), after, limit) }
    }
}
