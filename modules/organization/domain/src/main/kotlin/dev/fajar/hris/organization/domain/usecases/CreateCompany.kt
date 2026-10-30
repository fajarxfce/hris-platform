package dev.fajar.hris.organization.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.identity.domain.policies.validatePlatformSessionActor
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.organization.domain.entities.Company
import dev.fajar.hris.organization.domain.policies.validateCompany
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.util.UUID

class CreateCompany(
    private val companies: CompanyRepository,
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
    ): Result<MutationReceipt> {
        val access = actor.requirePermission("companies.create")
        if (access is Result.Failed) return access
        val company =
            Company(
                UUID.nameUUIDFromBytes("company:${actor.accountId}:$operationId".toByteArray()),
                code.trim().uppercase(),
                name.trim(),
                timezone,
                true,
                0,
            )
        val validation = validateCompany(company.code, company.name, company.timezone)
        if (validation is Result.Failed) return validation
        val scope = actor.copy(companyId = company.id)
        return transactions.run(scope) {
            val guard = identities.lockAccount(actor.accountId, shared = true)
            if (guard is Result.Failed) return@run guard
            val authority = identities.access(actor.accountId, null)
            if (authority is Result.Failed) return@run authority
            val identityAccess = (authority as Result.Success).value
            val checked =
                validatePlatformSessionActor(actor, identityAccess, clock.instant(), security)
                    .flatMap { it.requirePermission("companies.create") }
            if (checked is Result.Failed) return@run checked
            companies
                .create(scope, operationId, company)
                .flatMap { receipt ->
                    if (receipt.replayed) Result.Success(receipt)
                    else
                        identities
                            .grantMembership(
                                actor.accountId,
                                company.id,
                                PermissionCatalog.companyAdministrator,
                            )
                            .flatMap {
                                journal
                                    .record(
                                        scope,
                                        ChangeRecord(
                                            "company",
                                            company.id,
                                            "organization.company_created",
                                        ),
                                    )
                                    .map { receipt }
                            }
                }
                .flatMap { acquired ->
                    validatePlatformSessionActor(actor, identityAccess, clock.instant(), security)
                        .map { acquired }
                }
        }
    }
}
