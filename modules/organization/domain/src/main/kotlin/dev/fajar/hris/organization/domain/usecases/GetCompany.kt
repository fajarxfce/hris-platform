package dev.fajar.hris.organization.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.organization.domain.entities.Company
import dev.fajar.hris.organization.domain.repositories.CompanyRepository

class GetCompany(
    private val companies: CompanyRepository,
    private val transactions: TransactionRunner,
) {
    fun execute(actor: Actor): Result<Company> =
        actor.requirePermission("company.read").flatMap {
            transactions.run(actor) {
                companies.find(requireNotNull(actor.companyId)).flatMap {
                    if (it == null)
                        Result.Failed(Failure(FailureKind.NOT_FOUND, "company_not_found"))
                    else Result.Success(it)
                }
            }
        }
}
