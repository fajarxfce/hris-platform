package dev.fajar.hris.reporting.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.domain.entities.ReportCatalogue
import dev.fajar.hris.reporting.domain.policies.availableReportKinds
import java.time.Clock

class GetReportCatalogue(
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor): Result<ReportCatalogue> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permitted = actor.requirePermission("reports.read")
        if (permitted is Result.Failed) return permitted
        return transactions.run(actor) {
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            identities.access(actor.accountId, company).flatMap { snapshot ->
                val evaluatedAt = clock.instant()
                validateCompanySessionActor(actor, snapshot, evaluatedAt, security).flatMap {
                    current ->
                    current.requirePermission("reports.read").map {
                        ReportCatalogue(company, evaluatedAt, availableReportKinds(current))
                    }
                }
            }
        }
    }
}
