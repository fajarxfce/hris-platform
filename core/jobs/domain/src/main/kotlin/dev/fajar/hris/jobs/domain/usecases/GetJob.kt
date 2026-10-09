package dev.fajar.hris.jobs.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.util.UUID

class GetJob(
    private val jobs: JobRepository,
    private val transactions: TransactionRunner,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
) {
    fun execute(actor: Actor, id: UUID): Result<BackgroundJob> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val access =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (access is Result.Failed) return@run access
            val current = (access as Result.Success).value
            jobs.find(company, id).flatMap { job ->
                if (
                    job == null ||
                        (job.request.actorId != actor.accountId &&
                            "jobs.read" !in current.permissions)
                )
                    Result.Failed(Failure(FailureKind.NOT_FOUND, "job_not_found"))
                else Result.Success(job)
            }
        }
    }
}
