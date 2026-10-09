package dev.fajar.hris.reporting.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.reporting.domain.entities.HeadcountReport
import dev.fajar.hris.reporting.domain.policies.*
import dev.fajar.hris.reporting.domain.repositories.HeadcountReportRepository
import java.time.Clock
import java.time.LocalDate

class GetHeadcountReport(
    private val reports: HeadcountReportRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(actor: Actor, asOf: LocalDate): Result<HeadcountReport> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val permitted = validateHeadcountAccess(actor)
        if (permitted is Result.Failed) return permitted
        val date = validateHeadcountDate(asOf)
        if (date is Result.Failed) return date
        return transactions.run(actor) {
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities
                    .access(actor.accountId, company)
                    .flatMap { validateCompanyCommandActor(actor, it) }
                    .flatMap(::validateHeadcountAccess)
            if (checked is Result.Failed) return@run checked
            val evaluatedAt = clock.instant()
            reports.count(company, asOf).map { HeadcountReport(company, asOf, evaluatedAt, it) }
        }
    }
}
