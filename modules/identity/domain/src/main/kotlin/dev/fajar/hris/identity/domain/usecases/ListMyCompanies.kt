package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.core.domain.TransactionRunner
import dev.fajar.hris.core.domain.map
import dev.fajar.hris.identity.domain.entities.CompanyMembership
import dev.fajar.hris.identity.domain.repositories.IdentityRepository

class ListMyCompanies(
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor): Result<List<CompanyMembership>> =
        transactions.run(actor.copy(companyId = null)) {
            identities.memberships(actor.accountId).map { values ->
                values.filter { it.memberActive && it.companyActive }
            }
        }
}
