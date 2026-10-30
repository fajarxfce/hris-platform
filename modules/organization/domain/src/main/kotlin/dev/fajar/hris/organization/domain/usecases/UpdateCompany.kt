package dev.fajar.hris.organization.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.entities.Company
import dev.fajar.hris.organization.domain.policies.validateCompany
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.util.UUID

class UpdateCompany(
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val journal: ChangeJournalRepository,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        operationId: UUID,
        code: String,
        name: String,
        timezone: String,
        version: Long,
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("company.manage")
        if (access is Result.Failed) return access
        val companyId =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        val company =
            Company(companyId, code.trim().uppercase(), name.trim(), timezone, true, version)
        val validation = validateCompany(company.code, company.name, company.timezone)
        if (validation is Result.Failed) return validation
        if (version < 0) return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_version"))
        return transactions.run(actor) {
            val lock = companies.lock(company.id)
            if (lock is Result.Failed) return@run lock
            val membersGuard = members.lock(company.id, shared = true)
            if (membersGuard is Result.Failed) return@run membersGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val authority = identities.access(actor.accountId, company.id)
            if (authority is Result.Failed) return@run authority
            val identityAccess = (authority as Result.Success).value
            val checked =
                validateCompanySessionActor(actor, identityAccess, clock.instant(), security)
                    .flatMap { it.requirePermission("company.manage") }
            if (checked is Result.Failed) return@run checked
            companies
                .update(actor, operationId, company)
                .flatMap { receipt ->
                    if (receipt.replayed) Result.Success(receipt)
                    else
                        journal
                            .record(
                                actor,
                                ChangeRecord("company", company.id, "organization.company_updated"),
                            )
                            .map { receipt }
                }
                .flatMap { acquired ->
                    validateCompanySessionActor(actor, identityAccess, clock.instant(), security)
                        .map { acquired }
                }
        }
    }
}
