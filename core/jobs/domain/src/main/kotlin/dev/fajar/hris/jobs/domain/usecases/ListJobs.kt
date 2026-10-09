package dev.fajar.hris.jobs.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.policies.availableJobActions
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID

class ListJobs(
    private val jobs: JobRepository,
    private val transactions: TransactionRunner,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        size: Int,
        beforeAt: Instant?,
        beforeId: UUID?,
    ): Result<JobDetailsPage> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (size !in 1..200 || (beforeAt == null) != (beforeId == null))
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_pagination"))
        return transactions.run(actor) {
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val access =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanySessionActor(actor, it, clock.instant(), security)
                }
            if (access is Result.Failed) return@run access
            val current = (access as Result.Success).value
            jobs
                .list(
                    company,
                    if ("jobs.read" in current.permissions) null else actor.accountId,
                    size,
                    beforeAt,
                    beforeId,
                )
                .map { page ->
                    JobDetailsPage(
                        page.items.map { JobDetails(it, availableJobActions(current, it)) },
                        page.nextCreatedAt,
                        page.nextId,
                    )
                }
        }
    }
}
