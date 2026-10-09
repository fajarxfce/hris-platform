package dev.fajar.hris.payroll.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.repositories.*
import java.time.*
import java.util.UUID

class GetPayrollFinalization(
    private val finalizations: PayrollFinalizationRepository,
    private val policies: PayrollPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val jobs: JobRepository,
) {
    fun execute(actor: Actor, id: UUID): Result<PayrollFinalizationDetails> {
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (!canReadPayrollFinalization(actor))
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
        return transactions.run(actor) {
            val guard = policies.lock(company, shared = true)
            if (guard is Result.Failed) return@run guard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val checked =
                identities.access(actor.accountId, company).flatMap {
                    validateCompanyCommandActor(actor, it)
                }
            if (checked is Result.Failed) return@run checked
            if (!canReadPayrollFinalization((checked as Result.Success).value))
                return@run Result.Failed(Failure(FailureKind.FORBIDDEN, "access_denied"))
            val found = finalizations.find(company, id)
            if (found is Result.Failed) return@run found
            val finalization =
                (found as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.NOT_FOUND, "payroll_finalization_not_found")
                    )
            jobs.find(company, finalization.jobId).flatMap { job ->
                if (job == null)
                    Result.Failed(Failure(FailureKind.CONFLICT, "payroll_finalization_obsolete"))
                else Result.Success(PayrollFinalizationDetails(finalization, job))
            }
        }
    }
}
