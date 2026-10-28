package dev.fajar.hris.reporting.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.SavedReportFilterRepository
import java.time.Clock

class ListSavedReportFilters(
    private val filters: SavedReportFilterRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(actor: Actor): Result<List<SavedReportFilter>> {
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
            val loadedAccess = identities.access(actor.accountId, company)
            if (loadedAccess is Result.Failed) return@run loadedAccess
            val snapshot = (loadedAccess as Result.Success).value
            val access =
                validateCompanySessionActor(actor, snapshot, clock.instant(), security).flatMap {
                    it.requirePermission("reports.read")
                }
            if (access is Result.Failed) return@run access
            filters.list(company, actor.accountId).flatMap { value ->
                validateCompanySessionActor(actor, snapshot, clock.instant(), security).map {
                    value
                }
            }
        }
    }
}
